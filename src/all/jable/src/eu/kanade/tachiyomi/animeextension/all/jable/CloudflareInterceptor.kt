package eu.kanade.tachiyomi.animeextension.all.jable

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CloudflareInterceptor : Interceptor {

    private val application: Application by injectLazy()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val html = loadWithWebView(request.url.toString()) ?: return chain.proceed(request)

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("OK")
            .header("Content-Type", "text/html; charset=utf-8")
            .body(html.toResponseBody("text/html; charset=utf-8".toMediaType()))
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadWithWebView(url: String): String? {
        val latch = CountDownLatch(1)
        var result: String? = null

        Handler(Looper.getMainLooper()).post {
            val webView = WebView(application)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.userAgentString = USER_AGENT

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                    view?.evaluateJavascript("document.documentElement.outerHTML") { html ->
                        result = html?.removeSurrounding("\"")?.unescape()
                        latch.countDown()
                        view.destroy()
                    }
                }
            }
            webView.loadUrl(url)
        }

        latch.await(15, TimeUnit.SECONDS)
        return result
    }

    private fun String.unescape(): String = this
        .replace("\\u003C", "<")
        .replace("\\u003E", ">")
        .replace("\\u0026", "&")
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
