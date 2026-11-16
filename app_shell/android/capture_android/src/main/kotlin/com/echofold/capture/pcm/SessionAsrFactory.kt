package com.echofold.capture.pcm

import com.echofold.core.asr.engine.AsrConfig
import com.echofold.core.asr.engine.AsrEngineException
import com.echofold.core.asr.engine.ModelRef
import com.echofold.core.asr.engine.PcmFrame

/** 服务持有的固定模型实时 ASR 会话。 */
data class SessionAsrBinding(
    val model: ModelRef,
    val config: AsrConfig,
    val sink: SessionAsrSink,
    val release: () -> Unit,
) {
    init { require(config.model == model) }
}

/** 在录音工作线程、AudioRecord 启动前调用，绝不占用采集实时车道。 */
fun interface SessionAsrFactory {
    fun create(
        sessionId: String,
        onPcmFrame: (PcmFrame) -> Unit,
        onFinalText: (String) -> Unit,
        onPartialText: (String) -> Unit,
        onFailure: (AsrEngineException) -> Unit,
    ): SessionAsrBinding
}
