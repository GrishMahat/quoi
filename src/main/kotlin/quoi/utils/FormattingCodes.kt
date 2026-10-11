package quoi.utils

object FormattingCodes {
    fun strip(text: String): String {
        var index = 0
        while (index < text.length - 1 && !isCodeAt(text, index)) index++
        if (index >= text.length - 1) return text

        val result = StringBuilder(text.length)
        result.append(text, 0, index)
        var start = index
        while (index < text.length - 1) {
            if (isCodeAt(text, index)) {
                result.append(text, start, index)
                index += 2
                start = index
            } else index++
        }
        return result.append(text, start, text.length).toString()
    }

    private fun isCodeAt(text: String, index: Int): Boolean {
        return !(text[index] != '§' && text[index] != '&') && when (text[index + 1].lowercaseChar()) {
            in '0'..'9', in 'a'..'f', in 'k'..'o', 'r' -> true
            else -> false
        }
    }
}
