package quoi.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigCollectionsTest {
    @Test
    fun `list mutations update the backing collection before notifying persistence`() {
        val backing = mutableListOf("first", "third")
        val saved = mutableListOf<List<String>>()
        val list = ConfigList(backing, { saved.add(backing.toList()) }, {})

        list.add(1, "second")
        assertEquals("first", list.set(0, "updated"))
        assertEquals("third", list.removeAt(2))

        assertEquals(listOf("updated", "second"), list.toList())
        assertEquals(
            listOf(listOf("first", "second", "third"), listOf("updated", "second", "third"), listOf("updated", "second")),
            saved,
        )
    }

    @Test
    fun `bulk list operations persist their resulting contents`() {
        val backing = mutableListOf(1, 2)
        val saved = mutableListOf<List<Int>>()
        val list = ConfigList(backing, { saved.add(backing.toList()) }, {})

        list.addAll(listOf(3, 4))
        list.addAll(1, listOf(5, 6))
        list.removeAll(listOf(2, 5))
        list.retainAll(listOf(1, 3))
        list.clear()

        assertEquals(listOf(listOf(1, 2, 3, 4), listOf(1, 5, 6, 2, 3, 4), listOf(1, 6, 3, 4), listOf(1, 3), emptyList()), saved)
        assertEquals(emptyList(), list.toList())
    }

    @Test
    fun `list iterator mutations trigger persistence`() {
        var saves = 0
        val list = ConfigList(mutableListOf("first", "second"), { saves++ }, {})
        val iterator = list.listIterator()

        assertEquals("first", iterator.next())
        iterator.set("updated")
        iterator.add("inserted")
        assertEquals("second", iterator.next())
        iterator.remove()

        assertEquals(listOf("updated", "inserted"), list.toList())
        assertEquals(3, saves)
    }

    @Test
    fun `failed list mutations do not notify persistence or change the collection`() {
        var saves = 0
        val list = ConfigList(mutableListOf("kept"), { saves++ }, {})

        assertFailsWith<IndexOutOfBoundsException> { list.add(3, "invalid") }
        assertFailsWith<IndexOutOfBoundsException> { list.removeAt(3) }
        assertFailsWith<IndexOutOfBoundsException> { list[3] = "invalid" }

        assertEquals(listOf("kept"), list.toList())
        assertEquals(0, saves)
    }

    @Test
    fun `map mutations return previous values and notify persistence with updated contents`() {
        val backing = mutableMapOf("first" to 1)
        val saved = mutableListOf<Map<String, Int>>()
        val map = ConfigMap(backing, { saved.add(backing.toMap()) }, {})

        assertEquals(1, map.put("first", 2))
        map.putAll(mapOf("second" to 3, "third" to 4))
        assertEquals(3, map.remove("second"))
        map.clear()

        assertEquals(listOf(mapOf("first" to 2), mapOf("first" to 2, "second" to 3, "third" to 4), mapOf("first" to 2, "third" to 4), emptyMap()), saved)
        assertEquals(emptyMap(), map.toMap())
    }

    @Test
    fun `reads do not persist and explicit save and reload call their respective callbacks`() {
        var listSaves = 0
        var listReloads = 0
        var mapSaves = 0
        var mapReloads = 0
        val list = ConfigList(mutableListOf("old"), { listSaves++ }, { listReloads++ })
        val map = ConfigMap(mutableMapOf("key" to "old"), { mapSaves++ }, { mapReloads++ })

        assertEquals("old", list[0])
        assertEquals(1, list.size)
        assertEquals("old", map["key"])
        assertEquals(setOf("key"), map.keys)
        assertEquals(listOf(0, 0, 0, 0), listOf(listSaves, listReloads, mapSaves, mapReloads))

        list.save()
        list.reload()
        map.save()
        map.reload()

        assertEquals(listOf(1, 1, 1, 1), listOf(listSaves, listReloads, mapSaves, mapReloads))
    }
}
