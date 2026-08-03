package app.aurora.scripted.policy

import java.net.URI

object HostPolicy {
    fun isAllowed(
        baseUrl: String,
        requestUrl: String,
        allowedHosts: Set<String> = emptySet(),
    ): Boolean {
        val baseHost = try { URI(baseUrl).host?.lowercase() } catch (_: Exception) { null }
            ?: return false
        val uri = try { URI(requestUrl) } catch (_: Exception) { return false }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        if (host == baseHost) return true
        return allowedHosts.any { it.lowercase() == host }
    }
}
