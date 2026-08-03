package app.aurora.scripted.protocol

data class ProtocolLimits(
    val maxUrlLength: Int = 2048,
    val maxHeaderCount: Int = 16,
    val maxHeaderNameLength: Int = 64,
    val maxHeaderValueLength: Int = 1024,
    val maxRequestBodyBytes: Long = 0L,
    val maxResponseBodyBytes: Long = 2_000_000L,
    val defaultTimeoutMs: Long = 15_000L,
    val maxTimeoutMs: Long = 30_000L,
    val maxRetries: Int = 2,
    val maxResourcesPerResponse: Int = 8,
    val maxMangasPerPage: Int = 50,
    val maxChapters: Int = 500,
    val maxPages: Int = 200,
)
