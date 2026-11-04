package com.mrjis.medicinetime

import android.app.Application
import android.content.Context
import com.mrjis.medicinetime.alarm.AlarmNotifications
import com.mrjis.medicinetime.alarm.StockNotifications
import com.mrjis.medicinetime.util.LocaleHelper
import com.mrjis.medicinetime.work.MissedSweepWorker
import com.mrjis.medicinetime.work.StockCheckWorker
import kotlinx.coroutines.launch

class MedicineTimeApplication : Application() {

    lateinit var container: AppContainer
        private set

    /** Applies the chosen language (Android 12) so receivers and workers use it too. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        LocaleHelper.syncFromSystem(this, container.settingsStore)
        AlarmNotifications.createChannels(this)
        StockNotifications.createChannel(this)
        // Re-plan every alarm whenever the app process starts, then sweep for missed doses.
        container.appScope.launch { container.alarmScheduler.rescheduleAll() }
        container.appScope.launch {
            try {
                container.missedSweeper.sweep()
            } catch (e: Exception) {
                android.util.Log.e("MedicineTimeApp", "Missed-dose sweep failed", e)
            }
        }
        // Plus one sweep a day, even when the app is never opened.
        MissedSweepWorker.schedule(this)
        // And one low-stock check a day, around 09:00.
        StockCheckWorker.schedule(this)
    }
}