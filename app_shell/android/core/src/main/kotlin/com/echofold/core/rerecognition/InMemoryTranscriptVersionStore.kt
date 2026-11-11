package com.echofold.core.rerecognition

import com.echofold.core.model.SourceSet
import com.echofold.core.model.TranscriptVersion

/**
 * 纯内存的 [TranscriptVersionStore]，用于 JVM 单测与无设备演示。
 *
 * 所有读写都在同一把锁内完成，因此“校验来源 → 追加版本 → 切换默认”是不可分割的事务：
 * 并发提交只会被串行化，不会出现只写入一半的中间状态。设备端实现必须用数据库事务达成同样语义。
 *
 * 段的所有权规则：段创建方在登记 L0 证据时先调用 [registerSourceSet] 写入权威有序来源集合，
 * 之后每次换源（AI 重生成改变上游版本、用户编辑改变来源）都必须重新登记——旧的登记集合不会被静默修改，
 * 而是整体替换，使基于旧集合的任务在完成时被逐项比对拒绝。
 */
class InMemoryTranscriptVersionStore : TranscriptVersionStore {
    private val lock = Any()

    /** 段 → 版本列表，按提交先后排列。 */
    private val versionsBySegment = LinkedHashMap<String, MutableList<TranscriptVersion>>()

    /** 段 → 当前默认版本 ID。 */
    private val defaultBySegment = HashMap<String, String>()

    /** 段 → 权威有序来源集合。 */
    private val sourcesBySegment = LinkedHashMap<String, SourceSet>()

    /** 版本 ID → 所属段，用于拒绝跨段重复提交。 */
    private val segmentByVersionId = HashMap<String, String>()

    /**
     * 登记（或整体替换）该段的权威有序来源集合。
     *
     * 来源顺序有语义：这里登记的顺序就是之后 [commit] 逐项比对的顺序。
     * 换源时整体替换，使旧来源集合上启动的任务无法提交。
     */
    fun registerSourceSet(segmentId: String, sources: SourceSet) {
        require(segmentId.isNotBlank()) { "segmentId 不能为空白字符串" }
        synchronized(lock) {
            sourcesBySegment[segmentId] = sources
        }
    }

    override fun currentDefault(segmentId: String): TranscriptVersion? = synchronized(lock) {
        defaultBySegment[segmentId]?.let { versionId ->
            versionsBySegment[segmentId]?.firstOrNull { it.id == versionId }
        }
    }

    override fun versions(segmentId: String): List<TranscriptVersion> = synchronized(lock) {
        versionsBySegment[segmentId]?.toList().orEmpty()
    }

    override fun commit(version: TranscriptVersion, expectedSources: SourceSet): TranscriptVersion =
        synchronized(lock) {
            // 先完成全部校验，再一次性写入；任一步失败都不触碰版本表与默认指针。
            val registered = sourcesBySegment[version.segmentId]
                ?: throw VersionConflictException(
                    VersionConflict.SOURCE_SET_NOT_REGISTERED,
                    "段 ${version.segmentId} 尚未登记来源集合，拒绝提交 ${version.id}",
                )
            if (!sameOrderedSources(registered, expectedSources)) {
                throw VersionConflictException(
                    VersionConflict.SOURCE_MISMATCH,
                    "段 ${version.segmentId} 的来源集合逐项不一致：已登记 ${registered.canonicalJson}，" +
                        "提交声明 ${expectedSources.canonicalJson}",
                )
            }
            if (segmentByVersionId.containsKey(version.id)) {
                throw VersionConflictException(
                    VersionConflict.DUPLICATE_VERSION,
                    "版本 ID 已存在：${version.id}",
                )
            }
            val currentDefaultId = defaultBySegment[version.segmentId]
            if (version.parentVersionId != currentDefaultId) {
                throw VersionConflictException(
                    VersionConflict.PARENT_MISMATCH,
                    "版本 ${version.id} 的父版本 ${version.parentVersionId} 不是段 ${version.segmentId} " +
                        "当前默认版本 $currentDefaultId",
                )
            }

            versionsBySegment.getOrPut(version.segmentId) { mutableListOf() }.add(version)
            defaultBySegment[version.segmentId] = version.id
            segmentByVersionId[version.id] = version.segmentId
            version
        }

    /** 逐项比较两项三字段与顺序；容量不同直接判为不一致。 */
    private fun sameOrderedSources(left: SourceSet, right: SourceSet): Boolean {
        if (left.ordered.size != right.ordered.size) return false
        return left.ordered.indices.all { index -> left.ordered[index] == right.ordered[index] }
    }
}
