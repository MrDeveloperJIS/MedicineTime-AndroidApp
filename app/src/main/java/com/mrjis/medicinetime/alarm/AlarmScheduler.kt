package com.mrjis.medicinetime.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.mrjis.medicinetime.MainActivity
import com.mrjis.medicinetime.data.local.dao.DoseLogDao
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.data.repository.MedicineRepository
import com.mrjis.medicinetime.data.repository.ProfileRepository
import com.mrjis.medicinetime.domain.schedule.ScheduleResolver
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * Keeps up to two pending alarms per profile:
 *  - the next due dose group (when it fires, [AlarmReceiver] schedules the one after it)
 *  - the earliest snooze (a snoozed dose that must ring again)
 */
class AlarmScheduler(
    context: Context,
    private val profiles: ProfileRepository,
    private val medicines: MedicineRepository,
    private val doseLogs: DoseLogDao
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    /** Re-plans every profile. Safe to call any time (app start, boot, time change). */
    suspend fun rescheduleAll() {
        val now = LocalDateTime.now()
        profiles.observeAll().first().forEach {
            scheduleNext(it, now)
            scheduleNextSnooze(it.id)
        }
    }

    suspend fun scheduleNextById(profileId: String, after: LocalDateTime = LocalDateTime.now()) {
        val profile = profiles.get(profileId)
        if (profile == null) {
            cancel(profileId)
            cancelSnooze(profileId)
        } else {
            scheduleNext(profile, after)
        }
    }

    suspend fun scheduleNext(profile: ProfileEntity, after: LocalDateTime = LocalDateTime.now()) {
        val items = medicines.observeForProfile(profile.id).first()
        val next = ScheduleResolver.nextDue(items, profile, after)
        if (next == null) cancel(profile.id) else setAlarm(alarmPendingIntent(profile.id, next.atMillis()), next.atMillis())
    }

    /**
     * Plans the re-ring of snoozed doses: one alarm, at the earliest snooze time.
     * Cancels it when nothing is snoozed any more.
     */
    suspend fun scheduleNextSnooze(profileId: String) {
        val next = doseLogs.getSnoozed(profileId).mapNotNull { it.actionAt }.minOrNull()
        if (next == null) cancelSnooze(profileId) else setAlarm(snoozePendingIntent(profileId, next), next)
    }

    fun cancel(profileId: String) {
        alarmManager.cancel(alarmPendingIntent(profileId, 0L))
    }

    fun cancelSnooze(profileId: String) {
        alarmManager.cancel(snoozePendingIntent(profileId, 0L))
    }

    private fun setAlarm(operation: PendingIntent, atMillis: Long) {
        // setAlarmClock() is treated like a real alarm clock: exact, and exempt from Doze.
        val info = AlarmManager.AlarmClockInfo(atMillis, openAppPendingIntent())
        try {
            alarmManager.setAlarmClock(info, operation)
        } catch (e: SecurityException) {
            Log.e(TAG, "Could not schedule alarm", e)
        }
    }

    private fun alarmPendingIntent(profileId: String, atMillis: Long): PendingIntent {
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            // A different data Uri per profile keeps their pending intents separate.
            data = Uri.parse("medicinetime://alarm/$profileId")
            putExtra(EXTRA_PROFILE_ID, profileId)
            putExtra(EXTRA_AT_MILLIS, atMillis)
        }
        return PendingIntent.getBroadcast(appContext, 0, intent, PI_FLAGS)
    }

    private fun snoozePendingIntent(profileId: String, atMillis: Long): PendingIntent {
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            data = Uri.parse("medicinetime://snooze/$profileId")
            putExtra(EXTRA_PROFILE_ID, profileId)
            putExtra(EXTRA_AT_MILLIS, atMillis)
        }
        return PendingIntent.getBroadcast(appContext, 0, intent, PI_FLAGS)
    }

    private fun openAppPendingIntent(): PendingIntent =
        PendingIntent.getActivity(appContext, 0, Intent(appContext, MainActivity::class.java), PI_FLAGS)

    companion object {
        private const val TAG = "AlarmScheduler"
        private const val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        const val ACTION_ALARM = "com.mrjis.medicinetime.ACTION_DOSE_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.mrjis.medicinetime.ACTION_SNOOZE_ALARM"
        const val EXTRA_PROFILE_ID = "profileId"
        const val EXTRA_AT_MILLIS = "atMillis"
    }
}