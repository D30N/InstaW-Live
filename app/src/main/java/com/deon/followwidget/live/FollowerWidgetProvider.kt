package com.deon.followwidget.live

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Single resizable follower widget. The compact (iOS-style) card scales
 * proportionally to the widget's smaller dimension; wide-short widgets
 * use the full horizontal card.
 * Class name kept stable so existing home-screen widgets survive updates.
 */
class FollowerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) updateWidget(ctx, id)
        schedulePeriodic(ctx)
        // Kick off a redraw so a freshly placed widget populates quickly.
        enqueueRefresh(ctx, ids)
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        ids.forEach { WidgetStore.clearWidget(ctx, it) }
    }

    override fun onAppWidgetOptionsChanged(
        ctx: Context, mgr: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(ctx, mgr, appWidgetId, newOptions)
        updateWidget(ctx, appWidgetId)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        // Best-effort: redraw on system theme toggle so "system" themes
        // follow dark mode. (Not delivered to manifest receivers on modern
        // Android — the periodic worker + app-open refresh are the real paths.)
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            for (id in allWidgetIds(ctx)) updateWidget(ctx, id)
        }
    }

    companion object {
        private const val PERIODIC_TAG = "follower-refresh"

        fun allWidgetIds(ctx: Context): IntArray {
            val mgr = AppWidgetManager.getInstance(ctx)
            return mgr.getAppWidgetIds(ComponentName(ctx, FollowerWidgetProvider::class.java))
        }

        /** Enqueue a redraw for every widget instance. */
        fun refreshAllProviders(ctx: Context) {
            val all = allWidgetIds(ctx)
            if (all.isNotEmpty()) enqueueRefresh(ctx, all)
        }

        fun schedulePeriodic(ctx: Context) {            val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .addTag(PERIODIC_TAG)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                PERIODIC_TAG, ExistingPeriodicWorkPolicy.UPDATE, req
            )
        }

        fun enqueueRefresh(ctx: Context, ids: IntArray) {
            val data = Data.Builder().putIntArray(RefreshWorker.KEY_IDS, ids).build()
            val req = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "follower-refresh-now", ExistingWorkPolicy.REPLACE, req
            )
        }

        fun updateWidget(ctx: Context, appWidgetId: Int) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val opts = mgr.getAppWidgetOptions(appWidgetId)
            val minWidth = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            val minHeight = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            val isNameTop = WidgetStore.getCardStyle(ctx, appWidgetId) == "name_top"
            val isFullSize = minWidth > 250 && minHeight < 160
            // Name-top is the user's explicit choice — honor it at any size.
            // At small sizes use the compact name-top layout (like 1st widget
            // uses compact), at full size use the full name-top layout.
            val layoutRes = when {
                isNameTop && isFullSize -> R.layout.widget_follower_name_top
                isNameTop -> R.layout.widget_follower_name_top_compact
                isFullSize -> R.layout.widget_follower
                else -> R.layout.widget_follower_compact
            }
            val views = RemoteViews(ctx.packageName, layoutRes)
            val username = WidgetStore.getUsername(ctx, appWidgetId)

            if (username.isBlank()) {
                renderSetupPrompt(ctx, views, appWidgetId)
            } else {
                renderFollowerCard(ctx, views, appWidgetId, username)
            }

            // Continuous proportional scaling: for compact layout, and for
            // name-top at small sizes (where 1st widget uses compact).
            // Uses compact base values (26sp/26dp) so both styles match.
            val isCompact = layoutRes == R.layout.widget_follower_compact ||
                    layoutRes == R.layout.widget_follower_name_top_compact
            if (isCompact) {
                val scale = (minOf(minWidth, minHeight) / 110f).coerceIn(1f, 2.31f)
                views.setTextViewTextSize(R.id.count, TypedValue.COMPLEX_UNIT_SP, (26f * scale).coerceAtMost(54f))
                views.setTextViewTextSize(R.id.followers_label, TypedValue.COMPLEX_UNIT_SP, (11f * scale).coerceAtMost(23f))
                views.setTextViewTextSize(R.id.delta, TypedValue.COMPLEX_UNIT_SP, (11f * scale).coerceAtMost(23f))
                views.setTextViewTextSize(R.id.delta_dark, TypedValue.COMPLEX_UNIT_SP, (11f * scale).coerceAtMost(23f))
                views.setTextViewTextSize(R.id.delta_down, TypedValue.COMPLEX_UNIT_SP, (11f * scale).coerceAtMost(23f))
                views.setTextViewTextSize(R.id.delta_down_dark, TypedValue.COMPLEX_UNIT_SP, (11f * scale).coerceAtMost(23f))
                views.setTextViewTextSize(R.id.fullname, TypedValue.COMPLEX_UNIT_SP, (11f * scale).coerceAtMost(20f))
                views.setTextViewTextSize(R.id.username, TypedValue.COMPLEX_UNIT_SP, (10f * scale).coerceAtMost(21f))
                if (Build.VERSION.SDK_INT >= 31) {
                    val av = (26f * scale).coerceAtMost(60f).toInt()
                    views.setViewLayoutWidth(R.id.avatar, av.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                    views.setViewLayoutHeight(R.id.avatar, av.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                    val bd = (12f * scale).coerceAtMost(25f).toInt()
                    views.setViewLayoutWidth(R.id.verified_badge, bd.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                    views.setViewLayoutHeight(R.id.verified_badge, bd.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
                }
            }

            mgr.updateAppWidget(appWidgetId, views)
        }

        /**
         * Builds a RemoteViews for the in-app CARD STYLE preview. Uses the
         * live WidgetStore data for [username] and the given [themeKey]
         * (system themes resolved to concrete). Uses dedicated preview
         * layouts with readable text sizes. No widget id needed.
         * When [forPreview] is true, tap actions are omitted so the
         * preview itself isn't clickable.
         */
        fun createStylePreview(
            ctx: Context, style: String, themeKey: String, username: String,
            forPreview: Boolean = false
        ): RemoteViews {
            val layoutRes = if (style == "name_top") R.layout.preview_style_name_top
                            else R.layout.preview_style_count_top
            val views = RemoteViews(ctx.packageName, layoutRes)
            val theme = when (themeKey) {
                "system" -> if (WidgetStore.isSystemNightMode(ctx)) "dark" else "light"
                "system_frosted" ->
                    if (WidgetStore.isSystemNightMode(ctx)) "frosted_black" else "frosted_white"
                "system_liquid" ->
                    if (WidgetStore.isSystemNightMode(ctx)) "liquid_glass_dark" else "liquid_glass"
                else -> themeKey
            }
            // appWidgetId -1: only used for effectiveTheme (overridden) and
            // the tap PendingIntent request code (skipped for previews).
            renderFollowerCard(ctx, views, -1, username, theme, forPreview)
            return views
        }

        private fun renderSetupPrompt(ctx: Context, views: RemoteViews, appWidgetId: Int) {
            views.setInt(R.id.card, "setBackgroundResource", R.drawable.card_bg_light)
            views.setTextViewText(R.id.count, "—")
            views.setTextColor(R.id.count, ctx.getColor(R.color.ink))
            views.setTextViewText(R.id.followers_label, "followers")
            views.setTextColor(R.id.followers_label, ctx.getColor(R.color.ink_soft))
            views.setTextViewText(R.id.fullname, "Tap to set up")
            views.setTextColor(R.id.fullname, ctx.getColor(R.color.ink))
            views.setTextViewText(R.id.username, "open the app and add your details")
            views.setTextColor(R.id.username, ctx.getColor(R.color.ink_faint))
            views.setViewVisibility(R.id.username, View.VISIBLE)
            views.setTextViewText(R.id.delta, "")
            views.setViewVisibility(R.id.delta_down, View.GONE)
            views.setViewVisibility(R.id.delta_dark, View.GONE)
            views.setViewVisibility(R.id.delta_down_dark, View.GONE)
            views.setTextViewText(R.id.delta_label, "")
            views.setImageViewResource(R.id.avatar, R.drawable.avatar_placeholder)

            val configIntent = Intent(ctx, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            val pi = PendingIntent.getActivity(
                ctx, appWidgetId, configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.card, pi)
        }

        private fun renderFollowerCard(
            ctx: Context, views: RemoteViews, appWidgetId: Int, username: String,
            themeOverride: String? = null, forPreview: Boolean = false
        ) {
            val theme = themeOverride ?: WidgetStore.effectiveTheme(ctx, appWidgetId)
            val darkText = theme == "light" || theme == "frosted_white" || theme == "liquid_glass"
            val ink = ctx.getColor(if (darkText) R.color.ink else R.color.ink_dark)
            // White liquid glass is translucent: medium-gray secondary inks
            // wash out on it, so use darker glass-specific inks.
            val inkSoft = ctx.getColor(when {
                theme == "liquid_glass" -> R.color.ink_soft_glass
                darkText -> R.color.ink_soft
                else -> R.color.ink_soft_dark
            })
            val inkFaint = ctx.getColor(when {
                theme == "liquid_glass" -> R.color.ink_faint_glass
                darkText -> R.color.ink_faint
                else -> R.color.ink_faint_dark
            })

            views.setInt(
                R.id.card, "setBackgroundResource",
                when (theme) {
                    "dark" -> R.drawable.card_bg_dark
                    "frosted_white" -> R.drawable.card_frosted_white
                    "frosted_black" -> R.drawable.card_frosted_black
                    "liquid_glass" -> R.drawable.card_liquid_glass
                    "liquid_glass_dark" -> R.drawable.card_liquid_glass_dark
                    else -> R.drawable.card_bg_light
                }
            )

            val count = WidgetStore.getCount(ctx, username)
            val hasCount = count > 0
            views.setTextViewText(
                R.id.count,
                if (hasCount) String.format(Locale.US, "%,d", count) else "—"
            )
            views.setTextColor(R.id.count, ink)

            views.setTextViewText(R.id.followers_label, "followers")
            views.setTextColor(R.id.followers_label, inkSoft)

            // Display name bold on the top line, username below it.
            // Fallback: username bold on top, second line hidden — unless the
            // count was never set, in which case the second line shows a hint.
            val fullName = WidgetStore.getFullName(ctx, username)
            views.setTextColor(R.id.fullname, ink)
            views.setTextColor(R.id.username, inkFaint)
            when {
                !hasCount -> {
                    views.setTextViewText(R.id.fullname, fullName.ifEmpty { username })
                    views.setTextViewText(R.id.username, "Fetching from Instagram…")
                    views.setViewVisibility(R.id.username, View.VISIBLE)
                }
                fullName.isNotEmpty() -> {
                    views.setTextViewText(R.id.fullname, fullName)
                    views.setTextViewText(R.id.username, username)
                    views.setViewVisibility(R.id.username, View.VISIBLE)
                }
                else -> {
                    views.setTextViewText(R.id.fullname, username)
                    views.setViewVisibility(R.id.username, View.GONE)
                }
            }

            // Verified badge: show whenever the option is on for this
            // username (auto-fetched, or the manual switch in the config).
            views.setViewVisibility(
                R.id.verified_badge,
                if (WidgetStore.isVerified(ctx, username))
                    View.VISIBLE else View.GONE
            )

            // Manual growth value wins over the auto-computed weekly delta.
            val delta: Long? = WidgetStore.getManualDelta(ctx, username)?.toLong()
                ?: WidgetStore.weekDelta(ctx, username)
            if (delta == null) {
                views.setViewVisibility(R.id.delta, View.GONE)
                views.setViewVisibility(R.id.delta_dark, View.GONE)
                views.setViewVisibility(R.id.delta_down, View.GONE)
                views.setViewVisibility(R.id.delta_down_dark, View.GONE)
                views.setTextViewText(R.id.delta_label, "")
            } else {
                val sign = if (delta >= 0) "+" else "−"
                // Pill follows theme (dark card -> rich dark pill) and delta
                // sign (green for growth, red for drop). Safe visibility
                // toggle between four pre-styled TextViews — no reflection.
                val isDown = delta < 0
                val isGlass = theme == "liquid_glass" || theme == "liquid_glass_dark"
                // Glass uses the bright saturated pills (like the light
                // theme's), not the muted dark-card ones.
                val isDarkCard = !darkText && !isGlass
                val deltaView = when {
                    isDown && isDarkCard -> R.id.delta_down_dark
                    isDown -> R.id.delta_down
                    isDarkCard -> R.id.delta_dark
                    else -> R.id.delta
                }
                views.setViewVisibility(R.id.delta, View.GONE)
                views.setViewVisibility(R.id.delta_dark, View.GONE)
                views.setViewVisibility(R.id.delta_down, View.GONE)
                views.setViewVisibility(R.id.delta_down_dark, View.GONE)
                views.setViewVisibility(deltaView, View.VISIBLE)
                views.setTextViewText(
                    deltaView, sign + String.format(Locale.US, "%,d", kotlin.math.abs(delta))
                )
                // Text color is pre-set in XML per pill; re-apply for safety.
                views.setTextColor(
                    deltaView,
                    ctx.getColor(
                        when {
                            isDown && isDarkCard -> R.color.delta_down_dark
                            isDown -> R.color.delta_down
                            isDarkCard -> R.color.delta_up_dark
                            delta > 0 -> R.color.delta_up
                            else -> R.color.ink_faint
                        }
                    )
                )
                // Liquid Glass: saturated bright pill + white text (matches
                // the approved mockup), overriding the pale light-theme pill.
                if (isGlass) {
                    views.setInt(
                        deltaView, "setBackgroundResource",
                        if (isDown) R.drawable.delta_bg_glass_down
                        else R.drawable.delta_bg_glass
                    )
                    views.setTextColor(deltaView, ctx.getColor(android.R.color.white))
                }
                views.setTextViewText(R.id.delta_label, "within a week")
                views.setTextColor(R.id.delta_label, inkFaint)
            }

            val avatar = loadAvatar(ctx, username)
            if (avatar != null) views.setImageViewBitmap(R.id.avatar, avatar)
            else views.setImageViewResource(R.id.avatar, R.drawable.avatar_placeholder)

            // Tapping the card opens the Instagram app on this profile
            // (falls back to the browser when the app isn't installed).
            val pm = ctx.packageManager
            val appUri = android.net.Uri.parse("instagram://user?username=$username")
            val appIntent = Intent(Intent.ACTION_VIEW, appUri)
            val useApp = username.isNotEmpty() &&
                appIntent.resolveActivity(pm) != null &&
                runCatching { pm.getPackageInfo("com.instagram.android", 0); true }
                    .getOrDefault(false)
            val openIntent = if (useApp) {
                appIntent.setPackage("com.instagram.android")
            } else {
                Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(
                        if (username.isNotEmpty()) "https://www.instagram.com/$username/"
                        else "https://www.instagram.com/"
                    )
                )
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(
                ctx, appWidgetId, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (!forPreview) views.setOnClickPendingIntent(R.id.card, pi)
        }

        private fun loadAvatar(ctx: Context, username: String): Bitmap? {
            val path = WidgetStore.avatarPath(ctx, username) ?: return null
            return runCatching {
                // Downsample to ~256px first: a full-res photo passed via
                // RemoteViews.setImageViewBitmap would exceed the ~1MB binder
                // limit and fail the whole widget update.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 256 &&
                    bounds.outHeight / (sample * 2) >= 256
                ) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val src = BitmapFactory.decodeFile(path, opts) ?: return null
                circleCrop(src)
            }.getOrNull()
        }

        private fun circleCrop(src: Bitmap): Bitmap {
            val size = min(src.width, src.height)
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            val left = (src.width - size) / 2
            val top = (src.height - size) / 2
            canvas.drawBitmap(src, Rect(left, top, left + size, top + size), Rect(0, 0, size, size), paint)
            paint.xfermode = null
            return out
        }
    }
}
