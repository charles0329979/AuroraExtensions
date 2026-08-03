package app.aurora.scripted.js

import app.aurora.scripted.model.PageListResultParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageListJsTest {
    private val script =
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

    @Test
    fun extractsImagesFromFixtureHtml() {
        val html =
            """
            <!DOCTYPE html><html><body>
              <img data-aurora-image="https://placehold.co/800x1200/png?text=Aurora+Scripted+1" />
              <img data-aurora-image="https://placehold.co/800x1200/png?text=Aurora+Scripted+2" />
            </body></html>
            """.trimIndent()
        val inputJson =
            """{"html":${jsonQuote(html)},"chapterUrl":"https://aurora.scripted.invalid/manga/1/chapter/1","baseUrl":"https://aurora.scripted.invalid"}"""
        val raw = PageListJs.evaluate(script, inputJson)
        val result = PageListResultParser.parse(raw)
        assertEquals(2, result.pages.size)
        assertTrue(result.pages[0].imageUrl.contains("placehold.co"))
        assertEquals(setOf("placehold.co"), result.allowedHosts)
    }

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
}
