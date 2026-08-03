package app.aurora.scripted.model

data class ScriptedPage(
    val imageUrl: String,
    val headers: Map<String, String> = emptyMap(),
)

data class PageListResult(
    val pages: List<ScriptedPage>,
    val allowedHosts: Set<String> = emptySet(),
)
