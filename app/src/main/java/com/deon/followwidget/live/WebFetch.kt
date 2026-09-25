package com.deon.followwidget.live

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Fetch a profile through a real WebView instead of raw HTTP.
 *
 * Instagram fingerprints raw HTTP clients (TLS/JA3) and blocks them even
 * when the request carries a valid login session — but the in-app WebView
 * is a genuine browser environment, and it demonstrably loads instagram.com
 * on this phone (the login page works through it). So when every HTTP
 * strategy fails, we load the profile page in a hidden WebView with the
 * saved session cookies and pull the data out from inside the page.
 *
 * Must be called off the main thread — it blocks waiting for the page.
 */
object WebFetch {

    /**
     * Why the last fetch failed, for diagnostics shown in the UI:
     * login_required (session dead — user must log in again),
     * not_found, no_data, empty, parse, webview, timeout,
     * dash_nolink (no dashboard entry on the profile),
     * dash_nocount (dashboard opened, count not visible),
     * dash_noload / dash_timeout (page never loaded in time).
     */
    @Volatile
    var lastFailure: String? = null
        private set

    /**
     * Human-readable snapshot of what the dashboard page actually contained
     * when the count wasn't found (URL, title, text length, keywords). Shown
     * in the UI so the failure can be diagnosed remotely.
     */
    @Volatile
    var lastDiag: String? = null
        private set

    /**
     * Outcome of the last profile-meta grab (og: tags in the WebView),
     * for the in-app diagnostics: ok / no_meta / load_fail / timeout.
     */
    @Volatile
    var lastMetaDiag: String? = null
        private set

