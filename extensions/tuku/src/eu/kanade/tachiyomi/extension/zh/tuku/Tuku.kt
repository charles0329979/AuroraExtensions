package eu.kanade.tachiyomi.extension.zh.tuku

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
import org.jsoup.nodes.Element

@Source
abstract class Tuku : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/rank/", headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response, false)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/comics/", headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response, false)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("title", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response, false)

    private fun parseMangaList(response: Response, hasNextPage: Boolean): MangasPage {
        val mangas = response.asJsoup()
            .select(".rank-item, a[href^=\"/manga-\"][title]:has(img)")
            .mapNotNull(::mangaFromElement)
            .distinctBy(SManga::url)
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val link = if (element.tagName() == "a") {
            element
        } else {
            element.selectFirst("a.rank-card[href^=\"/manga-\"]") ?: return null
        }
        val title = link.attr("title").ifBlank {
            element.selectFirst(".rank-title[title]")?.attr("title").orEmpty()
        }.ifBlank { link.text() }
        if (title.isBlank()) return null
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(link.absUrl("href"))
            thumbnail_url = link.selectFirst("img")?.let { image ->
                image.absUrl("data-original").ifBlank { image.absUrl("src") }
            }
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info = document.selectFirst(".info-card") ?: error("作品详情不存在")
        val statusText = info.text()
        return SManga.create().apply {
            title = info.selectFirst(".info-text .title")?.text() ?: error("缺少漫画标题")
            thumbnail_url = info.selectFirst(".info-bg img")?.let { image ->
                image.absUrl("data-original").ifBlank { image.absUrl("src") }
            }
            author = info.selectFirst(".info-text .author")?.text()?.substringAfter("：")?.trim()
            genre = info.select(".tag-item").joinToString { it.text() }
            description = document.selectFirst(".manga-desc")?.text()
            status = when {
                statusText.contains("完结") -> SManga.COMPLETED
                statusText.contains("连载") -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("a.chapter-item[href^=/chapter]")
        .distinctBy { it.absUrl("href") }
        .map { anchor ->
            SChapter.create().apply {
                name = anchor.text().trim().ifBlank { anchor.attr("title") }
                setUrlWithoutDomain(anchor.absUrl("href"))
            }
        }
        .asReversed()

    override fun pageListParse(response: Response): List<Page> = response.asJsoup()
        .select("img.no-drag[data-original]")
        .mapIndexedNotNull { index, image ->
            image.absUrl("data-original").takeIf(String::isNotBlank)?.let { Page(index, imageUrl = it) }
        }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)
}
