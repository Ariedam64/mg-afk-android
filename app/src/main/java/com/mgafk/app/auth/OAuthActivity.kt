package com.mgafk.app.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mgafk.app.data.AppLog
import com.mgafk.app.data.websocket.Constants
import java.security.SecureRandom

/**
 * Opens a WebView to Discord OAuth, intercepts the mc_jwt cookie after redirect.
 */
class OAuthActivity : Activity() {

    companion object {
        private const val TAG = "OAuthActivity"
        private const val TOKEN_POLL_MS = 750L
        const val EXTRA_TOKEN = "token"
        const val RESULT_TOKEN = 1001

        /**
         * A same-origin page loaded first, only so the game's localStorage can
         * be written before Discord sends the user back to a room page.
         */
        private const val STATE_SEED_URL = "https://magicgarden.gg/platform/v1/version"

        /** Where the game keeps the state it expects back from Discord. */
        private const val STATE_STORAGE_KEY = "mc.oauth.redirectState"

        /** Random URL-safe state, the same shape the game generates. */
        private fun newOAuthState(): String {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }

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
    private val pollHandler = Handler(Looper.getMainLooper())

    /**
     * The room page now drops a Discord code whose oauthState does not match
     * localStorage[STATE_STORAGE_KEY], then signs the user in as a fresh guest
     * (device account) instead. That guest mc_jwt is what the poll used to
     * catch. So the state is seeded on magicgarden.gg first, then sent to
     * Discord, which hands it back through /oauth2/redirect as oauthState.
     */
    private val oauthState = newOAuthState()
    private var stateSeeded = false

    /**
     * The game now finishes the Discord code exchange with an async XHR
     * (POST .../authenticate-web) fired by the room page's own JS, which
     * completes after onPageFinished with no further navigation event.
     * Poll cookies so we still catch mc_jwt once that request lands.
     */
    private val pollRunnable = object : Runnable {
        override fun run() {
            checkForToken()
            pollHandler.postDelayed(this, TOKEN_POLL_MS)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                    if (!stateSeeded && url?.startsWith(STATE_SEED_URL) == true) {
                        seedStateThenOpenDiscord()
                        return
                    }
                    checkForToken()
                }
            }
        }

        setContentView(webView)

        // Clear existing cookies so Discord always prompts a fresh login,
        // then set the OAuth cookies and open the game origin to seed the state.
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            cm.flush()
            runOnUiThread {
                setOAuthCookies()
                webView.loadUrl(STATE_SEED_URL)
                pollHandler.postDelayed(pollRunnable, TOKEN_POLL_MS)
            }
        }
    }

    private fun seedStateThenOpenDiscord() {
        stateSeeded = true
        val js = "localStorage.setItem('$STATE_STORAGE_KEY', '$oauthState'); " +
            "localStorage.getItem('$STATE_STORAGE_KEY') === '$oauthState'"
        webView.evaluateJavascript(js) { stored ->
            AppLog.d(TAG, "OAuth state seeded: $stored")
            val url = Uri.parse(Constants.DISCORD_OAUTH_URL).buildUpon()
                .appendQueryParameter("state", oauthState)
                .build()
                .toString()
            webView.loadUrl(url)
        }
    }

    private fun checkForToken() {
        val cookies = CookieManager.getInstance().getCookie("https://magicgarden.gg")
        AppLog.d(TAG, "checkForToken url=${webView.url} cookies=$cookies")
        if (cookies == null) return
        val token = cookies.split(";")
            .map { it.trim() }
            .find { it.startsWith("mc_jwt=") }
            ?.removePrefix("mc_jwt=")
            ?.trim()

        if (!token.isNullOrBlank()) {
            pollHandler.removeCallbacks(pollRunnable)
            val result = Intent().apply { putExtra(EXTRA_TOKEN, token) }
            setResult(RESULT_TOKEN, result)
            finish()
        }
    }

    override fun onDestroy() {
        pollHandler.removeCallbacks(pollRunnable)
        webView.destroy()
        super.onDestroy()
    }
}