    fun fetch(ctx: Context, rawUsername: String, timeoutMs: Long = 45000): IgFetch.Profile? {
        val username = rawUsername.trim().lowercase().trimStart('@')
        if (username.isEmpty()) return null
        // Never block the main thread waiting on itself.
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        if (!IgSession.isLoggedIn(ctx)) {
            lastFailure = "login_required"
            return null
        }
        lastFailure = null
        lastDiag = null

        val appCtx = ctx.applicationContext
        val result = AtomicReference<IgFetch.Profile?>(null)
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<WebView>(1)
        val main = Handler(Looper.getMainLooper())

        main.post {
            try {
                val web = WebView(appCtx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // No images — we only need the HTML/JSON.
                    settings.loadsImagesAutomatically = false
                    settings.blockNetworkImage = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            try {
                                // 1) Ask Instagram's own API from inside the real
                                //    browser (session cookies + browser fingerprint
                                //    go along). Capped at 10s so a hanging request
                                //    can't block the DOM fallback below.
                                // 2) Poll the DOM for the embedded profile JSON
                                //    (React hydrates it late), up to ~15s.
                                // 3) Otherwise report the final URL + title so we
                                //    can tell a dead session from a block.
                                val u = JSONObject.quote(username)
                                val js = """(async () => {
                                  const u = $u;
                                  try {
                                    const ctrl = new AbortController();
                                    const to = setTimeout(() => ctrl.abort(), 10000);
                                    const r = await fetch('/api/v1/users/web_profile_info/?username=' + u,
                                      { headers: { 'X-IG-App-ID': '936619743392459' }, signal: ctrl.signal });
                                    clearTimeout(to);
                                    const t = await r.text();
                                    if (t && t.indexOf('edge_followed_by') !== -1) return 'API:' + t;
                                  } catch (e) {}
                                  for (let i = 0; i < 30; i++) {
                                    const h = document.documentElement.outerHTML;
                                    if (h.indexOf('edge_followed_by') !== -1) return 'DOM:' + h;
                                    await new Promise(rr => setTimeout(rr, 500));
                                  }
                                  return 'DIAG:url=' + location.href + '|title=' + document.title;
                                })()""".trimIndent()
                                view?.evaluateJavascript(js) { raw ->
                                    try {
                                        // evaluateJavascript returns a JSON-encoded string.
                                        val payload = JSONArray("[$raw]").getString(0)
                                        when {
                                            payload.startsWith("API:") ->
                                                result.set(
                                                    IgFetch.parseWebProfileInfoJson(
                                                        payload.removePrefix("API:"),
                                                        username
                                                    )
                                                )
                                            payload.startsWith("DOM:") ->
                                                result.set(
                                                    IgFetch.parseAuthedHtml(
                                                        username,
                                                        payload.removePrefix("DOM:")
                                                    )
                                                )
                                            payload.startsWith("DIAG:") ->
                                                lastFailure = classify(
                                                    payload.removePrefix("DIAG:")
                                                )
                                            else -> lastFailure = "empty"
                                        }
                                        if (result.get() == null && lastFailure == null) {
                                            lastFailure = "no_data"
                                        }
                                    } catch (_: Exception) {
                                        lastFailure = "parse"
                                    }
                                    latch.countDown()
                                }
                            } catch (_: Exception) {
                                lastFailure = "webview"
                                latch.countDown()
                            }
                        }
                    }
                }
                holder[0] = web
                web.loadUrl("https://www.instagram.com/$username/")
            } catch (_: Exception) {
                lastFailure = "webview"
                latch.countDown()
            }
        }

        val finished = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        main.post {
            try {
                holder[0]?.stopLoading()
                holder[0]?.destroy()
            } catch (_: Exception) {
            }
        }
        if (!finished && lastFailure == null) lastFailure = "timeout"
        return result.get()
    }

    /**
     * Re-assert a previously captured failure after a later strategy wiped
     * it — the dashboard diagnostic is the most useful one to show.
     */
    fun restoreFailure(failure: String?, diag: String?) {
        if (failure != null) lastFailure = failure
        if (diag != null) lastDiag = diag
    }

    private fun classify(diag: String): String {
        val lower = diag.lowercase()
        return when {
            "challenge" in lower || "/accounts/login" in lower -> "login_required"
            lower.contains("not found") -> "not_found"
            else -> "no_data"
        }
    }

    /**
     * The user's own professional dashboard renders the EXACT follower count
     * as page text ("Total followers 56,114"). It's the account owner's own
     * data, so reading it looks like normal browsing rather than scraping.
     *
     * Two attempts: (A) the dashboard URL directly — no link-hunting needed;
     * (B) the own profile page, finding and opening the "Professional
     * dashboard" entry (it only exists on the logged-in user's OWN profile,
     * so the page self-validates). Returns the exact count, or null.
     *
     * Must be called off the main thread.
     */
    fun fetchDashboardCount(ctx: Context, rawUsername: String): Long? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        if (!IgSession.isLoggedIn(ctx)) {
            lastFailure = "login_required"
            return null
        }
        lastFailure = null
        lastDiag = null
        val username = rawUsername.trim().lowercase().trimStart('@')

        // Attempt A: the Insights page — it carries the EXACT "Total
        // followers" number at the very bottom of the page:
        //   https://www.instagram.com/accounts/insights/?timeframe=14
        dashboardAttempt(ctx, "https://www.instagram.com/accounts/insights/?timeframe=14", needClick = false)
            ?.let { return it }
        if (lastFailure == "login_required") return null // session dead; B can't help
        // Attempt B: own profile page -> open the dashboard entry point.
        if (username.isNotEmpty()) {
            dashboardAttempt(ctx, "https://www.instagram.com/$username/", needClick = true)
                ?.let { return it }
        }
        return null
    }

    /**
     * Display name + avatar URL from the profile page's og: meta tags,
     * read inside the logged-in WebView (the same browser session the
     * dashboard uses — raw HTTP is fingerprinted and blocked, the WebView
     * is not). og:title looks like "Deepak Deon (@deepak.deon)" and
     * og:image is the avatar. Both tags are server-rendered in <head>,
     * so no JS-app rendering is needed.
     * Returns Pair(fullName, picUrl); either may be empty. Null when the
     * page itself couldn't be loaded. Must be called off the main thread.
     */
    fun grabProfileMeta(ctx: Context, rawUsername: String): Pair<String, String>? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        if (!IgSession.isLoggedIn(ctx)) {
            lastMetaDiag = "not_logged_in"
            return null
        }
        val username = rawUsername.trim().lowercase().trimStart('@')
        if (username.isEmpty()) return null
        lastMetaDiag = null

        val appCtx = ctx.applicationContext
        val main = Handler(Looper.getMainLooper())
        val out = AtomicReference<Pair<String, String>?>(null)
        val done = CountDownLatch(1)
        val webRef = AtomicReference<WebView?>(null)

        main.post {
            try {
                val web = WebView(appCtx).apply {
                    settings.javaScriptEnabled = true // needed for evaluateJavascript
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = false
                    settings.blockNetworkImage = true
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                lastMetaDiag = "load_fail"
                                done.countDown()
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            try {
                                view?.evaluateJavascript(
                                    """(() => {
                                      try {
                                        const mt = p => document.querySelector('meta[property="' + p + '"]');
                                        const t = mt('og:title'), i = mt('og:image');
                                        return JSON.stringify({t: t ? t.content : '', i: i ? i.content : ''});
                                      } catch (e) { return '{}'; }
                                    })()"""
                                ) { raw ->
                                    try {
                                        val o = JSONObject(JSONArray("[$raw]").getString(0))
                                        val title = o.optString("t", "")
                                        val img = o.optString("i", "")
                                        val name = title.substringBefore(" (@").trim()
                                        if (name.isNotEmpty() || img.isNotEmpty()) {
                                            out.set(Pair(name, img))
                                            lastMetaDiag = "ok name='$name' pic=${if (img.isNotEmpty()) "yes" else "no"}"
                                        } else {
                                            lastMetaDiag = "no_meta"
                                        }
                                    } catch (_: Exception) {
                                    }
                                    done.countDown()
                                }
                            } catch (_: Exception) {
                                done.countDown()
                            }
                        }
                    }
                }
                webRef.set(web)
                web.loadUrl("https://www.instagram.com/$username/")
            } catch (_: Exception) {
                done.countDown()
            }
        }

        done.await(25, TimeUnit.SECONDS)
        if (lastMetaDiag == null) lastMetaDiag = "timeout"
        main.post {
            try {
                webRef.getAndSet(null)?.destroy()
            } catch (_: Exception) {
            }
        }
        return out.get()
    }

    /**
     * One dashboard attempt: loads [url] in a hidden WebView with the saved
     * session cookies, then polls the rendered page for the exact follower
     * count. With [needClick], the page is the user's own profile: its
     * embedded JSON is scanned first (it carries the exact count even when
     * the visible text shows "56.1k"), then the professional dashboard is
     * opened through the More menu as a second chance.
     * Returns the count, or null (with [lastFailure] naming the stage).
     *
     * Robustness note: Instagram's pages are React SPAs whose client-side
     * navigations kill long-running injected scripts, so the app drives
     * the page instead — each check is a tiny synchronous JS snippet run
     * from a Kotlin polling loop.
     */
    private fun dashboardAttempt(
        ctx: Context,
        url: String,
        needClick: Boolean,
        timeoutMs: Long = 60000
    ): Long? {
        val appCtx = ctx.applicationContext
        val main = Handler(Looper.getMainLooper())
        val webRef = AtomicReference<WebView?>(null)
        val pageDone = CountDownLatch(1)
        val pageFailed = AtomicBoolean(false)

        main.post {
            try {
                val web = WebView(appCtx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = false
                    settings.blockNetworkImage = true
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                pageFailed.set(true)
                                pageDone.countDown()
                            }
                        }

                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            pageDone.countDown()
                        }
                    }
                }
                webRef.set(web)
                web.loadUrl(url)
            } catch (_: Exception) {
                pageFailed.set(true)
                pageDone.countDown()
            }
        }

        fun destroy() {
            main.post {
                try {
                    webRef.getAndSet(null)?.let {
                        it.stopLoading()
                        it.destroy()
                    }
                } catch (_: Exception) {
                }
            }
        }

        // Run one small synchronous JS snippet; null when the page is gone.
        fun eval(js: String, waitMs: Long = 9000): String? {
            val web = webRef.get() ?: return null
            val out = AtomicReference<String?>(null)
            val done = CountDownLatch(1)
            main.post {
                try {
                    web.evaluateJavascript(js) { raw ->
                        out.set(runCatching { JSONArray("[$raw]").getString(0) }.getOrNull())
                        done.countDown()
                    }
                } catch (_: Exception) {
                    done.countDown()
                }
            }
            done.await(waitMs, TimeUnit.MILLISECONDS)
            return out.get()
        }

        // One check: exact count from the page's embedded JSON first (the
        // server data behind "56.1k"), then the visible text. Returns
        // COUNT:<n>, LOGIN, POLL:<json>, JSERR:..., or a raw fallback.
        // insightsStrict: on the Insights page ONLY the exact "N Total
        // followers" pattern (plus embedded JSON) may match. The generic
        // number-before-"followers" pattern is banned there — while the
        // bottom of the page is still rendering it would wrongly grab
        // "External link taps 1,545" sitting above the Followers heading.
        fun countJs(insightsStrict: Boolean): String {
            val generic = if (insightsStrict) "" else """
            // Number BEFORE the word "followers" (profile layout:
            // "56.1k\nfollowers\n1,274\nfollowing"). Handles 56,114 and
            // abbreviated 56.1k / 1.2m forms. Guarded: never the number
            // above a "Followers" section heading (e.g. the Insights
            // page's "External link taps 1,545" sitting over "Followers").
            m = t.match(/([\d,]+(?:\.\d+)?)\s*([kmb])?\s+followers(?![\s\S]{0,60}total followers)/i);
            if (m) {
              let n = parseFloat(m[1].replace(/,/g, ''));
              const suf = (m[2] || '').toLowerCase();
              if (suf === 'k') n *= 1000;
              else if (suf === 'm') n *= 1000000;
              else if (suf === 'b') n *= 1000000000;
              n = Math.round(n);
              if (n > 500) return 'COUNT:' + n;
            }
""".trimIndent()
            return """(() => {
          try {
            const html = document.documentElement
              ? document.documentElement.innerHTML.substring(0, 800000) : '';
            const exactPats = [
              /"edge_followed_by"\s*:\s*\{\s*"count"\s*:\s*(\d+)/i,
              /"followed_by"\s*:\s*\{\s*"count"\s*:\s*(\d+)/i,
              /"follower_count"\s*:\s*(\d+)/i
            ];
            for (const p of exactPats) {
              const m = html.match(p);
              if (m) {
                const n = parseInt(m[1], 10);
                if (n > 500) return 'COUNT:' + n;
              }
            }
            const t = document.body ? document.body.innerText : '';
            // "56,149\nTotal followers" — the Insights page layout (the
            // exact number lives at the very bottom of the page).
            let m = t.match(/(\d{1,3}(?:,\d{3})+)\s+total followers/i);
            if (m) {
              const n = parseInt(m[1].replace(/,/g, ''), 10);
              if (n > 500) return 'COUNT:' + n;
            }
$generic
            // "Total followers 56,114" (dashboard layout) — but never the
            // number that belongs to "following".
            m = t.match(/followers[^0-9]{0,40}(\d{1,3}(?:,\d{3})+|\d{4,9})(?![\d,])(?!\s*following)/i);
            if (m) {
              const n = parseInt(m[1].replace(/,/g, ''), 10);
              if (n > 500) return 'COUNT:' + n;
            }
            if (window.location.href.toLowerCase().indexOf('/accounts/login') !== -1) return 'LOGIN';
            if (document.body) window.scrollTo(0, document.body.scrollHeight);
            const lt = t.toLowerCase();
            const hl = html.toLowerCase();
            return 'POLL:' + JSON.stringify({
              url: window.location.href,
              title: document.title,
              len: t.length,
              loginWall: lt.indexOf('log in') !== -1,
              sawDashboard: lt.indexOf('dashboard') !== -1,
              sawFollowersWord: lt.indexOf('followers') !== -1,
              sawSuspicious: lt.indexOf('suspicious') !== -1,
              htmlLen: html.length,
              hasEdge: hl.indexOf('edge_followed_by') !== -1,
              hasFcount: hl.indexOf('follower_count') !== -1,
              head: t.substring(0, 160)
            });
          } catch (e) { return 'JSERR:' + String((e && e.message) || e).substring(0, 120); }
        })()""".trimIndent()
        }

        var lastPoll: String? = null

        // Poll the current page until the deadline. Returns the count, or
        // null (sets lastFailure="login_required" when the session is dead).
        fun pollForCount(deadlineMs: Long): Long? {
            val deadline = System.currentTimeMillis() + deadlineMs
            val js = countJs(insightsStrict = !needClick)
            while (System.currentTimeMillis() < deadline) {
                when (val r = eval(js)) {
                    null -> Thread.sleep(1500)
                    "LOGIN" -> {
                        lastFailure = "login_required"
                        return null
                    }
                    else -> when {
                        r.startsWith("COUNT:") -> {
                            r.removePrefix("COUNT:").toLongOrNull()?.let { return it }
                            Thread.sleep(1500)
                        }
                        r.startsWith("POLL:") -> {
                            lastPoll = r.removePrefix("POLL:")
                            Thread.sleep(2500)
                        }
                        else -> {
                            lastPoll = "raw=" + r.take(160)
                            Thread.sleep(2500)
                        }
                    }
                }
            }
            return null
        }

        try {
            if (!pageDone.await(20000, TimeUnit.MILLISECONDS) || pageFailed.get()) {
                lastFailure = "dash_noload"
                return null
            }

            if (needClick) {
                // Phase 1: the profile page itself may carry the exact
                // count in its embedded JSON — scan it first.
                pollForCount(20000)?.let { return it }
                if (lastFailure == "login_required") return null

                // Phase 2: open the professional dashboard through the
                // More menu — the dashboard entry only exists on the
                // logged-in user's OWN profile, so the page self-validates.
                val findJs = """(() => {
                  try {
                    const norm = s => (s || '').replace(/\s+/g, ' ').trim().toLowerCase();
                    const els = [...document.querySelectorAll('a, button, [role="button"]')];
                    const f = els.find(e => norm(e.innerText).includes('professional dashboard'));
                    if (!f) return 'NONE';
                    if (f.tagName === 'A' && f.href) { window.location.href = f.href; return 'NAV'; }
                    f.click();
                    return 'NAV';
                  } catch (e) { return 'JSERR'; }
                })()""".trimIndent()
                val moreJs = """(() => {
                  try {
                    const norm = s => (s || '').replace(/\s+/g, ' ').trim().toLowerCase();
                    const els = [...document.querySelectorAll('button, [role="button"], a')];
                    const f = els.find(e => norm(e.innerText) === 'more');
                    if (!f) return 'NONE';
                    f.click();
                    return 'NAV';
                  } catch (e) { return 'JSERR'; }
                })()""".trimIndent()
                val snapJs = """(() => {
                  try {
                    const t = document.body ? document.body.innerText : '';
                    const lt = t.toLowerCase();
                    return 'SNAP:' + JSON.stringify({
                      url: window.location.href,
                      title: document.title,
                      len: t.length,
                      loginWall: lt.indexOf('log in') !== -1,
                      head: t.substring(0, 160)
                    });
                  } catch (e) { return 'JSERR'; }
                })()""".trimIndent()
                var opened = false
                var moreTried = false
                var profileSnap: String? = null
                val clickDeadline = System.currentTimeMillis() + 30000
                while (System.currentTimeMillis() < clickDeadline && !opened) {
                    when (eval(findJs)) {
                        "NAV" -> opened = true
                        else -> {
                            eval(snapJs)?.let {
                                if (it.startsWith("SNAP:")) profileSnap = it.removePrefix("SNAP:")
                            }
                            if (!moreTried) {
                                moreTried = true
                                if (eval(moreJs) == "NAV") Thread.sleep(3000)
                            } else {
                                Thread.sleep(2500)
                            }
                        }
                    }
                }
                if (!opened) {
                    lastDiag = (profileSnap ?: lastPoll ?: "no-profile-data").take(400)
                    lastFailure = "dash_nolink"
                    return null
                }
                // Let the dashboard page start rendering.
                Thread.sleep(4000)
            }

            // Final poll of the current page (dashboard, or the direct URL).
            pollForCount(30000)?.let { return it }
            if (lastFailure == "login_required") return null
            lastDiag = (lastPoll ?: "no-poll-data").take(400)
            lastFailure = "dash_nocount"
            return null
        } finally {
            destroy()
        }
    }
}
