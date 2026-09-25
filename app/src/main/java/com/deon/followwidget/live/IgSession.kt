package com.deon.followwidget.live

import android.content.Context

/**
 * The Instagram login session, captured from the in-app WebView login.
 * Only a session cookie is kept — the password never leaves the phone.
 * Stored in the app's private preferences, sent only to instagram.com.
 */
object IgSession {

    private const val PREFS = "follower_widget"
    private const val K_SESSION = "ig_sessionid"
    private const val K_CSRF = "ig_csrftoken"
    private const val K_UID = "ig_ds_user_id"
    private const val K_NAME = "ig_username"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(ctx: Context, sessionId: String, csrf: String, dsUserId: String, username: String) {
        prefs(ctx).edit()
            .putString(K_SESSION, sessionId)
            .putString(K_CSRF, csrf)
            .putString(K_UID, dsUserId)
            .putString(K_NAME, username.trim().lowercase().trimStart('@'))
            .apply()
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit()
            .remove(K_SESSION).remove(K_CSRF).remove(K_UID).remove(K_NAME)
            .apply()
    }

    fun isLoggedIn(ctx: Context): Boolean =
        prefs(ctx).getString(K_SESSION, "").orEmpty().isNotEmpty()

    fun username(ctx: Context): String =
        prefs(ctx).getString(K_NAME, "").orEmpty()

    fun sessionId(ctx: Context): String =
        prefs(ctx).getString(K_SESSION, "").orEmpty()

    fun csrfToken(ctx: Context): String =
        prefs(ctx).getString(K_CSRF, "").orEmpty()
}
