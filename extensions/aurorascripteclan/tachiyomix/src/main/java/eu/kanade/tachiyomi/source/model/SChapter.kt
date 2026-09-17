package eu.kanade.tachiyomi.source.model

interface SChapter {
    var url: String
    var name: String
    var chapter_number: Float
    var scanlator: String?
    var date_upload: Long

    companion object {
        fun create(): SChapter = SChapterImpl()
    }
}

private class SChapterImpl : SChapter {
    override var url: String = ""
    override var name: String = ""
    override var chapter_number: Float = -1f
    override var scanlator: String? = null
    override var date_upload: Long = 0L
}