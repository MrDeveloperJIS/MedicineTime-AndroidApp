package com.mrjis.medicinetime.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mrjis.medicinetime.MedicineTimeApplication
import kotlinx.coroutines.launch

/** Handles the Taken and Snooze buttons on the alarm notification. */
class DoseActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_TAKEN && action != ACTION_SNOOZE) return
        val profileId = intent.getStringExtra(AlarmScheduler.EXTRA_PROFILE_ID) ?: return
        val logIds = intent.getStringArrayExtra(AlarmService.EXTRA_LOG_IDS)?.toList() ?: return

        val container = (context.applicationContext as MedicineTimeApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                if (action == ACTION_TAKEN) {
                    container.doseActions.taken(profileId, logIds)
                } else {
                    val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, AlarmDefaults.DEFAULT_SNOOZE_MINUTES)
                    container.doseActions.snooze(profileId, logIds, minutes)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Dose action failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DoseActionReceiver"
        const val ACTION_TAKEN = "com.mrjis.medicinetime.ACTION_DOSE_TAKEN"
        const val ACTION_SNOOZE = "com.mrjis.medicinetime.ACTION_DOSE_SNOOZE"
        const val EXTRA_SNOOZE_MINUTES = "snoozeMinutes"
    }
}