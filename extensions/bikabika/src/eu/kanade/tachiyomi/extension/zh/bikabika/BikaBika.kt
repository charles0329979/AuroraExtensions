package eu.kanade.tachiyomi.extension.zh.bikabika

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
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

@Source
abstract class BikaBika : HttpSource() {

    override val supportsLatest = true

    override val client by lazy {
        network.client.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category/order/hits/page/$page", headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response, true)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/category/order/addtime/page/$page", headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response, true)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("key", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response, false)

    private fun parseMangaList(response: Response, paged: Boolean): MangasPage {
        val mangas = response.asJsoup()
            .select(".item > a.main:has(img), .books-row .item > a[href]:has(img)")
            .mapNotNull(::mangaFromElement)
            .distinctBy(SManga::url)
        return MangasPage(mangas, paged && mangas.size >= PAGE_SIZE)
    }

    private fun mangaFromElement(anchor: Element): SManga? {
        val image = anchor.selectFirst("img") ?: return null
        val title = image.attr("alt").trim()
            .ifEmpty { anchor.selectFirst(".title")?.text().orEmpty() }
        if (title.isEmpty()) return null
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(anchor.absUrl("href"))
            thumbnail_url = image.absUrl(
                if (image.hasAttr("data-original")) "data-original" else "src",
            )
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".book-hero h1.title")?.text()
                ?: error("缺少漫画标题")
            thumbnail_url = document.selectFirst(".book-hero img.bg")?.absUrl("src")
            author = document.selectFirst(".book-container__author")?.text()?.removePrefix("作者：")
            genre = document.selectFirst(".book-hero .tags")?.text()
            description = document.selectFirst(".book-container__detail")?.text()
            status = SManga.UNKNOWN
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select(".episode-list .desc_item > a[href$=.html]")
        .distinctBy { it.text() }
        .map { anchor ->
            SChapter.create().apply {
                name = anchor.text()
                setUrlWithoutDomain(anchor.absUrl("href"))
            }
        }
        .asReversed()

    override fun pageListParse(response: Response): List<Page> = response.asJsoup()
        .select("img.lazy-read[data-original]")
        .mapIndexedNotNull { index, image ->
            image.absUrl("data-original")
                .takeIf(String::isNotEmpty)
                ?.let { Page(index, imageUrl = it) }
        }
        .also { check(it.isNotEmpty()) { "章节没有可读取的图片" } }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private companion object {
        const val PAGE_SIZE = 20
    }
}
