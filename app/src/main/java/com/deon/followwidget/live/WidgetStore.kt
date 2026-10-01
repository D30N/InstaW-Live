package com.deon.followwidget.live

import android.app.UiModeManager
import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * All persisted state: per-widget config, per-username cached data,
 * daily count snapshots (for the weekly delta), and avatar files.
 */
object WidgetStore {

    private const val PREFS = "follower_widget"
    private const val SNAPSHOT_KEEP_MS = 8L * 24 * 60 * 60 * 1000 // 8 days
    private const val SNAPSHOT_MIN_GAP_MS = 60L * 60 * 1000 // 1 hour

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(username: String) =
        username.trim().lowercase().trimStart('@')

    // ---------- per-widget config ----------

    fun getUsername(ctx: Context, appWidgetId: Int): String =
        prefs(ctx).getString("w_${appWidgetId}_username", "").orEmpty()

    fun setUsername(ctx: Context, appWidgetId: Int, username: String) {
        prefs(ctx).edit().putString("w_${appWidgetId}_username", key(username)).apply()
    }

    fun getTheme(ctx: Context, appWidgetId: Int): String =
        prefs(ctx).getString("w_${appWidgetId}_theme", "light") ?: "light"

    fun setTheme(ctx: Context, appWidgetId: Int, theme: String) {
        prefs(ctx).edit().putString("w_${appWidgetId}_theme", theme).apply()
    }

    // ---------- per-widget card style ----------

    /** "count_top" (default) or "name_top". */
    fun getCardStyle(ctx: Context, appWidgetId: Int): String =
        prefs(ctx).getString("w_${appWidgetId}_cardstyle", null)
            ?: getDefaultCardStyle(ctx)

    fun setCardStyle(ctx: Context, appWidgetId: Int, style: String) {
        prefs(ctx).edit().putString("w_${appWidgetId}_cardstyle", style).apply()
    }

    /**
     * Theme actually used for rendering: "system" follows the phone's
     * dark mode (light card / dark card), "system_frosted" follows it
     * with frosted cards, "system_liquid" follows it with liquid glass
     * cards (white glass in light mode, dark glass in dark mode).
     * too (frosted white / frosted black). Fixed themes pass through.
     *
     * Night mode is read from the system setting first (reliable on
     * every widget update), falling back to the resource configuration.
     */
    fun effectiveTheme(ctx: Context, appWidgetId: Int): String {
        val stored = getTheme(ctx, appWidgetId)
        if (stored != "system" && stored != "system_frosted" && stored != "system_liquid") return stored
        val night = isSystemNightMode(ctx)
        return when (stored) {
            "system" -> if (night) "dark" else "light"
            "system_liquid" -> if (night) "liquid_glass_dark" else "liquid_glass"
            else -> if (night) "frosted_black" else "frosted_white"
        }
    }

