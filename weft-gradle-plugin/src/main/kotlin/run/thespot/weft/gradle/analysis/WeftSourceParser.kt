package run.thespot.weft.gradle.analysis

/**
 * Reads one Kotlin file and reports the facts the Weft checks need: plugin declarations, `install`
 * calls with the literal configuration written inside them, `.get<T>()` lookups, and literal
 * `WeftHealthCheck(...)` declarations.
 *
 * The parser is deliberately structural rather than semantic: it follows Kotlin's bracket, comment,
 * and literal rules, but it does not resolve imports, type aliases, inheritance chains, or values
 * built at runtime. Anything it cannot read with certainty is left out instead of guessed, and the
 * checks that consume the model describe that limit in their messages.
 */
internal class WeftSourceParser(private val file: KotlinSourceFile) {
    private val clean: String = file.clean
    private val text: String = file.text

    fun parse(): ParsedFile = ParsedFile(
        declarations = parseDeclarations(),
        installs = parseInstalls(),
        lookups = parseLookups(),
        healthChecks = parseHealthChecks(),
    )

    // region Declarations

    private fun parseDeclarations(): List<PluginDeclaration> {
        val declarations = mutableListOf<PluginDeclaration>()
        for (match in declarationRegex.findAll(clean)) {
            val name = match.groupValues[2]
            var index = skipBlanks(match.range.last + 1)
            if (index < clean.length && clean[index] == '<') {
                index = SourceText.findMatching(clean, index, '<', '>')?.plus(1) ?: continue
            }

            var depth = 0
            var supertypesStart = -1
            var bodyStart = -1
            var headerEnd = clean.length
            while (index < clean.length) {
                when (val character = clean[index]) {
                    '(', '[', '<' -> depth++
                    ')', ']', '>' -> depth--
                    else -> if (depth == 0) {
                        when (character) {
                            ':' -> if (supertypesStart < 0) supertypesStart = index + 1
                            '{' -> {
                                bodyStart = index
                                headerEnd = index
                            }

                            ';' -> headerEnd = index
                            '\n' -> if (supertypesStart >= 0) headerEnd = index
                        }
                    }
                }
                if (headerEnd != clean.length) break
                index++
            }

            val supertypes = if (supertypesStart in 0 until headerEnd) {
                clean.substring(supertypesStart, headerEnd)
            } else {
                ""
            }
            val body = if (bodyStart >= 0) {
                val bodyEnd = SourceText.findMatching(clean, bodyStart, '{', '}') ?: clean.lastIndex
                clean.substring(bodyStart + 1, bodyEnd)
            } else {
                ""
            }

            val capabilityType = SourceText.weftPluginCapabilityType(supertypes)
                ?: SourceText.declaredPluginType(body)

            declarations += PluginDeclaration(
                name = name,
                capabilityType = capabilityType,
                location = file.locationAt(match.range.first),
            )
        }
        return declarations
    }

    // endregion

    // region install(...)

    private fun parseInstalls(): List<InstallSite> {
        val installs = mutableListOf<InstallSite>()
        for (match in installRegex.findAll(clean)) {
            val open = clean.indexOf('(', match.range.first)
            val close = SourceText.findMatching(clean, open, '(', ')') ?: continue
            val arguments = SourceText.splitTopLevel(clean, open + 1, close)
            val firstArgument = arguments.firstOrNull()?.let { span ->
                clean.substring(span.start, span.end).trim()
            }.orEmpty()
            val symbol = SourceText.pluginSymbol(firstArgument) ?: continue

            var literals = emptyMap<String, StringLiteral>()
            var literalLocations = emptyMap<String, SourceLocation>()
            val blockStart = skipBlanks(close + 1)
            if (blockStart < clean.length && clean[blockStart] == '{') {
                val blockEnd = SourceText.findMatching(clean, blockStart, '{', '}') ?: clean.lastIndex
                val assignments = readAssignments(blockStart + 1, blockEnd)
                literals = assignments.mapValues { it.value.first }
                literalLocations = assignments.mapValues { it.value.second }
            }

            installs += InstallSite(
                symbol = symbol,
                location = file.locationAt(match.range.first),
                capabilityType = null,
                literals = literals,
                literalLocations = literalLocations,
            )
        }
        return installs
    }

