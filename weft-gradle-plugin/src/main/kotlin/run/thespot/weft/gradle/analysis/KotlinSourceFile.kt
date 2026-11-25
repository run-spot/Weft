package run.thespot.weft.gradle.analysis

/**
 * A Kotlin source file prepared for structural scanning.
 *
 * [text] is the file as written; [clean] has the same length as [text] with comments and string or
 * character literal bodies replaced by spaces (line breaks preserved). Offsets in [clean] therefore
 * address the same position in [text], which lets the parser find structure in [clean] and read the
 * literal written at that offset from [text].
 */
internal class KotlinSourceFile(
    val path: String,
    val text: String,
) {
    val clean: String = KotlinSourceSanitizer.sanitize(text)

    fun locationAt(offset: Int): SourceLocation = SourceLocation(
        path = path,
        line = KotlinSourceSanitizer.lineAt(text, offset),
        column = KotlinSourceSanitizer.columnAt(text, offset),
    )
}

/** A position in a scanned file, formatted as `path:line` in messages and reports. */
internal data class SourceLocation(
    val path: String,
    val line: Int,
    val column: Int,
) {
    override fun toString(): String = "$path:$line"
}

/**
 * Blanks comments and literal bodies while keeping every offset and line break in place.
 *
 * Kotlin nests block comments, and a string can contain braces, parentheses, and comment markers, so
 * a regex cannot safely find declarations in raw source. Scanning the blanked text avoids all of
 * that without a compiler frontend.
 */
internal object KotlinSourceSanitizer {
    fun sanitize(source: String): String {
        val output = StringBuilder(source.length)
        var index = 0
        while (index < source.length) {
            when {
                source.startsWith("//", index) -> {
                    while (index < source.length && source[index] != '\n') {
                        output.append(' ')
                        index++
                    }
                }

                source.startsWith("/*", index) -> {
                    var depth = 0
                    while (index < source.length) {
                        when {
                            source.startsWith("/*", index) -> {
                                depth++
                                output.append("  ")
                                index += 2
                            }

                            source.startsWith("*/", index) -> {
                                depth--
                                output.append("  ")
                                index += 2
                                if (depth == 0) break
                            }

                            else -> {
                                output.append(if (source[index] == '\n') '\n' else ' ')
                                index++
                            }
                        }
                    }
                }

                source.startsWith("\"\"\"", index) -> {
                    // Keep the delimiters so a literal can be located later; blank only its body.
                    output.append("\"\"\"")
                    index += 3
                    while (index < source.length) {
                        if (source.startsWith("\"\"\"", index)) {
                            output.append("\"\"\"")
                            index += 3
                            break
                        }
                        output.append(if (source[index] == '\n') '\n' else ' ')
                        index++
                    }
                }

                source[index] == '"' -> {
                    output.append('"')
                    index++
                    while (index < source.length) {
                        val character = source[index]
                        if (character == '\\' && index + 1 < source.length) {
                            output.append("  ")
                            index += 2
                            continue
                        }
                        if (character == '"') {
                            output.append('"')
                            index++
                            break
                        }
                        if (character == '\n') break
                        output.append(' ')
                        index++
                    }
                }

                source[index] == '\'' -> {
                    output.append('\'')
                    index++
                    while (index < source.length) {
                        val character = source[index]
                        if (character == '\\' && index + 1 < source.length) {
                            output.append("  ")
                            index += 2
                            continue
                        }
                        if (character == '\'' || character == '\n') {
                            output.append('\'')
                            index++
                            break
                        }
                        output.append(' ')
                        index++
                    }
                }

                else -> {
                    output.append(source[index])
                    index++
                }
            }
        }
        return output.toString()
    }

    fun lineAt(source: String, offset: Int): Int {
        var line = 1
        var index = 0
        val limit = offset.coerceAtMost(source.length)
        while (index < limit) {
            if (source[index] == '\n') line++
            index++
        }
        return line
    }

    fun columnAt(source: String, offset: Int): Int {
        val limit = offset.coerceAtMost(source.length)
        var column = 1
        var index = limit - 1
        while (index >= 0 && source[index] != '\n') {
            column++
            index--
        }
        return column
    }
}

/** A Kotlin string literal found in source, with what could be read from it statically. */
internal data class StringLiteral(
    val value: String,
    val hasInterpolation: Boolean,
)

internal object KotlinStringLiterals {
    /**
     * Decodes [expression] when it is a single string literal, and returns `null` for anything else
     * (a variable, a call, a concatenation), because the analyzer only trusts what is written down.
     */
    fun decode(expression: String): StringLiteral? {
        val text = expression.trim()
        return when {
            text.startsWith("\"\"\"") -> {
                val end = text.indexOf("\"\"\"", startIndex = 3)
                if (end < 0) return null
                val inner = text.substring(3, end)
                if (text.substring(end + 3).isNotBlank()) return null
                StringLiteral(inner, hasInterpolation(inner))
            }

            text.startsWith("\"") && text.endsWith("\"") && text.length >= 2 -> {
                val inner = text.substring(1, text.length - 1)
                if (containsUnescapedQuote(inner)) return null
                StringLiteral(unescape(inner), hasInterpolation(inner))
            }

            else -> null
        }
    }

    private fun containsUnescapedQuote(inner: String): Boolean {
        var index = 0
        while (index < inner.length) {
            if (inner[index] == '\\') {
                index += 2
                continue
            }
            if (inner[index] == '"') return true
            index++
        }
        return false
    }

    private fun unescape(inner: String): String {
        if (!inner.contains('\\')) return inner
        val output = StringBuilder(inner.length)
        var index = 0
        while (index < inner.length) {
            val character = inner[index]
            if (character != '\\' || index + 1 >= inner.length) {
                output.append(character)
                index++
                continue
            }
            when (val escaped = inner[index + 1]) {
                'n' -> output.append('\n')
                't' -> output.append('\t')
                'r' -> output.append('\r')
                'b' -> output.append('\b')
                '"' -> output.append('"')
                '\'' -> output.append('\'')
                '\\' -> output.append('\\')
                '$' -> output.append('$')
                'u' -> {
                    val hex = inner.substring(index + 2, (index + 6).coerceAtMost(inner.length))
                    val code = hex.toIntOrNull(16)
                    if (code != null) {
                        output.append(code.toChar())
                        index += 6
                        continue
                    }
                    output.append(escaped)
                }

                else -> output.append(escaped)
            }
            index += 2
        }
        return output.toString()
    }

    /** True when the literal is built at runtime and its final value cannot be read statically. */
    private fun hasInterpolation(inner: String): Boolean {
        var index = 0
        while (index < inner.length) {
            if (inner[index] == '\\') {
                index += 2
                continue
            }
            if (inner[index] == '$') return true
            index++
        }
        return false
    }
}
