package com.echofold.core.rerecognition

import com.echofold.core.model.SourceKind
import com.echofold.core.model.SourceSet
import com.echofold.core.model.SourceVersionRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** [InMemoryTranscriptVersionStore] 的提交事务语义：逐项来源校验、原子写入与默认切换。 */
class TranscriptVersionStoreTest {
    private val store = InMemoryTranscriptVersionStore()
    private val sources = Fixtures.textSources()

    @Test
    fun `首次提交成为默认`() {
        store.registerSourceSet(Fixtures.SEGMENT, sources)

        val version = store.commit(Fixtures.version(id = "v-l0"), sources)

        assertEquals(version, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(listOf(version), store.versions(Fixtures.SEGMENT))
    }

    @Test
    fun `追加提交保留父版本并切换默认`() {
        store.registerSourceSet(Fixtures.SEGMENT, sources)
        val parent = store.commit(Fixtures.version(id = "v-l0"), sources)

        val child = store.commit(
            Fixtures.version(id = "v-asr-2", text = "重识别文字", parentVersionId = parent.id),
            sources,
        )

        assertEquals(child, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(listOf("v-l0", "v-asr-2"), store.versions(Fixtures.SEGMENT).map { it.id })
        assertEquals(parent, store.versions(Fixtures.SEGMENT).first())
    }

    @Test
    fun `来源顺序不一致时拒绝且不写入任何数据`() {
        store.registerSourceSet(Fixtures.SEGMENT, sources)
        val reversed = SourceSet(listOf(Fixtures.finalRef("f2"), Fixtures.finalRef("f1")))

        assertEquals(
            VersionConflict.SOURCE_MISMATCH,
            conflictOf { store.commit(Fixtures.version(id = "v-l0"), reversed) },
        )
        assertNull(store.currentDefault(Fixtures.SEGMENT))
        assertEquals(emptyList<String>(), store.versions(Fixtures.SEGMENT).map { it.id })
    }

    @Test
    fun `来源缺项或多项时拒绝`() {
        store.registerSourceSet(Fixtures.SEGMENT, sources)

        assertEquals(
            VersionConflict.SOURCE_MISMATCH,
            conflictOf { store.commit(Fixtures.version(id = "v-missing"), SourceSet(listOf(Fixtures.finalRef("f1")))) },
        )
        assertEquals(
            VersionConflict.SOURCE_MISMATCH,
            conflictOf {
                store.commit(
                    Fixtures.version(id = "v-extra"),
                    SourceSet(listOf(Fixtures.finalRef("f1"), Fixtures.finalRef("f2"), Fixtures.finalRef("f3"))),
                )
            },
        )
        assertNull(store.currentDefault(Fixtures.SEGMENT))
    }

    @Test
    fun `来源版本标识变化时拒绝`() {
        store.registerSourceSet(Fixtures.SEGMENT, sources)
        val reversioned = SourceSet(
            listOf(
                SourceVersionRef("f1", "f1-v2", SourceKind.FINAL),
                Fixtures.finalRef("f2"),
            ),
        )

        assertEquals(
            VersionConflict.SOURCE_MISMATCH,
            conflictOf { store.commit(Fixtures.version(id = "v-l0"), reversioned) },
        )
        assertNull(store.currentDefault(Fixtures.SEGMENT))
    }

    @Test
    fun `未登记来源集合时拒绝`() {
        assertEquals(
            VersionConflict.SOURCE_SET_NOT_REGISTERED,
            conflictOf { store.commit(Fixtures.version(id = "v-l0"), sources) },
        )
        assertNull(store.currentDefault(Fixtures.SEGMENT))
    }

    @Test
    fun `父版本不是当前默认版本时拒绝`() {
        store.registerSourceSet(Fixtures.SEGMENT, sources)
        val parent = store.commit(Fixtures.version(id = "v-l0"), sources)

        assertEquals(
            VersionConflict.PARENT_MISMATCH,
            conflictOf { store.commit(Fixtures.version(id = "v-root", parentVersionId = null), sources) },
        )
        assertEquals(
            VersionConflict.PARENT_MISMATCH,
            conflictOf { store.commit(Fixtures.version(id = "v-ghost", parentVersionId = "v-ghost-parent"), sources) },
        )
        assertEquals(parent, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(listOf("v-l0"), store.versions(Fixtures.SEGMENT).map { it.id })
    }

    @Test
    fun `版本 ID 重复时拒绝`() {
        store.registerSourceSet(Fixtures.SEGMENT, sources)
        val parent = store.commit(Fixtures.version(id = "v-l0"), sources)

        assertEquals(
            VersionConflict.DUPLICATE_VERSION,
            conflictOf { store.commit(Fixtures.version(id = "v-l0"), sources) },
        )
        assertEquals(parent, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(1, store.versions(Fixtures.SEGMENT).size)
    }

    @Test
    fun `换源登记后旧来源集合无法再提交`() {
        store.registerSourceSet(Fixtures.SEGMENT, sources)
        val parent = store.commit(Fixtures.version(id = "v-l0"), sources)
        val narrowed = SourceSet(listOf(Fixtures.finalRef("f1")))

        store.registerSourceSet(Fixtures.SEGMENT, narrowed)

        assertEquals(
            VersionConflict.SOURCE_MISMATCH,
            conflictOf {
                store.commit(Fixtures.version(id = "v-old", parentVersionId = parent.id), sources)
            },
        )
        assertEquals(parent, store.currentDefault(Fixtures.SEGMENT))

        val replacement = store.commit(
            Fixtures.version(id = "v-new", parentVersionId = parent.id),
            narrowed,
        )
        assertEquals(replacement, store.currentDefault(Fixtures.SEGMENT))
        assertEquals(listOf("v-l0", "v-new"), store.versions(Fixtures.SEGMENT).map { it.id })
    }

    private fun conflictOf(block: () -> Unit): VersionConflict {
        val exception = assertThrows(VersionConflictException::class.java) { block() }
        return exception.conflict
    }
}
