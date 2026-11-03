package com.mrjis.medicinetime

import android.app.Application
import com.mrjis.medicinetime.alarm.AlarmNotifications
import kotlinx.coroutines.launch

class MedicineTimeApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AlarmNotifications.createChannels(this)
        // Re-plan every alarm whenever the app process starts.
        container.appScope.launch { container.alarmScheduler.rescheduleAll() }
    }
}