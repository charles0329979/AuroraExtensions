package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.CatalogueSource

abstract class HttpSource : CatalogueSource {
    abstract val baseUrl: String
    open val versionId: Int = 1
    override val id: Long = 0L
}