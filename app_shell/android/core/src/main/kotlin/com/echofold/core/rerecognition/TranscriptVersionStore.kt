package com.echofold.core.rerecognition

import com.echofold.core.model.SourceSet
import com.echofold.core.model.TranscriptVersion

/**
 * 提交事务被拒绝的结构性原因。
 *
 * 这些类别只描述可脱敏的事实（来源缺失、来源不一致、父版本过期、版本重复），
 * 不携带段正文、音频路径或模型路径。
 */
enum class VersionConflict {
    /** 该段尚未登记权威有序来源集合，无法逐项比对。 */
    SOURCE_SET_NOT_REGISTERED,

    /** `expectedSources` 与已登记来源集合在数量、顺序或任一项身份上不一致（换源、改版本、重排）。 */
    SOURCE_MISMATCH,

    /** 新版本的父版本不是该段当前默认版本：期间已有其他版本成为默认。 */
    PARENT_MISMATCH,

    /** 版本 ID 已存在：版本表只追加，同一 ID 不得重复提交。 */
    DUPLICATE_VERSION,
}

/** 版本提交事务被拒绝；[conflict] 是可直接回显给任务调度的脱敏原因码。 */
class VersionConflictException(
    val conflict: VersionConflict,
    message: String,
) : IllegalStateException(message)

/**
 * 转写版本表的提交事务边界。
 *
 * 版本表只追加：每次重识别、AI 校订或用户编辑新增一个 [TranscriptVersion]，已有版本（尤其 L0 证据）
 * 永不被覆盖。[commit] 必须是一个整体事务：
 *
 * 1. 逐项比对 [expectedSources] 与该段已登记的权威有序来源集合（`sourceId`/`versionId`/`sourceKind`
 *    三项与顺序全等），不一致时抛 [VersionConflictException]；
 * 2. 校验新版本 ID 未被使用、[TranscriptVersion.parentVersionId] 等于该段当前默认版本；
 * 3. 只有以上校验全部通过才追加版本并把它设为默认展示；任何一步失败都不得写入部分数据
 *    （既不新增版本，也不改变默认版本）。
 *
 * 实现必须对并发提交串行化，使“校验 + 写入”不可被其他提交穿插。
 */
interface TranscriptVersionStore {
    /** 该段当前默认展示版本；没有任何版本时返回 `null`（例如 GAP 尚未登记 L0 证据）。 */
    fun currentDefault(segmentId: String): TranscriptVersion?

    /** 该段全部版本，按提交先后排列的只读快照；旧版本必须始终可回查。 */
    fun versions(segmentId: String): List<TranscriptVersion>

    /**
     * 原子提交 [version]，并以 [expectedSources] 逐项校验来源。
     *
     * @return 已写入的版本（与入参相同），调用方据此确认默认展示已切换。
     * @throws VersionConflictException 来源集合、父版本或版本 ID 校验失败，且保证未写入任何数据。
     */
    fun commit(version: TranscriptVersion, expectedSources: SourceSet): TranscriptVersion
}
