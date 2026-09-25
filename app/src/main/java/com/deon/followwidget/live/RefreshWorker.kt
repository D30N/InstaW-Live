package com.deon.followwidget.live

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Fetches the latest Instagram data for the given widget ids (or all
 * widgets when no ids are supplied), then redraws them. Runs every 15
 * minutes via the periodic schedule plus on manual refresh taps.
 *
 * A failed fetch is not an error here — the widget keeps showing the
 * last fetched data.
 */
class RefreshWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ids = inputData.getIntArray(KEY_IDS)
            ?: FollowerWidgetProvider.allWidgetIds(applicationContext)

        for (id in ids) {
            val username = WidgetStore.getUsername(applicationContext, id)
            if (username.isNotBlank()) {
                runCatching { IgFetch.fetchAndStore(applicationContext, username) }
            }
            FollowerWidgetProvider.updateWidget(applicationContext, id)
        }
        return Result.success()
    }

    companion object {
        const val KEY_IDS = "ids"
    }
}
