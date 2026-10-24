package quoi.api.events.core

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class SubscriptionRegistryTest {
    private class TestEvent : Event()
    private val listener = object : EventListener {}

    private fun subscription(priority: Int) = Subscription<TestEvent>(listener, TestEvent::class.java, priority) {}

    @Test
    fun `registry starts empty and sorts additions by descending priority with stable ties`() {
        val registry = SubscriptionRegistry<TestEvent>()
        assertTrue(registry.dispatchSnapshot.isEmpty())
        val low = subscription(Priority.LOW)
        val firstHigh = subscription(Priority.HIGH)
        val secondHigh = subscription(Priority.HIGH)
        val normal = subscription(0)

        listOf(low, firstHigh, secondHigh, normal).forEach(registry::add)

        val expected = listOf(firstHigh, secondHigh, normal, low)
        assertContentEquals(expected, registry.subscriptions)
        assertContentEquals(expected, registry.dispatchSnapshot.asList())
    }

    @Test
    fun `adding and removing subscriptions does not change a previously captured dispatch snapshot`() {
        val registry = SubscriptionRegistry<TestEvent>()
        val original = subscription(0)
        val added = subscription(Priority.HIGH)
        registry.add(original)
        val snapshot = registry.dispatchSnapshot

        registry.add(added)
        registry.remove(original)

        assertContentEquals(listOf(original), snapshot.asList())
        assertContentEquals(listOf(added), registry.dispatchSnapshot.asList())
        assertContentEquals(listOf(added), registry.subscriptions)
    }

    @Test
    fun `removing an absent subscription preserves the remaining registrations`() {
        val registry = SubscriptionRegistry<TestEvent>()
        val remaining = subscription(0)
        registry.add(remaining)

        registry.remove(subscription(0))
        assertContentEquals(listOf(remaining), registry.dispatchSnapshot.asList())

        registry.remove(remaining)
        assertTrue(registry.subscriptions.isEmpty())
        assertTrue(registry.dispatchSnapshot.isEmpty())
    }
}