    /**
     * Reads every `property = "literal"` assignment inside a configuration block.
     *
     * Only direct string literals are returned: a value read from a variable, a call, or another
     * literal kind cannot be checked from source, and reporting it as empty would be wrong.
     */
    private fun readAssignments(start: Int, end: Int): Map<String, Pair<StringLiteral, SourceLocation>> {
        val assignments = linkedMapOf<String, Pair<StringLiteral, SourceLocation>>()
        val region = clean.substring(start, end)
        for (match in assignmentRegex.findAll(region)) {
            val property = match.groupValues[1]
            val valueStart = skipBlanks(start + match.range.last + 1)
            if (valueStart >= end || clean[valueStart] != '"') continue
            val (literal, _) = readLiteralAt(valueStart) ?: continue
            assignments.putIfAbsent(property, literal to file.locationAt(start + match.range.first))
        }
        return assignments
    }

    // endregion

    // region .get<T>()

    private fun parseLookups(): List<LookupSite> {
        val lookups = mutableListOf<LookupSite>()
        for (match in lookupRegex.findAll(clean)) {
            val open = clean.indexOf('<', match.range.first)
            val close = SourceText.findMatching(clean, open, '<', '>') ?: continue
            val type = SourceText.simpleName(clean.substring(open + 1, close)) ?: continue
            lookups += LookupSite(
                type = type,
                receiver = receiverBefore(match.range.first),
                location = file.locationAt(match.range.first),
            )
        }
        return lookups
    }

    /** The expression a `.get<T>()` call is called on, for example `LocalWeft.current`. */
    private fun receiverBefore(dotIndex: Int): String {
        var index = dotIndex - 1
        while (index >= 0 && (clean[index].isLetterOrDigit() || clean[index] == '_' || clean[index] == '.')) {
            index--
        }
        return clean.substring(index + 1, dotIndex).trim()
    }

    // endregion

    // region WeftHealthCheck(...)

    private fun parseHealthChecks(): List<DeclaredHealthCheck> {
        val checks = mutableListOf<DeclaredHealthCheck>()
        for (match in healthCheckRegex.findAll(clean)) {
            val open = clean.indexOf('(', match.range.first)
            val close = SourceText.findMatching(clean, open, '(', ')') ?: continue
            val check = readHealthCheck(match.range.first, open + 1, close) ?: continue
            checks += check
        }
        return checks
    }

    private fun readHealthCheck(declarationOffset: Int, start: Int, end: Int): DeclaredHealthCheck? {
        var name: String? = null
        var url: String? = null
        var method = "GET"
        var headers = emptyMap<String, String>()

        for (span in SourceText.splitTopLevel(clean, start, end)) {
            val argument = clean.substring(span.start, span.end)
            val named = namedArgumentRegex.find(argument) ?: continue
            val key = named.groupValues[1]
            val valueStart = span.start + named.range.last + 1
            when (key) {
                "name" -> name = literalValueAt(valueStart, span.end)
                "url" -> url = literalValueAt(valueStart, span.end)
                "method" -> method = literalValueAt(valueStart, span.end) ?: method
                "headers" -> headers = readMapLiterals(valueStart, span.end)
                else -> Unit
            }
        }

        val checkName = name ?: return null
        val checkUrl = url ?: return null
        return DeclaredHealthCheck(
            name = checkName,
            url = checkUrl,
            headers = headers,
            method = method,
            origin = "WeftHealthCheck declaration",
            location = file.locationAt(declarationOffset),
        )
    }

    /** Reads `mapOf("key" to "value", ...)` into a header map. */
    private fun readMapLiterals(start: Int, end: Int): Map<String, String> {
        val mapStart = clean.indexOf('(', start).takeIf { it in start until end } ?: return emptyMap()
        val mapEnd = SourceText.findMatching(clean, mapStart, '(', ')') ?: return emptyMap()
        val headers = linkedMapOf<String, String>()
        for (span in SourceText.splitTopLevel(clean, mapStart + 1, mapEnd)) {
            val entry = clean.substring(span.start, span.end)
            val separator = topLevelToSeparator(entry) ?: continue
            val key = literalValueAt(span.start, span.start + separator)
            val value = literalValueAt(span.start + separator + 4, span.end)
            if (key != null && value != null) headers[key] = value
        }
        return headers
    }

    private fun topLevelToSeparator(entry: String): Int? {
        var depth = 0
        var index = 0
        while (index < entry.length) {
            when (entry[index]) {
                '(', '[', '{', '<' -> depth++
                ')', ']', '}', '>' -> depth--
            }
            if (depth == 0 && entry.startsWith(" to ", index)) return index
            index++
        }
        return null
    }

    private fun literalValueAt(start: Int, end: Int): String? {
        val valueStart = skipBlanks(start)
        if (valueStart >= end) return null
        val (literal, _) = readLiteralAt(valueStart) ?: return null
        return literal.value.takeIf { !literal.hasInterpolation }
    }

