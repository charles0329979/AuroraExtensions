package eu.kanade.tachiyomi.extension.zh.didamanhua

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
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class DidaManhua : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")

    override fun popularMangaRequest(page: Int) = GET(categoryUrl(page), headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int) = GET(categoryUrl(page), headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // The site's public search endpoint currently returns an empty list even
        // for known titles. Keep search useful by filtering its paged catalogue.
        val url = categoryUrl(page).toHttpUrl().newBuilder()
            .addQueryParameter(LOCAL_QUERY, query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.queryParameter(LOCAL_QUERY).orEmpty()
        return parseMangaList(response, query)
    }

    private fun categoryUrl(page: Int) = if (page <= 1) "$baseUrl/category" else "$baseUrl/category/page/$page"

    private fun parseMangaList(response: Response, query: String = ""): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".lists-content li:has(a[href^=/book/])")
            .mapNotNull(::mangaFromCard)
            .distinctBy(SManga::url)
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
        val hasNextPage = document.selectFirst("a:matchesOwn(下一页|下页), a[href*=page/${currentPage(response) + 1}]") != null ||
            document.select(".lists-content li:has(a[href^=/book/])").size >= PAGE_SIZE
        return MangasPage(mangas, hasNextPage)
    }

    private fun currentPage(response: Response): Int = response.request.url.pathSegments
        .lastOrNull()
        ?.toIntOrNull()
        ?: 1

    private fun mangaFromCard(card: Element): SManga? {
        val anchor = card.selectFirst("a.vodlist__thumb[href^=/book/]")
            ?: card.selectFirst("a[href^=/book/]")
            ?: return null
        val title = anchor.attr("title").ifBlank { card.selectFirst("h2")?.text().orEmpty() }
        if (title.isBlank()) return null
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(anchor.absUrl("href"))
            thumbnail_url = anchor.attr("data-original").ifBlank {
                card.selectFirst("img")?.let { it.attr("data-original").ifBlank { it.absUrl("src") } }.orEmpty()
            }
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val header = document.selectFirst(".product-header") ?: error("详情结构已变化")
        val excerpts = header.select(".product-excerpt")
        val statusText = excerpts.firstOrNull { it.text().startsWith("状态：") }?.text().orEmpty()
        return SManga.create().apply {
            title = header.selectFirst(".product-title")?.ownText()?.trim().orEmpty()
                .ifBlank { error("缺少漫画标题") }
            author = excerpts.firstOrNull { it.text().startsWith("作者：") }
                ?.selectFirst("span")?.text()
            genre = excerpts.firstOrNull { it.text().startsWith("类型：") }
                ?.select("a")?.eachText()?.joinToString()
            status = when {
                statusText.contains("完结") -> SManga.COMPLETED
                statusText.contains("连载") -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
            thumbnail_url = header.selectFirst("img.thumb")?.absUrl("src")
            description = excerpts.firstOrNull { it.text().startsWith("漫画简介：") }
                ?.selectFirst("span")?.text()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("#myList a[href^=/chapter/]")
        .map { anchor ->
            SChapter.create().apply {
                name = anchor.text()
                setUrlWithoutDomain(anchor.absUrl("href"))
            }
        }
        .asReversed()

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val encrypted = PARAMS_REGEX.find(html)?.groupValues?.get(1) ?: error("缺少阅读参数")
        val payload = decryptParams(encrypted).parseAs<ChapterPayload>()
        require(payload.host == "ddmanhua.com") { "阅读参数域名不匹配" }
        return payload.images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun decryptParams(encoded: String): String {
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size > AES_BLOCK_BYTES) { "阅读参数长度无效" }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(PARAMS_KEY.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(bytes.copyOfRange(0, AES_BLOCK_BYTES)),
        )
        return cipher.doFinal(bytes.copyOfRange(AES_BLOCK_BYTES, bytes.size)).toString(Charsets.UTF_8)
    }

    private companion object {
        const val AES_BLOCK_BYTES = 16
        const val PAGE_SIZE = 30
        const val PARAMS_KEY = "9S8\$vJnU2ANeSRoF"
        const val LOCAL_QUERY = "aurora_query"
        val PARAMS_REGEX = Regex("""params\s*=\s*'([^']+)'""")
    }
}

@Serializable
private data class ChapterPayload(
    val host: String,
    val images: List<String>,
)
