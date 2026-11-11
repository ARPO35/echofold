package com.echofold.core.chunk

import com.echofold.core.model.AudioChunk
import com.echofold.core.model.ChunkState
import com.echofold.core.model.DispatchState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/** 换块模式。 */
enum class SwitchMode {
    /** 强制换块：先封存当前块，再按完整占位协议开下一个块（自动分块边界使用）。 */
    ROTATE,

    /** 幂等确保：已有 `OPEN` 块时直接合并返回该块，不产生第二个活动块（首次启动、暂停后继续使用）。 */
    ENSURE,
}

/** 换块结果。 */
sealed interface SwitchOutcome {
    /**
     * 块已 `OPEN` 且分发已恢复。
     *
     * [mergedIntoExisting] 为 `true` 表示请求合并到已存在的 `OPEN` 块（[SwitchMode.ENSURE] 的幂等路径）。
     */
    data class Opened(val chunk: AudioChunk, val mergedIntoExisting: Boolean = false) : SwitchOutcome

    /**
     * 重入请求：调用来自正在执行换块的同一线程/临界区，请求已合并到进行中的换块，
     * 其结果就是本请求的结果；不另开一次换块，也不阻塞等待自身。
     */
    data object MergedIntoInFlight : SwitchOutcome

    /** 占位协议失败：分发保持 `BLOCKED`，序号已消耗不可复用。 */
    data class PlaceholderFailed(
        val stage: PlaceholderStage,
        val chunk: AudioChunk?,
        val resourceEpoch: Long,
        val cause: Throwable?,
    ) : SwitchOutcome

    /** 旧块封存失败：分发保持 `BLOCKED`，旧块为 `SEAL_FAILED`。 */
    data class SealFailed(
        val stage: SealStage,
        val chunk: AudioChunk,
        val resourceEpoch: Long,
        val cause: Throwable?,
    ) : SwitchOutcome

    /** 空间不足，已按顺序封存当前块并进入低存储暂停；下一块占位未被创建。 */
    data class LowStoragePaused(val chunk: AudioChunk?) : SwitchOutcome

    /** 低存储停止序列失败：分发保持 `BLOCKED`，不自动恢复。 */
    data class LowStorageFailed(
        val stage: LowStorageStage,
        val chunk: AudioChunk?,
        val cause: Throwable?,
    ) : SwitchOutcome

    /**
     * 会话里存在未经本状态机接管的活动块（进程重启遗留的 `PLACEHOLDER/OPEN/SEALING`）。
     *
     * 不得再开新块，必须先由 [RecoveryScanner] 裁决该块，否则会出现两个活动块。
     */
    data class BlockedByUnsealedChunk(val chunk: AudioChunk) : SwitchOutcome
}

/**
 * 单会话换块状态机：所有换块与封存请求的唯一入口。
 *
 * 并发模型：
 * - 所有换块/封存请求经同一个 [Mutex] 串行执行，同一会话同一时刻最多一个 `PLACEHOLDER` 或 `OPEN` 块；
 * - 进行中的换块被并发请求“合并”：后来者不重复执行，而是 `await` 同一个 [CompletableDeferred]，
 *   因此拿到的是**同一个结果对象**；
 * - 同一临界区内的重入请求（例如编码器工厂里再次请求换块）不会再排队等自己，直接返回
 *   [SwitchOutcome.MergedIntoInFlight]，结果由外层换块统一决定。
 *
 * 序号模型：`nextSeq` 只增不减，初值由仓储已有块推导（[RecoveryScanner.nextSeqAfter]）；
 * 每次尝试占位前先消耗序号，因此占位/初始化/封存失败都不会导致序号被复用。
 *
 * 顺序模型：每次换块的固定顺序为 `BLOCKED 切点 → 空间预留 → 封存旧块 → 占位协议（独占文件/原子占位/
 * 编码器初始化/OPEN）→ RUNNING`。任一阶段失败都停在 `BLOCKED`，不向旧块追加、不复用序号。
 */
