package eu.kanade.tachiyomi.extension.zh.ttkmh

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class TTKMH : HttpSource() {

    override val supportsLatest = false

    private val json: Json by injectLazy()

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select(".slide-list li.swiper-slide")
            .mapNotNull(::mangaFromElement)
            .distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/search?key=$query", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a[href^=/manhua/][title]")
            .mapNotNull { mangaFromLink(it, document) }
            .distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val link = element.selectFirst("h3 a[href^=/manhua/]") ?: return null
        return mangaFromLink(link, element)
    }

    private fun mangaFromLink(link: Element, container: Element): SManga? {
        val title = link.attr("title").ifBlank { link.text() }
        if (title.isBlank()) return null
        val item = link.closest("li") ?: container
        val cover = item.selectFirst("[data-original]")?.attr("data-original")
        return SManga.create().apply {
            url = link.attr("href")
            this.title = title
            thumbnail_url = cover
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info = document.selectFirst(".vod-info") ?: error("作品详情不存在")
        val spans = info.select(".info span")
        return SManga.create().apply {
            title = info.selectFirst(".info h3 a")?.text().orEmpty()
            thumbnail_url = info.selectFirst(".pic img")?.attr("data-original")
            author = spans.firstOrNull { it.text().trim().startsWith("作者：") }
                ?.text()?.substringAfter("作者：")?.trim()
            genre = spans.firstOrNull { it.text().trim().startsWith("标签：") }
                ?.select("a")?.joinToString { it.text() }
            description = document.selectFirst("meta[name=description]")?.attr("content")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select(".episode-box li a[href^=/chapter/]")
        .map { link ->
            SChapter.create().apply {
                url = link.attr("href")
                name = link.text()
            }
        }
        .asReversed()

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val encrypted = PARAMS_REGEX.find(body)?.groupValues?.get(1)
            ?: error("章节图片参数不存在")
        val payload = Base64.decode(encrypted, Base64.DEFAULT)
        check(payload.size > 16) { "章节图片参数过短" }

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(AES_KEY.encodeToByteArray(), "AES"),
            IvParameterSpec(payload.copyOfRange(0, 16)),
        )
        val decrypted = cipher.doFinal(payload.copyOfRange(16, payload.size)).decodeToString()
        return json.decodeFromString<ImageParams>(decrypted).images.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    @Serializable
    private data class ImageParams(val images: List<String>)

    companion object {
        private const val AES_KEY = "9S8\$vJnU2ANeSRoF"
        private val PARAMS_REGEX = Regex("""\bparams\s*=\s*['\"]([^'\"]+)""")
    }
}
