package com.mrjis.medicinetime.ui.today

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.alarm.formatDose
import com.mrjis.medicinetime.domain.model.DoseUnit
import com.mrjis.medicinetime.ui.components.labelRes
import com.mrjis.medicinetime.util.formatAmount
import com.mrjis.medicinetime.util.formatTime
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The "As needed" section at the bottom of the Today list. Adds nothing when there are none. */
fun LazyListScope.asNeededSection(
    items: List<AsNeededItem>,
    nowMillis: Long,
    onTakeNow: (AsNeededItem) -> Unit
) {
    if (items.isEmpty()) return

    item(key = "as_needed_header") {
        Text(
            text = stringResource(R.string.as_needed),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )
    }
    items(items.size, key = { "as_needed_${items[it].medicineId}" }) { index ->
        val item = items[index]
        AsNeededCard(item = item, nowMillis = nowMillis, onTakeNow = { onTakeNow(item) })
    }
}

@Composable
private fun AsNeededCard(
    item: AsNeededItem,
    nowMillis: Long,
    onTakeNow: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = lastTakenText(context, item.lastTakenAtMillis, nowMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onTakeNow,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.today_action_take_now))
            }
        }
    }
}

/**
 * Confirm sheet: medicine, dose (pre-filled with the usual dose, editable) and the current time.
 * The app only shows the last time. It never blocks a dose or suggests a gap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeNowSheet(
    item: AsNeededItem,
    nowMillis: Long,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable(item.medicineId) {
        mutableStateOf(formatAmount(item.defaultDose ?: 1.0))
    }

    val amount: Double? = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
    val step = stepFor(item.doseUnit)
    val minusDescription = stringResource(R.string.take_now_decrease)
    val plusDescription = stringResource(R.string.take_now_increase)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.take_now_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.take_now_time, formatTime(toLocalTime(nowMillis))),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val next = ((amount ?: step) - step).coerceAtLeast(step)
                        text = formatAmount(roundAmount(next))
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .semantics { contentDescription = minusDescription },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("−", style = MaterialTheme.typography.headlineSmall)
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        if (value.length <= 6 && value.all { it.isDigit() || it == '.' || it == ',' }) {
                            text = value
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = amount == null,
                    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                    suffix = { Text(stringResource(item.doseUnit.labelRes())) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(stringResource(R.string.take_now_dose_label)) }
                )

                OutlinedButton(
                    onClick = { text = formatAmount(roundAmount((amount ?: 0.0) + step)) },
                    modifier = Modifier
                        .size(56.dp)
                        .semantics { contentDescription = plusDescription },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }

            // "2 tsp (10 ml)" for spoons, or an error hint while the field is empty or 0.
            Text(
                text = if (amount != null) {
                    formatDose(context, amount, item.doseUnit)
                } else {
                    stringResource(R.string.take_now_invalid)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (amount != null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
            )

            Button(
                onClick = { if (amount != null) onConfirm(amount) },
                enabled = amount != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(
                    text = stringResource(R.string.take_now_confirm),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

/** "Last taken: 2:30 PM (3 h ago)", or "Not taken yet". */
private fun lastTakenText(context: Context, lastAt: Long?, now: Long): String {
    if (lastAt == null) return context.getString(R.string.today_last_taken_never)

    val zone = ZoneId.systemDefault()
    val taken = Instant.ofEpochMilli(lastAt).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = formatTime(taken.toLocalTime())

    val whenText = when (taken.toLocalDate()) {
        today -> time
        today.minusDays(1) -> context.getString(R.string.today_when_yesterday, time)
        else -> context.getString(
            R.string.today_when_date,
            taken.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            time
        )
    }

    val minutes = ((now - lastAt) / 60_000L).coerceAtLeast(0L)
    val ago = when {
        minutes < 1 -> context.getString(R.string.today_ago_now)
        minutes < 60 -> context.getString(R.string.today_ago_minutes, minutes.toInt())
        minutes < 24 * 60 -> context.getString(R.string.today_ago_hours, (minutes / 60).toInt())
        else -> context.getString(R.string.today_ago_days, (minutes / (24 * 60)).toInt())
    }
    return context.getString(R.string.today_last_taken, whenText, ago)
}

/** Size of one + / - tap: half steps for tablets and spoons, whole numbers otherwise. */
private fun stepFor(unit: DoseUnit): Double = when (unit) {
    DoseUnit.TABLET, DoseUnit.TSP, DoseUnit.TBSP -> 0.5
    DoseUnit.ML, DoseUnit.DROP, DoseUnit.UNIT -> 1.0
}

private fun roundAmount(value: Double): Double = Math.round(value * 100.0) / 100.0

private fun toLocalTime(millis: Long): LocalTime =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()