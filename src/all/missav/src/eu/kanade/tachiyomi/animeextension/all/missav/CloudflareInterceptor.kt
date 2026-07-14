package eu.kanade.tachiyomi.animeextension.all.missav

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
        val response = chain.proceed(request)

        if (response.code != 403) {
            return response
        }

        response.close()

        val html = fetchWithWebView(request.url.toString())
            ?: throw Exception("Failed to load page via WebView")

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
    private fun fetchWithWebView(url: String): String? {
        val latch = CountDownLatch(1)
        var result: String? = null

        Handler(Looper.getMainLooper()).post {
            try {
                val webView = WebView(application)
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            view?.evaluateJavascript("document.documentElement.outerHTML") { html ->
                                result = html?.removeSurrounding("\"")
                                latch.countDown()
                                view.destroy()
                            }
                        }, 2000)
                    }
                }
                webView.loadUrl(url)
            } catch (e: Exception) {
                latch.countDown()
            }
        }

        latch.await(20, TimeUnit.SECONDS)
        return result
    }
}
