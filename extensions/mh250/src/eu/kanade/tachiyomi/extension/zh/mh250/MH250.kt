package eu.kanade.tachiyomi.extension.zh.mh250

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class MH250 : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/update", headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/a01".toHttpUrl().newBuilder()
            .addQueryParameter("searchkey", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val mangas = response.asJsoup()
            .select("a[href^=/book/]:has(img[alt])")
            .mapNotNull(::mangaFromElement)
            .distinctBy(SManga::url)
        return MangasPage(mangas, false)
    }

    private fun mangaFromElement(anchor: Element): SManga? {
        val image = anchor.selectFirst("img[alt]") ?: return null
        val title = image.attr("alt").trim()
        if (title.isEmpty()) return null
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(anchor.absUrl("href"))
            thumbnail_url = image.absUrl(imageUrlAttribute(image))
        }
    }

    private fun imageUrlAttribute(image: Element) = when {
        image.hasAttr("data-src") -> "data-src"
        image.hasAttr("data-original") -> "data-original"
        else -> "src"
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = meta(document, "og:title") ?: document.selectFirst("h1")?.text().orEmpty()
            author = meta(document, "og:novel:author")
            genre = meta(document, "og:novel:category")
            description = meta(document, "og:description")
                ?: document.selectFirst(".nmain_com_p2 p")?.text()
            thumbnail_url = meta(document, "og:image")
                ?: document.selectFirst(".nmain_com_p1 img[data-src]")?.absUrl("data-src")
            status = when (meta(document, "og:novel:status")) {
                "连载" -> SManga.ONGOING
                "完结", "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun meta(document: Document, property: String): String? =
        document.selectFirst("meta[property=$property]")
            ?.attr("content")
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("#ncp3_ul a[href$=.html]")
        .map { anchor ->
            SChapter.create().apply {
                name = anchor.selectFirst(".ncp3li_title")?.text()
                    ?: anchor.text()
                setUrlWithoutDomain(anchor.absUrl("href"))
            }
        }
        .asReversed()

    override fun pageListParse(response: Response): List<Page> = response.asJsoup()
        .select(".comicpage .imgpic img[data-original]")
        .mapIndexedNotNull { index, image ->
            image.absUrl("data-original")
                .takeIf(String::isNotEmpty)
                ?.let { Page(index, imageUrl = it) }
        }
        .also { check(it.isNotEmpty()) { "章节没有可读取的图片" } }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
