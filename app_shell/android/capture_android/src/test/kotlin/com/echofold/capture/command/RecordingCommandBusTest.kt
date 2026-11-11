package com.echofold.capture.command

import com.echofold.core.model.SessionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 有界命令总线与 commandId 幂等的 JVM 单测。 */
class RecordingCommandBusTest {

    private fun result(commandId: String, type: RecordingCommandType = RecordingCommandType.START) =
        RecordingCommandResult(
            commandId = commandId,
            type = type,
            accepted = true,
            status = SessionStatus.RECORDING,
        )

    @Test
    fun `首次提交入队，重复 commandId 不重复入队`() {
        val bus = RecordingCommandBus()
        val command = RecordingCommand("c-1", RecordingCommandType.START)

        assertTrue(bus.enqueue(command) is CommandSubmission.Accepted)
        val duplicate = bus.enqueue(command)

        assertTrue(duplicate is CommandSubmission.Duplicate)
        assertNull((duplicate as CommandSubmission.Duplicate).result)
        assertEquals(1, bus.pendingCount)
        assertEquals(command, bus.next())
        assertNull(bus.next())
    }

    @Test
    fun `已完成命令的重复提交返回首次结果`() {
        val bus = RecordingCommandBus()
        val command = RecordingCommand("c-1", RecordingCommandType.START)
        bus.enqueue(command)
        val first = result("c-1")
        assertTrue(bus.complete("c-1", first))

        val submission = bus.enqueue(command)

        assertTrue(submission is CommandSubmission.Duplicate)
        assertEquals(first, (submission as CommandSubmission.Duplicate).result)
        assertEquals(0, bus.pendingCount)
    }

    @Test
    fun `同一 commandId 的结果不会被覆盖`() {
        val bus = RecordingCommandBus()
        val first = result("c-1")
        assertTrue(bus.complete("c-1", first))

        val overwritten = result("c-1", RecordingCommandType.STOP)
        assertFalse(bus.complete("c-1", overwritten))
        assertEquals(first, bus.result("c-1"))
        assertEquals(1, bus.resultCount)
    }

    @Test
    fun `命令队列有界，满时返回 Full`() {
        val bus = RecordingCommandBus(capacity = 1)
        assertTrue(bus.enqueue(RecordingCommand("c-1", RecordingCommandType.START)) is CommandSubmission.Accepted)

        val full = bus.enqueue(RecordingCommand("c-2", RecordingCommandType.PAUSE))

        assertEquals(CommandSubmission.Full, full)
        assertEquals(1, bus.pendingCount)
    }

    @Test
    fun `结果表有界并按最旧淘汰`() {
        val bus = RecordingCommandBus(resultCapacity = 1)
        bus.complete("c-1", result("c-1"))
        bus.complete("c-2", result("c-2"))

        assertNull(bus.result("c-1"))
        assertEquals("c-2", bus.result("c-2")?.commandId)
        assertEquals(1, bus.resultCount)
    }

    @Test
    fun `完成结果会清掉同 ID 的待执行命令`() {
        val bus = RecordingCommandBus()
        bus.enqueue(RecordingCommand("c-1", RecordingCommandType.START))
        bus.complete("c-1", result("c-1"))

        assertEquals(0, bus.pendingCount)
    }

    @Test
    fun `结果 commandId 不匹配时拒绝写入`() {
        val bus = RecordingCommandBus()

        assertThrows(IllegalArgumentException::class.java) {
            bus.complete("c-1", result("c-2"))
        }
    }

    @Test
    fun `清空后不再返回任何结果`() {
        val bus = RecordingCommandBus()
        bus.enqueue(RecordingCommand("c-1", RecordingCommandType.START))
        bus.complete("c-1", result("c-1"))

        bus.clear()

        assertEquals(0, bus.pendingCount)
        assertEquals(0, bus.resultCount)
        assertNull(bus.result("c-1"))
    }

    @Test
    fun `commandId 不能为空白`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecordingCommand("  ", RecordingCommandType.START)
        }
    }
}
