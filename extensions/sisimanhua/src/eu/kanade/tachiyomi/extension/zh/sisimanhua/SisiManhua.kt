package eu.kanade.tachiyomi.extension.zh.sisimanhua

import android.util.Base64
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
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

@Source
abstract class SisiManhua : HttpSource() {

    override val supportsLatest = false

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/statics/search.aspx?key=$query", headers)

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    private fun parseMangaPage(response: Response): MangasPage = MangasPage(
        response.asJsoup().select("li:has(a.ImgA[href^=/manhua/])")
            .mapNotNull(::mangaFromElement)
            .distinctBy { it.url },
        false,
    )

    private fun mangaFromElement(element: Element): SManga? {
        val link = element.selectFirst("a.ImgA[href^=/manhua/]") ?: return null
        val title = element.selectFirst("a.txtA")?.text().orEmpty()
        if (title.isBlank()) return null
        return SManga.create().apply {
            url = link.attr("href")
            this.title = title
            thumbnail_url = link.selectFirst("img")?.attr("src")
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info = document.select(".Introduct_Sub .sub_r p.txtItme")
        return SManga.create().apply {
            title = document.selectFirst(".Introduct_Sub h1.title")?.text().orEmpty()
            thumbnail_url = document.selectFirst(".Introduct_Sub .pic img")?.attr("src")
            author = info.getOrNull(1)?.text()?.trim()
            genre = info.getOrNull(2)?.text()?.trim()
            description = document.selectFirst("p.txtDesc")?.text()?.substringAfter("介绍:")?.trim()
            status = if (info.firstOrNull()?.text()?.contains("完结") == true) {
                SManga.COMPLETED
            } else {
                SManga.ONGOING
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("#mh-chapter-list-ol-0 a[href^=/manhua/]")
        .map { link ->
            SChapter.create().apply {
                url = link.attr("href")
                name = link.selectFirst("span")?.text().orEmpty()
            }
        }
        .distinctBy { it.url }

    override fun pageListParse(response: Response): List<Page> {
        val encoded = IMAGE_LIST_REGEX.find(response.body.string())?.groupValues?.get(1)
            ?: error("章节图片清单不存在")
        val decoded = Base64.decode(encoded, Base64.DEFAULT).decodeToString()
        return decoded.split(IMAGE_SEPARATOR)
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    companion object {
        private const val IMAGE_SEPARATOR = "\$qingtiandy\$"
        private val IMAGE_LIST_REGEX = Regex("""qTcms_S_m_murl_e\s*=\s*[\"']([^\"']+)""")
    }
}
