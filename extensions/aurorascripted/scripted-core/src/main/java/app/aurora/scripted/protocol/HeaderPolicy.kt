package app.aurora.scripted.protocol

sealed class HeaderSanitizeResult {
    data class Ok(val headers: Map<String, String>) : HeaderSanitizeResult()
    data class Err(val error: ScriptError) : HeaderSanitizeResult()
}

/**
 * Sanitizes JS-supplied headers against allowlist / denylist.
 * Top-level [referer] wins over headers[`Referer`].
 * Never includes `Host`.
 */
object HeaderPolicy {
    private val ALLOWLIST = setOf(
        "accept",
        "accept-language",
        "user-agent",
        "referer",
        "cache-control",
    )

    private val DENYLIST_EXACT = setOf(
        "cookie",
        "authorization",
        "proxy-authorization",
        "host",
        "connection",
        "transfer-encoding",
        "content-length",
        "expect",
    )

    private val CACHE_CONTROL_PATTERN = Regex("""^(no-cache|max-age=\d+)$""", RegexOption.IGNORE_CASE)

    fun sanitize(
        jsHeaders: Map<String, String>,
        referer: String?,
        limits: ProtocolLimits,
        defaultReferer: String? = null,
        userAgentOverride: String? = null,
    ): HeaderSanitizeResult {
        if (jsHeaders.size > limits.maxHeaderCount) {
            return HeaderSanitizeResult.Err(
                ScriptError(ScriptErrorCode.LIMIT_EXCEEDED, "Too many headers: ${jsHeaders.size}"),
            )
        }

        val out = linkedMapOf<String, String>()
        for ((rawName, rawValue) in jsHeaders) {
            if (rawName.length > limits.maxHeaderNameLength ||
                rawValue.length > limits.maxHeaderValueLength
            ) {
                return HeaderSanitizeResult.Err(
                    ScriptError(ScriptErrorCode.LIMIT_EXCEEDED, "Header name/value too long: $rawName"),
                )
            }
            if (rawName.contains('\r') || rawName.contains('\n') || rawName.contains(':') ||
                rawValue.contains('\r') || rawValue.contains('\n')
            ) {
                return HeaderSanitizeResult.Err(
                    ScriptError(ScriptErrorCode.HEADER_FORBIDDEN, "Header contains illegal characters: $rawName"),
                )
            }

            val name = rawName.lowercase()
            if (isDenied(name)) {
                return HeaderSanitizeResult.Err(
                    ScriptError(ScriptErrorCode.HEADER_FORBIDDEN, "Forbidden header: $rawName"),
                )
            }
            if (name !in ALLOWLIST) {
                return HeaderSanitizeResult.Err(
                    ScriptError(ScriptErrorCode.HEADER_NOT_ALLOWLISTED, "Header not allowlisted: $rawName"),
                )
            }
            if (name == "cache-control" && !CACHE_CONTROL_PATTERN.matches(rawValue.trim())) {
                return HeaderSanitizeResult.Err(
                    ScriptError(ScriptErrorCode.HEADER_FORBIDDEN, "Invalid Cache-Control value"),
                )
            }
            // Canonical casing for known headers
            val canonical = canonicalName(name)
            out[canonical] = rawValue
        }

        val effectiveReferer = referer ?: out["Referer"] ?: defaultReferer
        out.remove("Referer")
        if (effectiveReferer != null) {
            if (effectiveReferer.length > limits.maxHeaderValueLength) {
                return HeaderSanitizeResult.Err(
                    ScriptError(ScriptErrorCode.LIMIT_EXCEEDED, "Referer too long"),
                )
            }
            out["Referer"] = effectiveReferer
        }

        if (userAgentOverride != null) {
            out["User-Agent"] = userAgentOverride
        }

        // Host must never appear
        out.keys.find { it.equals("Host", ignoreCase = true) }?.let {
            out.remove(it)
        }

        return HeaderSanitizeResult.Ok(out)
    }

    private fun isDenied(nameLower: String): Boolean {
        if (nameLower in DENYLIST_EXACT) return true
        if (nameLower.startsWith("proxy-")) return true
        if (nameLower.startsWith("x-forwarded-")) return true
        return false
    }

    private fun canonicalName(nameLower: String): String =
        when (nameLower) {
            "accept" -> "Accept"
            "accept-language" -> "Accept-Language"
            "user-agent" -> "User-Agent"
            "referer" -> "Referer"
            "cache-control" -> "Cache-Control"
            else -> nameLower
        }
}
