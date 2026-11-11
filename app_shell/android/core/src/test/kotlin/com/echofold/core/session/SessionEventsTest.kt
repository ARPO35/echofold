package com.echofold.core.session

import com.echofold.core.model.DispatchState
import com.echofold.core.model.SessionStatus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** 状态变更事件：稳定 ID、append-only 日志与热流顺序。 */
class SessionEventsTest {
    private val sessionId = "session-1"
    private val utcMs = 1_700_000_000_000L

    private fun event(sequence: Long = 0L) = SessionStateEvent(
        sessionId = sessionId,
        sequence = sequence,
        commandId = "cmd-$sequence",
        commandKind = SessionCommandKind.START,
        previousStatus = SessionStatus.IDLE,
        previousDispatchState = DispatchState.BLOCKED,
        status = SessionStatus.RECORDING,
        dispatchState = DispatchState.RUNNING,
        occurredAtUtcMs = utcMs,
    )

    @Test
    fun `事件 ID 由会话与序号稳定派生`() {
        assertEquals("$sessionId:state:0", SessionEventIds.of(sessionId, 0))
        assertEquals("$sessionId:state:42", SessionEventIds.of(sessionId, 42))
        assertEquals("$sessionId:state:7", event(sequence = 7).eventId)
    }

    @Test
    fun `复制事件不得留下陈旧 eventId`() {
        val event = event(sequence = 3)

        assertThrows(IllegalArgumentException::class.java) { event.copy(sequence = 4) }
        assertThrows(IllegalArgumentException::class.java) { event.copy(reason = " ") }
        assertThrows(IllegalArgumentException::class.java) {
            event.copy(sessionId = "session-2")
        }
        assertThrows(IllegalArgumentException::class.java) {
            event.copy(occurredAtUtcMs = -1)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `日志只追加且热流按顺序投递`() = runTest {
        val log = SessionEventLog()
        val received = mutableListOf<SessionStateEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            log.stream().collect { received += it }
        }

        val machine = SessionStateMachine(sessionId = sessionId, eventLog = log)
        machine.execute(SessionCommand.Start("s"))
        machine.execute(SessionCommand.Pause("p"))
        advanceUntilIdle()
        collector.cancel()

        assertEquals(2, log.size)
        assertEquals(log.events, received)
        assertEquals("$sessionId:state:0", log.events[0].eventId)
        assertEquals("$sessionId:state:1", log.latest!!.eventId)
        assertEquals(SessionStatus.PAUSED, log.latest!!.status)
    }
}
