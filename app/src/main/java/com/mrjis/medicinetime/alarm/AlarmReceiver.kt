package com.mrjis.medicinetime.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mrjis.medicinetime.MedicineTimeApplication
import com.mrjis.medicinetime.data.local.entity.DoseLogEntity
import com.mrjis.medicinetime.domain.model.DoseSource
import com.mrjis.medicinetime.domain.model.DoseStatus
import com.mrjis.medicinetime.domain.schedule.DueGroup
import com.mrjis.medicinetime.domain.schedule.ScheduleResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_ALARM) return
        val profileId = intent.getStringExtra(AlarmScheduler.EXTRA_PROFILE_ID) ?: return
        val atMillis = intent.getLongExtra(AlarmScheduler.EXTRA_AT_MILLIS, 0L)
        if (atMillis == 0L) return

        val container = (context.applicationContext as MedicineTimeApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                handleAlarm(context.applicationContext, container, profileId, atMillis)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleAlarm(
        context: Context,
        container: com.mrjis.medicinetime.AppContainer,
        profileId: String,
        atMillis: Long
    ) {
        val zone = ZoneId.systemDefault()
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), zone)

        val profile = container.profileRepository.get(profileId)
        if (profile == null) {
            container.alarmScheduler.cancel(profileId)
            return
        }

        // 1. Schedule the NEXT alarm first, so a crash below can't break the chain.
        //    If the phone was off and this alarm is late, skip ahead from "now".
        val now = LocalDateTime.now()
        container.alarmScheduler.scheduleNext(profile, if (now.isAfter(at)) now else at)

        // 2. Work out what is due at this minute (the schedule may have changed meanwhile).
        val medicines = container.medicineRepository.observeForProfile(profileId).first()
        val doses = ScheduleResolver.dueAt(medicines, profile, at)
        if (doses.isEmpty()) return

        // 3. Mark them PENDING. Existing rows (already taken or snoozed) are left alone.
        val nowMillis = System.currentTimeMillis()
        doses.forEach { dose ->
            val scheduledAt = dose.scheduledAtMillis(zone)
            val id = DoseLogEntity.idFor(dose.medicineId, scheduledAt)
            if (container.doseLogDao.get(id) == null) {
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
            }
        }

        // 4. Tell the user. (Step 3 replaces this with the ringing alarm screen.)
        AlarmNotifications.showDue(context, profile, DueGroup(at, doses))
    }
}