package com.echofold.core.rerecognition

import com.echofold.core.model.TranscriptVersion

/**
 * 一次重识别任务的终结结果。
 *
 * 四类终结互斥且只出现一次；原因码只描述可脱敏的结构事实（引擎失败、区间越界、模型不一致、换源、迟到等），
 * 不携带文字正文、音频路径或模型路径，可直接用于诊断摘要与重试决策。
 */
sealed interface RerecognitionResult {
    /** 对应任务的稳定幂等键；同一键的终结结果可安全复用。 */
    val key: RerecognitionKey

    /**
     * 成功：已原子提交新版本并切换为该段默认展示。
     *
     * [version] 的 `parentVersionId` 指向提交时的当前默认版本；旧版本未被改写。
     */
    data class Success(
        override val key: RerecognitionKey,
        val version: TranscriptVersion,
    ) : RerecognitionResult

    /** 执行失败或结果校验失败：未新增版本，原默认展示保持不变，可重试。 */
    data class Failed(
        override val key: RerecognitionKey,
        val reason: RerecognitionFailure,
    ) : RerecognitionResult

    /** 用户取消：未新增版本，原默认展示保持不变。 */
    data class Cancelled(
        override val key: RerecognitionKey,
    ) : RerecognitionResult

    /**
     * 请求在进入执行前被拒绝，或结果在提交前被判定为迟到/换源：未新增版本，默认展示保持不变。
     *
     * 迟到结果只以本结果回传，任务方可将它记入任务历史，但绝不写入该段默认版本。
     */
    data class Rejected(
        override val key: RerecognitionKey,
        val reason: RerecognitionRejection,
    ) : RerecognitionResult
}

/** 失败原因码（脱敏，不含自由文本）。 */
enum class RerecognitionFailure {
    /** 引擎初始化或推理抛出不可恢复异常。 */
    ENGINE_FAILURE,

    /** 没有产出任何 FINAL，或产出文字为空白：空白结果不得成为版本。 */
    EMPTY_TEXT,

    /** 结果结构非法：FINAL ID 重复、区间重叠或次序倒退。 */
    INVALID_RESULT,

    /** 结果区间越出请求锁定的音频区间。 */
    RANGE_OUT_OF_BOUNDS,

    /** 结果模型与会话固定模型不一致：不得写入该段版本。 */
    RESULT_MODEL_MISMATCH,

    /** 版本表本身故障（非来源/父版本冲突）。 */
    STORE_FAILURE,
}

/** 拒绝原因码（脱敏，不含自由文本）。 */
enum class RerecognitionRejection {
    /** 请求的会话不是本协调器所属会话。 */
    SESSION_MISMATCH,

    /** 请求模型或配置哈希与会话固定模型/配置不一致；绝不切换模型来“补齐”。 */
    SESSION_MODEL_MISMATCH,

    /** 该段已有另一个不同输入的活动任务；可先取消旧任务再提交。 */
    SEGMENT_BUSY,

    /** 完成时该段已观察到更新的跳读世代：结果迟到，不覆盖默认展示。 */
    LATE_GENERATION,

    /** 完成时该段的有序来源集合已变化（换源、改版本或重排）：结果过期。 */
    SOURCE_SET_CHANGED,

    /** 完成时该段默认版本已被其他版本取代：结果过期。 */
    SUPERSEDED,

    /** 该段尚无可用来源证据版本（例如从未登记 L0 的 GAP），无法构造来源集合。 */
    MISSING_SOURCES,

    /** 任务执行器拒绝接收任务（通常表示宿主正在关闭）。 */
    DISPATCH_FAILED,
}
