package quoi.module.settings

import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Test
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.impl.SwitchComponent
import quoi.module.settings.impl.TextInputComponent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingTest {
    private class TestSettings : SettingsDSL() {
        val registered = mutableListOf<Setting<*>>()
        val toggle = SwitchComponent("Enabled", true)
        val text = TextInputComponent("Label", "default", length = 8)

        var enabled by toggle
        var label by text

        override fun <K : Setting<T>, T> register(setting: K): K = setting.also { registered.add(it) }
    }

    @Test
    fun `delegated properties register their settings and share the underlying values`() {
        val settings = TestSettings()

        assertEquals(listOf<Setting<*>>(settings.toggle, settings.text), settings.registered)
        assertTrue(settings.enabled)
        assertEquals("default", settings.label)

        settings.enabled = false
        settings.label = "changed"
        assertFalse(settings.toggle.value)
        assertEquals("changed", settings.text.value)

        settings.toggle.value = true
        settings.text.value = "direct"
        assertTrue(settings.enabled)
        assertEquals("direct", settings.label)
    }

    @Test
    fun `reset restores default values through delegated properties`() {
        val settings = TestSettings()
        settings.enabled = false
        settings.label = "changed"

        settings.toggle.reset()
        settings.text.reset()

        assertTrue(settings.enabled)
        assertEquals("default", settings.label)
    }

    @Test
    fun `custom JSON names preserve the display name and value`() {
        val setting = SwitchComponent("Visible name", true)
        assertEquals("Visible name", setting.jsonName)

        setting.json("group.enabled")

        assertEquals("group.enabled", setting.jsonName)
        assertEquals("Visible name", setting.name)
        assertTrue(setting.value)
    }

    @Test
    fun `saved primitive values can be loaded into fresh settings`() {
        val source = TestSettings()
        source.enabled = false
        source.label = "saved"
        val restored = TestSettings()

        assertEquals(JsonPrimitive(false), source.toggle.write())
        assertEquals(JsonPrimitive("saved"), source.text.write())
        restored.toggle.read(source.toggle.write())
        restored.text.read(source.text.write())

        assertFalse(restored.enabled)
        assertEquals("saved", restored.label)
    }

    @Test
    fun `text length limit applies to delegated assignments and loaded JSON`() {
        val settings = TestSettings()

        settings.label = "1234567890"
        assertEquals("12345678", settings.label)

        settings.text.read(JsonPrimitive("abcdefghij"))
        assertEquals("abcdefgh", settings.label)
    }
}
