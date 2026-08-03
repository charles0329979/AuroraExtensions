package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.OkHttpClient
import okhttp3.Request

abstract class HttpSource : CatalogueSource {
    abstract val baseUrl: String
    open val versionId: Int = 1
    override val id: Long = 0L

    open val client: OkHttpClient
        get() = throw UnsupportedOperationException("Provided by host at runtime")

    open fun imageRequest(page: Page): Request =
        throw UnsupportedOperationException("Provided by host at runtime")
}
