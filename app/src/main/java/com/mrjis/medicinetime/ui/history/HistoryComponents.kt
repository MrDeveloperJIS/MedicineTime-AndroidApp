package com.mrjis.medicinetime.ui.history

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.alarm.formatDose
import com.mrjis.medicinetime.domain.dose.TodayStatus
import com.mrjis.medicinetime.domain.history.AdherenceSummary
import com.mrjis.medicinetime.domain.history.DayMark
import com.mrjis.medicinetime.domain.history.HistoryDay
import com.mrjis.medicinetime.domain.history.HistoryDose
import com.mrjis.medicinetime.ui.theme.extraColors
import com.mrjis.medicinetime.util.formatTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** "Today", "Yesterday", "Tomorrow", otherwise e.g. "Mon, 3 Nov" (with the year for other years). */
fun dayLabel(context: Context, date: LocalDate, today: LocalDate): String = when (date) {
    today -> context.getString(R.string.history_day_today)
    today.minusDays(1) -> context.getString(R.string.history_day_yesterday)
    today.plusDays(1) -> context.getString(R.string.history_day_tomorrow)
    else -> {
        val pattern = if (date.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy"
        date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }
}

@Composable
fun markColor(mark: DayMark): Color = when (mark) {
    DayMark.ALL_TAKEN -> MaterialTheme.colorScheme.primary
    DayMark.SOME_MISSED -> MaterialTheme.colorScheme.error
    DayMark.OPEN -> MaterialTheme.extraColors.warning
    DayMark.FUTURE -> MaterialTheme.colorScheme.outline
    DayMark.NONE -> Color.Transparent
}

/** Words for a calendar mark, so a day is never described by color alone. */
fun markLabelRes(mark: DayMark): Int? = when (mark) {
    DayMark.ALL_TAKEN -> R.string.history_legend_all_taken
    DayMark.SOME_MISSED -> R.string.history_legend_missed
    DayMark.OPEN -> R.string.history_legend_open
    DayMark.FUTURE -> R.string.history_legend_planned
    DayMark.NONE -> null
}

/** Adherence over the shown range. As-needed doses are counted apart, never in the percentage. */
@Composable
fun HistorySummaryCard(
    title: String,
    summary: AdherenceSummary,
    modifier: Modifier = Modifier
) {
    val percent = summary.percent
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (percent != null) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.history_summary_percent, percent),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.history_summary_percent_label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.history_summary_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryStat(
                    value = summary.taken,
                    label = stringResource(R.string.history_stat_taken),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                SummaryStat(
                    value = summary.missed,
                    label = stringResource(R.string.history_stat_missed),
                    color = if (summary.missed > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
                SummaryStat(
                    value = summary.asNeeded,
                    label = stringResource(R.string.as_needed),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = stringResource(R.string.history_summary_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun SummaryStat(
    value: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One day: its date, "x of y taken" and every dose in time order. */
@Composable
fun HistoryDayCard(
    day: HistoryDay,
    today: LocalDate,
    onDelete: (HistoryDose) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = dayLabel(context, day.date, today),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (day.summary.scheduled > 0) {
                    val markTint = if (day.mark == DayMark.NONE || day.mark == DayMark.FUTURE) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        markColor(day.mark)
                    }
                    Text(
                        text = stringResource(
                            R.string.today_progress,
                            day.summary.taken,
                            day.summary.scheduled
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = markTint
                    )
                }
            }

            if (day.doses.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_no_doses_day),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                day.doses.forEachIndexed { index, dose ->
                    HorizontalDivider(
                        modifier = Modifier.padding(top = if (index == 0) 8.dp else 0.dp)
                    )
                    HistoryDoseRow(
                        dose = dose,
                        onDelete = if (dose.canDelete) ({ onDelete(dose) }) else null
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryDoseRow(dose: HistoryDose, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    val extra = MaterialTheme.extraColors
    val time = formatTime(millisToTime(dose.scheduledAtMillis))
    val doseText = formatDose(context, dose.doseAmount, dose.doseUnit)
    val detail = if (dose.isAsNeeded) {
        stringResource(R.string.history_detail_as_needed, doseText)
    } else {
        doseText
    }

    val actionTime = dose.actionAtMillis?.let { formatTime(millisToTime(it)) } ?: time
    val (statusText, statusColor) = when (dose.status) {
        TodayStatus.TAKEN -> stringResource(R.string.today_status_taken, actionTime) to
                MaterialTheme.colorScheme.primary

        TodayStatus.MISSED -> stringResource(R.string.today_status_missed) to
                MaterialTheme.colorScheme.error

        TodayStatus.DUE -> stringResource(R.string.today_status_due) to extra.warning

        TodayStatus.SNOOZED -> stringResource(R.string.today_status_snoozed, actionTime) to
                extra.warning

        TodayStatus.UPCOMING -> stringResource(R.string.history_status_planned) to
                MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp)
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.width(84.dp)
        )
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
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = statusColor
            )
            if (onDelete != null) {
                val description = stringResource(R.string.history_delete_desc, dose.medicineName, time)
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = description }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** Confirm before removing a logged as-needed dose (stock is added back). */
@Composable
fun DeleteEntryDialog(
    dose: HistoryDose,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val zoned = Instant.ofEpochMilli(dose.scheduledAtMillis).atZone(ZoneId.systemDefault())
    val whenText = stringResource(
        R.string.today_when_date,
        zoned.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
        formatTime(zoned.toLocalTime())
    )
    val message = stringResource(
        R.string.history_delete_message,
        dose.medicineName,
        formatDose(context, dose.doseAmount, dose.doseUnit),
        whenText
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_delete_title)) },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun millisToTime(millis: Long): LocalTime =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()