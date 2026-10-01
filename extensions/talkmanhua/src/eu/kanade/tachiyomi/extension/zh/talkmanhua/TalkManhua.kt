package eu.kanade.tachiyomi.extension.zh.talkmanhua

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class TalkManhua : HttpSource() {

    override val supportsLatest = false

    private val json = Json { ignoreUnknownKeys = true }

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
        .add("Referer", "$baseUrl/")

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        val url = "$baseUrl/all".toHttpUrl().newBuilder()
            .addQueryParameter("sectionId", "3")
            .addQueryParameter("page", "1")
            .addQueryParameter("size", "30")
            .build()
        val response = client.get(url, headers).use { json.decodeFromString<ListResponse>(it.body.string()) }
        return MangasPage(response.data.map(BookDto::toSManga), false)
    }

    override suspend fun getLatestUpdates(page: Int) = getPopularManga(page)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        if (page > 1 || query.isBlank()) return MangasPage(emptyList(), false)

        val token = client.get("$baseUrl/search", headers).use { response ->
            response.asJsoup().selectFirst("meta[name=csrf-token]")?.attr("content")
                ?: error("搜索令牌不存在")
        }
        val searchHeaders = headers.newBuilder()
            .set("X-CSRF-TOKEN", token)
            .set("X-Requested-With", "XMLHttpRequest")
            .build()
        val body = FormBody.Builder().add("title", query).build()
        val response = client.post("$baseUrl/search", searchHeaders, body).use {
            json.decodeFromString<SearchResponse>(it.body.string())
        }
        return MangasPage(response.data.map(BookDto::toSManga), false)
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchDetails && !fetchChapters) return SMangaUpdate(manga, chapters)
        val document = client.get(baseUrl + manga.url, headers).use { it.asJsoup() }
        val updatedManga = if (fetchDetails) {
            manga.apply {
                title = document.selectFirst("#bookd")?.text() ?: title
                thumbnail_url = document.selectFirst(".detail-book-cover img")?.absUrl("src")
                author = document.selectFirst(".detail-book-author")?.text()?.substringAfter("：")?.trim()
                description = document.selectFirst(".detail-book-summary")?.text()?.substringAfter("：")?.trim()
                genre = document.selectFirst(".detail-book-keyword")?.text()?.substringAfter("：")?.trim()
                status = SManga.UNKNOWN
                initialized = true
            }
        } else {
            manga
        }
        val updatedChapters = if (fetchChapters) {
            document.select(".detail-chapters-list a.detail-chapters-list-item[href]").map { anchor ->
                SChapter.create().apply {
                    url = anchor.absUrl("href").toHttpUrl().encodedPath
                    name = anchor.selectFirst(".detail-chapters-list-item-bookName")?.text()?.trim()
                        .orEmpty().ifBlank { anchor.text().trim() }
                }
            }
        } else {
            chapters
        }
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(baseUrl + chapter.url, headers).use { response ->
        response.asJsoup().select("img.chapter-img-new[data-original]").mapIndexed { index, image ->
            Page(index, imageUrl = image.absUrl("data-original"))
        }
    }

    @Serializable
    private data class ListResponse(val data: List<BookDto> = emptyList())

    @Serializable
    private data class SearchResponse(val data: List<BookDto> = emptyList())

    @Serializable
    private data class BookDto(
        val id: Long,
        val title: String,
        val author: String? = null,
        val cover: String? = null,
        val intro: String? = null,
        val tags: String? = null,
        @SerialName("is_finished") val isFinished: Boolean = false,
    ) {
        fun toSManga() = SManga.create().apply {
            url = "/detail/$id.html"
            title = this@BookDto.title
            thumbnail_url = cover
            author = this@BookDto.author
            description = intro
            genre = tags
            status = if (isFinished) SManga.COMPLETED else SManga.ONGOING
        }
    }
}
