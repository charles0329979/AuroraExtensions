package app.aurora.scripted.http

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadInstructionInterceptorTest {
    @Test
    fun allowsImageWithMatchingHost() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody("png-bytes")
                    .addHeader("Content-Type", "image/png"),
            )
            val client = OkHttpClient.Builder()
                .addInterceptor(DownloadInstructionInterceptor())
                .build()
            val base = server.url("/").toString().trimEnd('/')
            val instruction = DownloadInstruction(baseUrl = base)
            val response = client.newCall(
                Request.Builder()
                    .url(server.url("/img.png"))
                    .tag(DownloadInstructionTag::class.java, DownloadInstructionTag(instruction))
                    .build(),
            ).execute()
            assertTrue(response.isSuccessful)
            assertEquals("png-bytes", response.body!!.string())
        }
    }

    @Test
    fun rejectsHostMismatch() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("x").addHeader("Content-Type", "image/png"))
            val client = OkHttpClient.Builder()
                .addInterceptor(DownloadInstructionInterceptor())
                .build()
            val instruction = DownloadInstruction(baseUrl = "https://example.com")
            assertThrows(IOException::class.java) {
                client.newCall(
                    Request.Builder()
                        .url(server.url("/img.png"))
                        .tag(DownloadInstructionTag::class.java, DownloadInstructionTag(instruction))
                        .build(),
                ).execute()
            }.also { assertEquals("HOST_NOT_ALLOWED", it.message) }
        }
    }

    @Test
    fun doesNotSendCookieFromInstruction() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody("ok").addHeader("Content-Type", "image/jpeg"),
            )
            val client = OkHttpClient.Builder()
                .addInterceptor(DownloadInstructionInterceptor())
                .build()
            val base = server.url("/").toString().trimEnd('/')
            val instruction = DownloadInstruction(
                baseUrl = base,
                headers = mapOf(
                    "Referer" to "$base/",
                    "Cookie" to "secret=1",
                    "Authorization" to "Bearer x",
                ),
            )
            client.newCall(
                Request.Builder()
                    .url(server.url("/img.jpg"))
                    .tag(DownloadInstructionTag::class.java, DownloadInstructionTag(instruction))
                    .build(),
            ).execute().close()
            val recorded = server.takeRequest()
            assertEquals("$base/", recorded.getHeader("Referer"))
            assertEquals(null, recorded.getHeader("Cookie"))
            assertEquals(null, recorded.getHeader("Authorization"))
        }
    }

    @Test
    fun rejectsHtmlMime() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody("<html/>").addHeader("Content-Type", "text/html"),
            )
            val client = OkHttpClient.Builder()
                .addInterceptor(DownloadInstructionInterceptor())
                .build()
            val base = server.url("/").toString().trimEnd('/')
            val instruction = DownloadInstruction(baseUrl = base)
            assertThrows(IOException::class.java) {
                client.newCall(
                    Request.Builder()
                        .url(server.url("/not-image"))
                        .tag(DownloadInstructionTag::class.java, DownloadInstructionTag(instruction))
                        .build(),
                ).execute()
            }.also { assertEquals("IMAGE_BLOCKED", it.message) }
        }
    }
}
