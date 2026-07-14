package eu.kanade.tachiyomi.animeextension.all.jable

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.utils.applicationContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CloudflareInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val result = loadWithWebView(originalRequest)

        if (result != null) {
            return Response.Builder()
                .request(originalRequest)
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("OK")
                .header("Content-Type", "text/html; charset=utf-8")
                .body(result.toResponseBody("text/html; charset=utf-8".toMediaType()))
                .build()
        }

        return chain.proceed(originalRequest)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadWithWebView(request: Request): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        var webView: WebView? = null

        Handler(Looper.getMainLooper()).post {
            try {
                webView = WebView(applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.userAgentString = request.header("User-Agent")
                        ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                }

                webView!!.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript(
                            "(function() { return document.documentElement.outerHTML; })();"
                        ) { html ->
                            result = html
                            latch.countDown()
                        }
                    }
                }

                webView!!.loadUrl(request.url.toString())
            } catch (e: Exception) {
                latch.countDown()
            }
        }

        try {
            latch.await(15, TimeUnit.SECONDS)
        } finally {
            Handler(Looper.getMainLooper()).post {
                try {
                    webView?.stopLoading()
                    webView?.destroy()
                } catch (_: Exception) {}
            }
        }

        return result?.let { cleanHtml(it) }
    }

    private fun cleanHtml(html: String): String {
        var cleaned = html
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length - 1)
        }
        cleaned = cleaned
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\u003C", "<")
            .replace("\\u003E", ">")
            .replace("\\u0026", "&")
        return cleaned
    }
}
