package eu.kanade.tachiyomi.extension.zh.manhuaba

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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class ManhuaBa : HttpSource() {

    override val supportsLatest = true
    private val json: Json by injectLazy()

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/custom/top", headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/custom/update", headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("key", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a.module-poster-item[href], .module-card-item")
            .mapNotNull(::mangaFromElement)
            .distinctBy(SManga::url)
        val hasNext = document.selectFirst("a:matchesOwn(下一页)")
            ?.attr("href")
            ?.let { it.isNotBlank() && it != response.request.url.encodedPath }
            ?: false
        return MangasPage(mangas, hasNext)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val link = if (element.tagName() == "a") {
            element
        } else {
            element.selectFirst("a.module-card-item-poster[href]") ?: return null
        }
        val title = link.attr("title").ifBlank {
            element.selectFirst(".module-poster-item-title, .module-card-item-title a")?.text().orEmpty()
        }
        if (title.isBlank()) return null
        val image = link.selectFirst("img")
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(link.absUrl("href"))
            thumbnail_url = image?.absUrl("data-original")
                ?.ifBlank { image.absUrl("data-src") }
                ?.ifBlank { image.absUrl("src") }
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info = document.selectFirst(".module-info") ?: error("作品详情不存在")
        val rows = info.select(".module-info-item")
        return SManga.create().apply {
            title = info.selectFirst("h1")?.text() ?: error("缺少漫画标题")
            thumbnail_url = info.selectFirst(".module-info-poster img")?.let { image ->
                image.absUrl("data-original")
                    .ifBlank { image.absUrl("data-src") }
                    .ifBlank { image.absUrl("src") }
            }
            author = rows.firstOrNull { it.text().startsWith("作者：") }
                ?.selectFirst(".module-info-item-content")?.text()
            genre = rows.firstOrNull { it.text().startsWith("类型：") }
                ?.select("a")?.joinToString { it.text() }
            description = info.selectFirst(".module-info-introduction-content")?.text()
            status = when {
                rows.any { it.text().contains("完结") } -> SManga.COMPLETED
                rows.any { it.text().startsWith("连载：") || it.text().startsWith("更新：") } -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("a.module-play-list-link[href]")
        .distinctBy { it.absUrl("href") }
        .map { anchor ->
            SChapter.create().apply {
                name = anchor.attr("title").ifBlank { anchor.text().trim() }
                setUrlWithoutDomain(anchor.absUrl("href"))
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

    private companion object {
        const val AES_KEY = "9S8\$vJnU2ANeSRoF"
        val PARAMS_REGEX = Regex("""\bparams\s*=\s*['\"]([^'\"]+)""")
    }
}
