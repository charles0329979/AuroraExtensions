package eu.kanade.tachiyomi.extension.zh.yumanhua

import eu.kanade.tachiyomi.multisrc.mmlook.MMLook
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.Response

@Source
abstract class Yumanhua : MMLook() {
    override fun popularMangaParse(response: Response): MangasPage {
        val entries = response.asJsoup().select(".rank-list > li").map { element ->
            val link = element.selectFirst(".simple-info > a")!!
            SManga.create().apply {
                url = link.attr("href")
                title = link.selectFirst("h2")!!.text()
                author = link.select("p").first()?.text()
                description = element.selectFirst(".cartoon-introduction")?.text()
                thumbnail_url = element.selectFirst(".cartoon-poster")?.attr("data-src")
            }
        }
        return MangasPage(entries, false)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        val info = document.selectFirst(".comic-info")!!
        title = info.selectFirst("h1.name")!!.text()
        thumbnail_url = info.selectFirst(".book-cover img")?.attr("data-src")
        author = document.selectFirst("meta[property=og:author]")?.attr("content")
        genre = info.select(".comic-tags span").joinToString { it.text() }
        description = document.selectFirst(".cartoon-introduction")?.text()
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select(".chaplist-box li > a").map { element ->
        SChapter.create().apply {
            url = element.attr("href").removePrefix("/").removeSuffix(".html")
            name = element.text()
        }
    }
}
