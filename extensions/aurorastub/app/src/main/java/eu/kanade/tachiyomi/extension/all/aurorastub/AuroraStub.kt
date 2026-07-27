package eu.kanade.tachiyomi.extension.all.aurorastub

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource

/**
 * Hardcoded Mihon test extension — no real manga websites.
 * Page images are generic placeholders from placehold.co.
 */
class AuroraStub : HttpSource() {

    override val name = "Aurora Stub"
    override val lang = "en"
    override val baseUrl = "https://example.invalid"
    override val supportsLatest = true

    override suspend fun getPopularManga(page: Int): MangasPage {
        return MangasPage(allManga(), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        return MangasPage(allManga(), false)
    }

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val trimmed = query.trim()
        val results = if (trimmed.isEmpty()) {
            allManga()
        } else {
            allManga().filter { it.title.contains(trimmed, ignoreCase = true) }
        }
        return MangasPage(results, false)
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) mangaDetails(manga) else manga
        val chapterList = if (fetchChapters) chapterList(manga) else chapters
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        return listOf(
            Page(0, imageUrl = "https://placehold.co/800x1200/png?text=Aurora+P1"),
            Page(1, imageUrl = "https://placehold.co/800x1200/png?text=Aurora+P2"),
            Page(2, imageUrl = "https://placehold.co/800x1200/png?text=Aurora+P3"),
        )
    }

    private fun allManga(): List<SManga> = listOf(
        stubManga("/manga/1", "Aurora Demo One"),
        stubManga("/manga/2", "Aurora Demo Two"),
    )

    private fun stubManga(url: String, title: String): SManga = SManga.create().apply {
        this.url = url
        this.title = title
        this.thumbnail_url = "https://placehold.co/400x600/png?text=Aurora"
        this.author = "Aurora Stub"
        this.artist = "Aurora Stub"
        this.description = "Hardcoded stub title for AuroraReader extension testing. Not real manga."
        this.genre = "Test, Stub"
        this.status = SManga.ONGOING
        this.initialized = true
    }

    private fun mangaDetails(manga: SManga): SManga {
        return allManga().firstOrNull { it.url == manga.url } ?: manga
    }

    private fun chapterList(manga: SManga): List<SChapter> {
        val mangaId = manga.url.trimEnd('/').substringAfterLast('/')
        return listOf(
            SChapter.create().apply {
                url = "/manga/$mangaId/chapter/1"
                name = "Chapter 1"
                chapter_number = 1f
            },
            SChapter.create().apply {
                url = "/manga/$mangaId/chapter/2"
                name = "Chapter 2"
                chapter_number = 2f
            },
        )
    }
}