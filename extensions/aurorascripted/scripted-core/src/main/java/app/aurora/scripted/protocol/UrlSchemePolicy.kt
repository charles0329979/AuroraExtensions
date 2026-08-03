package app.aurora.scripted.protocol

import java.net.URI

/**
 * Validates URL scheme, host allowlist, userinfo ban, and length.
 * Returns null on success, or a [ScriptError] on failure.
 */
object UrlSchemePolicy {
    private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "::1")
    private val FORBIDDEN_SCHEMES = setOf(
        "javascript", "file", "content", "data", "blob", "about", "ws", "wss",
    )

    fun check(
        url: String,
        baseUrl: String,
        allowedHosts: Set<String>,
        allowHttp: Boolean,
        maxUrlLength: Int,
    ): ScriptError? {
        if (url.length > maxUrlLength) {
            return ScriptError(ScriptErrorCode.LIMIT_EXCEEDED, "URL exceeds maxUrlLength")
        }

        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return ScriptError(ScriptErrorCode.INVALID_URL, "Unparseable URL")
        }

        val scheme = uri.scheme?.lowercase()
            ?: return ScriptError(ScriptErrorCode.INVALID_URL, "Missing scheme")

        if (scheme in FORBIDDEN_SCHEMES) {
            return ScriptError(ScriptErrorCode.SCHEME_FORBIDDEN, "Forbidden scheme: $scheme")
        }

        when (scheme) {
            "https" -> Unit
            "http" -> {
                if (!allowHttp) {
                    return ScriptError(ScriptErrorCode.SCHEME_FORBIDDEN, "HTTP not allowed")
                }
                val host = uri.host?.lowercase()
                    ?: return ScriptError(ScriptErrorCode.INVALID_URL, "Missing host")
                if (host !in LOOPBACK_HOSTS) {
                    return ScriptError(
                        ScriptErrorCode.SCHEME_FORBIDDEN,
                        "HTTP only allowed for loopback when allowHttp=true",
                    )
                }
            }
            else -> return ScriptError(ScriptErrorCode.SCHEME_FORBIDDEN, "Unsupported scheme: $scheme")
        }

        if (uri.userInfo != null && uri.userInfo.isNotEmpty()) {
            return ScriptError(ScriptErrorCode.INVALID_URL, "Userinfo forbidden in URL")
        }

        val host = uri.host?.lowercase()
            ?: return ScriptError(ScriptErrorCode.INVALID_URL, "Missing host")

        val baseHost = try {
            URI(baseUrl).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return ScriptError(ScriptErrorCode.INVALID_URL, "Invalid baseUrl")

        val allowed = allowedHosts.map { it.lowercase() }.toSet()
        if (host != baseHost && host !in allowed) {
            return ScriptError(ScriptErrorCode.HOST_NOT_ALLOWED, "Host not allowed: $host")
        }

        return null
    }
}
