package com.mrjis.medicinetime.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.alarm.formatDose
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.domain.dose.TodayDose
import com.mrjis.medicinetime.domain.dose.TodayGroup
import com.mrjis.medicinetime.domain.dose.TodayStatus
import com.mrjis.medicinetime.ui.AppViewModelProvider
import com.mrjis.medicinetime.ui.components.ProfileSwitcher
import com.mrjis.medicinetime.ui.theme.extraColors
import com.mrjis.medicinetime.ui.today.TodayEvent
import com.mrjis.medicinetime.ui.today.TodayViewModel
import com.mrjis.medicinetime.util.formatTime
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.mrjis.medicinetime.ui.today.TakeNowSheet
import com.mrjis.medicinetime.ui.today.asNeededSection

@Composable
fun TodayScreen(
    currentProfile: ProfileEntity?,
    profiles: List<ProfileEntity>,
    onSelectProfile: (String) -> Unit,
    onManageProfiles: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Which as-needed medicine has its Take now sheet open (survives rotation).
    var takeNowId by rememberSaveable { mutableStateOf<String?>(null) }

    // Each "taken" shows a snackbar with Undo. A newer one replaces the older one.
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is TodayEvent.Taken -> {
                    val message = if (event.count == 1 && event.singleName != null) {
                        context.getString(R.string.today_taken_one, event.singleName)
                    } else {
                        context.resources.getQuantityString(
                            R.plurals.today_taken_many,
                            event.count,
                            event.count
                        )
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo(event)
                }
            }
        }
    }

    val profileId = state.profile?.id
    val groups = state.groups
    val missed = state.missed
    val sheetItem = state.asNeeded.firstOrNull { it.medicineId == takeNowId }

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
                        onSelect = onSelectProfile,
                        onManage = onManageProfiles
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp)
            ) {
                item(key = "header") {
                    Header(
                        takenCount = state.takenCount,
                        totalCount = state.totalCount
                    )
                }

                if (missed.isNotEmpty()) {
                    item(key = "missed") {
                        MissedCard(
                            missed = missed,
                            onTake = { doses ->
                                if (profileId != null) viewModel.take(profileId, doses)
                            }
                        )
                    }
                }

                // Only when there is nothing at all: no planned doses and no as-needed medicines.
                if (state.loaded && groups.isEmpty() && state.asNeeded.isEmpty()) {
                    item(key = "empty") { EmptyState() }
                }

                items(groups.size, key = { groups[it].at.toString() }) { index ->
                    TimelineItem(
                        group = groups[index],
                        isLast = index == groups.lastIndex,
                        onTake = { doses ->
                            if (profileId != null) viewModel.take(profileId, doses)
                        }
                    )
                }

                asNeededSection(
                    items = state.asNeeded,
                    nowMillis = state.nowMillis,
                    onTakeNow = { takeNowId = it.medicineId }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp)
        )

        if (sheetItem != null && profileId != null) {
            TakeNowSheet(
                item = sheetItem,
                nowMillis = state.nowMillis,
                onConfirm = { amount ->
                    viewModel.takeAsNeeded(profileId, sheetItem, amount)
                    takeNowId = null
                },
                onDismiss = { takeNowId = null }
            )
        }
    }
}

@Composable
private fun Header(takenCount: Int, totalCount: Int) {
    val dateText = LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.nav_today),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = dateText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (totalCount > 0) {
            Text(
                text = stringResource(R.string.today_progress, takenCount, totalCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.today_empty),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.today_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** Warning card: doses missed in the last 24 hours, each with "Taken late". */
@Composable
private fun MissedCard(
    missed: List<TodayDose>,
    onTake: (List<TodayDose>) -> Unit
) {
    val context = LocalContext.current
    val error = MaterialTheme.colorScheme.error
    val today = LocalDate.now()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = error.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface)
        ),
        border = BorderStroke(1.dp, error.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.today_missed_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = error
            )
            Text(
                text = stringResource(R.string.today_missed_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            missed.forEachIndexed { index, dose ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                val time = formatTime(dose.scheduledAt.toLocalTime())
                val whenText = if (dose.scheduledAt.toLocalDate() == today) {
                    stringResource(R.string.today_missed_at_today, time)
                } else {
                    stringResource(R.string.today_missed_at_yesterday, time)
                }
                val detail = stringResource(
                    R.string.today_missed_detail,
                    formatDose(context, dose.doseAmount, dose.doseUnit),
                    whenText
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dose.medicineName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { onTake(listOf(dose)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.today_action_taken_late))
                    }
                }
            }

            if (missed.size > 1) {
                Button(
                    onClick = { onTake(missed) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.today_action_all_taken_late))
                }
            }
        }
    }
}

/** One time on the timeline: the time, a dot on a line, and a card with every dose due then. */
@Composable
private fun TimelineItem(
    group: TodayGroup,
    isLast: Boolean,
    onTake: (List<TodayDose>) -> Unit
) {
    val dotColor = groupColor(group)
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Text(
            text = formatTime(group.at.toLocalTime()),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier
                .width(84.dp)
                .padding(top = 14.dp)
        )

        Box(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val dotY = 26.dp.toPx()
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x, if (isLast) dotY else size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawCircle(color = dotColor, radius = 7.dp.toPx(), center = Offset(x, dotY))
                }
        )

        Card(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                group.doses.forEachIndexed { index, dose ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    DoseRow(dose = dose, onTake = { onTake(listOf(dose)) })
                }
                if (group.open.size > 1) {
                    Button(
                        onClick = { onTake(group.open) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.today_action_all_taken))
                    }
                }
            }
        }
    }
}

@Composable
private fun DoseRow(dose: TodayDose, onTake: () -> Unit) {
    val context = LocalContext.current
    val extra = MaterialTheme.extraColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dose.medicineName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatDose(context, dose.doseAmount, dose.doseUnit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val (statusText, statusColor) = when (dose.status) {
                TodayStatus.TAKEN -> stringResource(
                    R.string.today_status_taken,
                    dose.actionAtMillis?.let { formatTime(millisToTime(it)) } ?: ""
                ) to MaterialTheme.colorScheme.primary

                TodayStatus.MISSED -> stringResource(R.string.today_status_missed) to
                        MaterialTheme.colorScheme.error

                TodayStatus.DUE -> stringResource(R.string.today_status_due) to extra.warning

                TodayStatus.SNOOZED -> stringResource(
                    R.string.today_status_snoozed,
                    dose.actionAtMillis?.let { formatTime(millisToTime(it)) } ?: ""
                ) to extra.warning

                TodayStatus.UPCOMING -> null to Color.Unspecified
            }
            if (statusText != null) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }
        }

        when (dose.status) {
            TodayStatus.TAKEN -> Unit
            // Not due yet: still possible (taken early), but not the main action.
            TodayStatus.UPCOMING -> OutlinedButton(
                onClick = onTake,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.today_action_taken))
            }

            else -> Button(
                onClick = onTake,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.today_action_taken))
            }
        }
    }
}

@Composable
private fun groupColor(group: TodayGroup): Color {
    val extra = MaterialTheme.extraColors
    return when {
        group.allTaken -> MaterialTheme.colorScheme.primary
        group.doses.any { it.status == TodayStatus.MISSED } -> MaterialTheme.colorScheme.error
        group.doses.any { it.status == TodayStatus.DUE || it.status == TodayStatus.SNOOZED } -> extra.warning
        else -> MaterialTheme.colorScheme.outline
    }
}

private fun millisToTime(millis: Long): LocalTime =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()