package eu.kanade.tachiyomi.animeextension.all.jable

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.HttpUrl
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
            response.header("cf-ray") != null

        if (!isCloudflare) {
            return response
        }

        response.close()

        // Try to get cookies from WebView's CookieManager
        // These cookies are set when the user manually verifies in WebView
        val cookies = CookieManager.getInstance()
            ?.getCookie(originalRequest.url.toString())

        if (!cookies.isNullOrEmpty() && cookies.contains("cf_clearance")) {
            // We have valid cookies from WebView, use them
            val cookieList = cookies.split(";")
                .mapNotNull { Cookie.parse(originalRequest.url, it.trim()) }

            val cookieHeader = cookieList.joinToString("; ") { "${it.name}=${it.value}" }

            return chain.proceed(
                originalRequest.newBuilder()
                    .header("Cookie", cookieHeader)
                    .build(),
            )
        }

        // No valid cookies found - user needs to verify manually
        throw IOException(
            "Cloudflare 验证失败。请在 Aniyomi 中执行以下步骤：\n" +
                "1. 进入 Jable 源\n" +
                "2. 点击右上角的 WebView 图标（地球图标）\n" +
                "3. 在 WebView 中完成 Cloudflare 验证\n" +
                "4. 关闭 WebView 后重试",
        )
    }
}
