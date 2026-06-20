package eu.kanade.tachiyomi.animeextension.all.jable

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.utils.applicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class CloudFlareBypassResult(
    val cookies: String,
    val userAgent: String,
)

class CloudflareBypass {

    companion object {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Synchronized
    fun getCookies(pageUrl: String, customUserAgent: String? = null): CloudFlareBypassResult? {
        clearCookiesForUrl(pageUrl)

        val latch = CountDownLatch(1)
        var result: CloudFlareBypassResult? = null
        var webView: WebView? = null
        val cancelled = AtomicBoolean(false)

        val userAgentToUse = customUserAgent ?: UA

        Handler(Looper.getMainLooper()).post {
            webView = WebView(applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.userAgentString = userAgentToUse
            }

            webView!!.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pollForClearance(pageUrl, userAgentToUse, cancelled) { bypassResult ->
                        result = bypassResult
                        latch.countDown()
                    }
                }
            }

            CookieManager.getInstance().setCookie(pageUrl, "")
            webView!!.loadUrl(pageUrl)
        }

        try {
            latch.await(30, TimeUnit.SECONDS)
        } finally {
            cancelled.set(true)
            Handler(Looper.getMainLooper()).post {
                try {
                    webView?.stopLoading()
                    webView?.destroy()
                } catch (_: Exception) {}
            }
        }

        return result
    }

    private fun pollForClearance(
        url: String,
        userAgent: String,
        cancelled: AtomicBoolean,
        onComplete: (CloudFlareBypassResult) -> Unit,
    ) {
        val handler = Handler(Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        val maxDurationMs = 30_000L
        val pollIntervalMs = 500L

        val runnable = object : Runnable {
            override fun run() {
                if (cancelled.get()) return
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= maxDurationMs) return

                val cookies = CookieManager.getInstance().getCookie(url)

                if (cookies?.contains("cf_clearance=") == true) {
                    onComplete(CloudFlareBypassResult(cookies, userAgent))
                } else {
                    handler.postDelayed(this, pollIntervalMs)
                }
            }
        }
        handler.post(runnable)
    }

    private fun clearCookiesForUrl(pageUrl: String) {
        val domain = Uri.parse(pageUrl).host ?: return
        val cookieManager = CookieManager.getInstance()

        listOf("https://$domain", "https://www.$domain").forEach { url ->
            cookieManager.getCookie(url)?.split(";")?.forEach { cookieStr ->
                val cookieName = cookieStr.substringBefore("=").trim()
                if (cookieName.isNotEmpty()) {
                    cookieManager.setCookie(url, "$cookieName=; Max-Age=0; path=/")
                    cookieManager.setCookie(url, "$cookieName=; Max-Age=0; path=/; domain=.$domain")
                }
            }
        }
        cookieManager.flush()
    }
}
