package app.aurora.scripted.http

object ImageMimePolicy {
    private val allowedSubtypes = setOf("jpeg", "jpg", "png", "webp", "gif")

    fun isAllowed(contentType: String?): Boolean {
        if (contentType.isNullOrBlank()) return false
        val normalized = contentType.trim().lowercase()
        if (!normalized.startsWith("image/")) return false
        val subtype = normalized.removePrefix("image/").substringBefore(';').trim()
        return subtype in allowedSubtypes
    }
}
