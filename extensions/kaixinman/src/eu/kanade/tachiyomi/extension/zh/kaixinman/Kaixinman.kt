package eu.kanade.tachiyomi.extension.zh.kaixinman

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
import kotlinx.serialization.SerialName
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
abstract class Kaixinman : HttpSource() {

    override val supportsLatest = false

    private val json: Json by injectLazy()

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage = MangasPage(
        response.asJsoup().select(".comic-item")
            .mapNotNull(::mangaFromElement)
            .distinctBy { it.url },
        false,
    )

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/search?q=$query", headers)

    override fun searchMangaParse(response: Response): MangasPage = MangasPage(
        response.asJsoup().select(".comic-item")
            .mapNotNull(::mangaFromElement)
            .distinctBy { it.url },
        false,
    )

    private fun mangaFromElement(element: Element): SManga? {
        val link = element.selectFirst(".comic-info h4 a[href^=/comic/]") ?: return null
        val title = link.attr("title").ifBlank { link.text() }
        if (title.isBlank()) return null
        return SManga.create().apply {
            url = link.attr("href")
            this.title = title
            thumbnail_url = element.selectFirst(".comic-cover[data-original]")?.attr("data-original")
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val detail = document.selectFirst(".comic-detail") ?: error("作品详情不存在")
        val data = detail.select("p.data span.text-muted")
        return SManga.create().apply {
            title = detail.selectFirst("h1.title > span:first-child")?.text().orEmpty()
            thumbnail_url = document.selectFirst(".comic-cover-box img")?.let {
                it.attr("data-original").ifBlank { it.attr("src") }
            }
            author = data.firstOrNull { it.text().startsWith("作者：") }
                ?.text()?.substringAfter("作者：")?.trim()
            genre = data.firstOrNull { it.text().startsWith("类型：") }
                ?.select("a")?.joinToString { it.text() }
            description = detail.selectFirst("p.desc .left")?.text()?.substringAfter("简介：")?.trim()
            status = when (document.selectFirst(".comic-cover-box .name")?.text()) {
                "完结" -> SManga.COMPLETED
                else -> SManga.ONGOING
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select(".chapter-list li a[href^=/chapter/]")
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
        val params = json.decodeFromString<ImageParams>(decrypted)
        val imageHost = params.imagesHosts.firstOrNull().orEmpty().trimEnd('/')
        return params.chapterImages.mapIndexed { index, path ->
            val imageUrl = when {
                path.startsWith("http://") || path.startsWith("https://") -> path
                imageHost.isBlank() -> path
                else -> imageHost + "/" + path.trimStart('/')
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    @Serializable
    private data class ImageParams(
        @SerialName("chapter_images") val chapterImages: List<String>,
        @SerialName("images_hosts") val imagesHosts: List<String>,
    )

    companion object {
        private const val AES_KEY = "5V&RoR%Jf@pJPydF"
        private val PARAMS_REGEX = Regex("""\bparams\s*=\s*['\"]([^'\"]+)""")
    }
}
