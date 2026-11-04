package com.mrjis.medicinetime.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.domain.model.MedicineForm
import com.mrjis.medicinetime.domain.model.ScheduleMode
import com.mrjis.medicinetime.domain.model.StockUnit
import com.mrjis.medicinetime.ui.AppViewModelProvider
import com.mrjis.medicinetime.ui.components.labelRes
import com.mrjis.medicinetime.ui.medicines.ChoiceChip
import com.mrjis.medicinetime.ui.medicines.DraftError
import com.mrjis.medicinetime.ui.medicines.MedicineEditViewModel
import com.mrjis.medicinetime.ui.medicines.NumberField
import com.mrjis.medicinetime.ui.medicines.PhaseEditor
import com.mrjis.medicinetime.ui.medicines.allowedDoseUnits
import com.mrjis.medicinetime.ui.medicines.stockUnit

/** Add or edit one medicine. Closes itself (via [onClose]) after save, delete, or if the medicine is gone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicineEditScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedicineEditViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val draft by viewModel.draft.collectAsState()
    val loaded by viewModel.loaded.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val showErrors by viewModel.showErrors.collectAsState()
    val validation by viewModel.validation.collectAsState()

    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(finished) {
        if (finished) onClose()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // ---- top bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.back))
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = viewModel::save,
                enabled = loaded && !saving,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.save))
            }
        }

        if (!loaded) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(
                        if (viewModel.isEditing) R.string.edit_medicine else R.string.add_medicine
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // ---- name ----
                val nameError = showErrors && DraftError.NAME_BLANK in validation.medicineErrors
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                    label = { Text(stringResource(R.string.medicine_name)) },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.name_required)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )

                // ---- form ----
                SectionLabel(stringResource(R.string.medicine_form))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MedicineForm.entries.forEach { form ->
                        ChoiceChip(
                            text = stringResource(form.labelRes()),
                            selected = draft.form == form,
                            onClick = { viewModel.setForm(form) }
                        )
                    }
                }

                // ---- dose unit (only when the form has a choice) ----
                val units = draft.form.allowedDoseUnits()
                if (units.size > 1) {
                    SectionLabel(stringResource(R.string.dose_unit))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        units.forEach { unit ->
                            ChoiceChip(
                                text = stringResource(unit.labelRes()),
                                selected = draft.doseUnit == unit,
                                onClick = { viewModel.update { it.copy(doseUnit = unit) } }
                            )
                        }
                    }
                }

                // ---- scheduled / as needed ----
                SectionLabel(stringResource(R.string.schedule_type))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ChoiceChip(
                        text = stringResource(R.string.schedule_mode_scheduled),
                        selected = draft.scheduleMode == ScheduleMode.SCHEDULED,
                        onClick = { viewModel.setScheduleMode(ScheduleMode.SCHEDULED) }
                    )
                    ChoiceChip(
                        text = stringResource(R.string.as_needed),
                        selected = draft.scheduleMode == ScheduleMode.AS_NEEDED,
                        onClick = { viewModel.setScheduleMode(ScheduleMode.AS_NEEDED) }
                    )
                }

                // ---- stock (when editing, this is the current stock) ----
                val stockSuffix = stringResource(
                    if (draft.stockUnit == StockUnit.ML) R.string.unit_ml else draft.doseUnit.labelRes()
                )
                NumberField(
                    value = draft.stock,
                    onValueChange = { v -> viewModel.update { it.copy(stock = v) } },
                    label = stringResource(R.string.stock_label),
                    suffix = stockSuffix,
                    errorText = if (showErrors && DraftError.STOCK_INVALID in validation.medicineErrors) {
                        stringResource(R.string.number_invalid)
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (viewModel.isEditing) {
                    Text(
                        text = stringResource(R.string.stock_edit_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (draft.scheduleMode == ScheduleMode.AS_NEEDED) {
                    // ---- as-needed fields ----
                    Text(
                        text = stringResource(R.string.as_needed_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NumberField(
                        value = draft.defaultDose,
                        onValueChange = { v -> viewModel.update { it.copy(defaultDose = v) } },
                        label = stringResource(R.string.default_dose_label),
                        suffix = stringResource(draft.doseUnit.labelRes()),
                        errorText = if (showErrors && DraftError.DEFAULT_DOSE_INVALID in validation.medicineErrors) {
                            stringResource(R.string.dose_invalid)
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    NumberField(
                        value = draft.lowStockLevel,
                        onValueChange = { v -> viewModel.update { it.copy(lowStockLevel = v) } },
                        label = stringResource(R.string.low_stock_label),
                        suffix = stockSuffix,
                        errorText = if (showErrors && DraftError.LOW_STOCK_INVALID in validation.medicineErrors) {
                            stringResource(R.string.number_invalid)
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // ---- schedule phases ----
                    draft.phases.forEachIndexed { index, phase ->
                        key(phase.id) {
                            PhaseEditor(
                                index = index,
                                phaseCount = draft.phases.size,
                                phase = phase,
                                issues = validation.phaseIssues[phase.id],
                                invalidSlotIds = validation.invalidSlotIds,
                                showErrors = showErrors,
                                doseUnit = draft.doseUnit,
                                onPhaseChange = { transform ->
                                    viewModel.update { d -> d.updatePhase(phase.id, transform) }
                                },
                                onSlotChange = { slotId, transform ->
                                    viewModel.update { d -> d.updateSlot(phase.id, slotId, transform) }
                                },
                                onAddSlot = { viewModel.update { it.withNewSlot(phase.id) } },
                                onRemoveSlot = { slotId ->
                                    viewModel.update { it.withoutSlot(phase.id, slotId) }
                                },
                                onRemovePhase = { viewModel.update { it.withoutPhase(phase.id) } }
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { viewModel.update { it.withNextPhase() } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.add_phase))
                    }
                }

                // ---- notes ----
                OutlinedTextField(
                    value = draft.notes,
                    onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                    label = { Text(stringResource(R.string.notes_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showErrors && !validation.isValid) {
                    Text(
                        text = stringResource(R.string.fix_errors),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (viewModel.isEditing) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }

                Spacer(modifier = Modifier.padding(bottom = 16.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_medicine_title)) },
            text = { Text(stringResource(R.string.delete_medicine_message, draft.name.trim())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground
    )
}