package com.echofold.core.rerecognition

import com.echofold.core.asr.engine.AsrEngine

/**
 * 离线重识别执行器：驱动会话固定引擎把一段本地证据音频识别成新 FINAL。
 *
 * 隔离它的理由是 Phase 1 的 [AsrEngine] 契约只规定“初始化 → 送帧 → 冲刷”，文字经事件通道投递到上层，
 * 因此“驱动引擎并收敛 FINAL”必须由一个明确的实现承担。实现必须满足：
 *
 * - 只使用注入的 [AsrEngine] 与本地证据音频，不访问任何网络端点，也不下载或切换第二个模型；
 * - 引擎初始化/推理不可恢复失败时抛 `AsrEngineException`，不得静默改用其他模型版本；
 * - 按采集顺序返回新 FINAL；没有产出时返回空列表（协调器据此判为空白结果）；
 * - 取消由协调器处理：返回值在取消之后到达会被直接丢弃，实现无需回滚任何已产出数据。
 */
fun interface RerecognitionTranscriber {
    /** 使用 [engine] 对 [request] 描述的区间执行一次离线识别。 */
    fun transcribe(engine: AsrEngine, request: RerecognitionRequest): List<RecognizedSpan>
}