class ChunkSwitcher(
    private val sessionId: String,
    private val repository: ChunkRepository,
    private val fileSystem: ChunkFileSystem,
    private val encoderFactory: (AudioChunk) -> ChunkEncoderHandle,
    private val budget: SpaceBudget? = null,
    private val reserveBytes: Long = 0L,
    private val resourceEpoch: () -> Long = { 1L },
    private val nowUtcMs: () -> Long = { System.currentTimeMillis() },
    private val drainPending: () -> Unit = {},
    private val flushMetadata: () -> Unit = {},
) {
    private val gate = ChunkDispatchGate(DispatchState.BLOCKED)
    private val placeholderProtocol = PlaceholderProtocol(repository, fileSystem, gate, nowUtcMs)
    private val opMutex = Mutex()
    private val registryLock = Any()

    /** 进行中的换块；并发请求在此合并。 */
    private var inFlight: CompletableDeferred<SwitchOutcome>? = null

    /** 标记“当前线程正在执行换块临界区”，用于识别重入请求并避免自锁。 */
    private val executingSwitch = ThreadLocal<Boolean>()
    private val coalescedJoins = AtomicInteger(0)

    private var nextSeq: Int = RecoveryScanner.nextSeqAfter(repository.listBySession(sessionId))
    private var activeChunk: AudioChunk? = null
    private var activePipeline: SealingPipeline? = null

    /** 当前活动块已预留的字节数；封存或失败后清零。 */
    private var activeHold: Long = 0L

    /** 最近一次封存结果：重复/并发封存请求合并到它，不会二次改名。 */
    private var lastSeal: SealOutcome? = null

    private val lowStorageGuard: LowStorageGuard? = budget?.let { space ->
        LowStorageGuard(
            budget = space,
            gate = gate,
            sealer = { sealCurrentInternal() },
            flushMetadata = flushMetadata,
        )
    }

    /** 分发放行状态：`BLOCKED` 期间任何 [write] 都会被拒绝。 */
    val dispatchState: DispatchState get() = gate.state

    /** 当前活动块（`PLACEHOLDER/OPEN/SEALING`），没有则为 `null`。 */
    val activeChunkOrNull: AudioChunk? get() = activeChunk

    /** 下一个将被消耗的序号，只增不减。 */
    val nextChunkSeq: Int get() = nextSeq

    /** 已合并到进行中换块的并发请求数（诊断用）。 */
    val coalescedRequestCount: Int get() = coalescedJoins.get()

    /**
     * 强制换块：封存当前块并按完整占位协议开下一个块。
     *
     * 并发调用合并为同一次换块并返回同一结果；失败时保持 `BLOCKED`。
     */
    suspend fun switchToNextChunk(): SwitchOutcome = runSwitch(SwitchMode.ROTATE)

    /**
     * 幂等确保存在 `OPEN` 块：已有 `OPEN` 块时返回该块（`mergedIntoExisting = true`），否则执行一次换块。
     */
    suspend fun ensureOpenChunk(): SwitchOutcome = runSwitch(SwitchMode.ENSURE)

    /**
     * 封存当前块（暂停、结束、换会话前使用）。
     *
     * 与换块共用同一把串行锁；封存后分发保持 `BLOCKED`。没有活动块时返回 `null`。
     */
    suspend fun sealCurrent(): SealOutcome? = opMutex.withLock {
        gate.block()
        val outcome = sealCurrentInternal() ?: lastSeal
        if (outcome != null) {
            budget?.release(activeHold)
            activeHold = 0L
        }
        outcome
    }

    /**
     * 写入 PCM。
     *
     * 分发 `BLOCKED` 或没有 `OPEN` 块时抛 [IllegalStateException]；`SEALING` 期间由
     * [SealingPipeline.write] 抛出同样的异常，保证切点后不会有数据落到正在封存的块里。
     */
    fun write(bytes: ByteArray, frameIndex: Long) {
        if (gate.state == DispatchState.BLOCKED) {
            throw IllegalStateException("分发处于 BLOCKED，拒绝写入帧 $frameIndex")
        }
        val pipeline = activePipeline ?: throw IllegalStateException("没有 OPEN 块，拒绝写入帧 $frameIndex")
        pipeline.write(bytes, frameIndex)
    }

    private suspend fun runSwitch(mode: SwitchMode): SwitchOutcome {
        // 重入：同一线程已在换块临界区内，等待自己会死锁，故直接声明合并。
        if (executingSwitch.get() == true) return SwitchOutcome.MergedIntoInFlight

        synchronized(registryLock) { inFlight }?.let { running ->
            coalescedJoins.incrementAndGet()
            return running.await()
        }

        val mine = CompletableDeferred<SwitchOutcome>()
        val leader = synchronized(registryLock) {
            if (inFlight != null) {
                false
            } else {
                inFlight = mine
                true
            }
        }
        if (!leader) {
            coalescedJoins.incrementAndGet()
            val other = synchronized(registryLock) { inFlight }
            return other?.await() ?: SwitchOutcome.MergedIntoInFlight
        }

        try {
            val outcome = opMutex.withLock {
                executingSwitch.set(true)
                try {
                    executeSwitch(mode)
                } finally {
                    executingSwitch.remove()
                }
            }
            // 先撤下进行中登记再完成：避免后续请求误合并到已经结束的结果。
            synchronized(registryLock) { if (inFlight === mine) inFlight = null }
            mine.complete(outcome)
            return outcome
        } catch (t: Throwable) {
            synchronized(registryLock) { if (inFlight === mine) inFlight = null }
            mine.completeExceptionally(t)
            throw t
        }
    }

    private fun executeSwitch(mode: SwitchMode): SwitchOutcome {
        gate.block()

        val current = activeChunk
        if (mode == SwitchMode.ENSURE && current != null && current.state == ChunkState.OPEN) {
            gate.resume()
            return SwitchOutcome.Opened(current, mergedIntoExisting = true)
        }

        // 未经本状态机接管的活动块（重启遗留）必须先由恢复扫描裁决，否则会出现两个活动块。
        repository.findActive(sessionId)?.let { unsealed ->
            if (unsealed.id != current?.id) return SwitchOutcome.BlockedByUnsealedChunk(unsealed)
        }

        // 空间预留先于新块占位：失败即进入低存储序列，绝不创建下一块。
        if (budget != null && !budget.reserve(reserveBytes)) {
            val guard = checkNotNull(lowStorageGuard) { "配置了空间预算却没有低存储保护序列" }
            return when (val outcome = guard.enter(current, activeHold)) {
                is LowStorageOutcome.Paused -> {
                    activeHold = 0L
                    SwitchOutcome.LowStoragePaused(outcome.chunk)
                }

                is LowStorageOutcome.Failed -> {
                    activeHold = 0L
                    SwitchOutcome.LowStorageFailed(outcome.stage, outcome.chunk, outcome.cause)
                }
            }
        }

        // 封存旧块：切点前已接受的 PCM 按原块归属消费完毕后才允许登记新块。
        if (current != null) {
            val sealed = sealCurrentInternal()
            budget?.release(activeHold)
            activeHold = 0L
            if (sealed is SealOutcome.Failed) {
                budget?.release(reserveBytes)
                return SwitchOutcome.SealFailed(sealed.stage, sealed.chunk, sealed.resourceEpoch, sealed.cause)
            }
        }

        // 序号一经尝试即消耗：失败也不回退，杜绝复用。
        val seq = nextSeq
        nextSeq = seq + 1
        val epoch = resourceEpoch()
        val outcome = placeholderProtocol.open(
            sessionId = sessionId,
            chunkSeq = seq,
            tempPath = ChunkPaths.tempPath(sessionId, seq),
            resourceEpoch = epoch,
            encoderFactory = encoderFactory,
        )
        return when (outcome) {
            is PlaceholderOutcome.Open -> {
                activeChunk = outcome.chunk
                activePipeline = SealingPipeline(
                    chunk = outcome.chunk,
                    repository = repository,
                    fileSystem = fileSystem,
                    encoder = outcome.encoder,
                    drainPending = drainPending,
                    nowUtcMs = nowUtcMs,
                )
                activeHold = reserveBytes
                gate.resume()
                SwitchOutcome.Opened(outcome.chunk)
            }

            is PlaceholderOutcome.Failed -> {
                activeChunk = null
                activePipeline = null
                budget?.release(reserveBytes)
                SwitchOutcome.PlaceholderFailed(outcome.stage, outcome.chunk, outcome.resourceEpoch, outcome.cause)
            }
        }
    }

    /** 封存当前块并释放流水线；没有活动块时返回 `null`。 */
    private fun sealCurrentInternal(): SealOutcome? {
        val pipeline = activePipeline ?: return null
        val outcome = pipeline.seal()
        activePipeline = null
        activeChunk = null
        lastSeal = outcome
        return outcome
    }
}
