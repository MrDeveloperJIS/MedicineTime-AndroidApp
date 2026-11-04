package com.mrjis.medicinetime.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.domain.history.DayMark
import com.mrjis.medicinetime.domain.history.HistoryDay
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** A month grid. Each day has a dot: all taken, missed, still open or planned. */
@Composable
fun HistoryCalendar(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    days: List<HistoryDay>,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val marks: Map<LocalDate, DayMark> = days.associate { it.date to it.mark }
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val offset = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val rows = (offset + month.lengthOfMonth() + 6) / 7
    val numbers = NumberFormat.getIntegerInstance(locale)

    val previousDescription = stringResource(R.string.history_prev_month)
    val nextDescription = stringResource(R.string.history_next_month)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPrevious,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = previousDescription },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("‹", style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    text = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onNext,
                    enabled = canGoNext,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = nextDescription },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                for (i in 0 until 7) {
                    Text(
                        text = firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.SHORT, locale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayNumber = row * 7 + col - offset + 1
                        if (dayNumber < 1 || dayNumber > month.lengthOfMonth()) {
                            Box(modifier = Modifier.weight(1f).height(54.dp))
                        } else {
                            val date = month.atDay(dayNumber)
                            val mark = marks[date] ?: DayMark.NONE
                            val dateText = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
                            val markLabel = markLabelRes(mark)?.let { stringResource(it) }
                            val description = if (markLabel != null) {
                                stringResource(R.string.history_cell_desc, dateText, markLabel)
                            } else {
                                dateText
                            }
                            DayCell(
                                number = numbers.format(dayNumber.toLong()),
                                mark = mark,
                                isSelected = date == selected,
                                isToday = date == today,
                                description = description,
                                onClick = { onSelect(date) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendItem(DayMark.ALL_TAKEN, R.string.history_legend_all_taken)
                    LegendItem(DayMark.SOME_MISSED, R.string.history_legend_missed)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendItem(DayMark.OPEN, R.string.history_legend_open)
                    LegendItem(DayMark.FUTURE, R.string.history_legend_planned)
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    number: String,
    mark: DayMark,
    isSelected: Boolean,
    isToday: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)
    val highlighted = isSelected || isToday

    Box(
        modifier = modifier
            .height(54.dp)
            .padding(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .then(if (isSelected) Modifier.background(primary.copy(alpha = 0.16f)) else Modifier)
                .then(if (isToday) Modifier.border(2.dp, primary, shape) else Modifier)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clearAndSetSemantics { }
            )
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(8.dp)
                    .background(markColor(mark), CircleShape)
            )
        }
    }
}

@Composable
private fun LegendItem(mark: DayMark, labelRes: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(markColor(mark), CircleShape)
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}