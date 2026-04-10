package com.mgafk.app.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mgafk.app.data.websocket.Constants

/**
 * Opens a WebView to Discord OAuth, intercepts the mc_jwt cookie after redirect.
 */
class OAuthActivity : Activity() {

    companion object {
        const val EXTRA_TOKEN = "token"
        const val RESULT_TOKEN = 1001

        /** Pre-set cookies required by the game's OAuth flow */
        private fun setOAuthCookies() {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setCookie("https://magicgarden.gg", "mc_oauth_room_id=MgAFK; path=/; domain=.magicgarden.gg")
            cm.setCookie("https://magicgarden.gg", "mc_oauth_redirect_uri=https://magicgarden.gg/oauth2/redirect; path=/; domain=.magicgarden.gg")
            cm.flush()
        }
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Clear existing cookies so Discord always prompts a fresh login,
        // then set the OAuth cookies once the clear is done.
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            cm.flush()
            runOnUiThread { setOAuthCookies() }
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    checkForToken()
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    checkForToken()
                }
            }
        }

        setContentView(webView)
        webView.loadUrl(Constants.DISCORD_OAUTH_URL)
    }

    private fun checkForToken() {
        val cookies = CookieManager.getInstance().getCookie("https://magicgarden.gg") ?: return
        val token = cookies.split(";")
            .map { it.trim() }
            .find { it.startsWith("mc_jwt=") }
            ?.removePrefix("mc_jwt=")
            ?.trim()

        if (!token.isNullOrBlank()) {
            val result = Intent().apply { putExtra(EXTRA_TOKEN, token) }
            setResult(RESULT_TOKEN, result)
            finish()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
