package eu.kanade.tachiyomi.extension.zh.dmanhua

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
abstract class DManhua : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/sort".toHttpUrl().newBuilder()
            .addQueryParameter("category", "0")
            .addQueryParameter("tag", "0")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.asJsoup()
            .select("a[href^=/comic/]:has(article.card)")
            .mapNotNull(::mangaFromCard)
            .distinctBy(SManga::url)
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document
            .select("a[href^=/comic/]:has(article.card)")
            .mapNotNull(::mangaFromCard)
            .distinctBy(SManga::url)
        val hasNextPage = document.selectFirst("a[href*=page]:matchesOwn(下一页|下页|›|»)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromCard(anchor: Element): SManga? {
        val title = anchor.selectFirst(".cardtitle")?.text().orEmpty()
        if (title.isBlank()) return null
        val image = anchor.selectFirst("img")
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(anchor.absUrl("href"))
            thumbnail_url = image?.absUrl("data-src")?.ifBlank { image.absUrl("src") }
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info = document.selectFirst(".info") ?: error("详情结构已变化")
        val statusText = info.selectFirst(".status")?.text().orEmpty()
        return SManga.create().apply {
            title = info.selectFirst("h1")?.text() ?: error("缺少漫画标题")
            author = info.selectFirst(".author:contains(作者)")?.text()?.substringAfter("作者：")?.trim()
            genre = info.select(".author:contains(标签) a").joinToString { it.text() }
            thumbnail_url = document.selectFirst("img.cover")?.let { image ->
                image.absUrl("data-src").ifBlank { image.absUrl("src") }
            }
            description = document.selectFirst(".description")?.text()
            status = when {
                statusText.contains("完结") -> SManga.COMPLETED
                statusText.contains("连载") -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("a.chaper-btn[href^=/chapter/]")
        .distinctBy { it.absUrl("href") }
        .map { anchor ->
            SChapter.create().apply {
                name = anchor.text().trim()
                setUrlWithoutDomain(anchor.absUrl("href"))
            }
        }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val count = PAGE_COUNT_REGEX.find(html)?.groupValues?.get(1)?.toIntOrNull()
            ?: error("缺少正文页数")
        val imageBase = IMAGE_BASE_REGEX.find(html)?.groupValues?.get(1)
            ?: error("缺少正文图片目录")
        return (1..count).mapIndexed { index, page ->
            Page(index, imageUrl = "$imageBase$page.webp")
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private companion object {
        val PAGE_COUNT_REGEX = Regex("""var\s+num\s*=\s*eval\([\"'](\d+)[\"']\)""")
        val IMAGE_BASE_REGEX = Regex("""var\s+pasd\s*=\s*[\"'](https?://[^\"']+/)[\"']""")
    }
}
