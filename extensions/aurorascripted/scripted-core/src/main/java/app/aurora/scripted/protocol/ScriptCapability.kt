package app.aurora.scripted.protocol

data class ScriptCapability(
    val operations: Set<ScriptedOperation>,
    /** Extra hosts beyond baseUrl host (CDN, image CDN). Lowercase FQDNs. */
    val allowedHosts: Set<String> = emptySet(),
    /** If false, http:// is rejected even for loopback. Default false → HTTPS only. */
    val allowHttp: Boolean = false,
)
