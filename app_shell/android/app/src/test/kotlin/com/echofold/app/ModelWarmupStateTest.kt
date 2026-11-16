package com.echofold.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** 预热状态机契约：失败不静默降级也不假启动，状态与原因均可经 status 查询。 */
class ModelWarmupStateTest {
    @Test
    fun `初始为空闲且不携带失败原因`() {
        val state = ModelWarmupState()
        assertEquals("idle", state.toStatusMap()["state"])
        assertNull(state.toStatusMap()["message"])
    }

    @Test
    fun `加载与就绪状态可查`() {
        val state = ModelWarmupState()
        state.markLoading("install-1")
        assertEquals("loading", state.toStatusMap()["state"])
        assertEquals("install-1", state.toStatusMap()["installationId"])

        state.markReady("install-1")
        assertEquals("ready", state.toStatusMap()["state"])
        assertEquals("install-1", state.toStatusMap()["installationId"])
    }

    @Test
    fun `预热失败保留原因且可查询`() {
        val state = ModelWarmupState()
        state.markLoading("install-1")
        state.markFailed("install-1", "native boom")
        val status = state.toStatusMap()
        assertEquals("failed", status["state"])
        assertEquals("install-1", status["installationId"])
        assertEquals("native boom", status["message"])
    }

    @Test
    fun `失败后重试成功回到就绪`() {
        val state = ModelWarmupState()
        state.markFailed("install-1", "native boom")
        state.markLoading("install-1")
        state.markReady("install-1")
        assertEquals("ready", state.toStatusMap()["state"])
    }

    @Test
    fun `无模型时保持空闲并说明原因`() {
        val state = ModelWarmupState()
        state.markIdle("尚未安装模型，无预热目标")
        val status = state.toStatusMap()
        assertEquals("idle", status["state"])
        assertEquals("尚未安装模型，无预热目标", status["message"])
    }
}
