package app.aurora.scripted.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageListResultParserTest {
    @Test
    fun parsesValidJson() {
        val json = """
            {
              "pages": [
                {
                  "imageUrl": "https://example.com/a.png",
                  "headers": { "Referer": "https://example.com/" }
                },
                {
                  "imageUrl": "http://example.com/b.png"
                }
              ],
              "allowedHosts": ["placehold.co", "cdn.example.com"]
            }
        """.trimIndent()

        val result = PageListResultParser.parse(json)

        assertEquals(2, result.pages.size)
        assertEquals("https://example.com/a.png", result.pages[0].imageUrl)
        assertEquals(mapOf("Referer" to "https://example.com/"), result.pages[0].headers)
        assertEquals("http://example.com/b.png", result.pages[1].imageUrl)
        assertEquals(emptyMap<String, String>(), result.pages[1].headers)
        assertEquals(setOf("placehold.co", "cdn.example.com"), result.allowedHosts)
    }

    @Test
    fun stripsDenylistedHeaders() {
        val json = """
            {
              "pages": [
                {
                  "imageUrl": "https://example.com/a.png",
                  "headers": {
                    "Referer": "https://example.com/",
                    "Cookie": "secret=1",
                    "Authorization": "Bearer token",
                    "Proxy-Authorization": "Basic abc",
                    "cookie": "lower=1",
                    "AUTHORIZATION": "upper=1"
                  }
                }
              ]
            }
        """.trimIndent()

        val result = PageListResultParser.parse(json)

        assertEquals(mapOf("Referer" to "https://example.com/"), result.pages[0].headers)
    }

    @Test
    fun emptyAllowedHostsWhenOmitted() {
        val json = """
            {
              "pages": [
                { "imageUrl": "https://example.com/a.png" }
              ]
            }
        """.trimIndent()

        val result = PageListResultParser.parse(json)

        assertEquals(emptySet<String>(), result.allowedHosts)
    }

    @Test
    fun rejectsInvalidJson() {
        assertThrows(IllegalArgumentException::class.java) {
            PageListResultParser.parse("{not json")
        }
    }

    @Test
    fun rejectsEmptyPages() {
        assertThrows(IllegalArgumentException::class.java) {
            PageListResultParser.parse("""{"pages":[]}""")
        }
    }

    @Test
    fun rejectsMissingPages() {
        assertThrows(IllegalArgumentException::class.java) {
            PageListResultParser.parse("""{"allowedHosts":["example.com"]}""")
        }
    }

    @Test
    fun rejectsBadScheme() {
        assertThrows(IllegalArgumentException::class.java) {
            PageListResultParser.parse(
                """{"pages":[{"imageUrl":"ftp://example.com/a.png"}]}""",
            )
        }
    }

    @Test
    fun rejectsMissingImageUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            PageListResultParser.parse("""{"pages":[{"headers":{}}]}""")
        }
    }

    @Test
    fun rejectsEmptyImageUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            PageListResultParser.parse("""{"pages":[{"imageUrl":""}]}""")
        }
    }
}
