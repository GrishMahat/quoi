package quoi.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TickerTest {
    @Test
    fun `empty ticker is complete and stays complete`() {
        val ticker = ticker {}

        assertTrue(ticker.tick())
        assertTrue(ticker.tick())
    }

    @Test
    fun `actions run in order at most once per tick`() {
        val calls = mutableListOf<String>()
        val ticker = ticker {
            action { calls.add("first") }
            action { calls.add("second") }
        }

        assertFalse(ticker.tick())
        assertEquals(listOf("first"), calls)
        assertFalse(ticker.tick())
        assertEquals(listOf("first", "second"), calls)
        assertTrue(ticker.tick())
        assertTrue(ticker.tick())
        assertEquals(2, calls.size)
    }

    @Test
    fun `delay waits exactly the requested number of ticks before an action`() {
        var calls = 0
        val ticker = ticker {
            action(ticks = 3) { calls++ }
        }

        repeat(3) {
            assertFalse(ticker.tick())
            assertEquals(0, calls)
        }
        assertFalse(ticker.tick())
        assertEquals(1, calls)
        assertTrue(ticker.tick())
    }

    @Test
    fun `zero and negative delays allow the next action on the current tick`() {
        var calls = 0
        val ticker = ticker {
            delay(0)
            delay(-2)
            action { calls++ }
        }

        assertFalse(ticker.tick())
        assertEquals(1, calls)
        assertTrue(ticker.tick())
    }

    @Test
    fun `await checks each tick and starts its post delay only when the condition succeeds`() {
        var ready = false
        var checks = 0
        var calls = 0
        val ticker = ticker {
            await(delayAfter = 2) {
                checks++
                ready
            }
            action { calls++ }
        }

        repeat(2) { assertFalse(ticker.tick()) }
        assertEquals(2, checks)
        assertEquals(0, calls)

        ready = true
        repeat(2) {
            assertFalse(ticker.tick())
            assertEquals(0, calls)
        }
        assertFalse(ticker.tick())
        assertEquals(3, checks)
        assertEquals(1, calls)
        assertTrue(ticker.tick())
    }

    @Test
    fun `satisfied await without delay runs the following action on the same tick`() {
        var calls = 0
        val ticker = ticker {
            await { true }
            action { calls++ }
        }

        assertFalse(ticker.tick())
        assertEquals(1, calls)
        assertTrue(ticker.tick())
    }

    @Test
    fun `cancellation clears remaining work and completes immediately`() {
        val calls = mutableListOf<String>()
        val ticker = ticker {
            action {
                calls.add("cancel")
                cancel()
            }
            action(ticks = 2) { calls.add("skipped") }
        }

        assertTrue(ticker.tick())
        repeat(4) { assertTrue(ticker.tick()) }
        assertEquals(listOf("cancel"), calls)
    }

    @Test
    fun `ordinary action failures propagate to the caller`() {
        val ticker = ticker {
            action { throw IllegalStateException("action failed") }
        }

        val error = assertFailsWith<IllegalStateException> { ticker.tick() }

        assertEquals("action failed", error.message)
    }
}
