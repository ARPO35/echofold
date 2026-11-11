package com.echofold.capture.session

import com.echofold.capture.command.RecordingCommand
import com.echofold.capture.command.RecordingCommandType
import com.echofold.capture.command.RecordingError
import com.echofold.core.model.DispatchState
import com.echofold.core.model.SessionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 录音命令状态机：权限/前台/管线失败不进入录音态，暂停继续与幂等。 */
class RecordingControllerTest {

    private class FakeEnvironment(
        var microphonePermission: Boolean = true,
        var foregroundAllowed: Boolean = true,
        var pipelineAvailable: Boolean = true,
    ) : RecordingEnvironment {
        var acquired = 0
        var released = 0
        var paused = 0
        var resumed = 0

        override fun hasMicrophonePermission(): Boolean = microphonePermission

        override fun canStartForeground(): Boolean = foregroundAllowed

        override fun acquirePipeline(): Boolean {
            if (!pipelineAvailable) return false
            acquired++
            return true
        }

        override fun pausePipeline() {
            paused++
        }

        override fun resumePipeline() {
            resumed++
        }

        override fun releasePipeline() {
            released++
        }
    }

    private fun command(id: String, type: RecordingCommandType) = RecordingCommand(id, type)

    private fun RecordingController.start(id: String = "c-start") = handle(command(id, RecordingCommandType.START))

    @Test
    fun `缺少麦克风权限时 START 不进入录音态`() {
        val environment = FakeEnvironment(microphonePermission = false)
        val controller = RecordingController(environment)

        val result = controller.start()

        assertFalse(result.accepted)
        assertEquals(RecordingError.MIC_PERMISSION_DENIED, result.error)
        assertEquals(SessionStatus.IDLE, result.status)
        assertEquals(0, environment.acquired)
        assertEquals(RecordingError.MIC_PERMISSION_DENIED, controller.lastError)
    }

    @Test
    fun `前台条件不满足时 START 不进入录音态`() {
        val environment = FakeEnvironment(foregroundAllowed = false)
        val controller = RecordingController(environment)

        val result = controller.start()

        assertFalse(result.accepted)
        assertEquals(RecordingError.FOREGROUND_UNAVAILABLE, result.error)
        assertEquals(SessionStatus.IDLE, controller.status)
        assertEquals(0, environment.acquired)
    }

    @Test
    fun `采集管线失败时 START 不进入录音态`() {
        val environment = FakeEnvironment(pipelineAvailable = false)
        val controller = RecordingController(environment)

        val result = controller.start()

        assertFalse(result.accepted)
        assertEquals(RecordingError.PIPELINE_UNAVAILABLE, result.error)
        assertEquals(SessionStatus.IDLE, controller.status)
    }

    @Test
    fun `成功 START 只获取一次管线并进入录音态`() {
        val environment = FakeEnvironment()
        val controller = RecordingController(environment)

        val first = controller.start("c-1")
        val second = controller.start("c-2")

        assertTrue(first.accepted)
        assertEquals(SessionStatus.RECORDING, first.status)
        assertTrue(second.accepted)
        assertEquals(SessionStatus.RECORDING, controller.status)
        assertEquals(1, environment.acquired)
    }

    @Test
    fun `PAUSE 与 RESUME 切换状态并驱动管线`() {
        val environment = FakeEnvironment()
        val controller = RecordingController(environment)
        controller.start()

        val paused = controller.handle(command("c-pause", RecordingCommandType.PAUSE))
        assertEquals(SessionStatus.PAUSED, paused.status)
        assertTrue(paused.accepted)
        assertEquals(1, environment.paused)

        val resumed = controller.handle(command("c-resume", RecordingCommandType.RESUME))
        assertEquals(SessionStatus.RECORDING, resumed.status)
        assertEquals(1, environment.resumed)
    }

    @Test
    fun `暂停中权限被撤销时 RESUME 被拒绝且保持暂停`() {
        val environment = FakeEnvironment()
        val controller = RecordingController(environment)
        controller.start()
        controller.handle(command("c-pause", RecordingCommandType.PAUSE))
        environment.microphonePermission = false

        val result = controller.handle(command("c-resume", RecordingCommandType.RESUME))

        assertFalse(result.accepted)
        assertEquals(RecordingError.MIC_PERMISSION_DENIED, result.error)
        assertEquals(SessionStatus.PAUSED, controller.status)
        assertEquals(0, environment.resumed)
    }

    @Test
    fun `STOP 释放资源并回到空闲`() {
        val environment = FakeEnvironment()
        val controller = RecordingController(environment)
        controller.start()
        controller.reportDispatch(DispatchState.BLOCKED)

        val result = controller.handle(command("c-stop", RecordingCommandType.STOP))

        assertTrue(result.accepted)
        assertEquals(SessionStatus.IDLE, result.status)
        assertEquals(1, environment.released)
        assertEquals(DispatchState.RUNNING, controller.dispatchState)
    }

    @Test
    fun `空闲态 PAUSE 与 RESUME 返回非法状态`() {
        val environment = FakeEnvironment()
        val controller = RecordingController(environment)

        val pause = controller.handle(command("c-pause", RecordingCommandType.PAUSE))
        val resume = controller.handle(command("c-resume", RecordingCommandType.RESUME))

        assertFalse(pause.accepted)
        assertEquals(RecordingError.ILLEGAL_STATE, pause.error)
        assertFalse(resume.accepted)
        assertEquals(RecordingError.ILLEGAL_STATE, resume.error)
        assertEquals(SessionStatus.IDLE, controller.status)
    }

    @Test
    fun `空闲态 STOP 是幂等 no-op 且不释放管线`() {
        val environment = FakeEnvironment()
        val controller = RecordingController(environment)

        val result = controller.handle(command("c-stop", RecordingCommandType.STOP))

        assertTrue(result.accepted)
        assertEquals(SessionStatus.IDLE, result.status)
        assertEquals(0, environment.released)
    }

    @Test
    fun `重复 PAUSE 是幂等成功`() {
        val environment = FakeEnvironment()
        val controller = RecordingController(environment)
        controller.start()
        controller.handle(command("c-pause-1", RecordingCommandType.PAUSE))

        val again = controller.handle(command("c-pause-2", RecordingCommandType.PAUSE))

        assertTrue(again.accepted)
        assertEquals(SessionStatus.PAUSED, again.status)
        assertEquals(1, environment.paused)
    }

    @Test
    fun `释放后清空错误并回到空闲`() {
        val environment = FakeEnvironment(microphonePermission = false)
        val controller = RecordingController(environment)
        controller.start()
        assertTrue(controller.lastError != null)

        environment.microphonePermission = true
        controller.start("c-2")
        assertNull(controller.lastError)

        controller.release()
        assertEquals(SessionStatus.IDLE, controller.status)
        assertEquals(1, environment.released)
    }

    @Test
    fun `被拒绝的结果保留当前状态`() {
        val environment = FakeEnvironment()
        val controller = RecordingController(environment)
        controller.start()
        controller.handle(command("c-stop", RecordingCommandType.STOP))

        val result = controller.handle(command("c-resume", RecordingCommandType.RESUME))

        assertFalse(result.accepted)
        assertEquals(RecordingError.ILLEGAL_STATE, result.error)
        assertEquals(SessionStatus.IDLE, result.status)
    }
}
