package eu.kanade.tachiyomi.extension.zh.kanman

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@keiyoushi.annotation.Source
abstract class Kanman : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/139 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = listRequest(page, "click", "")

    override fun popularMangaParse(response: Response) = listParse(response)

    override fun latestUpdatesRequest(page: Int) = listRequest(page, "date", "")

    override fun latestUpdatesParse(response: Response) = listParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = listRequest(page, "click", query)

    override fun searchMangaParse(response: Response) = listParse(response)

    private fun listRequest(page: Int, orderBy: String, query: String): Request {
        val url = "$apiBase/getsortlist/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", PAGE_SIZE.toString())
            .addQueryParameter("orderby", orderBy)
            .addQueryParameter("search_type", "")
            .addQueryParameter("comic_sort", "")
            .addQueryParameter("search_key", query)
            .addQueryParameter("platformname", "android")
            .addQueryParameter("productname", "kmh")
            .build()
        return GET(url, headers)
    }

    private fun listParse(response: Response): MangasPage {
        val result = response.parseAs<ListResponse>()
        val mangas = result.data.map { item ->
            SManga.create().apply {
                title = item.name
                url = "/comic/${item.id}"
                author = item.author
                genre = item.types
                    ?.split('|')
                    ?.map { it.substringAfter(',').trim() }
                    ?.filter { it.isNotBlank() }
                    ?.joinToString()
                thumbnail_url = "https://image.yqmh.com/mh/${item.id}.jpg-300x400.webp"
            }
        }
        return MangasPage(mangas, result.page.currentPage < result.page.totalPage)
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(infoUrl(manga.url), headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val info = response.parseAs<ComicInfo>()
        val freeCount = info.chapters.count { it.isPublic }
        return SManga.create().apply {
            title = info.name
            author = info.author
            genre = info.types.joinToString { it.name }
            thumbnail_url = info.covers.firstOrNull()?.replace("http://", "https://")
                ?: "https://image.yqmh.com/mh/${info.id}.jpg-600x800.webp"
            description = buildString {
                append(info.description.ifBlank { info.feature })
                append("\n\n公开阅读范围：$freeCount/${info.chapters.size} 话。本插件仅列出官方公开免费章节；付费内容请在官方平台解锁。")
            }
            status = when {
                info.updateStatus.contains("完结") -> SManga.COMPLETED
                info.updateStatus.contains("连载") || info.status == 1 -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListRequest(manga: SManga) = GET(infoUrl(manga.url), headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val info = response.parseAs<ComicInfo>()
        return info.chapters
            .filter(Chapter::isPublic)
            .map { chapter ->
                SChapter.create().apply {
                    name = chapter.name.trim()
                    url = "/reader/${info.id}/${chapter.id}"
                    date_upload = chapter.createdAt * 1000
                    chapter_number = 0F
                }
            }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val parts = chapter.url.trim('/').split('/', limit = 3)
        require(parts.size == 3) { "章节地址无效" }
        val url = "$baseUrl/api/getchapterinfov2".toHttpUrl().newBuilder()
            .addQueryParameter("product_id", "1")
            .addQueryParameter("productname", "kmh")
            .addQueryParameter("platformname", "wap")
            .addQueryParameter("comic_id", parts[1])
            .addQueryParameter("chapter_newid", parts[2])
            .addQueryParameter("isWebp", "1")
            .addQueryParameter("quality", "low")
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapter = response.parseAs<PageResponse>().data.currentChapter
        check(chapter.price == 0) { "该章节需在官方平台解锁" }
        return chapter.images.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun infoUrl(mangaUrl: String) = "$apiBase/getcomicinfo_body/".toHttpUrl().newBuilder()
        .addQueryParameter("comic_id", mangaUrl.substringAfterLast('/'))
        .addQueryParameter("platformname", "android")
        .addQueryParameter("productname", "kmh")
        .build()

    private val apiBase = "https://comic.321mh.com/app_api/v5"

    private companion object {
        const val PAGE_SIZE = 30
    }
}

@Serializable
private data class ListResponse(
    val page: ListPage,
    val data: List<ListItem> = emptyList(),
)

@Serializable
private data class ListPage(
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("total_page") val totalPage: Int = 1,
)

@Serializable
private data class ListItem(
    @SerialName("comic_id") val id: Long,
    @SerialName("comic_name") val name: String,
    @SerialName("comic_author") val author: String? = null,
    @SerialName("comic_type") val types: String? = null,
)

@Serializable
private data class ComicInfo(
    @SerialName("comic_id") val id: Long,
    @SerialName("comic_name") val name: String,
    @SerialName("comic_author") val author: String? = null,
    @SerialName("comic_feature") val feature: String = "",
    @SerialName("comic_desc") val description: String = "",
    @SerialName("comic_status") val status: Int = 0,
    @SerialName("update_status_str") val updateStatus: String = "",
    @SerialName("comic_type_new") val types: List<ComicType> = emptyList(),
    @SerialName("cover_list") val covers: List<String> = emptyList(),
    @SerialName("comic_chapter") val chapters: List<Chapter> = emptyList(),
)

@Serializable
private data class ComicType(val name: String)

@Serializable
private data class Chapter(
    @SerialName("chapter_name") val name: String,
    @SerialName("chapter_id") val id: String,
    @SerialName("create_date") val createdAt: Long = 0,
    val price: Int = 0,
    val islock: Int = 0,
) {
    val isPublic: Boolean get() = price == 0 && islock == 0
}

@Serializable
private data class PageResponse(val data: PageData)

@Serializable
private data class PageData(
    @SerialName("current_chapter") val currentChapter: CurrentChapter,
)

@Serializable
private data class CurrentChapter(
    val price: Int = 0,
    @SerialName("chapter_img_list") val images: List<String> = emptyList(),
)
