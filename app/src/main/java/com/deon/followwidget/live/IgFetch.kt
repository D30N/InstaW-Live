package com.deon.followwidget.live

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches public Instagram profile data through Instagram's unofficial
 * web_profile_info endpoint — no login, no session, no account involved.
 *
 * This is the fragile part: Instagram rate-limits or blocks these calls
 * whenever it feels like it (especially from datacenter IPs). Everything
 * here fails soft — a failed fetch simply keeps the last known data.
 */
object IgFetch {

    data class Profile(
        val username: String,
        val fullName: String,
        val followers: Long,
        val isVerified: Boolean,
        val picUrl: String
    )

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    private const val APP_ID = "936619743392459"

    /**
     * Which source supplied the name/photo on the last dashboard fetch,
     * for the in-app diagnostics: meta / wpi / ppa / webview / none.
     */
    @Volatile
    var lastEnrichDiag: String? = null
        private set

    /** Null when Instagram refuses (rate-limit, block, unknown user…). */
    fun fetch(ctx: Context, rawUsername: String): Profile? {
        val u = rawUsername.trim().lowercase().trimStart('@')
        if (u.isEmpty()) return null
        // Strategy chain — each fails soft, the next one is tried:
        // 0. professional dashboard (logged-in user's own account only) —
        //    the EXACT count as rendered page text; it's the owner's own
        //    data, so it looks like normal browsing
        // 1. web_profile_info as the logged-in user (session cookie) — least likely blocked
        // 2. web_profile_info direct, anonymous (rich JSON)
        // 3. web_profile_info via a public CORS proxy (different egress IP)
        // 4. public profile page HTML direct — parse og: meta tags
        // 5. public profile page HTML via the proxy — parse og: meta tags
        if (IgSession.isLoggedIn(ctx)) {
            val dashCount = runCatching { WebFetch.fetchDashboardCount(ctx, u) }.getOrNull()
            // The later strategies overwrite WebFetch's failure info — keep
            // the dashboard diagnostic so the UI can show what actually
            // happened on the page that matters.
            val dashFailure = WebFetch.lastFailure
            val dashDiag = WebFetch.lastDiag
            if (dashCount != null) {
                // The dashboard gives the EXACT count but no name/photo.
                // Enrich from the most reliable source first: the og:
                // meta tags read inside the logged-in WebView (same
                // browser session as the dashboard — raw HTTP gets
                // fingerprinted and blocked, the WebView does not).
                // The HTTP enrichment chain stays as fallback.
                val meta = runCatching { WebFetch.grabProfileMeta(ctx, u) }.getOrNull()
                val metaName = meta?.first.orEmpty()
                val metaPic = meta?.second.orEmpty()
                var enrichSrc = "none"
                val enriched = if (metaName.isEmpty() && metaPic.isEmpty()) {
                    runCatching { fetchWebProfileInfo(u, direct = true, ctx = ctx) }.getOrNull()
                        ?.also { enrichSrc = "wpi" }
                        ?: runCatching { fetchProfilePageAuthed(ctx, u) }.getOrNull()
                            ?.also { enrichSrc = "ppa" }
                        ?: runCatching { WebFetch.fetch(ctx, u) }.getOrNull()
                            ?.also { enrichSrc = "webview" }
                } else {
                    enrichSrc = "meta"
                    null
                }
                lastEnrichDiag = enrichSrc
                WebFetch.restoreFailure(dashFailure, dashDiag)
                return Profile(
                    username = u,
                    fullName = metaName.takeIf { it.isNotEmpty() }
                        ?: enriched?.fullName?.takeIf { it.isNotEmpty() }
                        ?: WidgetStore.getFullName(ctx, u),
                    followers = dashCount,
                    isVerified = enriched?.isVerified ?: WidgetStore.isVerified(ctx, u),
                    picUrl = metaPic.takeIf { it.isNotEmpty() }
                        ?: enriched?.picUrl.orEmpty()
                )
            }
            runCatching { fetchWebProfileInfo(u, direct = true, ctx = ctx) }.getOrNull()
                ?.let { return it }
            // The API endpoint can stay blocked even for logged-in sessions;
            // the profile page itself, fetched with the session, usually isn't.
            runCatching { fetchProfilePageAuthed(ctx, u) }.getOrNull()?.let { return it }
            // Last resort: a real in-app browser. Raw HTTP gets fingerprinted
            // and blocked, but the WebView is a genuine browser environment.
            runCatching { WebFetch.fetch(ctx, u) }.getOrNull()?.let { return it }
            WebFetch.restoreFailure(dashFailure, dashDiag)
        }
        runCatching { fetchWebProfileInfo(u, direct = true, ctx = null) }.getOrNull()?.let { return it }
        runCatching { fetchWebProfileInfo(u, direct = false, ctx = null) }.getOrNull()?.let { return it }
        runCatching { fetchProfilePage(u, direct = true) }.getOrNull()?.let { return it }
        runCatching { fetchProfilePage(u, direct = false) }.getOrNull()?.let { return it }
        return null
    }

