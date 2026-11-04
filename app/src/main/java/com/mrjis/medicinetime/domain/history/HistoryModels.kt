package com.mrjis.medicinetime.domain.history

import com.mrjis.medicinetime.domain.dose.TodayStatus
import com.mrjis.medicinetime.domain.model.DoseSource
import com.mrjis.medicinetime.domain.model.DoseUnit
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * One dose in the history. [status] reuses the Today statuses:
 * TAKEN / MISSED are final; UPCOMING, DUE and SNOOZED are still open (today or later).
 * As-needed doses are always TAKEN.
 */
data class HistoryDose(
    val logId: String,
    val medicineId: String,
    val medicineName: String,
    val doseAmount: Double,
    val doseUnit: DoseUnit,
    val source: DoseSource,
    val status: TodayStatus,
    /** Planned time. For an as-needed dose: the moment it was logged. */
    val scheduledAtMillis: Long,
    /** TAKEN: when it was taken. SNOOZED: when it rings again. */
    val actionAtMillis: Long?
) {
    val isAsNeeded: Boolean get() = source == DoseSource.AS_NEEDED

    /** Only as-needed entries can be deleted from History (plan 4.2c). */
    val canDelete: Boolean get() = isAsNeeded
}

/** Counts for one day. As-needed doses are listed apart and never counted as scheduled. */
data class DaySummary(
    val taken: Int,
    val missed: Int,
    /** Scheduled doses not answered yet (upcoming, due or snoozed). */
    val open: Int,
    val asNeeded: Int
) {
    val scheduled: Int get() = taken + missed + open
}

/** Calendar mark for a day. */
enum class DayMark {
    /** No scheduled dose that day. */
    NONE,

    /** Every scheduled dose was taken. */
    ALL_TAKEN,

    /** At least one scheduled dose was missed. */
    SOME_MISSED,

    /** Nothing missed, but some doses are still open (today). */
    OPEN,

    /** A day after today with planned doses. */
    FUTURE
}

data class HistoryDay(
    val date: LocalDate,
    /** In time order. */
    val doses: List<HistoryDose>,
    val summary: DaySummary,
    val mark: DayMark
)

/** Adherence over several days. As-needed doses are excluded from the percentage. */
data class AdherenceSummary(
    val taken: Int,
    val missed: Int,
    val open: Int,
    val asNeeded: Int
) {
    /** Doses with a final answer. Open doses are not counted either way. */
    val settled: Int get() = taken + missed

    /** Taken out of taken + missed, rounded. null when there is nothing to measure yet. */
    val percent: Int? get() =
        if (settled == 0) null else (taken * 100.0 / settled).roundToInt()

    companion object {
        fun of(days: List<HistoryDay>): AdherenceSummary = AdherenceSummary(
            taken = days.sumOf { it.summary.taken },
            missed = days.sumOf { it.summary.missed },
            open = days.sumOf { it.summary.open },
            asNeeded = days.sumOf { it.summary.asNeeded }
        )
    }
}