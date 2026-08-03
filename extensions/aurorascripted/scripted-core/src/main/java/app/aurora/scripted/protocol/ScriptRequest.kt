package app.aurora.scripted.protocol

/**
 * Kotlin → JS.
 */
data class ScriptRequest(
    val protocolVersion: Int,
    val sourceId: String,
    val operationId: ScriptedOperation,
    val requestId: String,
    val args: ScriptArgs,
    val priorResource: PriorResourceResult? = null,
)

data class ScriptArgs(
    val page: Int? = null,
    val query: String? = null,
    val mangaUrl: String? = null,
    val mangaKey: String? = null,
    val chapterUrl: String? = null,
    val chapterKey: String? = null,
    val html: String? = null,
    val filtersJson: String? = null,
)

data class PriorResourceResult(
    val resourceId: String,
    val finalUrl: String,
    val statusCode: Int,
    val contentType: String?,
    val bodyText: String? = null,
    val truncated: Boolean = false,
    val error: ScriptError? = null,
)
