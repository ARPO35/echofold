package com.echofold.core.session

import com.echofold.core.model.DispatchState
import com.echofold.core.model.DispatchState.BLOCKED
import com.echofold.core.model.DispatchState.RUNNING
import com.echofold.core.model.SessionStatus
import com.echofold.core.model.SessionStatus.FAILED
import com.echofold.core.model.SessionStatus.IDLE
import com.echofold.core.model.SessionStatus.LOW_STORAGE_PAUSED
import com.echofold.core.model.SessionStatus.PAUSED
import com.echofold.core.model.SessionStatus.RECORDING
import com.echofold.core.model.SessionStatus.STOPPING
import com.echofold.core.time.ClockSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 串行状态机：幂等命令、非法转换、状态不变式与事件顺序。 */
class SessionStateMachineTest {
    private val sessionId = "session-1"

    private class FixedClock(
        private val utcMs: Long = 1_700_000_000_000L,
        private val monoNs: Long = 1_000_000_000L,
    ) : ClockSource {
        override fun utcNowMs(): Long = utcMs

        override fun monoNowNs(): Long = monoNs
    }

    private fun machine(
        status: SessionStatus = IDLE,
        dispatch: DispatchState = BLOCKED,
        ledgerCapacity: Int = SessionStateMachine.DEFAULT_LEDGER_CAPACITY,
        eventLog: SessionEventLog = SessionEventLog(),
    ) = SessionStateMachine(
        sessionId = sessionId,
        initialStatus = status,
        initialDispatchState = dispatch,
        ledgerCapacity = ledgerCapacity,
        clock = FixedClock(),
        eventLog = eventLog,
    )

    @Test
    fun `Start 从 IDLE 进入 RECORDING 并恢复分发`() = runTest {
        val machine = machine()

        val result = machine.execute(SessionCommand.Start("cmd-start"))

        val applied = assertInstanceOf(SessionCommandResult.Applied::class.java, result)
        assertEquals(RECORDING, applied.status)
        assertEquals(RUNNING, applied.dispatchState)
        assertEquals(SessionSnapshot(sessionId, RECORDING, RUNNING), machine.snapshot())

        val event = machine.eventLog.latest!!
        assertEquals("$sessionId:state:0", event.eventId)
        assertEquals(IDLE, event.previousStatus)
        assertEquals(BLOCKED, event.previousDispatchState)
        assertEquals(RECORDING, event.status)
        assertEquals(RUNNING, event.dispatchState)
        assertEquals(SessionCommandKind.START, event.commandKind)
        assertEquals("cmd-start", event.commandId)
    }

    @Test
    fun `暂停与继续保持状态不变式`() = runTest {
        val machine = machine()
        machine.execute(SessionCommand.Start("s"))

        val paused = machine.execute(SessionCommand.Pause("p"))
        assertEquals(PAUSED, paused.status)
        assertEquals(BLOCKED, paused.dispatchState)

        val resumed = machine.execute(SessionCommand.Resume("r"))
        assertEquals(RECORDING, resumed.status)
        assertEquals(RUNNING, resumed.dispatchState)

        val statuses = machine.eventLog.events.map { it.previousStatus to it.status }
        assertEquals(
            listOf(IDLE to RECORDING, RECORDING to PAUSED, PAUSED to RECORDING),
            statuses,
        )
        machine.eventLog.events.forEach {
            assertTrue(SessionStateMachine.invariantHolds(it.status, it.dispatchState))
        }
    }

    @Test
    fun `Stop 进入 STOPPING 且分发必须 BLOCKED`() = runTest {
        val machine = machine()
        machine.execute(SessionCommand.Start("s"))

        val result = machine.execute(SessionCommand.Stop("stop"))

        assertEquals(STOPPING, result.status)
        assertEquals(BLOCKED, result.dispatchState)
        assertTrue(SessionStateMachine.invariantHolds(result.status, result.dispatchState))
    }

