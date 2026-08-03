package app.aurora.scripted.http

import app.aurora.scripted.policy.HostPolicy
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

class DownloadInstructionInterceptor : Interceptor {
    private val deniedHeaders = setOf(
        "cookie",
        "authorization",
        "proxy-authorization",
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val tag = original.tag(DownloadInstructionTag::class.java)

        val request =
            if (tag == null) {
                original
            } else {
                val instruction = tag.instruction
                if (!HostPolicy.isAllowed(
                        instruction.baseUrl,
                        original.url.toString(),
                        instruction.allowedHosts,
                    )
                ) {
                    throw IOException("HOST_NOT_ALLOWED")
                }
                original.newBuilder()
                    .apply {
                        instruction.headers
                            .filterKeys { it.lowercase() !in deniedHeaders }
                            .forEach { (name, value) -> header(name, value) }
                    }
                    .build()
            }

        val response = chain.proceed(request)
        if (tag != null && !ImageMimePolicy.isAllowed(response.header("Content-Type"))) {
            response.close()
            throw IOException("IMAGE_BLOCKED")
        }
        return response
    }
}
