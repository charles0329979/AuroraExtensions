package eu.kanade.tachiyomi.extension.zh.soman

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
import java.util.Base64

/**
 * Reader-safe Soman route.
 *
 * Hisoman is an index and does not host reader images. Its raw results point to
 * a mixture of paid, retired and independently changing sites. This source uses
 * the current readable Veryim route from that index so every exposed result has
 * a local chapter and image pipeline instead of opening an external browser.
 */
@Source
abstract class Soman : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/", headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/update/", headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/statics/search.aspx".toHttpUrl().newBuilder()
            .addQueryParameter("key", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val mangas = response.asJsoup()
            .select(".media")
            .mapNotNull(::mangaFromCard)
            .distinctBy(SManga::url)
        return MangasPage(mangas, false)
    }

    private fun mangaFromCard(card: Element): SManga? {
        val anchor = card.selectFirst("a[href^=/manhua/][title]") ?: return null
        val title = anchor.attr("title").trim().ifBlank { anchor.text().trim() }
        if (title.isBlank()) return null
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(anchor.absUrl("href"))
            thumbnail_url = card.selectFirst("img")?.absUrl("src")?.normalizeUrl()
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        fun meta(property: String) = document.selectFirst("meta[property=$property]")?.attr("content")?.trim()
        val state = meta("og:novel:status").orEmpty()
        return SManga.create().apply {
            title = meta("og:title") ?: document.selectFirst(".info2 h1")?.text() ?: error("缺少漫画标题")
            author = meta("og:novel:author")
            genre = meta("og:novel:category")
            thumbnail_url = meta("og:image")?.normalizeUrl()
            description = meta("og:description")
                ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()
            status = when {
                state.contains("完结") -> SManga.COMPLETED
                state.contains("连载") -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select(".rowzhangjie .list-charts a[href^=/manhua/][href$=.html]")
        .distinctBy { it.absUrl("href") }
        .map { anchor ->
            SChapter.create().apply {
                name = anchor.text().trim()
                setUrlWithoutDomain(anchor.absUrl("href"))
            }
        }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val images = ENCODED_IMAGE_LIST.findAll(html)
            .map { it.groupValues[1] }
            .filter(String::isNotBlank)
            .flatMap { encoded ->
                val decoded = Base64.getDecoder().decode(encoded).toString(Charsets.UTF_8)
                decoded.split(IMAGE_SEPARATOR).asSequence()
            }
            .map(String::trim)
            .filter(String::isNotBlank)
            .map { it.normalizeUrl() }
            .distinct()
            .toList()
        require(images.isNotEmpty()) { "章节没有可读取的漫画图片" }
        return images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun String.normalizeUrl() = when {
        startsWith("//") -> "https:$this"
        else -> this
    }

    private companion object {
        const val IMAGE_SEPARATOR = "\$qingtiandy\$"
        val ENCODED_IMAGE_LIST = Regex("""qTcms_S_m_murl_e\d*\s*=\s*"([^"]*)"""")
    }
}