    @Test
    fun `低存储停止进入 LOW_STORAGE_PAUSED 且不自动恢复分发`() = runTest {
        val machine = machine()
        machine.execute(SessionCommand.Start("s"))

        val stopped = machine.execute(SessionCommand.LowStorageStop("low"))
        assertEquals(LOW_STORAGE_PAUSED, stopped.status)
        assertEquals(BLOCKED, stopped.dispatchState)

        // 重启后仍须用户明确继续。
        machine.execute(SessionCommand.Resume("resume"))
        assertEquals(RECORDING, machine.snapshot().status)
        assertEquals(RUNNING, machine.snapshot().dispatchState)
    }

    @Test
    fun `重复 commandId 返回首次结果且不重复副作用`() = runTest {
        val machine = machine()

        val first = machine.execute(SessionCommand.Start("start-once"))
        val second = machine.execute(SessionCommand.Start("start-once"))

        assertSame(first, second, "重放必须原样返回首次结果")
        assertEquals(1, machine.eventLog.size)
        assertEquals(RECORDING, machine.snapshot().status)

        machine.execute(SessionCommand.Pause("pause-once"))
        machine.execute(SessionCommand.Resume("resume-once"))
        val eventsBeforeReplay = machine.eventLog.size

        val replayedPause = machine.execute(SessionCommand.Pause("pause-once"))

        assertEquals(PAUSED, replayedPause.status, "返回的是首次执行时的结果快照")
        assertEquals(RECORDING, machine.snapshot().status, "重放不得把当前状态改回 PAUSED")
        assertEquals(eventsBeforeReplay, machine.eventLog.size)
    }

    @Test
    fun `被拒绝的 commandId 幂等返回同一拒绝结果`() = runTest {
        val machine = machine()

        val first = machine.execute(SessionCommand.Resume("resume-idle"))
        val second = machine.execute(SessionCommand.Resume("resume-idle"))

        val rejected = assertInstanceOf(SessionCommandResult.Rejected::class.java, first)
        assertSame(first, second)
        assertEquals(IDLE, rejected.status)
        assertEquals(BLOCKED, rejected.dispatchState)
        assertEquals(0, machine.eventLog.size)
        assertEquals(IDLE, machine.snapshot().status)
    }

    @Test
    fun `非法转换返回 Rejected 且状态不变`() = runTest {
        val machine = machine()

        val illegal = listOf(
            SessionCommand.Pause("p"),
            SessionCommand.Resume("r"),
            SessionCommand.Stop("stop"),
            SessionCommand.LowStorageStop("low"),
            SessionCommand.RetryPlaceholder("retry"),
            SessionCommand.MarkFailed("fail", "不应从空闲态失败"),
        )

        illegal.forEach { command ->
            val result = machine.execute(command)
            assertInstanceOf(SessionCommandResult.Rejected::class.java, result, command.kind.wireName)
            assertEquals(IDLE, result.status)
            assertEquals(BLOCKED, result.dispatchState)
        }

        assertEquals(SessionSnapshot(sessionId, IDLE, BLOCKED), machine.snapshot())
        assertEquals(0, machine.eventLog.size)
    }

    @Test
    fun `暂停后再次 Pause 与录音中 Start 都被拒绝`() = runTest {
        val machine = machine()
        machine.execute(SessionCommand.Start("s"))
        machine.execute(SessionCommand.Pause("p"))

        val duplicatePause = machine.execute(SessionCommand.Pause("p-again"))
        val startAgain = machine.execute(SessionCommand.Start("s-again"))

        assertInstanceOf(SessionCommandResult.Rejected::class.java, duplicatePause)
        assertInstanceOf(SessionCommandResult.Rejected::class.java, startAgain)
        assertEquals(PAUSED, machine.snapshot().status)
        assertEquals(BLOCKED, machine.snapshot().dispatchState)
        assertEquals(2, machine.eventLog.size)
    }

    @Test
    fun `RetryPlaceholder 在录音或暂停状态恢复分发`() = runTest {
        val paused = machine()
        paused.execute(SessionCommand.Start("s"))
        paused.execute(SessionCommand.Pause("p"))

        val retried = paused.execute(SessionCommand.RetryPlaceholder("retry"))
        assertEquals(RECORDING, retried.status)
        assertEquals(RUNNING, retried.dispatchState)

        val stopping = machine()
        stopping.execute(SessionCommand.Start("s2"))
        stopping.execute(SessionCommand.Stop("stop"))
        assertInstanceOf(
            SessionCommandResult.Rejected::class.java,
            stopping.execute(SessionCommand.RetryPlaceholder("retry-stopping")),
        )
        assertEquals(STOPPING, stopping.snapshot().status)
    }

