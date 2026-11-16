package com.echofold.app

/**
 * 模型预热状态机（无 Android 依赖，便于单测）。
 *
 * 预热失败不静默降级：失败原因保留在 [Phase.Failed]，供 status() 查询；
 * 也不改变 available 判定——是否可实时转写仍由模型安装状态决定。
 */
internal class ModelWarmupState {
    sealed interface Phase {
        /** 尚未预热：无模型或启动后未触发。 */
        data class Idle(val message: String?) : Phase

        /** 预热进行中。 */
        data class Loading(val installationId: String) : Phase

        /** 已就绪：进程内权重可用。 */
        data class Ready(val installationId: String) : Phase

        /** 预热失败：原因可查，可重试。 */
        data class Failed(val installationId: String?, val message: String) : Phase
    }

    @Volatile
    var phase: Phase = Phase.Idle(null)
        private set

    fun markIdle(message: String?) {
        phase = Phase.Idle(message)
    }

    fun markLoading(installationId: String) {
        phase = Phase.Loading(installationId)
    }

    fun markReady(installationId: String) {
        phase = Phase.Ready(installationId)
    }

    fun markFailed(installationId: String?, message: String) {
        phase = Phase.Failed(installationId, message)
    }

    /** status() 用的可选 warmState 字段；只增不改现有键。 */
    fun toStatusMap(): Map<String, Any?> = when (val p = phase) {
        is Phase.Idle -> mapOf("state" to "idle", "message" to p.message)
        is Phase.Loading -> mapOf("state" to "loading", "installationId" to p.installationId)
        is Phase.Ready -> mapOf("state" to "ready", "installationId" to p.installationId)
        is Phase.Failed -> mapOf(
            "state" to "failed",
            "installationId" to p.installationId,
            "message" to p.message,
        )
    }
}