    private fun apiUrl(username: String) =
        "https://www.instagram.com/api/v1/users/web_profile_info/?username=" +
            URLEncoder.encode(username, "UTF-8")

    private fun proxyUrl(target: String) =
        "https://api.allorigins.win/raw?url=" + URLEncoder.encode(target, "UTF-8")

    private fun fetchWebProfileInfo(username: String, direct: Boolean, ctx: Context?): Profile? {
        val url = URL(if (direct) apiUrl(username) else proxyUrl(apiUrl(username)))
        val authed = ctx != null && IgSession.isLoggedIn(ctx)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            requestMethod = "GET"
            setRequestProperty("User-Agent", UA)
            setRequestProperty("x-ig-app-id", APP_ID)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            setRequestProperty("Referer", "https://www.instagram.com/$username/")
            if (authed) {
                val csrf = IgSession.csrfToken(ctx!!)
                setRequestProperty(
                    "Cookie",
                    "sessionid=${IgSession.sessionId(ctx)}" +
                        (if (csrf.isNotEmpty()) "; csrftoken=$csrf" else "")
                )
                if (csrf.isNotEmpty()) setRequestProperty("x-csrftoken", csrf)
            }
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            return parseWebProfileInfoJson(body, username)
        } finally {
            conn.disconnect()
        }
    }

    /** Parse a web_profile_info JSON body (exact follower count). */
    fun parseWebProfileInfoJson(body: String, username: String): Profile? {
        return runCatching {
            val user = JSONObject(body)
                .optJSONObject("data")?.optJSONObject("user") ?: return null
            val followers = user.optJSONObject("edge_followed_by")?.optLong("count", -1) ?: -1
            if (followers < 0) return null
            Profile(
                username = user.optString("username", username),
                fullName = user.optString("full_name", ""),
                followers = followers,
                isVerified = user.optBoolean("is_verified", false),
                picUrl = user.optString(
                    "profile_pic_url_hd",
                    user.optString("profile_pic_url", "")
                )
            )
        }.getOrNull()
    }

    /**
     * Fallback: fetch the public profile page and read the og: meta tags.
     * og:description looks like "56.1K Followers, 1,234 Following, 890 Posts …".
     * og:title looks like "Full Name (@username)". og:image is the avatar.
     */
    private fun fetchProfilePage(username: String, direct: Boolean): Profile? {
        val target = "https://www.instagram.com/$username/"
        val url = URL(if (direct) target else proxyUrl(target))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            requestMethod = "GET"
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "text/html")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != 200) return null
            // Meta tags sit in <head>; the first 64KB is plenty.
            val html = conn.inputStream.bufferedReader().readText().take(65536)
            val desc = metaContent(html, "og:description") ?: return null
            val followers = parseFollowerCount(desc) ?: return null
            val title = metaContent(html, "og:title") ?: ""
            val fullName = title.substringBefore(" (@").trim()
            return Profile(
                username = username,
                fullName = fullName,
                followers = followers,
                isVerified = false,
                picUrl = metaContent(html, "og:image") ?: ""
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Profile page fetched WITH the login session. A logged-in page view
     * carries the full embedded JSON (exact follower count), and is far
     * less likely to be blocked than the API endpoint.
     */
    private fun fetchProfilePageAuthed(ctx: Context, username: String): Profile? {
        val csrf = IgSession.csrfToken(ctx)
        val conn = (URL("https://www.instagram.com/$username/").openConnection()
            as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            requestMethod = "GET"
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "text/html")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty(
                "Cookie",
                "sessionid=${IgSession.sessionId(ctx)}" +
                    (if (csrf.isNotEmpty()) "; csrftoken=$csrf" else "")
            )
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != 200) return null
            // Embedded JSON can sit well past <head>; read up to 256KB.
            val html = conn.inputStream.bufferedReader().readText().take(262144)
            return parseAuthedHtml(username, html)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Parse a logged-in profile page: exact count from the embedded JSON
     * first, falling back to the og: meta tags.
     */
    fun parseAuthedHtml(username: String, html: String): Profile? {
        // Exact count from the embedded JSON first (try both known keys —
        // Instagram renames these without notice).
        val edge = Regex(""""edge_followed_by"\s*:\s*\{"count"\s*:\s*(\d+)""")
            .find(html)?.groupValues?.get(1)?.toLongOrNull()
            ?: Regex(""""follower_count"\s*:\s*(\d+)""")
                .find(html)?.groupValues?.get(1)?.toLongOrNull()
        val fullName = Regex(""""full_name"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(html)?.groupValues?.get(1)
            ?.replace("\\u0026", "&")?.replace("\\\"", "\"").orEmpty()
        val verified = Regex(""""is_verified"\s*:\s*(true|false)""")
            .find(html)?.groupValues?.get(1) == "true"
        val pic = Regex(""""profile_pic_url_hd"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            .find(html)?.groupValues?.get(1)
            ?.replace("\\u0026", "&").orEmpty()
        if (edge != null) {
            return Profile(
                username = username,
                fullName = fullName,
                followers = edge,
                isVerified = verified,
                picUrl = pic
            )
        }
        // Fall back to the og: meta tags, like the anonymous page fetch.
        val desc = metaContent(html, "og:description") ?: return null
        val followers = parseFollowerCount(desc) ?: return null
        val title = metaContent(html, "og:title") ?: ""
        return Profile(
            username = username,
            fullName = title.substringBefore(" (@").trim(),
            followers = followers,
            isVerified = false,
            picUrl = metaContent(html, "og:image") ?: ""
        )
    }

    private fun metaContent(html: String, property: String): String? {
        val re = Regex(
            """<meta\s+property=["']""" + Regex.escape(property) +
                """["']\s+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        return re.find(html)?.groupValues?.get(1)
            ?.replace("&amp;", "&")?.replace("&quot;", "\"")
    }

    private fun parseFollowerCount(desc: String): Long? {
        // e.g. "56.1K Followers, 1,234 Following, 890 Posts"
        val m = Regex("""([\d.,]+)\s*([KMB]?)\s*Followers""", RegexOption.IGNORE_CASE)
            .find(desc) ?: return null
        val num = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return null
        val mult = when (m.groupValues[2].uppercase()) {
            "K" -> 1_000L
            "M" -> 1_000_000L
            "B" -> 1_000_000_000L
            else -> 1L
        }
        return (num * mult).toLong()
    }

    private fun downloadBytes(url: String): ByteArray? {
        if (url.isEmpty()) return null
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Referer", "https://www.instagram.com/")
            }
            try {
                if (conn.responseCode != 200) return null
                conn.inputStream.readBytes().takeIf { it.isNotEmpty() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /**
     * Fetches and persists everything the widget needs. Returns true on
     * success. Must be called off the main thread.
     */
    fun fetchAndStore(ctx: Context, rawUsername: String): Boolean {
        val p = fetch(ctx, rawUsername) ?: return false
        WidgetStore.setCount(ctx, p.username, p.followers)
        if (p.fullName.isNotEmpty()) WidgetStore.setFullName(ctx, p.username, p.fullName)
        WidgetStore.setVerified(ctx, p.username, p.isVerified)
        downloadBytes(p.picUrl)?.let { WidgetStore.saveAvatar(ctx, p.username, it) }
        WidgetStore.setLastFetch(ctx, p.username, System.currentTimeMillis())
        return true
    }
}
