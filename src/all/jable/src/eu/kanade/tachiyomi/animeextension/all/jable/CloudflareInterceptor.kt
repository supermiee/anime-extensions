package eu.kanade.tachiyomi.animeextension.all.jable

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

class CloudflareInterceptor(
    private val client: OkHttpClient,
    private val userAgentProvider: () -> String = { DEFAULT_UA },
) : Interceptor {

    companion object {
        private val ERROR_CODES = listOf(403, 503)
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    private val cfBypass by lazy { CloudflareBypass() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)

        // Check if blocked by Cloudflare
        if (response.code !in ERROR_CODES) {
            return response
        }

        val isCloudflare = response.header("Server") in SERVER_CHECK || response.header("cf-ray") != null
        if (!isCloudflare) {
            return response
        }

        response.close()

        // Try to bypass using WebView
        val customUA = userAgentProvider()
        val bypassResult = cfBypass.getCookies(originalRequest.url.toString(), customUA)

        if (bypassResult != null) {
            return chain.proceed(
                originalRequest.newBuilder()
                    .header("Cookie", bypassResult.cookies)
                    .header("User-Agent", bypassResult.userAgent)
                    .build(),
            )
        }

        // If bypass fails, return original response
        return chain.proceed(originalRequest)
    }
}
