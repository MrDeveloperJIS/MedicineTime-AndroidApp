package com.mrjis.medicinetime

import android.app.Application
import com.mrjis.medicinetime.alarm.AlarmNotifications
import com.mrjis.medicinetime.work.MissedSweepWorker
import kotlinx.coroutines.launch

class MedicineTimeApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AlarmNotifications.createChannels(this)
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
    }
}