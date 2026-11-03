package com.mrjis.medicinetime.alarm

import android.content.Context
import com.mrjis.medicinetime.AppContainer
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.data.local.entity.DoseLogEntity
import com.mrjis.medicinetime.domain.model.DoseStatus
import com.mrjis.medicinetime.domain.model.DoseUnit
import com.mrjis.medicinetime.ui.components.labelRes
import com.mrjis.medicinetime.util.formatAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** One medicine inside a ringing alarm. */
data class RingItem(
    val logId: String,
    val medicineName: String,
    val doseAmount: Double,
    val doseUnit: DoseUnit
)

/** Everything one ringing alarm shows: one profile, the doses that are waiting for an answer. */
data class RingGroup(
    val profileId: String,
    val profileName: String,
    val scheduledAtMillis: Long,
    val items: List<RingItem>
) {
    val logIds: List<String> get() = items.map { it.logId }

    fun scheduledTime(zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        Instant.ofEpochMilli(scheduledAtMillis).atZone(zone).toLocalTime()
}

/** "1 tablet", or "2 tsp (10 ml)" for spoons so the amount is clear. */
fun formatDose(context: Context, amount: Double, unit: DoseUnit): String {
    val unitLabel = context.getString(unit.labelRes())
    val mlFactor = unit.mlFactor
    return if (mlFactor != null && mlFactor != 1.0) {
        context.getString(R.string.alarm_dose_with_ml, formatAmount(amount), unitLabel, formatAmount(amount * mlFactor))
    } else {
        context.getString(R.string.alarm_dose_plain, formatAmount(amount), unitLabel)
    }
}

fun RingItem.doseText(context: Context): String = formatDose(context, doseAmount, doseUnit)

/** "Napa, 1 tablet" */
fun RingItem.line(context: Context): String =
    context.getString(R.string.alarm_dose_line, medicineName, doseText(context))

/**
 * Builds [RingGroup]s from the dose log. Only doses still PENDING are included,
 * so a dose answered elsewhere drops out by itself.
 */
object RingLoader {

    suspend fun load(container: AppContainer, profileId: String, logIds: List<String>): RingGroup? =
        build(container, profileId, container.doseLogDao.getByIds(logIds))

    fun observe(container: AppContainer, profileId: String, logIds: List<String>): Flow<RingGroup?> =
        container.doseLogDao.observeByIds(logIds).map { build(container, profileId, it) }

    private suspend fun build(
        container: AppContainer,
        profileId: String,
        logs: List<DoseLogEntity>
    ): RingGroup? {
        val pending = logs.filter { it.status == DoseStatus.PENDING }.sortedBy { it.scheduledAt }
        if (pending.isEmpty()) return null
        val profile = container.profileRepository.get(profileId) ?: return null

        val items = pending.mapNotNull { log ->
            val medicine = container.medicineRepository.get(log.medicineId)?.medicine
                ?: return@mapNotNull null
            RingItem(log.id, medicine.name, log.doseAmountSnapshot, medicine.doseUnit)
        }
        if (items.isEmpty()) return null

        return RingGroup(profileId, profile.name, pending.first().scheduledAt, items)
    }
}