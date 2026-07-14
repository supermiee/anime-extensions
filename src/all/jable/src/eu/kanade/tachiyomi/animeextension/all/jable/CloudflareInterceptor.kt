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

        // Check if blocked by Cloudflare
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

        // Try to get existing cookies from WebView's CookieManager
        val cookies = CookieManager.getInstance()
            ?.getCookie(originalRequest.url.toString())

        if (!cookies.isNullOrEmpty() && cookies.contains("cf_clearance")) {
            val cookieList = cookies.split(";")
                .mapNotNull { Cookie.parse(originalRequest.url, it.trim()) }
            val cookieHeader = cookieList.joinToString("; ") { "${it.name}=${it.value}" }

            val retryResponse = chain.proceed(
                originalRequest.newBuilder()
                    .header("Cookie", cookieHeader)
                    .build(),
            )
            if (retryResponse.code !in ERROR_CODES) {
                return retryResponse
            }
            retryResponse.close()
        }

        // Cookies missing or stale - use WebView to solve challenge automatically
        val bypass = CloudflareBypass()
        val bypassResult = bypass.getCookies(originalRequest.url.toString())

        if (bypassResult != null) {
            return chain.proceed(
                originalRequest.newBuilder()
                    .header("Cookie", bypassResult.cookies)
                    .header("User-Agent", bypassResult.userAgent)
                    .build(),
            )
        }

        // All attempts failed
        throw IOException(
            "Cloudflare verification failed. Please:\n" +
                "1. Open Jable source in Aniyomi\n" +
                "2. Tap the WebView icon (globe icon) in the top right\n" +
                "3. Complete Cloudflare verification in WebView\n" +
                "4. Close WebView and try again",
        )
    }
}
