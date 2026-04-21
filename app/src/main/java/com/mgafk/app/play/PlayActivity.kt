package com.mgafk.app.play

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.mgafk.app.data.AppLog
import com.mgafk.app.data.repository.GeminiFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen WebView that plays the game with the user's cookie pre-set and
 * the Gemini userscript injected. The host `MainActivity` auto-disconnects the
 * AFK session before starting this activity and reconnects once it finishes.
 */
class PlayActivity : Activity() {

    companion object {
        const val EXTRA_COOKIE = "cookie"
        const val EXTRA_ROOM = "room"
        const val EXTRA_GAME_URL = "gameUrl"
        const val EXTRA_INJECT_GEMINI = "injectGemini"
        private const val TAG = "PlayActivity"
        private val USERSCRIPT_VERSION_REGEX = Regex("""//\s*@version\s+(\S+)""")
    }

    private lateinit var webView: WebView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Userscript fetched on [onCreate]; null if offline on first launch. */
    private var geminiScript: String? = null
    private var fetchJob: Job? = null
    /** Set once the userscript has been injected into the current page load. */
    private var injectedForCurrentLoad = false
    /** When false, skip both the GM_* polyfills and the userscript injection. */
    private var injectGemini = true

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val cookie = intent.getStringExtra(EXTRA_COOKIE).orEmpty()
        val room = intent.getStringExtra(EXTRA_ROOM).orEmpty()
        val gameUrl = intent.getStringExtra(EXTRA_GAME_URL).orEmpty().ifBlank { "magicgarden.gg" }
        injectGemini = intent.getBooleanExtra(EXTRA_INJECT_GEMINI, true)

        if (cookie.isBlank() || room.isBlank()) {
            AppLog.w(TAG, "Missing cookie or room — finishing")
            finish()
            return
        }

        // Pre-set mc_jwt cookie for both the requested host and its apex domain
        // so the game's auth check passes on the very first request.
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        val apex = Uri.parse("https://$gameUrl").host ?: gameUrl
        cm.setCookie("https://$gameUrl", "mc_jwt=$cookie; path=/; domain=.$apex; secure")
        cm.flush()

