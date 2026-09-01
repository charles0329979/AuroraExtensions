package eu.kanade.tachiyomi.extension.zh.manhuadaquan

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Source
abstract class ManhuaDaquan : HttpSource() {
    override val supportsLatest = true

    private fun Response.document(): Document = Jsoup.parse(body.byteStream(), null, request.url.toString())

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.document().select(".newUpdate li").take(20).map { element ->
            val link = element.selectFirst("a[href^=/manhua/]")!!
            SManga.create().apply {
                url = link.attr("href")
                title = link.attr("title").ifBlank { link.text() }
                thumbnail_url = link.attr("i")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isBlank()) {
        popularMangaRequest(page)
    } else {
        POST("$baseUrl/search.asp", headers, FormBody.Builder().add("key", query).build())
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = response.document().select("a[href^=/manhua/][title]")
            .distinctBy { it.attr("href") }
            .take(30)
            .map { link ->
                SManga.create().apply {
                    url = link.attr("href")
                    title = link.attr("title").ifBlank { link.text() }
                    thumbnail_url = link.attr("i").ifBlank {
                        link.selectFirst("img")?.attr("src")
                    }
                }
            }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.document()
        val info = document.selectFirst("#intro_l")!!
        title = info.selectFirst("h1")!!.text()
        thumbnail_url = info.selectFirst(".info_cover img")?.absUrl("src")
        author = info.select(".info p").firstOrNull { it.text().contains("原著作者") }
            ?.text()?.substringAfter("：")?.trim()
        genre = info.select(".info p").firstOrNull { it.text().contains("剧情类别") }
            ?.select("a")?.joinToString { it.text() }
        description = document.selectFirst("#intro1 p")?.text()
        status = SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.document().select("#play_0 li > a[href^=/p/]").map { link ->
        SChapter.create().apply {
            url = link.attr("href")
            name = link.attr("title").ifBlank { link.text() }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val script = response.document().html()
        val encoded = Regex("qTcms_S_m_murl_e=\"([^\"]+)\"").find(script)?.groupValues?.get(1)
            ?: error("Image data not found")
        val decoded = String(Base64.decode(encoded, Base64.DEFAULT))
        return decoded.split("\$qingtiandy\$")
            .filter { it.startsWith("http") }
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
