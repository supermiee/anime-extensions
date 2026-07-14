package eu.kanade.tachiyomi.animeextension.all.jable

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class CloudflareInterceptor : Interceptor {

    companion object {
        private val ERROR_CODES = listOf(403, 503)
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)

        if (response.code !in ERROR_CODES) {
            return response
        }

        val isCloudflare = response.header("Server") in SERVER_CHECK ||
            response.header("cf-ray") != null ||
            response.header("cf-mitigated") != null

        if (!isCloudflare) {
            return response
        }

        response.close()

        val cookies = CookieManager.getInstance()
            ?.getCookie(originalRequest.url.toString())

        if (!cookies.isNullOrEmpty() && cookies.contains("cf_clearance")) {
            val cookieList = cookies.split(";")
                .mapNotNull { Cookie.parse(originalRequest.url, it.trim()) }
            val cookieHeader = cookieList.joinToString("; ") { "${it.name}=${it.value}" }

            return chain.proceed(
                originalRequest.newBuilder()
                    .header("Cookie", cookieHeader)
                    .build(),
            )
        }

        throw IOException(
            "Cloudflare blocked. Please:\n" +
                "1. Tap WebView icon (globe) in top right\n" +
                "2. Complete verification in WebView\n" +
                "3. Close WebView and retry",
        )
    }
}
