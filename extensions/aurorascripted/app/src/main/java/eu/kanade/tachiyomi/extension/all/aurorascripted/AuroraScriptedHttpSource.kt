package eu.kanade.tachiyomi.extension.all.aurorascripted

import app.aurora.scripted.http.DownloadInstruction
import app.aurora.scripted.http.DownloadInstructionInterceptor
import app.aurora.scripted.http.DownloadInstructionTag
import app.aurora.scripted.js.PageListJs
import app.aurora.scripted.model.PageListResultParser
import app.aurora.scripted.policy.HostPolicy
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * S1 demo: Kotlin catalogue surfaces + JS-driven pageList image URL discovery.
 */
class AuroraScriptedHttpSource : HttpSource() {

    override val name = "Aurora Scripted"
    override val lang = "en"
    override val baseUrl = "https://aurora.scripted.invalid"
    override val supportsLatest = false

    private val instructionInterceptor = DownloadInstructionInterceptor()

    override val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(instructionInterceptor)
            .build()
    }

    override suspend fun getPopularManga(page: Int): MangasPage =
        MangasPage(listOf(demoManga()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        MangasPage(emptyList(), false)

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val trimmed = query.trim()
        val manga = demoManga()
        val results =
            if (trimmed.isEmpty() || manga.title.contains(trimmed, ignoreCase = true)) {
                listOf(manga)
            } else {
                emptyList()
            }
        return MangasPage(results, false)
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) demoManga() else manga
        val chapterList = if (fetchChapters) demoChapters() else chapters
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // Bundled constants (mirrored in assets/ for inspection); ClassLoader cannot open APK assets.
        val html = BUNDLED_CHAPTER_HTML
        val script = BUNDLED_PAGE_LIST_JS
        val inputJson =
            """{"html":${jsonQuote(html)},"chapterUrl":"$baseUrl${chapter.url}","baseUrl":"$baseUrl"}"""
        val raw = PageListJs.evaluate(script, inputJson, timeoutMs = 5_000)
        val result = PageListResultParser.parse(raw)
        for (p in result.pages) {
            if (!HostPolicy.isAllowed(baseUrl, p.imageUrl, result.allowedHosts)) {
                throw IllegalStateException("HOST_NOT_ALLOWED: ${p.imageUrl}")
            }
        }
        // Pass all constructor args so Kotlin does not emit DefaultConstructorMarker
        // (host Page has a 4th uri param; named/default calls crash at runtime).
        return result.pages.mapIndexed { index, p ->
            Page(index, "", p.imageUrl, null)
        }
    }

    override fun imageRequest(page: Page): Request {
        val url = page.imageUrl ?: error("null imageUrl")
        val instruction =
            DownloadInstruction(
                baseUrl = baseUrl,
                allowedHosts = setOf("placehold.co"),
                headers = mapOf("Referer" to "$baseUrl/"),
            )
        return Request.Builder()
            .url(url)
            .tag(DownloadInstructionTag::class.java, DownloadInstructionTag(instruction))
            .build()
    }

    private fun demoManga(): SManga =
        SManga.create().apply {
            url = "/manga/1"
            title = "Aurora Scripted Demo"
            thumbnail_url = "https://placehold.co/400x600/png?text=Scripted"
            author = "Aurora"
            artist = "Aurora"
            description = "S1 ScriptedSource demo. Chapter HTML is bundled; images from placehold.co."
            genre = "Test, Scripted"
            status = SManga.ONGOING
            initialized = true
        }

    private fun demoChapters(): List<SChapter> =
        listOf(
            SChapter.create().apply {
                url = "/manga/1/chapter/1"
                name = "Chapter 1"
                chapter_number = 1f
            },
        )

    private fun jsonQuote(value: String): String =
        buildString {
            append('"')
            value.forEach { c ->
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
            append('"')
        }

    companion object {
        private val BUNDLED_PAGE_LIST_JS =
            """
            function pageList(input) {
              var data = (typeof input === "string") ? JSON.parse(input) : input;
              var html = data.html || "";
              var re = /data-aurora-image="([^"]+)"/g;
              var pages = [];
              var m;
              while ((m = re.exec(html)) !== null) {
                pages.push({ imageUrl: m[1], headers: { Referer: data.baseUrl + "/" } });
              }
              return JSON.stringify({
                pages: pages,
                allowedHosts: ["placehold.co"]
              });
            }
            """.trimIndent()

        private val BUNDLED_CHAPTER_HTML =
            """
            <!DOCTYPE html>
            <html><body>
              <img data-aurora-image="https://placehold.co/800x1200/png?text=Aurora+Scripted+1" />
              <img data-aurora-image="https://placehold.co/800x1200/png?text=Aurora+Scripted+2" />
            </body></html>
            """.trimIndent()
    }
}
