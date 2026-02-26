package com.energomonitor.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import android.content.Context
import android.content.Intent

class TemperatureWidget1x2Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TemperatureWidget()
    
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Ensure work is scheduled when first widget is added
        val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "widget_update",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Cancel work when the last widget is removed
        WorkManager.getInstance(context).cancelUniqueWork("widget_update")
    }
}