        // Allow Chrome DevTools to inspect this WebView via chrome://inspect.
        WebView.setWebContentsDebuggingEnabled(true)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
            }
            cm.setAcceptThirdPartyCookies(this, true)
            // Native HTTP bridge: bypasses CORS for cross-origin requests Gemini
            // makes to e.g. mg-api.ariedam.fr — same role as GM_xmlhttpRequest.
            addJavascriptInterface(WebBridge(this, scope), "MgAfkBridge")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = false

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    injectedForCurrentLoad = false
                    if (injectGemini) {
                        view?.evaluateJavascript(gmPolyfillScript(), null)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (injectGemini) injectGeminiScript(view)
                }
            }
        }

        setContentView(webView)

        // Kick off a background fetch for the latest Gemini. If we already have
        // it cached that returns immediately; otherwise the WebView will
        // re-inject as soon as the fetch completes. Skipped entirely when the
        // user opted out of mod injection.
        if (injectGemini) {
            fetchJob = scope.launch {
                geminiScript = withContext(Dispatchers.IO) {
                    GeminiFetcher.fetchLatest(this@PlayActivity)
                }
                // If the page already finished loading, inject now.
                if (!injectedForCurrentLoad) injectGeminiScript(webView)
            }
        }

        webView.loadUrl("https://$gameUrl/r/$room")
    }

    private fun injectGeminiScript(view: WebView?) {
        val script = geminiScript ?: return
        val target = view ?: return
        if (injectedForCurrentLoad) return
        injectedForCurrentLoad = true
        // Parse `// @version X.Y.Z` out of the userscript's metadata block so
        // GM_info.script.version matches what we actually injected — that's what
        // Gemini's updater compares against to decide if it's up-to-date.
        val version = USERSCRIPT_VERSION_REGEX.find(script)?.groupValues?.get(1) ?: "0.0.0"
        val gmInfo = """
            window.GM_info = {
              scriptHandler: 'MgAfk WebView',
              version: ${jsonString(version)},
              script: {
                name: 'Gemini',
                version: ${jsonString(version)},
                namespace: 'mgafk',
                description: 'Gemini userscript injected by MG AFK'
              }
            };
        """.trimIndent()
        target.evaluateJavascript(gmInfo, null)
        target.evaluateJavascript(script, null)
    }

    private fun jsonString(value: String): String =
        kotlinx.serialization.json.JsonPrimitive(value).toString()

    /** Polyfill the GM_* APIs Gemini relies on. */
    private fun gmPolyfillScript(): String = """
        (function() {
          if (typeof window.GM_setValue === 'function') return;
          var prefix = 'GM_';

          // ── Persistent storage → localStorage ───────────────────────────
          window.GM_setValue = function(k, v) {
            try { localStorage.setItem(prefix + k, JSON.stringify(v)); }
            catch (e) { console.warn('GM_setValue failed', e); }
          };
          window.GM_getValue = function(k, def) {
            try {
              var raw = localStorage.getItem(prefix + k);
              if (raw === null || raw === undefined) return def;
              return JSON.parse(raw);
            } catch (e) { return def; }
          };
          window.GM_deleteValue = function(k) {
            try { localStorage.removeItem(prefix + k); } catch (e) {}
          };
          window.GM_listValues = function() {
            var out = [];
            for (var i = 0; i < localStorage.length; i++) {
              var k = localStorage.key(i);
              if (k && k.indexOf(prefix) === 0) out.push(k.slice(prefix.length));
            }
            return out;
          };
          window.GM_addStyle = function(css) {
            var s = document.createElement('style');
            s.textContent = css;
            (document.head || document.documentElement).appendChild(s);
            return s;
          };

          // GM_info is injected separately right before the userscript runs,
          // with the actual version parsed from the script's metadata header.

          // ── GM_xmlhttpRequest → native bridge (bypasses CORS) ──────────
          // Tampermonkey's GM_xmlhttpRequest skips the browser's same-origin
          // policy; we route through Java/OkHttp to get the same effect in a
          // raw WebView.
          window.__MgAfkBridge = {
            pending: {},
            nextId: 0,
            // Convert a base64 string to an ArrayBuffer / Blob.
            _b64ToBytes: function(b64) {
              var binary = atob(b64);
              var len = binary.length;
              var bytes = new Uint8Array(len);
              for (var i = 0; i < len; i++) bytes[i] = binary.charCodeAt(i);
              return bytes;
            },
            callback: function(id, res) {
              var cb = this.pending[id];
              delete this.pending[id];
              if (!cb) return;
              if (res && res.error) {
                if (cb.onerror) cb.onerror({ error: res.error, status: 0 });
                return;
              }
              // Decode body. Native sends `body` as text (utf-8) and may also
              // send `bodyB64` for binary responses (blob / arraybuffer).
              var responseText = res.body || '';
              var response = responseText;
              try {
                if (cb.responseType === 'json') {
                  response = responseText ? JSON.parse(responseText) : null;
                } else if (cb.responseType === 'blob') {
                  var bytes = res.bodyB64 ? this._b64ToBytes(res.bodyB64)
                    : new TextEncoder().encode(responseText);
                  response = new Blob([bytes]);
                } else if (cb.responseType === 'arraybuffer') {
                  var bytesAB = res.bodyB64 ? this._b64ToBytes(res.bodyB64)
                    : new TextEncoder().encode(responseText);
                  response = bytesAB.buffer;
                }
              } catch (e) {
                if (cb.onerror) cb.onerror({ error: 'parse error: ' + e.message, status: res.status });
                return;
              }
              var headersText = (res && res.headers) || '';
              if (cb.onload) cb.onload({
                status: res.status,
                statusText: res.statusText || '',
                responseText: responseText,
                response: response,
                readyState: 4,
                finalUrl: res.finalUrl || cb.url,
                responseHeaders: headersText,
              });
            },
          };
          window.GM_xmlhttpRequest = function(opts) {
            opts = opts || {};
            var id = String(++window.__MgAfkBridge.nextId);
            window.__MgAfkBridge.pending[id] = {
              onload: opts.onload,
              onerror: opts.onerror,
              url: opts.url,
              responseType: opts.responseType || 'text',
            };
            try {
              var headersJson = JSON.stringify(opts.headers || {});
              var body = (opts.data == null) ? null : String(opts.data);
              window.MgAfkBridge.httpRequest(
                opts.method || 'GET',
                opts.url,
                headersJson,
                body,
                id,
                opts.responseType || 'text'
              );
            } catch (e) {
              delete window.__MgAfkBridge.pending[id];
              if (opts.onerror) opts.onerror({ error: e.message, status: 0 });
            }
            return { abort: function() { delete window.__MgAfkBridge.pending[id]; } };
          };
        })();
    """.trimIndent()

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        fetchJob?.cancel()
        scope.cancel()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
