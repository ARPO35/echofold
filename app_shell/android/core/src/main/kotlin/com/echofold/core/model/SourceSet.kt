package com.echofold.core.model

/** 规范来源集合 JSON 的模式版本，参与哈希计算。 */
const val SOURCE_SET_SCHEMA: String = "echofold.source-set.v1"

/** `sourceFinalIdsHash` 对应规范 JSON 的模式版本，参与哈希计算。 */
const val SOURCE_FINAL_IDS_SCHEMA: String = "echofold.source-final-ids.v1"

/**
 * 有序来源版本引用（多源产物、重识别幂等键、标题依赖均使用它）。
 *
 * `sourceId` 是来源实体（转写段或 FINAL）的稳定 ID，`versionId` 是所引用版本的稳定 ID，
 * `sourceKind` 区分 FINAL/AI/USER/GAP。三项一起构成可逐项比对的源身份。
 */
data class SourceVersionRef(
    val sourceId: String,
    val versionId: String,
    val sourceKind: SourceKind,
) {
    init {
        requireNotBlank(sourceId, "sourceId")
        requireNotBlank(versionId, "versionId")
    }
}

/**
 * 有序来源版本集合。
 *
 * 数组顺序就是语义输入顺序：排序、去重或只保留末项都会改变产物含义，因此构造时拒绝重复
 * 来源并保留调用方顺序。[sourceSetHash] 由 [canonicalJson] 计算。
 */
data class SourceSet(val ordered: List<SourceVersionRef>) {
    init {
        require(ordered.isNotEmpty()) { "SourceSet.ordered 不能为空：多源产物至少需要一个来源版本" }
        val seen = HashSet<String>(ordered.size)
        for (ref in ordered) {
            require(seen.add(ref.sourceId)) {
                "SourceSet.ordered 不能重复出现 sourceId=${ref.sourceId}：数组顺序有语义，禁止去重"
            }
        }
    }

    /**
     * 规范 JSON：键固定顺序、无空白、UTF-8、数组顺序保留。
     *
     * 形状固定为
     * `{"schema":"echofold.source-set.v1","sources":[{"sourceId":...,"versionId":...,"sourceKind":...}]}`，
     * 字符串按 JSON 规则转义，枚举取大写名称。任何字段顺序或空白变化都会改变 [sourceSetHash]。
     */
    val canonicalJson: String = buildString {
        append("{\"schema\":")
        append(CanonicalJson.quote(SOURCE_SET_SCHEMA))
        append(",\"sources\":[")
        ordered.forEachIndexed { index, ref ->
            if (index > 0) append(',')
            append("{\"sourceId\":")
            append(CanonicalJson.quote(ref.sourceId))
            append(",\"versionId\":")
            append(CanonicalJson.quote(ref.versionId))
            append(",\"sourceKind\":")
            append(CanonicalJson.quote(ref.sourceKind.name))
            append('}')
        }
        append("]}")
    }

    /** [canonicalJson] 的 SHA-256，64 位小写十六进制；可在响应回显、缓存键与提交事务三处逐项比对。 */
    val sourceSetHash: String = CanonicalJson.sha256Hex(canonicalJson)
}

/**
 * 计算有序 FINAL ID 列表的稳定哈希，用于重识别幂等键
 * `segmentId + inputStartMs + inputEndMs + modelVersion + sourceFinalIdsHash`。
 *
 * 规范 JSON 形状：`{"schema":"echofold.source-final-ids.v1","finalIds":[...]}`。
 * 顺序保留（相邻 FINAL 的拼接顺序影响语义），重复 ID 视为非法输入。
 */
fun sourceFinalIdsHash(sourceFinalIds: List<String>): String {
    require(sourceFinalIds.isNotEmpty()) { "sourceFinalIds 不能为空" }
    val seen = HashSet<String>(sourceFinalIds.size)
    for (finalId in sourceFinalIds) {
        requireNotBlank(finalId, "sourceFinalIds 元素")
        require(seen.add(finalId)) { "sourceFinalIds 不能包含重复值：$finalId" }
    }
    val canonicalJson = buildString {
        append("{\"schema\":")
        append(CanonicalJson.quote(SOURCE_FINAL_IDS_SCHEMA))
        append(",\"finalIds\":[")
        sourceFinalIds.forEachIndexed { index, finalId ->
            if (index > 0) append(',')
            append(CanonicalJson.quote(finalId))
        }
        append("]}")
    }
    return CanonicalJson.sha256Hex(canonicalJson)
}
