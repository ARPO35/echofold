package com.echofold.asr.engine

import com.echofold.asr.model.VersionNumber
import com.echofold.asr.repository.InstallationState
import com.echofold.asr.repository.ModelRepository
import com.echofold.core.asr.engine.AsrConfig
import com.echofold.core.asr.engine.AsrEngine
import com.echofold.core.asr.engine.AsrEngineException
import java.io.File

/**
 * 按 [AsrConfig] 的会话固定 [com.echofold.core.asr.engine.ModelRef] 创建引擎。
 *
 * 创建前重新核对：安装是否存在、是否 pending-delete、身份（modelId/version/sha256）是否一致、
 * ABI 是否被设备支持、运行时版本是否满足清单下限。任一项不满足都抛 [AsrEngineException]，
 * 不尝试别的安装或远端 ASR。创建成功后引擎绑定的目录不再重新解析，保证会话期间“固定版本”。
 *
 * 租约由调用方在创建前申请（实时会话、重识别任务各自持有）；pending-delete 的安装只有在
 * 仍被活跃租约引用时才能创建引擎，以确保已开始的会话/任务可以完成。
 */
class AsrEngineProvider(
    private val repository: ModelRepository,
    private val runtimeProvider: () -> SherpaRuntime,
    /** 当前 sherpa-onnx 运行时版本，如 `1.10.0`。 */
    private val runtimeVersion: String,
    /** 设备支持的 ABI，按优先级降序（通常来自 `Build.SUPPORTED_ABIS`）。 */
    private val deviceAbis: List<String>,
    private val resampler: com.echofold.core.asr.engine.Resampler? = null,
    private val onFinalText: (String) -> Unit = {},
    /** 每帧解码后同步交付非空且变化的 partial；端点 final 后重新开始去重。 */
    private val onPartialText: (String) -> Unit = {},
) {
    init {
        require(runtimeVersion.isNotBlank()) { "runtimeVersion 不能为空白字符串" }
        require(deviceAbis.isNotEmpty()) { "deviceAbis 不能为空" }
    }

    /**
     * 创建并初始化引擎。
     *
     * @throws AsrEngineException 模型缺失、身份/ABI/运行时版本不符或初始化失败
     */
    fun create(config: AsrConfig): AsrEngine {
        val installed = repository.find(config.model.installationId)
            ?: throw AsrEngineException("模型安装不存在：${config.model.installationId}")
        if (installed.state != InstallationState.ACTIVE) {
            // pending-delete 只阻止**新**租约；已持有租约的会话仍需用固定模型完成重识别。
            val heldLeases = repository.leaseManager().refCount(config.model.installationId)
            if (heldLeases == 0) {
                throw AsrEngineException("模型安装处于 pending-delete 且没有活跃租约，不能用于新会话：${config.model.installationId}")
            }
        }
        if (installed.manifest.modelId != config.model.modelId ||
            installed.manifest.version != config.model.modelVersion ||
            installed.manifest.sha256 != config.model.sha256
        ) {
            throw AsrEngineException(
                "模型身份不一致：索引 ${installed.manifest.modelId}@${installed.manifest.version}/${installed.manifest.sha256}，" +
                    "会话固定 ${config.model.modelId}@${config.model.modelVersion}/${config.model.sha256}",
            )
        }
        if (installed.abi.none { it in deviceAbis }) {
            throw AsrEngineException("模型 ABI 不兼容：模型支持 ${installed.abi}，设备 $deviceAbis")
        }
        val runtimeCompatible =
            try {
                VersionNumber.atLeast(runtimeVersion, installed.runtimeMinVersion)
            } catch (e: IllegalArgumentException) {
                throw AsrEngineException("运行时版本号非法：$runtimeVersion", e)
            }
        if (!runtimeCompatible) {
            throw AsrEngineException("运行时版本过低：$runtimeVersion < ${installed.runtimeMinVersion}")
        }
        val directory = File(installed.directoryPath)
        val engine =
            SherpaOnnxEngine(
                runtime = runtimeProvider(),
                modelDirectoryResolver = { ref -> if (ref == config.model) directory else null },
                resampler = resampler,
                onFinalText = onFinalText,
                onPartialText = onPartialText,
            )
        try {
            engine.initialize(config)
        } catch (e: AsrEngineException) {
            engine.close()
            throw e
        } catch (e: Exception) {
            engine.close()
            throw AsrEngineException("引擎初始化失败：${e.message ?: e.javaClass.simpleName}", e)
        }
        return engine
    }

    companion object {
        /** 使用设备 `Build.SUPPORTED_ABIS` 构造 provider（生产入口）。 */
        fun forDevice(
            repository: ModelRepository,
            runtimeProvider: () -> SherpaRuntime,
            runtimeVersion: String,
            resampler: com.echofold.core.asr.engine.Resampler? = null,
            onFinalText: (String) -> Unit = {},
            onPartialText: (String) -> Unit = {},
        ): AsrEngineProvider =
            AsrEngineProvider(
                repository = repository,
                runtimeProvider = runtimeProvider,
                runtimeVersion = runtimeVersion,
                deviceAbis = android.os.Build.SUPPORTED_ABIS.toList(),
                resampler = resampler,
                onFinalText = onFinalText,
                onPartialText = onPartialText,
            )
    }
}
