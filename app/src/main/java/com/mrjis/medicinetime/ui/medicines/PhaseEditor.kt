package com.mrjis.medicinetime.ui.medicines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.domain.model.DoseUnit
import com.mrjis.medicinetime.domain.model.Meal
import com.mrjis.medicinetime.domain.model.RepeatType
import com.mrjis.medicinetime.domain.model.SlotType
import com.mrjis.medicinetime.domain.model.Weekdays
import com.mrjis.medicinetime.ui.components.AppTimePickerDialog
import com.mrjis.medicinetime.ui.components.labelRes
import com.mrjis.medicinetime.ui.theme.extraColors
import com.mrjis.medicinetime.util.formatTime
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * One schedule phase: dates, repeat rule and the list of times (slots) with their doses.
 * It only shows state and reports changes; [MedicineEditViewModel] owns the draft.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhaseEditor(
    index: Int,
    phaseCount: Int,
    phase: PhaseDraft,
    issues: PhaseIssues?,
    invalidSlotIds: Set<String>,
    showErrors: Boolean,
    doseUnit: DoseUnit,
    onPhaseChange: ((PhaseDraft) -> PhaseDraft) -> Unit,
    onSlotChange: (slotId: String, transform: (SlotDraft) -> SlotDraft) -> Unit,
    onAddSlot: () -> Unit,
    onRemoveSlot: (String) -> Unit,
    onRemovePhase: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- header ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (phaseCount > 1) stringResource(R.string.phase_title, index + 1)
                    else stringResource(R.string.schedule_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (phaseCount > 1) {
                    TextButton(
                        onClick = onRemovePhase,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.remove_phase),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ---- dates ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(
                    label = stringResource(R.string.start_date),
                    date = phase.startDate,
                    onClick = { pickingStart = true },
                    modifier = Modifier.weight(1f)
                )
                DateButton(
                    label = stringResource(R.string.end_date),
                    date = phase.endDate,
                    onClick = { pickingEnd = true },
                    modifier = Modifier.weight(1f)
                )
            }
            if (issues?.endBeforeStart == true) {
                Message(stringResource(R.string.date_end_before_start), isError = true)
            }
            if (issues?.overlapsPrevious == true) {
                Message(stringResource(R.string.phase_overlap), isError = true)
            }
            if (issues?.gapBefore == true) {
                Message(stringResource(R.string.phase_gap), isError = false)
            }

            // ---- repeat rule ----
            Text(
                text = stringResource(R.string.repeat_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RepeatType.entries.forEach { type ->
                    ChoiceChip(
                        text = stringResource(type.labelRes()),
                        selected = phase.repeatType == type,
                        onClick = { onPhaseChange { it.copy(repeatType = type) } }
                    )
                }
            }

            if (phase.repeatType == RepeatType.WEEKDAYS) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DayOfWeek.entries.forEach { day ->
                        ChoiceChip(
                            text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            selected = Weekdays.contains(phase.repeatDays, day),
                            onClick = {
                                onPhaseChange { it.copy(repeatDays = it.repeatDays xor Weekdays.bit(day)) }
                            }
                        )
                    }
                }
                if (showErrors && issues?.weekdaysEmpty == true) {
                    Message(stringResource(R.string.weekdays_empty), isError = true)
                }
            }

            if (phase.repeatType == RepeatType.EVERY_N_DAYS) {
                NumberField(
                    value = phase.intervalDays,
                    onValueChange = { v ->
                        onPhaseChange { it.copy(intervalDays = v.filter(Char::isDigit).take(3)) }
                    },
                    label = stringResource(R.string.interval_label),
                    suffix = null,
                    errorText = if (showErrors && issues?.intervalInvalid == true) {
                        stringResource(R.string.interval_invalid)
                    } else null,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---- times and doses ----
            Text(
                text = stringResource(R.string.times_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            phase.slots.forEach { slot ->
                key(slot.id) {
                    SlotEditor(
                        slot = slot,
                        doseUnit = doseUnit,
                        canRemove = phase.slots.size > 1,
                        doseInvalid = showErrors && slot.id in invalidSlotIds,
                        onChange = { transform -> onSlotChange(slot.id, transform) },
                        onRemove = { onRemoveSlot(slot.id) }
                    )
                }
            }
            if (issues?.duplicateSlots == true) {
                Message(stringResource(R.string.duplicate_slots), isError = true)
            }
            if (showErrors && issues?.noSlots == true) {
                Message(stringResource(R.string.no_slots), isError = true)
            }
            OutlinedButton(
                onClick = onAddSlot,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.add_time))
            }
        }
    }

    if (pickingStart) {
        AppDatePickerDialog(
            initial = phase.startDate,
            onDismiss = { pickingStart = false },
            onConfirm = { newStart ->
                // Keep the phase length when the start moves.
                onPhaseChange { p ->
                    val length = ChronoUnit.DAYS.between(p.startDate, p.endDate).coerceAtLeast(0)
                    p.copy(startDate = newStart, endDate = newStart.plusDays(length))
                }
                pickingStart = false
            }
        )
    }
    if (pickingEnd) {
        AppDatePickerDialog(
            initial = phase.endDate,
            onDismiss = { pickingEnd = false },
            onConfirm = { newEnd ->
                onPhaseChange { it.copy(endDate = newEnd) }
                pickingEnd = false
            }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// One time slot
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotEditor(
    slot: SlotDraft,
    doseUnit: DoseUnit,
    canRemove: Boolean,
    doseInvalid: Boolean,
    onChange: ((SlotDraft) -> SlotDraft) -> Unit,
    onRemove: () -> Unit
) {
    var pickingTime by remember { mutableStateOf(false) }
    // Local text so an empty field while typing doesn't snap back to 0.
    var offsetText by remember { mutableStateOf(slot.offsetMinutes.toString()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SlotType.entries.forEach { type ->
                    ChoiceChip(
                        text = stringResource(type.labelRes()),
                        selected = slot.slotType == type,
                        onClick = { onChange { it.copy(slotType = type) } }
                    )
                }
            }

            if (slot.slotType == SlotType.CLOCK) {
                OutlinedButton(
                    onClick = { pickingTime = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = formatTime(slot.clockTime),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Meal.entries.forEach { meal ->
                        ChoiceChip(
                            text = stringResource(meal.labelRes()),
                            selected = slot.meal == meal,
                            onClick = { onChange { it.copy(meal = meal) } }
                        )
                    }
                }
                NumberField(
                    value = offsetText,
                    onValueChange = { v ->
                        val digits = v.filter(Char::isDigit).take(3)
                        offsetText = digits
                        digits.toIntOrNull()?.let { minutes ->
                            onChange { it.copy(offsetMinutes = minutes) }
                        }
                    },
                    label = stringResource(R.string.minutes_label),
                    suffix = null,
                    errorText = null,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            NumberField(
                value = slot.dose,
                onValueChange = { v -> onChange { it.copy(dose = v) } },
                label = stringResource(R.string.dose_label),
                suffix = stringResource(doseUnit.labelRes()),
                errorText = if (doseInvalid) stringResource(R.string.dose_invalid) else null,
                modifier = Modifier.fillMaxWidth()
            )

            if (canRemove) {
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.remove_time),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (pickingTime) {
        AppTimePickerDialog(
            initial = slot.clockTime,
            onDismiss = { pickingTime = false },
            onConfirm = { time ->
                onChange { it.copy(clockTime = time) }
                pickingTime = false
            }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Small shared pieces (also used by MedicineEditScreen)
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = text, style = MaterialTheme.typography.bodyLarge) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            selectedLabelColor = MaterialTheme.colorScheme.primary
        ),
        modifier = Modifier.heightIn(min = 40.dp)
    )
}

/** Text field for numbers. [errorText] != null turns it red and shows the message under it. */
@Composable
internal fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String?,
    errorText: String?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = if (suffix != null) {
            { Text(suffix) }
        } else null,
        singleLine = true,
        isError = errorText != null,
        supportingText = if (errorText != null) {
            { Text(errorText) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
    )
}

@Composable
private fun DateButton(
    label: String,
    date: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = formatDate(date), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** Red for problems that block saving, amber for warnings. */
@Composable
private fun Message(text: String, isError: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.extraColors.warning
    )
}

private fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    // Material's date picker works in UTC milliseconds.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                }
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        DatePicker(state = state)
    }
}