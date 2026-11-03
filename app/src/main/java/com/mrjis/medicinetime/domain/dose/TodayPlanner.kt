package com.mrjis.medicinetime.domain.dose

import com.mrjis.medicinetime.data.local.entity.DoseLogEntity
import com.mrjis.medicinetime.data.local.entity.MedicineWithSchedule
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.domain.model.DoseStatus
import com.mrjis.medicinetime.domain.model.DoseUnit
import com.mrjis.medicinetime.domain.schedule.ScheduleResolver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class TodayStatus { UPCOMING, DUE, SNOOZED, TAKEN, MISSED }

/** One planned dose of one medicine, merged with its log (if any). */
data class TodayDose(
    val logId: String,
    val medicineId: String,
    val medicineName: String,
    val doseAmount: Double,
    val doseUnit: DoseUnit,
    val scheduledAt: LocalDateTime,
    val scheduledAtMillis: Long,
    val status: TodayStatus,
    /** TAKEN: when it was taken. SNOOZED: when it rings again. */
    val actionAtMillis: Long?
)

/** Everything due at the same minute. */
data class TodayGroup(
    val at: LocalDateTime,
    val doses: List<TodayDose>
) {
    /** Doses still waiting for the user (not yet taken). */
    val open: List<TodayDose> get() = doses.filter { it.status != TodayStatus.TAKEN }
    val allTaken: Boolean get() = doses.all { it.status == TodayStatus.TAKEN }
}

/** Pure logic (no Android classes): turns the schedule plus the dose log into the Today timeline. */
object TodayPlanner {

    /**
     * Log range to load for [date]. A meal offset can move a dose into the previous or next
     * day, so one day of margin is kept on both sides. [extraDaysBack] widens the start,
     * used when yesterday is planned too (the missed warning card).
     */
    fun logRange(
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        extraDaysBack: Long = 0L
    ): Pair<Long, Long> {
        val from = date.minusDays(1 + extraDaysBack).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        return from to to
    }

    fun plan(
        medicines: List<MedicineWithSchedule>,
        profile: ProfileEntity,
        date: LocalDate,
        logs: List<DoseLogEntity>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        windowMillis: Long = DoseRules.MISSED_WINDOW_MILLIS
    ): List<TodayGroup> {
        val logsById = logs.associateBy { it.id }
        val updatedAt = medicines.associate { it.medicine.id to it.medicine.updatedAt }

        val doses = ScheduleResolver.occurrencesOn(medicines, profile, date).mapNotNull { due ->
            val at = due.scheduledAtMillis(zone)
            val id = DoseLogEntity.idFor(due.medicineId, at)
            val log = logsById[id]

            // A dose planned before the medicine was last saved was never really planned.
            if (log == null && at < (updatedAt[due.medicineId] ?: 0L)) return@mapNotNull null

            val status = when {
                log == null -> when {
                    at > nowMillis -> TodayStatus.UPCOMING
                    nowMillis - at > windowMillis -> TodayStatus.MISSED
                    else -> TodayStatus.DUE
                }
                log.status == DoseStatus.TAKEN -> TodayStatus.TAKEN
                log.status == DoseStatus.MISSED -> TodayStatus.MISSED
                DoseRules.isMissed(log, nowMillis, windowMillis) -> TodayStatus.MISSED
                log.status == DoseStatus.SNOOZED -> TodayStatus.SNOOZED
                else -> TodayStatus.DUE // PENDING
            }

            TodayDose(
                logId = id,
                medicineId = due.medicineId,
                medicineName = due.medicineName,
                doseAmount = log?.doseAmountSnapshot ?: due.doseAmount,
                doseUnit = due.doseUnit,
                scheduledAt = due.scheduledAt,
                scheduledAtMillis = at,
                status = status,
                actionAtMillis = log?.actionAt
            )
        }

        return doses
            .groupBy { it.scheduledAt }
            .toSortedMap()
            .map { (at, list) -> TodayGroup(at, list.sortedBy { it.medicineName.lowercase() }) }
    }

    /**
     * Doses for the missed warning card: MISSED within the last [bannerMillis] (24 h),
     * oldest first. Pass the groups of yesterday and today together.
     */
    fun recentMissed(
        groups: List<TodayGroup>,
        nowMillis: Long,
        bannerMillis: Long = DoseRules.MISSED_BANNER_MILLIS
    ): List<TodayDose> =
        groups
            .flatMap { it.doses }
            .filter { it.status == TodayStatus.MISSED && nowMillis - it.scheduledAtMillis <= bannerMillis }
            .distinctBy { it.logId }
            .sortedBy { it.scheduledAtMillis }
}