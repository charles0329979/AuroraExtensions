package eu.kanade.tachiyomi.extension.zh.manhua456

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

@Source
abstract class Manhua456 : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/rank/", headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response, false)

    override fun latestUpdatesRequest(page: Int) = GET(
        if (page == 1) "$baseUrl/update/" else "$baseUrl/update/$page/",
        headers,
    )

    override fun latestUpdatesParse(response: Response) = parseMangaList(response, true)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("keywords", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response, true)

    private fun parseMangaList(response: Response, paged: Boolean): MangasPage {
        val mangas = response.asJsoup()
            .select(".itemBox")
            .mapNotNull(::mangaFromCard)
            .distinctBy(SManga::url)
        return MangasPage(mangas, paged && mangas.size >= PAGE_SIZE)
    }

    private fun mangaFromCard(card: Element): SManga? {
        val anchor = card.selectFirst(".itemTxt a.title[href*=/manhua/]") ?: return null
        val title = anchor.text().trim()
        if (title.isBlank()) return null
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(anchor.absUrl("href").toHttps())
            thumbnail_url = card.selectFirst(".itemImg img")?.absUrl("src")?.toHttps()
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info = document.selectFirst(".Introduct_Sub") ?: error("详情结构已变化")
        val tags = info.select(".sub_r p.txtItme a").map { it.text().trim() }.filter(String::isNotBlank)
        return SManga.create().apply {
            title = document.selectFirst("#comicName")?.text() ?: error("缺少漫画标题")
            author = info.selectFirst(".sub_r p.txtItme")?.ownText()?.trim()
            genre = tags.filterNot { it == "连载中" || it == "已完结" || it == "完结" }.joinToString()
            thumbnail_url = info.selectFirst("#Cover img")?.absUrl("src")?.toHttps()
            description = document.selectFirst("#simple-des")?.text()?.removePrefix("介绍:")?.trim()
            status = when {
                tags.any { it.contains("完结") } -> SManga.COMPLETED
                tags.any { it.contains("连载") } -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("#list_block ul.Drama a[href*=/manhua/][href$=.html]")
        .distinctBy { it.absUrl("href") }
        .map { anchor ->
            SChapter.create().apply {
                name = anchor.text().trim()
                setUrlWithoutDomain(anchor.absUrl("href").toHttps())
            }
        }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val json = CHAPTER_IMAGES.find(html)?.groupValues?.get(1) ?: error("缺少章节图片清单")
        val chapterPath = CHAPTER_PATH.find(html)?.groupValues?.get(1).orEmpty()
        val images = json.parseAs<List<String>>()
        require(images.isNotEmpty()) { "章节没有漫画图片" }
        return images.mapIndexed { index, url -> Page(index, imageUrl = resolveImageUrl(url, chapterPath)) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun String.toHttps() = replace(HTTP_PREFIX, HTTPS_PREFIX)

    private fun resolveImageUrl(filename: String, chapterPath: String): String = when {
        filename.startsWith("http://") || filename.startsWith("https://") -> filename.toHttps()
        filename.startsWith("//") -> "https:$filename"
        filename.startsWith("/") || filename.startsWith("image") ->
            "$LEGACY_IMAGE_HOST/${filename.trimStart('/')}"
        chapterPath.isBlank() -> error("章节图片缺少路径")
        else -> {
            val chapterId = chapterPath.trim('/').substringAfterLast('/').toLongOrNull() ?: 0L
            val host = if (chapterId > NEW_IMAGE_THRESHOLD) NEW_IMAGE_HOST else LEGACY_IMAGE_HOST
            "$host/${chapterPath.trim('/')}/${filename.trimStart('/')}"
        }
    }

    private companion object {
        const val PAGE_SIZE = 36
        const val HTTP_PREFIX = "http://"
        const val HTTPS_PREFIX = "https://"
        const val LEGACY_IMAGE_HOST = "https://res.tgmhfc.uk"
        const val NEW_IMAGE_HOST = "https://res456.tgmhfc.uk"
        const val NEW_IMAGE_THRESHOLD = 600000L
        val CHAPTER_IMAGES = Regex("""var\s+chapterImages\s*=\s*(\[.*?])\s*;""")
        val CHAPTER_PATH = Regex("""var\s+chapterPath\s*=\s*"([^"]+)"""")
    }
}
