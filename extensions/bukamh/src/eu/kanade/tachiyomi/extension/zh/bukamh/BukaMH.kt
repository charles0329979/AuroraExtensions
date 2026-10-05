package eu.kanade.tachiyomi.extension.zh.bukamh

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
import keiyoushi.network.get
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
abstract class BukaMH : HttpSource() {

    override val supportsLatest = true
    private val json: Json by injectLazy()

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/custom/hot", headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/custom/update", headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        if (page > 1 || query.isBlank()) return MangasPage(emptyList(), false)
        listOf("/search", "/index.php/search").forEach { path ->
            val found = searchByPath(path, query)
            if (found.mangas.isNotEmpty()) return found
        }
        return fallbackPopularSearch(query)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("key", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaList(response)

    private suspend fun searchByPath(path: String, query: String): MangasPage {
        val url = "$baseUrl$path".toHttpUrl().newBuilder()
            .addQueryParameter("key", query)
            .build()
        return client.get(url, headers).use(::parseMangaList)
    }

    private suspend fun fallbackPopularSearch(query: String): MangasPage {
        val normalized = query.trim()
        val popular = client.get("$baseUrl/custom/hot", headers).use(::parseMangaList).mangas
        val matched = popular.filter { manga ->
            manga.title.contains(normalized, ignoreCase = true) ||
                normalized.contains(manga.title, ignoreCase = true)
        }
        return MangasPage(matched.ifEmpty { if (normalized == "漫画") popular else emptyList() }, false)
    }

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".u_list li, .u_list .pic, a.name[href]")
            .mapNotNull(::mangaFromElement)
            .distinctBy(SManga::url)
        val hasNext = document.selectFirst("a:matchesOwn(下一页)")
            ?.attr("href")
            ?.let { it.isNotBlank() && it != response.request.url.encodedPath }
            ?: false
        return MangasPage(mangas, hasNext)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val container = if (element.hasClass("pic")) element.parent() ?: element else element
        val link = when {
            element.tagName() == "a" -> element
            element.hasClass("pic") -> element.selectFirst("a[href]") ?: return null
            else -> container.selectFirst(".pic a[href], a.name[href]") ?: return null
        }
        val titleLink = if (link.hasClass("name")) link else container.selectFirst("a.name")
        val title = titleLink?.text().orEmpty().ifBlank { link.attr("title") }
        if (title.isBlank()) return null
        return SManga.create().apply {
            this.title = title
            setUrlWithoutDomain(link.absUrl("href"))
            thumbnail_url = container.selectFirst(".pic img")?.absUrl("src")
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val info = document.selectFirst(".infocomic") ?: error("作品详情不存在")
        val rows = info.select(".infobox .info p.tage")
        return SManga.create().apply {
            title = info.selectFirst(".infobox > .title")?.text() ?: error("缺少漫画标题")
            thumbnail_url = info.selectFirst(".infobox .img img")?.absUrl("src")
            author = rows.firstOrNull { it.text().startsWith("作者：") }
                ?.text()?.substringAfter("作者：")?.trim()
            genre = rows.firstOrNull { it.text().startsWith("类型：") }
                ?.select("a")?.joinToString { it.text() }
            description = info.children().firstOrNull { it.hasClass("text") }?.text()
            status = when {
                rows.any { it.text().contains("完结") } -> SManga.COMPLETED
                rows.any { it.text().contains("更新") } -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select(".chapterbox .list a[href$=.html]")
        .distinctBy { it.absUrl("href") }
        .map { anchor ->
            SChapter.create().apply {
                name = anchor.text().trim()
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
