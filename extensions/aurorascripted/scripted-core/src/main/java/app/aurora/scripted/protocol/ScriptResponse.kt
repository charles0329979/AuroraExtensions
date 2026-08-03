package app.aurora.scripted.protocol

/**
 * JS → Kotlin.
 */
data class ScriptResponse(
    val protocolVersion: Int,
    val sourceId: String,
    val operationId: ScriptedOperation,
    val requestId: String,
    val status: ScriptResponseStatus,
    val error: ScriptError? = null,
    val resources: List<ResourceRequest> = emptyList(),
    val payload: ScriptPayload? = null,
)

/**
 * Declared by JS. Not executable until validated into ResourceInstruction.
 */
data class ResourceRequest(
    val resourceId: String,
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: Map<String, String> = emptyMap(),
    val referer: String? = null,
    val expectedContentType: ExpectedContentType = ExpectedContentType.ANY,
    val timeoutMs: Long? = null,
    val retryPolicy: RetryPolicy = RetryPolicy.TRANSIENT_NETWORK,
    val sourceId: String,
)

/**
 * Kotlin-owned sanitized instruction after validation.
 * Host header is set exclusively from the URL by OkHttp — never copied from JS.
 */
data class ResourceInstruction(
    val resourceId: String,
    val sourceId: String,
    val url: String,
    val method: HttpMethod,
    val headers: Map<String, String>,
    val expectedContentType: ExpectedContentType,
    val timeoutMs: Long,
    val retryPolicy: RetryPolicy,
    val maxResponseBodyBytes: Long,
)

data class ScriptPayload(
    val mangasPage: MangasPagePayload? = null,
    val manga: MangaPayload? = null,
    val chapters: List<ChapterPayload>? = null,
    val pages: List<PagePayload>? = null,
)

data class MangasPagePayload(
    val mangas: List<MangaPayload>,
    val hasNextPage: Boolean,
)

data class MangaPayload(
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val initialized: Boolean = false,
)

data class ChapterPayload(
    val url: String,
    val name: String,
    val chapterNumber: Float? = null,
    val dateUpload: Long? = null,
    val scanlator: String? = null,
)

data class PagePayload(
    val index: Int,
    val imageUrl: String,
    val referer: String? = null,
)
