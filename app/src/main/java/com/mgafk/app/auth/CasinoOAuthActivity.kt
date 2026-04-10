package com.mgafk.app.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Opens a WebView to the Casino Discord OAuth flow.
 * The callback page sends a postMessage with { type: "aries_discord_auth", apiKey, discordId, discordUsername }.
 * We inject JS to intercept that message and pass it back via a JavascriptInterface.
 */
class CasinoOAuthActivity : Activity() {

    companion object {
        const val EXTRA_API_KEY = "casino_api_key"
        const val RESULT_CASINO_AUTH = 1002
        private const val OAUTH_URL = "https://ariesmod-api.ariedam.fr/auth/discord/login"
    }

    private lateinit var webView: WebView
    private var finished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Clear cookies so Discord always prompts fresh login
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()

            // Bridge: JS -> Kotlin
            addJavascriptInterface(AuthBridge(), "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // The callback page does window.opener.postMessage(...) which doesn't
                    // work in a WebView (no opener). We fake window.opener so the postMessage
                    // comes to us, AND we scrape the page for the API key as a fallback.
                    view?.evaluateJavascript(
                        """
                        (function() {
                            // 1) Fake window.opener so the page's postMessage reaches us
                            if (!window.opener) {
                                window.opener = {
                                    postMessage: function(data, origin) {
                                        if (data && data.type === 'aries_discord_auth' && data.apiKey) {
                                            AndroidBridge.onApiKey(data.apiKey);
                                        }
                                    }
                                };
                            }

                            // 2) Listen for any postMessage on the page itself
                            window.addEventListener('message', function(event) {
                                if (event.data && event.data.type === 'aries_discord_auth' && event.data.apiKey) {
                                    AndroidBridge.onApiKey(event.data.apiKey);
                                }
                            });

                            // 3) Scrape the page body for an API key pattern as fallback
                            var body = document.body ? document.body.innerText : '';
                            var match = body.match(/[a-f0-9]{64}/i) || body.match(/[a-f0-9-]{36}/i);
                            if (match) {
                                AndroidBridge.onApiKey(match[0]);
                            }

                            // 4) Also check for global data the page might expose
                            if (window.ariesAuthData && window.ariesAuthData.apiKey) {
                                AndroidBridge.onApiKey(window.ariesAuthData.apiKey);
                            }
                        })();
                        """.trimIndent(),
                        null,
                    )
                }
            }
        }

        setContentView(webView)
        webView.loadUrl(OAUTH_URL)
    }

    private fun returnApiKey(apiKey: String) {
        if (finished) return
        finished = true
        val result = Intent().apply { putExtra(EXTRA_API_KEY, apiKey) }
        setResult(RESULT_CASINO_AUTH, result)
        finish()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    inner class AuthBridge {
        @JavascriptInterface
        fun onApiKey(apiKey: String) {
            runOnUiThread { returnApiKey(apiKey) }
        }
    }
}
