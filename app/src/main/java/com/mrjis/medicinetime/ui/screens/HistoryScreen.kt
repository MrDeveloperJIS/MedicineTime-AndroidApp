package com.mrjis.medicinetime.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.ui.AppViewModelProvider
import com.mrjis.medicinetime.ui.components.ProfileSwitcher
import com.mrjis.medicinetime.ui.history.DeleteEntryDialog
import com.mrjis.medicinetime.ui.history.HistoryCalendar
import com.mrjis.medicinetime.ui.history.HistoryDayCard
import com.mrjis.medicinetime.ui.history.HistoryEvent
import com.mrjis.medicinetime.ui.history.HistoryMode
import com.mrjis.medicinetime.ui.history.HistorySummaryCard
import com.mrjis.medicinetime.ui.history.HistoryViewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    currentProfile: ProfileEntity?,
    profiles: List<ProfileEntity>,
    onSelectProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Which entry has its delete confirmation open (survives rotation).
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HistoryEvent.Deleted ->
                    snackbarHostState.showSnackbar(context.getString(R.string.history_deleted))
            }
        }
    }

    val deleteDose = deleteId?.let { id ->
        state.days.asSequence()
            .flatMap { it.doses.asSequence() }
            .firstOrNull { it.logId == id }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentProfile != null) {
                    ProfileSwitcher(
                        current = currentProfile,
                        profiles = profiles,
                        onSelect = onSelectProfile
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "header") {
                    Text(
                        text = stringResource(R.string.nav_history),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                item(key = "mode") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.mode == HistoryMode.TIMELINE,
                            onClick = { viewModel.setMode(HistoryMode.TIMELINE) },
                            label = {
                                Text(
                                    text = stringResource(R.string.history_view_timeline),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        FilterChip(
                            selected = state.mode == HistoryMode.CALENDAR,
                            onClick = { viewModel.setMode(HistoryMode.CALENDAR) },
                            label = {
                                Text(
                                    text = stringResource(R.string.history_view_calendar),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                if (state.loaded) {
                    when (state.mode) {
                        HistoryMode.TIMELINE -> timelineContent(
                            state = state,
                            onRange = viewModel::setRangeDays,
                            onDelete = { deleteId = it.logId }
                        )

                        HistoryMode.CALENDAR -> calendarContent(
                            state = state,
                            viewModel = viewModel,
                            onDelete = { deleteId = it.logId }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp)
        )

        if (deleteDose != null) {
            DeleteEntryDialog(
                dose = deleteDose,
                onConfirm = {
                    viewModel.delete(deleteDose.logId)
                    deleteId = null
                },
                onDismiss = { deleteId = null }
            )
        }
    }
}

/** Range chips, the summary card, then one card per day that has doses (newest first). */
private fun androidx.compose.foundation.lazy.LazyListScope.timelineContent(
    state: com.mrjis.medicinetime.ui.history.HistoryUiState,
    onRange: (Int) -> Unit,
    onDelete: (com.mrjis.medicinetime.domain.history.HistoryDose) -> Unit
) {
    item(key = "range") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.rangeDays == 7,
                onClick = { onRange(7) },
                label = {
                    Text(
                        text = stringResource(R.string.history_range_7),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp)
            )
            FilterChip(
                selected = state.rangeDays == 30,
                onClick = { onRange(30) },
                label = {
                    Text(
                        text = stringResource(R.string.history_range_30),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
    }

    val daysWithDoses = state.days.filter { it.doses.isNotEmpty() }
    if (daysWithDoses.isEmpty()) {
        item(key = "empty") { HistoryEmptyState() }
        return
    }

    item(key = "summary") {
        HistorySummaryCard(
            title = stringResource(
                if (state.rangeDays == 7) R.string.history_range_7 else R.string.history_range_30
            ),
            summary = state.summary
        )
    }

    items(daysWithDoses.size, key = { "day_${daysWithDoses[it].date}" }) { index ->
        HistoryDayCard(
            day = daysWithDoses[index],
            today = state.today,
            onDelete = onDelete
        )
    }
}

/** Month grid, the month summary, then the detail of the selected day. */
private fun androidx.compose.foundation.lazy.LazyListScope.calendarContent(
    state: com.mrjis.medicinetime.ui.history.HistoryUiState,
    viewModel: HistoryViewModel,
    onDelete: (com.mrjis.medicinetime.domain.history.HistoryDose) -> Unit
) {
    item(key = "calendar") {
        HistoryCalendar(
            month = state.month,
            today = state.today,
            selected = state.selectedDate,
            days = state.days,
            canGoNext = state.canGoNextMonth,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            onSelect = viewModel::selectDate
        )
    }

    item(key = "day_detail") {
        val day = state.selectedDay
        if (day != null) {
            HistoryDayCard(day = day, today = state.today, onDelete = onDelete)
        }
    }

    item(key = "summary") {
        HistorySummaryCard(
            title = state.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())),
            summary = state.summary
        )
    }
}

@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.history_empty),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.history_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}