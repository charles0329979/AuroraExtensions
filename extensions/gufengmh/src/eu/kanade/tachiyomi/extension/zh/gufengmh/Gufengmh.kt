package eu.kanade.tachiyomi.extension.zh.gufengmh

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
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Element
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class Gufengmh : HttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(::imageInterceptor)
        .build()

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/139 Safari/537.36")

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response, false)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/category", headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response, false)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/index.php/search".toHttpUrl().newBuilder()
            .addQueryParameter("key", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response, false)

    private fun parseMangaList(response: Response, hasNextPage: Boolean): MangasPage {
        val mangas = response.asJsoup().select(".side_commend li").mapNotNull(::mangaFromCard)
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromCard(card: Element): SManga? {
        val title = card.selectFirst("h2, h3")?.text()
            ?: card.selectFirst("img[title]")?.attr("title")
            ?: return null
        val anchor = card.selectFirst("a[href$='.html']") ?: return null
        val image = card.selectFirst("img")
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(anchor.absUrl("href"))
            thumbnail_url = image?.attr("data-original")?.takeIf(String::isNotBlank)
                ?: image?.absUrl("src")
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info = document.selectFirst(".novel_info_main") ?: error("详情结构已变化")
        val labels = info.select(".novel_info_title p span").eachText()
        return SManga.create().apply {
            title = info.selectFirst("h1")?.text() ?: error("缺少漫画标题")
            author = info.selectFirst(".novel_info_title i")?.text()?.removePrefix("作者：")
            genre = labels.filterNot { it.contains("连载") || it.contains("完结") }.joinToString()
            status = when {
                labels.any { it.contains("完结") } -> SManga.COMPLETED
                labels.any { it.contains("连载") } -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
            thumbnail_url = info.selectFirst("img")?.absUrl("src")
            description = document.selectFirst(".novel_info_content, .content")?.text()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("#ul_all_chapters a[href]")
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
        require(payload.host == "www.gfmh.app") { "阅读参数域名不匹配" }
        return payload.images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun imageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host !in IMAGE_HOSTS) return chain.proceed(request)

        var response = chain.proceed(request)
        if (!response.isSuccessful && request.url.host != FALLBACK_IMAGE_HOST) {
            response.close()
            response = chain.proceed(
                request.newBuilder()
                    .url(request.url.newBuilder().host(FALLBACK_IMAGE_HOST).build())
                    .build(),
            )
        }
        if (!response.isSuccessful || response.body.contentType()?.toString()?.startsWith("text/html") == true) {
            return response
        }

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val key = SecretKeySpec(IMAGE_KEY.toByteArray(Charsets.UTF_8), "AES")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(IMAGE_KEY.toByteArray(Charsets.UTF_8)))
        val decrypted = cipher.doFinal(response.body.bytes())
        return response.newBuilder()
            .body(decrypted.toResponseBody("image/webp".toMediaType()))
            .build()
    }

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
        const val IMAGE_KEY = "my2ecret782ecret"
        const val FALLBACK_IMAGE_HOST = "img1.baipiaoguai.org"
        val IMAGE_HOSTS = setOf("s2.325784.xyz", FALLBACK_IMAGE_HOST)
        val PARAMS_REGEX = Regex("""params\s*=\s*'([^']+)'""")
    }
}

@Serializable
private data class ChapterPayload(
    val host: String,
    val images: List<String>,
)
