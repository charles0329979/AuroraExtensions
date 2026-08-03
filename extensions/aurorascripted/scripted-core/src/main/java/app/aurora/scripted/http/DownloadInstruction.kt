package app.aurora.scripted.http

data class DownloadInstruction(
    val baseUrl: String,
    val allowedHosts: Set<String> = emptySet(),
    val headers: Map<String, String> = emptyMap(),
)

data class DownloadInstructionTag(
    val instruction: DownloadInstruction,
)
