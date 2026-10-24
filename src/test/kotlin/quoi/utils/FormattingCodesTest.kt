package quoi.utils

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals

class FormattingCodesTest {
    @ParameterizedTest
    @CsvSource(
        "'§aHello §lworld§r', 'Hello world'",
        "'&aHello &Lworld&R', 'Hello world'",
        "'§A&b§lText&r', 'Text'",
        "'start§0middle&fend', 'startmiddleend'",
        "'§a&b§r', ''",
        "'§a猫 &l😺§r', '猫 😺'",
    )
    fun `strips valid section sign and ampersand codes`(input: String, expected: String) {
        assertEquals(expected, FormattingCodes.strip(input))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "plain text", "&", "§", "Fish & chips", "§z &x &g", "end&", "end§"])
    fun `preserves plain text invalid codes and trailing markers`(input: String) {
        assertEquals(input, FormattingCodes.strip(input))
    }

    @ParameterizedTest
    @CsvSource(
        "'§aFish & chips§r&', 'Fish & chips&'",
        "'&x§bText§z', '&xText§z'",
        "'&&aText§', '&Text§'",
    )
    fun `preserves literal markers while removing adjacent valid codes`(input: String, expected: String) {
        assertEquals(expected, FormattingCodes.strip(input))
    }
}
