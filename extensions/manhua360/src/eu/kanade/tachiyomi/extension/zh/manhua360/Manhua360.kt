package eu.kanade.tachiyomi.extension.zh.manhua360

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

@keiyoushi.annotation.Source
abstract class Manhua360 : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET(listUrl("hits", page), headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int) = GET(listUrl("addtime", page), headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    private fun listUrl(order: String, page: Int): String =
        "$baseUrl/index.php/category/order/$order" + if (page > 1) "/page/$page" else ""

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/index.php/search".toHttpUrl().newBuilder()
            .addQueryParameter("key", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".common-comic-item, li.comic-item, .comic-list-item")
            .mapNotNull(::mangaFromCard)
            .distinctBy(SManga::url)
        val hasNextPage = document.selectFirst("a:matchesOwn(下一页|下页|›|»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromCard(card: Element): SManga? {
        val anchor = card.selectFirst("a.cover[href^=/index.php/comic/], a[href^=/index.php/comic/]:has(img.cover)") ?: return null
        val image = anchor.selectFirst("img")
        val title = image?.attr("alt")?.trim().orEmpty()
            .ifBlank { card.selectFirst(".comic__title, .comic-info-box .comic-name")?.text().orEmpty() }
        if (title.isBlank()) return null
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(anchor.absUrl("href"))
            thumbnail_url = image?.absUrl("data-original")?.ifBlank { image.absUrl("src") }
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info = document.selectFirst(".de-info__box, .detail-container > .comic-info-box")
            ?: error("详情结构已变化")
        val state = document.selectFirst(".de-chapter__title > span, .comic-update-info")?.text().orEmpty()
        return SManga.create().apply {
            title = info.selectFirst(".j-comic-title, h1.comic-name")?.text() ?: error("缺少漫画标题")
            author = info.selectFirst(".comic-author .name")?.text()
                ?: info.selectFirst(".au-name")?.text()?.substringAfter("作者：")?.trim()
            genre = info.select(".comic-status a[href*=category/tags]").joinToString { it.text() }
                .ifBlank { info.selectFirst(".comic-tags")?.ownText()?.trim().orEmpty() }
            thumbnail_url = info.selectFirst(".de-info__cover img")?.let { image ->
                image.absUrl("data-original").ifBlank { image.absUrl("src") }
            } ?: info.selectFirst(".box-back")?.attr("style")?.let { style ->
                BACKGROUND_REGEX.find(style)?.groupValues?.get(1)
            }
            description = document.selectFirst(".comic-intro .intro-total")?.text()
                ?: document.selectFirst(".detail-intro .comic-intro")?.text()
                ?: document.selectFirst(".comic-intro .intro")?.text()
            status = when {
                state.contains("完结") -> SManga.COMPLETED
                state.contains("连载") -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mobile = document.select(".catalog-list li.chapter-item a[href^=/index.php/chapter/]")
        val anchors = if (mobile.isNotEmpty()) {
            mobile
        } else {
            document.select(".chapter__list a.j-chapter-link[href^=/index.php/chapter/]").asReversed()
        }
        return anchors.distinctBy { it.absUrl("href") }.map { anchor ->
            SChapter.create().apply {
                name = anchor.text().trim()
                setUrlWithoutDomain(anchor.absUrl("href"))
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> = response.asJsoup()
        .select(".rd-article__pic img.lazy-read[data-original], li.comic-page img[src^=http]")
        .mapIndexed { index, image ->
            val url = image.absUrl("data-original").ifBlank { image.absUrl("src") }
                .replace("https://s1.bzcdn.net/", "https://s2.bzcdn.net/")
            Page(index, imageUrl = url)
        }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private companion object {
        val BACKGROUND_REGEX = Regex("""url\(['\"]?([^'\")]+)""")
    }
}