    /**
     * True when the phone is in dark mode. Asks UiModeManager (the system
     * service) directly — always fresh. The old Settings.Secure
     * "ui_night_mode" value goes stale on some phones after toggling dark
     * mode off, and the app's cached Resources configuration can be stale
     * when a background worker renders the widget.
     */
    fun isSystemNightMode(ctx: Context): Boolean {
        val um = ctx.getSystemService(UiModeManager::class.java)
        when (um?.nightMode) {
            UiModeManager.MODE_NIGHT_YES -> return true
            UiModeManager.MODE_NIGHT_NO -> return false
        }
        // AUTO or unknown — fall back to the old chain.
        val secure = runCatching {
            android.provider.Settings.Secure.getInt(
                ctx.contentResolver, "ui_night_mode", -1
            )
        }.getOrDefault(-1)
        if (secure == 2) return true
        if (secure == 1) return false
        return ctx.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    fun clearWidget(ctx: Context, appWidgetId: Int) {
        prefs(ctx).edit()
            .remove("w_${appWidgetId}_username")
            .remove("w_${appWidgetId}_theme")
            .remove("w_${appWidgetId}_cardstyle")
            .apply()
    }

    // ---------- per-username cached data ----------

    fun getCount(ctx: Context, rawUsername: String): Long =
        prefs(ctx).getLong("u_${key(rawUsername)}_count", -1)

    fun setCount(ctx: Context, rawUsername: String, count: Long) {
        val u = key(rawUsername)
        prefs(ctx).edit().putLong("u_${u}_count", count).apply()
        recordSnapshot(ctx, u, count)
    }

    fun getFullName(ctx: Context, rawUsername: String): String =
        prefs(ctx).getString("u_${key(rawUsername)}_fullname", "").orEmpty()

    fun setFullName(ctx: Context, rawUsername: String, fullName: String) {
        prefs(ctx).edit().putString("u_${key(rawUsername)}_fullname", fullName).apply()
    }

    // ---------- last successful fetch ----------

    fun getLastFetch(ctx: Context, rawUsername: String): Long =
        prefs(ctx).getLong("u_${key(rawUsername)}_lastfetch", 0)

    fun setLastFetch(ctx: Context, rawUsername: String, timeMs: Long) {
        prefs(ctx).edit().putLong("u_${key(rawUsername)}_lastfetch", timeMs).apply()
    }

    // ---------- verified badge ----------

    fun isVerified(ctx: Context, rawUsername: String): Boolean =
        prefs(ctx).getBoolean("u_${key(rawUsername)}_verified", false)

    fun setVerified(ctx: Context, rawUsername: String, v: Boolean) {
        prefs(ctx).edit().putBoolean("u_${key(rawUsername)}_verified", v).apply()
    }

    // ---------- manual growth / delta ----------

    private const val KEY_DELTA_PREFIX = "delta_manual_"

    /**
     * Manually set growth value for a username (the green "+123" text).
     * Null when unset — the auto-computed weekly delta is used instead.
     */
    fun getManualDelta(ctx: Context, rawUsername: String): Int? {
        val k = KEY_DELTA_PREFIX + key(rawUsername)
        val p = prefs(ctx)
        return if (p.contains(k)) p.getInt(k, 0) else null
    }

    fun setManualDelta(ctx: Context, rawUsername: String, v: Int?) {
        val k = KEY_DELTA_PREFIX + key(rawUsername)
        val e = prefs(ctx).edit()
        if (v == null) e.remove(k) else e.putInt(k, v)
        e.apply()
    }

    // ---------- snapshots / weekly delta ----------

    private fun getSnapshots(ctx: Context, u: String): JSONArray {
        val raw = prefs(ctx).getString("u_${u}_snaps", "[]").orEmpty()
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    private fun recordSnapshot(ctx: Context, u: String, count: Long) {
        val now = System.currentTimeMillis()
        val snaps = getSnapshots(ctx, u)
        // prune old
        var kept = JSONArray()
        for (i in 0 until snaps.length()) {
            val arr = snaps.optJSONArray(i) ?: continue
            if (now - arr.optLong(0) <= SNAPSHOT_KEEP_MS) kept.put(arr)
        }
        // append if enough time passed since last snapshot or count changed
        val last = if (kept.length() > 0) kept.optJSONArray(kept.length() - 1) else null
        val shouldAppend = last == null ||
            now - last.optLong(0) >= SNAPSHOT_MIN_GAP_MS ||
            last.optLong(1) != count
        if (shouldAppend) {
            kept.put(JSONArray().put(now).put(count))
        }
        prefs(ctx).edit().putString("u_${u}_snaps", kept.toString()).apply()
    }

    /**
     * Change in followers over the last 7 days (or since the oldest
     * snapshot if younger than that). Null when there is not enough
     * history to say anything meaningful.
     */
    fun weekDelta(ctx: Context, rawUsername: String, now: Long = System.currentTimeMillis()): Long? {
        val snaps = getSnapshots(ctx, key(rawUsername))
        if (snaps.length() == 0) return null
        val weekAgo = now - 7L * 24 * 60 * 60 * 1000
        // Baselines older than the keep window are stale (e.g. the Sep 23
        // seed) and must never anchor the rolling 7-day delta.
        val oldestUsable = now - SNAPSHOT_KEEP_MS
        var base: JSONArray? = null
        for (i in 0 until snaps.length()) {
            val arr = snaps.optJSONArray(i) ?: continue
            val t = arr.optLong(0)
            if (t <= weekAgo && t >= oldestUsable) base = arr // latest snapshot ~7 days old
        }
        if (base == null) {
            // No snapshot near 7 days ago — oldest usable snapshot we have.
            for (i in 0 until snaps.length()) {
                val arr = snaps.optJSONArray(i) ?: continue
                if (arr.optLong(0) >= oldestUsable) { base = arr; break }
            }
        }
        base ?: return null
        // Need a little history for the delta to mean anything (an hour —
        // the seed covers day one, real snapshots take over after that).
        if (now - base.optLong(0) < 60L * 60 * 1000) return null
        val current = getCount(ctx, rawUsername)
        if (current < 0) return null
        return current - base.optLong(1)
    }

    /**
     * One-line snapshot summary for the in-app diagnostics.
     */
    fun snapshotDiag(ctx: Context, rawUsername: String): String {
        val snaps = getSnapshots(ctx, key(rawUsername))
        val n = snaps.length()
        if (n == 0) return "snaps=0"
        val ageH = (System.currentTimeMillis() -
            (snaps.optJSONArray(0)?.optLong(0) ?: 0)) / 3600000
        return "snaps=$n oldest=${ageH}h ago delta=${weekDelta(ctx, rawUsername)}"
    }

    // ---------- avatars ----------

    private fun avatarFile(ctx: Context, rawUsername: String): File {
        val dir = File(ctx.filesDir, "avatars").apply { mkdirs() }
        val safe = key(rawUsername).replace(Regex("[^a-z0-9_.]"), "_")
        return File(dir, "$safe.png")
    }

    fun saveAvatar(ctx: Context, rawUsername: String, bytes: ByteArray) {
        runCatching { avatarFile(ctx, rawUsername).writeBytes(bytes) }
    }

    fun avatarPath(ctx: Context, rawUsername: String): String? {
        val f = avatarFile(ctx, rawUsername)
        return if (f.exists() && f.length() > 0) f.absolutePath else null
    }

    // ---------- global defaults ----------

    fun getDefaultUsername(ctx: Context): String =
        prefs(ctx).getString("default_username", "").orEmpty()

    fun setDefaultUsername(ctx: Context, username: String) {
        prefs(ctx).edit().putString("default_username", key(username)).apply()
    }

    fun getDefaultTheme(ctx: Context): String =
        prefs(ctx).getString("default_theme", "light") ?: "light"

    fun setDefaultTheme(ctx: Context, theme: String) {
        prefs(ctx).edit().putString("default_theme", theme).apply()
    }

    /** Default card style for new widgets: "count_top" or "name_top". */
    fun getDefaultCardStyle(ctx: Context): String =
        prefs(ctx).getString("default_cardstyle", "count_top") ?: "count_top"

    fun setDefaultCardStyle(ctx: Context, style: String) {
        prefs(ctx).edit().putString("default_cardstyle", style).apply()
    }

    // ---------- app UI theme (the app's own screens) ----------

    /** "light", "dark" or "system" (default). */
    fun getAppTheme(ctx: Context): String =
        prefs(ctx).getString("app_theme", "system") ?: "system"

    fun setAppTheme(ctx: Context, theme: String) {
        prefs(ctx).edit().putString("app_theme", theme).apply()
    }

    /** True when the app's own UI should render dark. */
    fun isAppDarkTheme(ctx: Context): Boolean = when (getAppTheme(ctx)) {
        "dark" -> true
        "light" -> false
        else -> isSystemNightMode(ctx)
    }
}
