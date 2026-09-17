package eu.kanade.tachiyomi.source.model

interface SManga {
    var url: String
    var title: String
    var thumbnail_url: String?
    var artist: String?
    var author: String?
    var status: Int
    var description: String?
    var genre: String?
    var update_strategy: UpdateStrategy
    var initialized: Boolean

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        fun create(): SManga = SMangaImpl()
    }
}

private class SMangaImpl : SManga {
    override var url: String = ""
    override var title: String = ""
    override var thumbnail_url: String? = null
    override var artist: String? = null
    override var author: String? = null
    override var status: Int = 0
    override var description: String? = null
    override var genre: String? = null
    override var update_strategy: UpdateStrategy = UpdateStrategy.ALWAYS_UPDATE
    override var initialized: Boolean = false
}