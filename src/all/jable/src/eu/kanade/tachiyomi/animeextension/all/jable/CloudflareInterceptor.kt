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

        // Not blocked, return as-is
        if (response.code !in ERROR_CODES) {
            return response
        }

        // Check if it's Cloudflare
        val isCloudflare = response.header("Server") in SERVER_CHECK ||
            response.header("cf-ray") != null ||
            response.header("cf-mitigated") != null

        if (!isCloudflare) {
            return response
        }

        response.close()

        // Try to get existing cookies from manual WebView verification
        val cookies = CookieManager.getInstance()
            ?.getCookie(originalRequest.url.toString())

        if (!cookies.isNullOrEmpty() && cookies.contains("cf_clearance")) {
            val cookieList = cookies.split(";")
                .mapNotNull { Cookie.parse(originalRequest.url, it.trim()) }
            val cookieHeader = cookieList.joinToString("; ") { "${it.name}=${it.value}" }

            // Retry with cookies
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

        // No valid cookies - user must verify manually
        throw IOException(
            "需要 Cloudflare 验证。请按以下步骤操作：\n" +
                "1. 点击右上角 WebView 图标（地球图标）\n" +
                "2. 在打开的页面中完成验证\n" +
                "3. 关闭 WebView 后重试",
        )
    }
}
