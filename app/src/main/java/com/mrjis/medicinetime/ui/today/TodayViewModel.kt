package com.mrjis.medicinetime.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrjis.medicinetime.AppContainer
import com.mrjis.medicinetime.alarm.DoseRequest
import com.mrjis.medicinetime.alarm.DoseUndo
import com.mrjis.medicinetime.data.local.entity.MedicineWithSchedule
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.domain.dose.TodayDose
import com.mrjis.medicinetime.domain.dose.TodayGroup
import com.mrjis.medicinetime.domain.dose.TodayPlanner
import com.mrjis.medicinetime.domain.dose.TodayStatus
import com.mrjis.medicinetime.domain.model.DoseUnit
import com.mrjis.medicinetime.domain.model.ScheduleMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/** One as-needed medicine on the Today screen. */
data class AsNeededItem(
    val medicineId: String,
    val name: String,
    val doseUnit: DoseUnit,
    /** Pre-fills the Take now sheet. */
    val defaultDose: Double?,
    /** When the most recent dose was logged, or null if never. */
    val lastTakenAtMillis: Long?
)

data class TodayUiState(
    val loaded: Boolean = false,
    val profile: ProfileEntity? = null,
    val groups: List<TodayGroup> = emptyList(),
    /** Doses missed in the last 24 hours (yesterday and today), oldest first. */
    val missed: List<TodayDose> = emptyList(),
    /** As-needed medicines. They are not part of the timeline or the "x of y taken" count. */
    val asNeeded: List<AsNeededItem> = emptyList(),
    val nowMillis: Long = 0L
) {
    val totalCount: Int get() = groups.sumOf { it.doses.size }
    val takenCount: Int
        get() = groups.sumOf { group -> group.doses.count { it.status == TodayStatus.TAKEN } }
}

/** One-time messages for the screen (it shows a snackbar with Undo). */
sealed interface TodayEvent {
    data class Taken(
        val profileId: String,
        val undos: List<DoseUndo>,
        val count: Int,
        /** Name of the medicine when exactly one was taken. */
        val singleName: String?
    ) : TodayEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val container: AppContainer) : ViewModel() {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Emits the current time now and every 30 seconds, so UPCOMING -> DUE -> MISSED updates by itself. */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MILLIS)
        }
    }

    val state: StateFlow<TodayUiState> = container.currentProfileProvider.current
        .flatMapLatest { profile ->
            if (profile == null) flowOf(TodayUiState(loaded = true)) else planFor(profile)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private val _events = MutableSharedFlow<TodayEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TodayEvent> = _events.asSharedFlow()

    /** Doses being written right now, so a fast double tap cannot write (and undo) twice. */
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun planFor(profile: ProfileEntity): Flow<TodayUiState> {
        // The date changes at midnight, which switches the log range we listen to.
        val dates = ticker.map { toDate(it) }.distinctUntilChanged()
        val logs = dates.flatMapLatest { date ->
            // One extra day back, because yesterday is planned too (missed warning card).
            val (from, to) = TodayPlanner.logRange(date, zone, extraDaysBack = 1)
            container.doseLogDao.observeBetween(profile.id, from, to)
        }
        val medicinesFlow = container.medicineRepository.observeForProfile(profile.id)
        val asNeededFlow = medicinesFlow.flatMapLatest { asNeededItems(it) }

        return combine(medicinesFlow, logs, asNeededFlow, ticker) { medicines, logList, asNeeded, now ->
            val date = toDate(now)
            val today = TodayPlanner.plan(medicines, profile, date, logList, now, zone)
            val yesterday = TodayPlanner.plan(medicines, profile, date.minusDays(1), logList, now, zone)
            TodayUiState(
                loaded = true,
                profile = profile,
                groups = today,
                missed = TodayPlanner.recentMissed(yesterday + today, now),
                asNeeded = asNeeded,
                nowMillis = now
            )
        }
    }

    /** One item per as-needed medicine, each following its own "last taken" row. */
    private fun asNeededItems(list: List<MedicineWithSchedule>): Flow<List<AsNeededItem>> {
        val meds = list.map { it.medicine }.filter { it.scheduleMode == ScheduleMode.AS_NEEDED }
        // combine() of an empty list never emits, so this case is handled on its own.
        if (meds.isEmpty()) return flowOf(emptyList())
        return combine(
            meds.map { med ->
                container.doseLogDao.observeLastAsNeeded(med.id).map { last ->
                    AsNeededItem(
                        medicineId = med.id,
                        name = med.name,
                        doseUnit = med.doseUnit,
                        defaultDose = med.defaultDose,
                        lastTakenAtMillis = last?.scheduledAt
                    )
                }
            }
        ) { it.toList() }
    }

    /**
     * Marks doses as taken (early, on time or late) and tells the screen to show Undo.
     * Used by the timeline buttons and by "Taken late" on the missed card.
     */
    fun take(profileId: String, doses: List<TodayDose>) {
        // Ignore a second tap on doses that are already taken, so Undo always reverts the real change.
        val alreadyTaken = state.value.groups
            .flatMap { it.doses }
            .filter { it.status == TodayStatus.TAKEN }
            .map { it.logId }
            .toSet()
        val todo = doses
            .filter { it.logId !in alreadyTaken }
            .filter { inFlight.add(it.logId) } // false when this dose is already being written
        if (todo.isEmpty()) return

        // appScope, so the write is never cancelled if the screen is closed right after the tap.
        container.appScope.launch {
            try {
                val undos = container.doseActions.takeScheduled(
                    profileId,
                    todo.map { DoseRequest(it.medicineId, it.scheduledAtMillis, it.doseAmount) }
                )
                _events.emit(
                    TodayEvent.Taken(
                        profileId = profileId,
                        undos = undos,
                        count = todo.size,
                        singleName = todo.singleOrNull()?.medicineName
                    )
                )
            } finally {
                todo.forEach { inFlight.remove(it.logId) }
            }
        }
    }

    /** "Take now" for an as-needed medicine. Shows the same Undo snackbar as a scheduled dose. */
    fun takeAsNeeded(profileId: String, item: AsNeededItem, amount: Double) {
        if (amount <= 0.0) return
        val key = "as_needed_${item.medicineId}"
        if (!inFlight.add(key)) return // a double tap while the first write is running

        container.appScope.launch {
            try {
                val undo = container.doseActions.takeAsNeeded(profileId, item.medicineId, amount)
                _events.emit(
                    TodayEvent.Taken(
                        profileId = profileId,
                        undos = listOf(undo),
                        count = 1,
                        singleName = item.name
                    )
                )
            } finally {
                inFlight.remove(key)
            }
        }
    }

    fun undo(event: TodayEvent.Taken) {
        container.appScope.launch { container.doseActions.undo(event.profileId, event.undos) }
    }

    private fun toDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private companion object {
        const val TICK_MILLIS = 30_000L
    }
}