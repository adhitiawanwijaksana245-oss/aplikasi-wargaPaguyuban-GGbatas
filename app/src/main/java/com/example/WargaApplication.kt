package com.example

import android.app.Application
import com.example.util.BackgroundSyncScheduler
import com.example.util.NotificationHelper

class WargaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.initNotificationChannels(this)
        BackgroundSyncScheduler.schedulePeriodicSync(this)
    }
}
