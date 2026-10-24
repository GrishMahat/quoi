package quoi.utils

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NumberUtilsTest {
    @ParameterizedTest
    @CsvSource("I, 1", "IV, 4", "IX, 9", "XL, 40", "XC, 90", "CD, 400", "CM, 900", "MCMXCIV, 1994", "12, 12", "0, 0")
    fun `parses Roman numerals including subtractive pairs and decimal strings`(input: String, expected: Int) {
        assertEquals(expected, romanToInt(input))
    }

    @ParameterizedTest
    @CsvSource("-1.0, 10.0", "0.0, 10.0", "0.25, 15.0", "0.5, 20.0", "1.0, 30.0", "2.0, 30.0")
    fun `interpolation clamps the fraction to the endpoints`(fraction: Double, expected: Double) {
        assertEquals(expected, fraction.lerp(10.0, 30.0), 1e-9)
    }

    @Test
    fun `interpolation supports descending and equal endpoints`() {
        assertEquals(25.0, 0.25.lerp(30.0, 10.0), 1e-9)
        assertEquals(10.0, 0.5.lerp(10.0, 10.0), 1e-9)
    }

    @Test
    fun `rounds positive and negative numbers to the requested decimal places`() {
        assertEquals(12.35, 12.3456.round(2).toDouble(), 1e-9)
        assertEquals(-12.35, (-12.3456).round(2).toDouble(), 1e-9)
        assertEquals(12.0, 12.3456.round(0).toDouble(), 1e-9)
        assertEquals(12.0, 12.round(3).toDouble(), 1e-9)
        assertFailsWith<IllegalArgumentException> { 12.round(-1) }
    }

    @Test
    fun `comma formatting remains US formatted when the default locale differs`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)

            assertEquals("1,234,567", 1_234_567.commas())
            assertEquals("-1,234.5", (-1234.5).commas())
        } finally {
            Locale.setDefault(original)
        }
    }
}
