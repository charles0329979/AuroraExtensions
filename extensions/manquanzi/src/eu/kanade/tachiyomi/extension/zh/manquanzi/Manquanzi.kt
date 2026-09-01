package eu.kanade.tachiyomi.extension.zh.manquanzi

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
abstract class Manquanzi : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/custom/top", headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/custom/update", headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addPathSegment(query)
            .apply { if (page > 1) addPathSegment(page.toString()) }
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document
            .select("li.acgn-item:has(a.acgn-thumbnail[href^=/comic/]), .down-game-ul li:has(a.pic[href^=/comic/])")
            .mapNotNull(::mangaFromCard)
            .distinctBy(SManga::url)
        val hasNextPage = document.selectFirst("#pnv a:matchesOwn(下一页|下页|›|»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromCard(card: Element): SManga? {
        val anchor = card.selectFirst("a.acgn-thumbnail[href^=/comic/], a.pic[href^=/comic/]") ?: return null
        val title = card.selectFirst(".acgn-title")?.text()
            ?: anchor.selectFirst("img[alt]")?.attr("alt")
            ?: anchor.attr("title").substringBefore(',')
        if (title.isBlank()) return null
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(anchor.absUrl("href"))
            thumbnail_url = anchor.selectFirst("img.cover, img")?.absUrl("src")
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val statusText = document.selectFirst("meta[property=og:novel:status]")?.attr("content").orEmpty()
        return SManga.create().apply {
            title = document.selectFirst("meta[property=og:novel:book_name]")?.attr("content")
                ?.takeIf(String::isNotBlank)
                ?: document.selectFirst("#detail-title")?.text()
                ?: error("缺少漫画标题")
            author = document.selectFirst("meta[property=og:novel:author]")?.attr("content")
            genre = document.selectFirst("meta[property=og:novel:category]")?.attr("content")
            description = document.selectFirst("meta[property=og:description]")?.attr("content")
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            status = when {
                statusText.contains("完结") -> SManga.COMPLETED
                statusText.contains("连载") -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("#j_chapter_list a[href^=/chapter/], a.wekrank-slide[href^=/chapter/]")
        .map { anchor ->
            SChapter.create().apply {
                name = anchor.attr("title").trim().ifBlank {
                    anchor.selectFirst(".bookname")?.text()?.trim().orEmpty().ifBlank { anchor.text().trim() }
                }
                setUrlWithoutDomain(anchor.absUrl("href"))
            }
        }
        .asReversed()

    override fun pageListParse(response: Response): List<Page> = response.asJsoup()
        .select(".rd-article__pic img.lazy-read[data-original], li.comic-page img[src]")
        .mapIndexed { index, image ->
            val url = image.attr("data-original").ifBlank { image.absUrl("src") }
            Page(index, imageUrl = url)
        }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
