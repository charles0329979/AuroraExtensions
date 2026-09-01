package eu.kanade.tachiyomi.extension.zh.manhua36

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
abstract class Manhua36 : HttpSource() {

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
        val mangas = document.select("ul.rankList > li")
            .mapNotNull(::mangaFromCard)
            .distinctBy(SManga::url)
        val hasNextPage = document.selectFirst(".divpage a.end[href]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromCard(card: Element): SManga? {
        val titleNode = card.selectFirst(".info .title") ?: return null
        val anchor = titleNode.parent() ?: return null
        val href = anchor.attr("href")
        if (!href.startsWith("/manhua/") || href.endsWith(".html")) return null
        return SManga.create().apply {
            title = titleNode.text().trim()
            setUrlWithoutDomain(anchor.absUrl("href"))
            thumbnail_url = card.selectFirst("a[href^=/manhua/] img")?.absUrl("src")?.normalizeImageUrl()
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info = document.selectFirst(".detailTop .content .info") ?: error("详情结构已变化")
        val subtitles = info.select("p.subtitle")
        val state = info.selectFirst("p.subtitle.y")?.text().orEmpty()
        return SManga.create().apply {
            title = info.selectFirst("p.title")?.text() ?: error("缺少漫画标题")
            author = subtitles.firstOrNull { it.text().startsWith("作者：") }
                ?.text()?.substringAfter("作者：")?.trim()
            genre = subtitles.firstOrNull { it.text().startsWith("类型：") }
                ?.select("a")?.joinToString { it.text() }
            thumbnail_url = document.selectFirst(".detailTop img.cover")?.absUrl("src")?.normalizeImageUrl()
            description = document.selectFirst(".detailContent > p")?.text()
            status = when {
                state.contains("完结") -> SManga.COMPLETED
                state.contains("更新") || state.isNotBlank() -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("ul.chapterList a[href^=/manhua/][href$=.html]")
        .map { anchor ->
            SChapter.create().apply {
                name = anchor.text().trim()
                setUrlWithoutDomain(anchor.absUrl("href"))
            }
        }
        .asReversed()

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val encrypted = PARAMS_REGEX.find(html)?.groupValues?.get(1) ?: error("缺少阅读参数")
        val payload = decryptParams(encrypted).parseAs<ChapterPayload>()
        require(payload.host == "m.36mh.org") { "阅读参数域名不匹配" }
        require(payload.images.isNotEmpty()) { "章节没有漫画图片" }
        return payload.images.mapIndexed { index, url -> Page(index, imageUrl = url.normalizeImageUrl()) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun String.normalizeImageUrl() = replace("http://www.36mh.org", "https://m.36mh.org")

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
        const val PARAMS_KEY = "9S8\$vJnU2ANeSRoF"
        val PARAMS_REGEX = Regex("""params\s*=\s*'([^']+)'""")
    }
}

@Serializable
private data class ChapterPayload(
    val host: String,
    val images: List<String>,
)
