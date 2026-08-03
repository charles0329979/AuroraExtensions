package app.aurora.scripted.protocol

data class ScriptedSourceManifest(
    val protocolVersion: Int = SCRIPTED_PROTOCOL_VERSION,
    val sourceId: String,
    val name: String,
    val lang: String,
    val baseUrl: String,
    val capabilities: ScriptCapability,
    /** Optional default UA override (still applied only by Kotlin). */
    val userAgent: String? = null,
    /** Default Referer template or absolute URL; Kotlin may apply if JS omits Referer. */
    val defaultReferer: String? = null,
    val limits: ProtocolLimits = ProtocolLimits(),
)
