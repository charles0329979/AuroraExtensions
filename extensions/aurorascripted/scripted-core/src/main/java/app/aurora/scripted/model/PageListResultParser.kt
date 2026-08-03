package app.aurora.scripted.model

import java.net.URI

object PageListResultParser {
    private val DENYLISTED_HEADERS = setOf(
        "cookie",
        "authorization",
        "proxy-authorization",
    )

    fun parse(json: String): PageListResult {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw IllegalArgumentException("Invalid JSON")
        }

        val pagesRaw = extractArray(trimmed, "pages")
            ?: throw IllegalArgumentException("Missing pages")
        if (pagesRaw.isEmpty()) {
            throw IllegalArgumentException("Empty pages")
        }

        val pages = pagesRaw.map { parsePageObject(it) }
        val allowedHosts = extractStringArray(trimmed, "allowedHosts")?.toSet() ?: emptySet()
        return PageListResult(pages = pages, allowedHosts = allowedHosts)
    }

    private fun parsePageObject(obj: String): ScriptedPage {
        val imageUrl = extractString(obj, "imageUrl")
            ?: throw IllegalArgumentException("Missing imageUrl")
        if (imageUrl.isEmpty()) {
            throw IllegalArgumentException("Missing imageUrl")
        }
        validateImageUrl(imageUrl)

        val headersObj = extractObject(obj, "headers")
        val headers =
            if (headersObj == null) {
                emptyMap()
            } else {
                extractStringMap(headersObj)
                    .filterKeys { it.lowercase() !in DENYLISTED_HEADERS }
            }
        return ScriptedPage(imageUrl = imageUrl, headers = headers)
    }

    private fun validateImageUrl(imageUrl: String) {
        val uri =
            try {
                URI(imageUrl)
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid imageUrl: $imageUrl")
            }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw IllegalArgumentException("Invalid imageUrl scheme: $imageUrl")
        }
    }

    /** Extract a JSON string field value (handles escaped quotes minimally). */
    private fun extractString(json: String, key: String): String? {
        val keyPattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val match = keyPattern.find(json) ?: return null
        return unescape(match.groupValues[1])
    }

    private fun extractObject(json: String, key: String): String? {
        val keyIdx = Regex("\"${Regex.escape(key)}\"\\s*:").find(json)?.range?.last ?: return null
        val start = json.indexOf('{', keyIdx)
        if (start < 0) return null
        return sliceBalanced(json, start, '{', '}')
    }

    private fun extractArray(json: String, key: String): List<String>? {
        val keyMatch = Regex("\"${Regex.escape(key)}\"\\s*:").find(json) ?: return null
        val start = json.indexOf('[', keyMatch.range.last)
        if (start < 0) return null
        val arrayBody = sliceBalanced(json, start, '[', ']') ?: return null
        val inner = arrayBody.substring(1, arrayBody.length - 1).trim()
        if (inner.isEmpty()) return emptyList()
        return splitTopLevelObjects(inner)
    }

    private fun extractStringArray(json: String, key: String): List<String>? {
        val keyMatch = Regex("\"${Regex.escape(key)}\"\\s*:").find(json) ?: return null
        val start = json.indexOf('[', keyMatch.range.last)
        if (start < 0) return null
        val arrayBody = sliceBalanced(json, start, '[', ']') ?: return null
        val inner = arrayBody.substring(1, arrayBody.length - 1)
        val values = Regex("\"((?:\\\\.|[^\"\\\\])*)\"").findAll(inner).map { unescape(it.groupValues[1]) }.toList()
        return values
    }

    private fun extractStringMap(obj: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val pattern = Regex("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        pattern.findAll(obj).forEach { m ->
            result[unescape(m.groupValues[1])] = unescape(m.groupValues[2])
        }
        return result
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

    private fun sliceBalanced(text: String, start: Int, open: Char, close: Char): String? {
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

    private fun unescape(value: String): String =
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
}
