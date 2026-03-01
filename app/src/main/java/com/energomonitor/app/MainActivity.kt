package com.energomonitor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.energomonitor.app.ui.EnergomonitorAppContent
import com.energomonitor.app.ui.theme.EnergomonitorTheme
import com.energomonitor.app.widget.WidgetUpdateWorker
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Queue a widget update when the app starts
        try {
            val updateWork = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            WorkManager.getInstance(this).enqueue(updateWork)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        setContent {
            EnergomonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EnergomonitorAppContent()
                }
            }
        }
    }
}
