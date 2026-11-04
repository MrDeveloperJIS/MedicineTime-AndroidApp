package com.mrjis.medicinetime.domain.history

import com.mrjis.medicinetime.data.local.entity.HistoryLogRow
import com.mrjis.medicinetime.data.local.entity.MedicineWithSchedule
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.domain.dose.DoseRules
import com.mrjis.medicinetime.domain.dose.TodayDose
import com.mrjis.medicinetime.domain.dose.TodayPlanner
import com.mrjis.medicinetime.domain.dose.TodayStatus
import com.mrjis.medicinetime.domain.model.DoseSource
import com.mrjis.medicinetime.domain.model.DoseStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure logic (no Android classes): builds the history days from the schedule and the dose log.
 *
 * A day shows:
 *  1. the scheduled doses planned for that day (from the same planner as the Today screen),
 *     so a dose that never rang still shows as missed, and
 *  2. logged doses that no plan explains: as-needed doses, doses of a medicine deleted
 *     later, and doses from before the schedule was edited.
 */
object HistoryPlanner {

    /**
     * Logs to load for [from]..[to]. A meal offset can move a dose into the neighbouring day,
     * so there is a margin on both sides.
     */
    fun logRange(
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Pair<Long, Long> {
        val start = from.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    /** The days from [from] to [to] (both included), newest first. */
    fun buildDays(
        medicines: List<MedicineWithSchedule>,
        profile: ProfileEntity,
        from: LocalDate,
        to: LocalDate,
        rows: List<HistoryLogRow>,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        windowMillis: Long = DoseRules.MISSED_WINDOW_MILLIS
    ): List<HistoryDay> {
        if (to.isBefore(from)) return emptyList()

        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val logs = rows.map { it.log }

        // Plan one day beyond each end too: a dose belongs to its phase day even when a meal
        // offset moves its clock time into the next or previous day. Knowing those ids stops
        // such a dose from also being listed as an unplanned log on the neighbouring day.
        val planned: Map<LocalDate, List<TodayDose>> =
            datesBetween(from.minusDays(1), to.plusDays(1)).associateWith { date ->
                TodayPlanner.plan(medicines, profile, date, logs, nowMillis, zone, windowMillis)
                    .flatMap { it.doses }
            }
        val plannedIds: Set<String> = planned.values.flatten().mapTo(HashSet()) { it.logId }

        val extrasByDate: Map<LocalDate, List<HistoryDose>> = rows
            .filter { it.log.id !in plannedIds }
            .groupBy { toDate(it.log.scheduledAt, zone) }
            .mapValues { (_, list) -> list.map { it.toHistoryDose(nowMillis, windowMillis) } }

        return datesBetween(from, to).reversed().map { date ->
            val doses = (planned[date].orEmpty().map { it.toHistoryDose() } +
                    extrasByDate[date].orEmpty())
                .sortedWith(compareBy({ it.scheduledAtMillis }, { it.medicineName.lowercase() }))
            val summary = summarize(doses)
            HistoryDay(date, doses, summary, markFor(date, today, summary))
        }
    }

    private fun summarize(doses: List<HistoryDose>): DaySummary {
        val scheduled = doses.filter { !it.isAsNeeded }
        return DaySummary(
            taken = scheduled.count { it.status == TodayStatus.TAKEN },
            missed = scheduled.count { it.status == TodayStatus.MISSED },
            open = scheduled.count {
                it.status != TodayStatus.TAKEN && it.status != TodayStatus.MISSED
            },
            asNeeded = doses.count { it.isAsNeeded }
        )
    }

    private fun markFor(date: LocalDate, today: LocalDate, summary: DaySummary): DayMark = when {
        summary.scheduled == 0 -> DayMark.NONE
        date.isAfter(today) -> DayMark.FUTURE
        summary.missed > 0 -> DayMark.SOME_MISSED
        summary.open > 0 -> DayMark.OPEN
        else -> DayMark.ALL_TAKEN
    }

    private fun TodayDose.toHistoryDose() = HistoryDose(
        logId = logId,
        medicineId = medicineId,
        medicineName = medicineName,
        doseAmount = doseAmount,
        doseUnit = doseUnit,
        source = DoseSource.SCHEDULED,
        status = status,
        scheduledAtMillis = scheduledAtMillis,
        actionAtMillis = actionAtMillis
    )

    private fun HistoryLogRow.toHistoryDose(nowMillis: Long, windowMillis: Long): HistoryDose {
        val status = when {
            log.status == DoseStatus.TAKEN -> TodayStatus.TAKEN
            log.status == DoseStatus.MISSED -> TodayStatus.MISSED
            DoseRules.isMissed(log, nowMillis, windowMillis) -> TodayStatus.MISSED
            log.status == DoseStatus.SNOOZED -> TodayStatus.SNOOZED
            else -> TodayStatus.DUE // PENDING
        }
        return HistoryDose(
            logId = log.id,
            medicineId = log.medicineId,
            medicineName = medicineName,
            doseAmount = log.doseAmountSnapshot,
            doseUnit = doseUnit,
            source = log.source,
            status = status,
            scheduledAtMillis = log.scheduledAt,
            actionAtMillis = log.actionAt
        )
    }

    private fun toDate(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun datesBetween(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { d -> d.plusDays(1).takeIf { !it.isAfter(to) } }.toList()
}