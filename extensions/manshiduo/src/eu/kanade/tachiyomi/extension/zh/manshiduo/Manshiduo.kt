package eu.kanade.tachiyomi.extension.zh.manshiduo

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
abstract class Manshiduo : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/custom/update", headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/custom/update".toHttpUrl().newBuilder()
            .addQueryParameter(LOCAL_QUERY, query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.queryParameter(LOCAL_QUERY).orEmpty()
        return parseMangaList(response, query)
    }

    private fun parseMangaList(response: Response, query: String = ""): MangasPage {
        val mangas = response.asJsoup()
            .select("ul.indexList li:has(a[href^=/comic/])")
            .mapNotNull(::mangaFromCard)
            .distinctBy(SManga::url)
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    private fun mangaFromCard(card: Element): SManga? {
        val anchor = card.selectFirst("a[href^=/comic/]") ?: return null
        val image = anchor.selectFirst("img") ?: return null
        val title = image.attr("title").ifBlank { image.attr("alt") }
        if (title.isBlank()) return null
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(anchor.absUrl("href"))
            thumbnail_url = image.absUrl("src")
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val content = document.selectFirst(".detailTop .content") ?: error("详情结构已变化")
        return SManga.create().apply {
            title = content.selectFirst(".title")?.text() ?: error("缺少漫画标题")
            author = content.selectFirst(".subtitle:contains(作者：)")?.text()?.removePrefix("作者：")
            genre = content.selectFirst(".subtitle:contains(类型：)")?.text()?.removePrefix("类型：")
            thumbnail_url = content.selectFirst("img.cover")?.absUrl("src")
            description = document.selectFirst(".detailContent > p")?.text()
            status = SManga.UNKNOWN
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select(".chapterList a[href^=/chapter/]")
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
        require(payload.host == "manshiduo.org") { "阅读参数域名不匹配" }
        return payload.images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun decryptParams(encoded: String): String {
        val bytes = Base64.getDecoder().decode(encoded)
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
        const val LOCAL_QUERY = "aurora_query"
        val PARAMS_REGEX = Regex("""params\s*=\s*'([^']+)'""")
    }
}

@Serializable
private data class ChapterPayload(
    val host: String,
    val images: List<String>,
)
