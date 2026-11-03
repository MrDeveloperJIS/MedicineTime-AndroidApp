package com.mrjis.medicinetime.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mrjis.medicinetime.MedicineTimeApplication
import kotlinx.coroutines.launch

/**
 * Re-plans every alarm after events that make Android forget or invalidate them:
 * reboot, app update, clock change and timezone change.
 */
class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> Unit
            else -> return
        }

        val container = (context.applicationContext as MedicineTimeApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.alarmScheduler.rescheduleAll()
            } catch (e: Exception) {
                Log.e(TAG, "Rescheduling after ${intent.action} failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "RescheduleReceiver"
    }
}