package app.aurora.scripted.protocol

/**
 * Minimal JSON helpers for protocol parsing (no kotlinx.serialization dependency).
 */
internal object ProtocolJson {
    fun unescape(value: String): String =
        buildString(value.length) {
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    when (val n = value[i + 1]) {
                        '"', '\\', '/' -> {
                            append(n)
                            i += 2
                        }
                        'n' -> {
                            append('\n')
                            i += 2
                        }
                        't' -> {
                            append('\t')
                            i += 2
                        }
                        else -> {
                            append(c)
                            i++
                        }
                    }
                } else {
                    append(c)
                    i++
                }
            }
        }

    fun sliceBalanced(text: String, start: Int, open: Char, close: Char): String? {
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else if (c == '\\') {
                    escape = true
                } else if (c == '"') {
                    inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    fun extractString(json: String, key: String): String? {
        val keyPattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val match = keyPattern.find(json) ?: return null
        return unescape(match.groupValues[1])
    }

    fun extractNullableString(json: String, key: String): String? {
        val nullPattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*null")
        if (nullPattern.containsMatchIn(json)) return null
        return extractString(json, key)
    }

    fun extractLong(json: String, key: String): Long? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)")
        val match = pattern.find(json) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    fun extractInt(json: String, key: String): Int? = extractLong(json, key)?.toInt()

    fun extractBoolean(json: String, key: String): Boolean? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(true|false)")
        val match = pattern.find(json) ?: return null
        return match.groupValues[1] == "true"
    }

    fun extractObject(json: String, key: String): String? {
        val keyIdx = Regex("\"${Regex.escape(key)}\"\\s*:").find(json)?.range?.last ?: return null
        val after = json.substring(keyIdx + 1).trimStart()
        if (after.startsWith("null")) return null
        val start = json.indexOf('{', keyIdx)
        if (start < 0) return null
        return sliceBalanced(json, start, '{', '}')
    }

    fun extractArray(json: String, key: String): List<String>? {
        val keyMatch = Regex("\"${Regex.escape(key)}\"\\s*:").find(json) ?: return null
        val start = json.indexOf('[', keyMatch.range.last)
        if (start < 0) return null
        val arrayBody = sliceBalanced(json, start, '[', ']') ?: return null
        val inner = arrayBody.substring(1, arrayBody.length - 1).trim()
        if (inner.isEmpty()) return emptyList()
        return splitTopLevelObjects(inner)
    }

    fun extractStringMap(obj: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val pattern = Regex("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        pattern.findAll(obj).forEach { m ->
            result[unescape(m.groupValues[1])] = unescape(m.groupValues[2])
        }
        return result
    }

    fun objectKeys(obj: String): Set<String> {
        val keys = linkedSetOf<String>()
        var depth = 0
        var inString = false
        var escape = false
        var i = 0
        while (i < obj.length) {
            val c = obj[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else if (c == '\\') {
                    escape = true
                } else if (c == '"') {
                    inString = false
                }
                i++
                continue
            }
            when (c) {
                '"' -> {
                    if (depth == 1) {
                        // potential key at object root level
                        val end = findStringEnd(obj, i)
                        if (end > i) {
                            val key = unescape(obj.substring(i + 1, end))
                            var j = end + 1
                            while (j < obj.length && obj[j].isWhitespace()) j++
                            if (j < obj.length && obj[j] == ':') {
                                keys.add(key)
                            }
                            i = end + 1
                            continue
                        }
                    }
                    inString = true
                }
                '{' -> depth++
                '}' -> depth--
                '[' -> {
                    // skip arrays at any depth by advancing with slice
                    val sliced = sliceBalanced(obj, i, '[', ']')
                    if (sliced != null) {
                        i += sliced.length
                        continue
                    }
                }
            }
            i++
        }
        return keys
    }

    private fun findStringEnd(text: String, startQuote: Int): Int {
        var escape = false
        for (i in startQuote + 1 until text.length) {
            val c = text[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') return i
        }
        return -1
    }

    private fun splitTopLevelObjects(inner: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escape = false
        for (i in inner.indices) {
            val c = inner[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else if (c == '\\') {
                    escape = true
                } else if (c == '"') {
                    inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out.add(inner.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return out
    }
}
