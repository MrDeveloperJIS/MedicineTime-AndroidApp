package com.mrjis.medicinetime.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mrjis.medicinetime.AppContainer
import com.mrjis.medicinetime.MedicineTimeApplication
import com.mrjis.medicinetime.data.local.entity.DoseLogEntity
import com.mrjis.medicinetime.domain.model.DoseSource
import com.mrjis.medicinetime.domain.model.DoseStatus
import com.mrjis.medicinetime.domain.schedule.ScheduleResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Receives both kinds of alarm: a dose is due, or a snoozed dose must ring again. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != AlarmScheduler.ACTION_ALARM && action != AlarmScheduler.ACTION_SNOOZE_ALARM) return
        val profileId = intent.getStringExtra(AlarmScheduler.EXTRA_PROFILE_ID) ?: return
        val atMillis = intent.getLongExtra(AlarmScheduler.EXTRA_AT_MILLIS, 0L)
        if (atMillis == 0L) return

        val appContext = context.applicationContext
        val container = (appContext as MedicineTimeApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                val logIds = if (action == AlarmScheduler.ACTION_ALARM) {
                    handleDoseAlarm(container, profileId, atMillis)
                } else {
                    handleSnoozeAlarm(container, profileId)
                }
                if (logIds.isNotEmpty()) ring(appContext, container, profileId, logIds)
            } catch (e: Exception) {
                Log.e(TAG, "Alarm handling failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** A scheduled dose group is due. Returns the log ids that should ring. */
    private suspend fun handleDoseAlarm(
        container: AppContainer,
        profileId: String,
        atMillis: Long
    ): List<String> {
        val zone = ZoneId.systemDefault()
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), zone)

        val profile = container.profileRepository.get(profileId)
        if (profile == null) {
            container.alarmScheduler.cancel(profileId)
            container.alarmScheduler.cancelSnooze(profileId)
            return emptyList()
        }

        // 1. Schedule the NEXT alarm first, so a crash below can't break the chain.
        //    If the phone was off and this alarm is late, skip ahead from "now".
        val now = LocalDateTime.now()
        container.alarmScheduler.scheduleNext(profile, if (now.isAfter(at)) now else at)

        // 2. Work out what is due at this minute (the schedule may have changed meanwhile).
        val medicines = container.medicineRepository.observeForProfile(profileId).first()
        val doses = ScheduleResolver.dueAt(medicines, profile, at)

        // 3. Mark them PENDING. A dose that already has an answer (taken, snoozed) does not ring again.
        val nowMillis = System.currentTimeMillis()
        val ringIds = mutableListOf<String>()
        doses.forEach { dose ->
            val scheduledAt = dose.scheduledAtMillis(zone)
            val id = DoseLogEntity.idFor(dose.medicineId, scheduledAt)
            val existing = container.doseLogDao.get(id)
            if (existing == null) {
                container.doseLogDao.upsert(
                    DoseLogEntity(
                        id = id,
                        profileId = profileId,
                        medicineId = dose.medicineId,
                        source = DoseSource.SCHEDULED,
                        scheduledAt = scheduledAt,
                        status = DoseStatus.PENDING,
                        doseAmountSnapshot = dose.doseAmount,
                        updatedAt = nowMillis
                    )
                )
                ringIds += id
            } else if (existing.status == DoseStatus.PENDING) {
                ringIds += id
            }
        }
        return ringIds
    }

    /** A snooze ran out. Puts the due doses back to PENDING and returns their log ids. */
    private suspend fun handleSnoozeAlarm(container: AppContainer, profileId: String): List<String> {
        if (container.profileRepository.get(profileId) == null) {
            container.alarmScheduler.cancelSnooze(profileId)
            return emptyList()
        }

        val now = System.currentTimeMillis()
        val due = container.doseLogDao.getSnoozed(profileId)
            .filter { (it.actionAt ?: 0L) <= now + SNOOZE_GRACE_MILLIS }
        due.forEach { log ->
            container.doseLogDao.upsert(
                log.copy(status = DoseStatus.PENDING, actionAt = now, updatedAt = now)
            )
        }

        // Other doses may still be snoozed for later.
        container.alarmScheduler.scheduleNextSnooze(profileId)
        return due.map { it.id }
    }

    private suspend fun ring(
        context: Context,
        container: AppContainer,
        profileId: String,
        logIds: List<String>
    ) {
        if (AlarmService.start(context, profileId, logIds)) return
        // Android refused the foreground service. At least show the alarm notification.
        RingLoader.load(container, profileId, logIds)?.let { AlarmNotifications.show(context, it) }
    }

    private companion object {
        const val TAG = "AlarmReceiver"
        const val SNOOZE_GRACE_MILLIS = 5_000L
    }
}