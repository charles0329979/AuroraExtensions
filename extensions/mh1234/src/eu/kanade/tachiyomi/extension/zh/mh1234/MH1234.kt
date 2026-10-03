package eu.kanade.tachiyomi.extension.zh.mh1234

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class MH1234 : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("category")
            addPathSegment("order")
            addPathSegment("hits")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()
        return mangaListParse(client.get(url))
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("category")
            addPathSegment("order")
            addPathSegment("addtime")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()
        return mangaListParse(client.get(url))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = if (query.isNotBlank()) {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addPathSegment(query)
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()
        mangaListParse(client.get(url))
    } else {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected?.second ?: "0"
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected?.second ?: "0"
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selected?.second ?: "id"
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("category")
            addPathSegment("tags")
            addPathSegment(genre)
            addPathSegment("finish")
            addPathSegment(status)
            addPathSegment("order")
            addPathSegment(sort)
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()
        mangaListParse(client.get(url))
    }

    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(MANGA_LIST_SELECTOR).map(::mangaFromElement)
        val hasNextPage = document.selectFirst(NEXT_PAGE_SELECTOR) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("a.comic-card__link")!!.let {
            setUrlWithoutDomain(it.absUrl("href"))
            title = it.selectFirst(".comic-card__title")!!.text()
            thumbnail_url = it.selectFirst("img.comic-card__image")?.absUrl("data-src")
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        val info = document.selectFirst(".mint-work-info") ?: error("作品详情不存在")
        title = info.selectFirst("#mintWorkTitle")?.text().orEmpty()
        thumbnail_url = document.selectFirst("#mintWorkCover")?.absUrl("src")
        author = info.select("p").getOrNull(0)?.text()?.removeSuffix(" 著")?.trim()
        val category = info.select("p").getOrNull(1)
        genre = category?.clone()?.also { it.select(".mint-tag").remove() }?.text()?.trim()
        status = when (category?.selectFirst(".mint-tag")?.text()) {
            "连载" -> SManga.ONGOING
            "完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = document.selectFirst("#mintIntroPanel > div")?.text()
            ?: document.selectFirst("meta[name=description]")?.attr("content")
    }

    private fun parseChapterList(document: Document): List<SChapter> = document
        .select("[data-mint-chapters] a[href^=/go/]")
        .mapNotNull { element ->
            val chapterTitle = element.text()
            if (chapterTitle.isBlank() || chapterTitle.contains("APP")) return@mapNotNull null
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = chapterTitle
            }
        }
        .reversed()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        var newUrl = chapter.url.replace("/go/", READER_URL)
        if (newUrl.startsWith("/")) newUrl = getChapterUrl(chapter)
        return client.get(newUrl).asJsoup().select("img.reader-image").mapIndexed { index, image ->
            Page(index, imageUrl = image.absUrl("data-src"))
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/category/").asJsoup()
        .select("a.filter-tag")
        .associate { it.text() to it.attr("href").substringAfterLast("/") }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            data?.parseAs<Map<String, String>>()?.let { add(GenreFilter(it)) }
            add(StatusFilter())
            add(SortFilter())
        },
    )

    companion object {
        private const val READER_URL = "https://reader.hqread.cc/r/"
        private const val MANGA_LIST_SELECTOR = ".comic-card"
        private const val NEXT_PAGE_SELECTOR = ".pagination-wrapper a:contains(下一页), .pagination-wrapper a:contains(>)"
    }
}
