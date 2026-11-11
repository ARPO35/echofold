package com.echofold.core.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 有界幂等记录：容量上限、LRU 淘汰与访问刷新。 */
class SessionCommandLedgerTest {
    @Test
    fun `容量满后淘汰最久未使用的 commandId`() {
        val ledger = SessionCommandLedger<Int>(capacity = 2)

        ledger.put("a", 1)
        ledger.put("b", 2)
        // 读取 a 刷新其最近使用位置，因此下一轮应淘汰 b。
        assertEquals(1, ledger.get("a"))

        ledger.put("c", 3)

        assertEquals(2, ledger.size)
        assertTrue(ledger.contains("a"))
        assertFalse(ledger.contains("b"))
        assertTrue(ledger.contains("c"))
        assertEquals(listOf("a", "c"), ledger.commandIds())
    }

    @Test
    fun `覆盖已有键不会增长也不会触发淘汰`() {
        val ledger = SessionCommandLedger<String>(capacity = 2)

        ledger.put("a", "first")
        ledger.put("b", "second")
        assertEquals("first", ledger.put("a", "updated"))

        assertEquals(2, ledger.size)
        assertEquals("updated", ledger.get("a"))
        assertTrue(ledger.contains("b"))
    }

    @Test
    fun `容量为一仍保留最新命令`() {
        val ledger = SessionCommandLedger<Int>(capacity = 1)

        ledger.put("a", 1)
        ledger.put("b", 2)

        assertEquals(1, ledger.size)
        assertFalse(ledger.contains("a"))
        assertEquals(2, ledger.get("b"))
    }

    @Test
    fun `未知命令与空记录行为明确`() {
        val ledger = SessionCommandLedger<Int>(capacity = 3)

        assertTrue(ledger.isEmpty)
        assertNull(ledger.get("missing"))
        assertFalse(ledger.contains("missing"))

        ledger.put("a", 1)
        ledger.clear()

        assertTrue(ledger.isEmpty)
        assertNull(ledger.get("a"))
    }

    @Test
    fun `非法容量与空白 commandId 被拒绝`() {
        assertThrows(IllegalArgumentException::class.java) { SessionCommandLedger<Int>(capacity = 0) }
        assertThrows(IllegalArgumentException::class.java) { SessionCommandLedger<Int>(capacity = -1) }

        val ledger = SessionCommandLedger<Int>(capacity = 2)
        assertThrows(IllegalArgumentException::class.java) { ledger.put(" ", 1) }
    }
}
