package com.deon.followwidget.live

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

/**
 * Fires a notification each time a tracked account crosses a 5K follower
 * milestone (60K, 65K, 70K, …). Called from [WidgetStore.setCount], so it
 * works from widget updates without the app open.
 */
object MilestoneAlerts {
    private const val CHANNEL_ID = "milestones"

    fun notify(ctx: Context, rawUsername: String, milestone: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Milestones",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val handle = "@" + rawUsername.trim().trimStart('@')
        val label = String.format(Locale.US, "%,d", milestone)
        val notif = Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("\uD83C\uDF89 ${milestone / 1000}K followers!")
            .setContentText("Congratulations \uD83E\uDD73 $handle just crossed $label followers")
            .setAutoCancel(true)
            .build()
        // One id per milestone: re-crossing the same 5K never double-fires.
        mgr.notify(milestone.toInt(), notif)
    }
}