    @Test
    fun `MarkFailed 从活动状态进入 FAILED 并保留原因`() = runTest {
        val machine = machine()
        machine.execute(SessionCommand.Start("s"))

        val result = machine.execute(SessionCommand.MarkFailed("fail-1", "AAC 编码器初始化失败"))
        val applied = assertInstanceOf(SessionCommandResult.Applied::class.java, result)

        assertEquals(FAILED, applied.status)
        assertEquals(BLOCKED, applied.dispatchState)
        assertEquals("AAC 编码器初始化失败", applied.event.reason)
        assertEquals(FAILED, machine.snapshot().status)

        assertInstanceOf(
            SessionCommandResult.Rejected::class.java,
            machine.execute(SessionCommand.MarkFailed("fail-2", "再次失败")),
        )
        assertEquals(FAILED, machine.snapshot().status)
    }

    @Test
    fun `所有状态都满足分发不变式`() = runTest {
        assertTrue(SessionStateMachine.invariantHolds(RECORDING, RUNNING))
        assertTrue(SessionStateMachine.invariantHolds(RECORDING, BLOCKED))

        listOf(IDLE, PAUSED, STOPPING, LOW_STORAGE_PAUSED, FAILED).forEach { status ->
            assertTrue(SessionStateMachine.invariantHolds(status, BLOCKED), "$status 必须允许 BLOCKED")
            assertFalse(SessionStateMachine.invariantHolds(status, RUNNING), "$status 不得为 RUNNING")
        }

        assertThrows(IllegalArgumentException::class.java) {
            machine(status = IDLE, dispatch = RUNNING)
        }
        assertThrows(IllegalArgumentException::class.java) {
            machine(status = STOPPING, dispatch = RUNNING)
        }
    }

    @Test
    fun `事件 ID 稳定递增且重复命令不追加事件`() = runTest {
        val machine = machine()
        val ids = mutableListOf<String>()

        ids += machine.execute(SessionCommand.Start("s")).eventIdOrEmpty()
        ids += machine.execute(SessionCommand.Pause("p")).eventIdOrEmpty()
        machine.execute(SessionCommand.Pause("p"))
        ids += machine.execute(SessionCommand.Resume("r")).eventIdOrEmpty()

        assertEquals(
            listOf("$sessionId:state:0", "$sessionId:state:1", "$sessionId:state:2"),
            ids,
        )
        assertEquals(ids, machine.eventLog.events.map { it.eventId })
    }

    @Test
    fun `Ledger 容量有界且旧 commandId 淘汰后重放仍受状态保护`() = runTest {
        val machine = machine(ledgerCapacity = 2)

        machine.execute(SessionCommand.Start("c1"))
        machine.execute(SessionCommand.Pause("c2"))
        machine.execute(SessionCommand.Resume("c3"))

        assertEquals(2, machine.ledgerSize)
        assertFalse(machine.isCommandRecorded("c1"))

        val eventsBefore = machine.eventLog.size
        val replayedStart = machine.execute(SessionCommand.Start("c1"))

        assertInstanceOf(SessionCommandResult.Rejected::class.java, replayedStart)
        assertEquals(RECORDING, machine.snapshot().status)
        assertEquals(eventsBefore, machine.eventLog.size)
    }

    @Test
    fun `并发命令被 Mutex 串行化且只应用一次`() = runTest {
        val machine = machine(status = RECORDING, dispatch = RUNNING)

        val results = coroutineScope {
            (1..16).map { index ->
                async(Dispatchers.Default) {
                    machine.execute(SessionCommand.Pause("pause-$index"))
                }
            }.awaitAll()
        }

        assertEquals(1, results.count { it is SessionCommandResult.Applied })
        assertEquals(15, results.count { it is SessionCommandResult.Rejected })
        assertEquals(PAUSED, machine.snapshot().status)
        assertEquals(1, machine.eventLog.size)
    }

    private fun SessionCommandResult.eventIdOrEmpty(): String =
        (this as? SessionCommandResult.Applied)?.event?.eventId.orEmpty()
}
