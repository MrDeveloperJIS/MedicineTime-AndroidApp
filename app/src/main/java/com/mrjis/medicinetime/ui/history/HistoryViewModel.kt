package com.mrjis.medicinetime.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrjis.medicinetime.AppContainer
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.domain.history.AdherenceSummary
import com.mrjis.medicinetime.domain.history.HistoryDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class HistoryMode { TIMELINE, CALENDAR }

data class HistoryUiState(
    val loaded: Boolean = false,
    val profile: ProfileEntity? = null,
    val mode: HistoryMode = HistoryMode.TIMELINE,
    /** Timeline range: the last N days, today included. */
    val rangeDays: Int = 7,
    /** Calendar month. */
    val month: YearMonth = YearMonth.now(),
    /** Calendar: the day whose detail is shown. */
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    /** Days of the current range (timeline) or month (calendar), newest first. */
    val days: List<HistoryDay> = emptyList(),
    val summary: AdherenceSummary = AdherenceSummary(taken = 0, missed = 0, open = 0, asNeeded = 0)
) {
    val selectedDay: HistoryDay? get() = days.firstOrNull { it.date == selectedDate }
    val canGoNextMonth: Boolean get() = month.isBefore(YearMonth.from(today))
}

/** One-time messages for the screen. */
sealed interface HistoryEvent {
    data object Deleted : HistoryEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    private val mode = MutableStateFlow(HistoryMode.TIMELINE)
    private val rangeDays = MutableStateFlow(7)
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())

    private data class Query(
        val profile: ProfileEntity?,
        val mode: HistoryMode,
        val rangeDays: Int,
        val month: YearMonth
    )

    private data class Loaded(
        val query: Query,
        val today: LocalDate,
        val days: List<HistoryDay>
    )

    /** Re-queries only when the profile, mode, range or month changes. Picking a day does not. */
    private val loaded: Flow<Loaded> =
        combine(
            container.currentProfileProvider.current,
            mode,
            rangeDays,
            month
        ) { profile, m, range, ym -> Query(profile, m, range, ym) }
            .distinctUntilChanged()
            .flatMapLatest { query ->
                val today = LocalDate.now()
                val profile = query.profile
                if (profile == null) {
                    flowOf(Loaded(query, today, emptyList()))
                } else {
                    val (from, to) = rangeFor(query, today)
                    container.historyRepository.observeDays(profile, from, to)
                        .map { days -> Loaded(query, today, days) }
                }
            }

    val state: StateFlow<HistoryUiState> = combine(loaded, selected) { l, sel ->
        HistoryUiState(
            loaded = true,
            profile = l.query.profile,
            mode = l.query.mode,
            rangeDays = l.query.rangeDays,
            month = l.query.month,
            selectedDate = sel,
            today = l.today,
            days = l.days,
            summary = AdherenceSummary.of(l.days)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private val _events = MutableSharedFlow<HistoryEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HistoryEvent> = _events.asSharedFlow()

    fun setMode(value: HistoryMode) {
        mode.value = value
    }

    fun setRangeDays(value: Int) {
        rangeDays.value = value
    }

    fun previousMonth() = setMonth(month.value.minusMonths(1))

    fun nextMonth() {
        if (month.value.isBefore(YearMonth.now())) setMonth(month.value.plusMonths(1))
    }

    fun selectDate(date: LocalDate) {
        selected.value = date
    }

    private fun setMonth(value: YearMonth) {
        month.value = value
        // Today when it is in that month, otherwise the 1st.
        selected.value = if (value == YearMonth.now()) LocalDate.now() else value.atDay(1)
    }

    /** Deletes a logged as-needed dose. Stock is added back by the repository. */
    fun delete(logId: String) {
        // appScope, so the write is never cancelled if the screen is closed right after the tap.
        container.appScope.launch {
            if (container.historyRepository.deleteAsNeeded(logId)) {
                _events.emit(HistoryEvent.Deleted)
            }
        }
    }

    private fun rangeFor(query: Query, today: LocalDate): Pair<LocalDate, LocalDate> =
        when (query.mode) {
            HistoryMode.TIMELINE -> today.minusDays((query.rangeDays - 1).toLong()) to today
            HistoryMode.CALENDAR -> query.month.atDay(1) to query.month.atEndOfMonth()
        }
}