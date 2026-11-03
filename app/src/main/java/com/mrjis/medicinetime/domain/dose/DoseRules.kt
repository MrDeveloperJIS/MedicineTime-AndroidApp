package com.mrjis.medicinetime.domain.dose

import com.mrjis.medicinetime.data.local.entity.DoseLogEntity
import com.mrjis.medicinetime.domain.model.DoseSource
import com.mrjis.medicinetime.domain.model.DoseStatus

/** Pure rules about when a dose counts as missed. The window becomes a setting in M6. */
object DoseRules {

    /** A dose unanswered this long after it rang is MISSED (plan 5.4: 2 hours). */
    const val MISSED_WINDOW_MILLIS = 2 * 60 * 60_000L

    /** The warning card on Today only lists doses missed within this time. */
    const val MISSED_BANNER_MILLIS = 24 * 60 * 60_000L

    /** How many days back the sweeper looks for planned doses that never rang. */
    const val SWEEP_LOOKBACK_DAYS = 2L

    /**
     * True when an open (PENDING or SNOOZED) scheduled dose has waited too long.
     *  - PENDING: counted from the last time it rang (a re-ring after a snooze restarts the clock).
     *  - SNOOZED: counted from the moment it was due to ring again.
     * As-needed doses are never missed.
     */
    fun isMissed(
        log: DoseLogEntity,
        nowMillis: Long,
        windowMillis: Long = MISSED_WINDOW_MILLIS
    ): Boolean {
        if (log.source != DoseSource.SCHEDULED) return false
        return when (log.status) {
            DoseStatus.PENDING ->
                nowMillis - maxOf(log.scheduledAt, log.actionAt ?: 0L) > windowMillis
            DoseStatus.SNOOZED ->
                nowMillis - (log.actionAt ?: log.scheduledAt) > windowMillis
            else -> false
        }
    }
}