    // endregion

    /** Reads the string literal beginning at [offset] and returns it with the offset just past it. */
    private fun readLiteralAt(offset: Int): Pair<StringLiteral, Int>? {
        if (offset >= text.length) return null
        if (text.startsWith("\"\"\"", offset)) {
            val end = text.indexOf("\"\"\"", startIndex = offset + 3)
            if (end < 0) return null
            val literal = KotlinStringLiterals.decode(text.substring(offset, end + 3)) ?: return null
            return literal to end + 3
        }
        if (text[offset] != '"') return null
        var index = offset + 1
        while (index < text.length) {
            when (text[index]) {
                '\\' -> index += 2
                '"' -> {
                    val literal = KotlinStringLiterals.decode(text.substring(offset, index + 1)) ?: return null
                    return literal to index + 1
                }

                '\n' -> return null
                else -> index++
            }
        }
        return null
    }

    private fun skipBlanks(start: Int): Int {
        var index = start
        while (index < clean.length && clean[index].isWhitespace()) index++
        return index
    }

    private companion object {
        val declarationRegex = Regex("""\b(class|object|interface)\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val installRegex = Regex("""\binstall\s*\(""")
        val lookupRegex = Regex("""\.\s*get\s*<""")
        val healthCheckRegex = Regex("""\bWeftHealthCheck\s*\(""")
        val assignmentRegex = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=""")
        val namedArgumentRegex = Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=""")
    }
}

/** Offsets into a source string, with [end] exclusive. */
internal data class Span(val start: Int, val end: Int)

internal object SourceText {
    fun findMatching(text: String, openIndex: Int, open: Char, close: Char): Int? {
        var depth = 0
        var index = openIndex
        while (index < text.length) {
            when (text[index]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return null
    }

    /** Splits the arguments in `[start, end)` at top-level commas. */
    fun splitTopLevel(text: String, start: Int, end: Int): List<Span> {
        val spans = mutableListOf<Span>()
        var depth = 0
        var itemStart = start
        var index = start
        while (index < end) {
            when (text[index]) {
                '(', '[', '{', '<' -> depth++
                ')', ']', '}', '>' -> depth--
                ',' -> if (depth == 0) {
                    spans += Span(itemStart, index)
                    itemStart = index + 1
                }
            }
            index++
        }
        if (itemStart < end) spans += Span(itemStart, end)
        return spans.filter { text.substring(it.start, it.end).isNotBlank() }
    }

    /** Last identifier of a type reference such as `com.example.MapPlugin`. */
    fun simpleName(type: String): String? {
        var depth = 0
        val visible = StringBuilder()
        for (character in type.trim()) {
            when (character) {
                '<' -> depth++
                '>' -> depth--
                else -> if (depth == 0) visible.append(character)
            }
        }
        val name = visible.toString().substringAfterLast('.').trim()
        return name.takeIf { identifierRegex.matches(it) }
    }

    /** Plugin type from a supertype list, e.g. `WeftPlugin<MapConfig, MapPlugin>`. */
    fun weftPluginCapabilityType(supertypes: String): String? {
        val marker = weftPluginRegex.find(supertypes) ?: return null
        val open = supertypes.indexOf('<', marker.range.first)
        val close = findMatching(supertypes, open, '<', '>') ?: return null
        val arguments = splitTopLevel(supertypes, open + 1, close)
        return arguments.getOrNull(1)?.let { simpleName(supertypes.substring(it.start, it.end)) }
    }

    /** Plugin type from `override val type: KClass<X> = X::class` inside a declaration body. */
    fun declaredPluginType(body: String): String? {
        val assignment = typeAssignmentRegex.find(body)
        if (assignment != null) return simpleName(assignment.groupValues[1])
        val declaration = typeDeclarationRegex.find(body)
        return declaration?.let { simpleName(it.groupValues[1]) }
    }

    /** The class or object name installed by an `install(...)` call. */
    fun pluginSymbol(argument: String): String? {
        val trimmed = argument.trim()
        val name = trimmed.substringAfterLast('.')
        return name.takeIf { identifierRegex.matches(it) }
    }

    private val identifierRegex = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
    private val weftPluginRegex = Regex("""\bWeftPlugin\s*<""")
    private val typeAssignmentRegex =
        Regex("""\boverride\s+val\s+type\b[^\n=]*=\s*([A-Za-z_][A-Za-z0-9_.]*)\s*::\s*class""")
    private val typeDeclarationRegex = Regex("""\bval\s+type\s*:\s*KClass\s*<\s*([A-Za-z_][A-Za-z0-9_.]*)\s*>""")
}
