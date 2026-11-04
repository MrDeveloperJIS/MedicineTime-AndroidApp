package com.mrjis.medicinetime.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.data.local.entity.MedicineWithSchedule
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.domain.model.RepeatType
import com.mrjis.medicinetime.domain.model.ScheduleMode
import com.mrjis.medicinetime.domain.stock.StockCalculator
import com.mrjis.medicinetime.domain.stock.StockInfo
import com.mrjis.medicinetime.domain.stock.StockLevel
import com.mrjis.medicinetime.ui.AppViewModelProvider
import com.mrjis.medicinetime.ui.components.ProfileSwitcher
import com.mrjis.medicinetime.ui.components.labelRes
import com.mrjis.medicinetime.ui.medicines.MedicinesViewModel
import com.mrjis.medicinetime.ui.medicines.RefillDialog
import com.mrjis.medicinetime.ui.medicines.StockBadge
import com.mrjis.medicinetime.ui.medicines.stockAmountText
import com.mrjis.medicinetime.ui.medicines.stockForecastText

@Composable
fun MedicinesScreen(
    currentProfile: ProfileEntity?,
    profiles: List<ProfileEntity>,
    onSelectProfile: (String) -> Unit,
    onAddMedicine: () -> Unit,
    onOpenMedicine: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MedicinesViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val medicines by viewModel.medicines.collectAsState()
    val stock by viewModel.stock.collectAsState()
    val list = medicines

    // Id of the medicine whose "I bought more" dialog is open.
    var refillId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
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
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onAddMedicine,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.add_medicine))
            }
        }

        Text(
            text = stringResource(R.string.nav_medicines),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState()
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(list, key = { it.medicine.id }) { item ->
                    MedicineCard(
                        item = item,
                        info = stock[item.medicine.id],
                        onClick = { onOpenMedicine(item.medicine.id) },
                        onRefill = { refillId = item.medicine.id }
                    )
                }
            }
        }
    }

    val refillItem = refillId?.let { id -> list?.firstOrNull { it.medicine.id == id } }
    if (refillItem != null) {
        val m = refillItem.medicine
        RefillDialog(
            medicineName = m.name,
            doseUnit = m.doseUnit,
            stockUnit = m.stockUnit,
            current = stock[m.id]?.current
                ?: if (StockCalculator.isTracked(m)) m.stockBaseline else 0.0,
            onConfirm = { amount ->
                viewModel.refill(m.id, amount)
                refillId = null
            },
            onDismiss = { refillId = null }
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.medicines_empty),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.medicines_empty_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun MedicineCard(
    item: MedicineWithSchedule,
    info: StockInfo?,
    onClick: () -> Unit,
    onRefill: () -> Unit
) {
    val medicine = item.medicine
    val tracked = StockCalculator.isTracked(medicine)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = medicine.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (tracked && info != null) {
                    StockBadge(info = info)
                }
            }
            Text(
                text = stringResource(medicine.form.labelRes()) + " · " + scheduleSummary(item),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                !tracked -> Text(
                    text = stringResource(R.string.stock_not_tracked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Tracked, but the live numbers are still loading.
                info == null -> Unit

                medicine.scheduleMode == ScheduleMode.AS_NEEDED -> {
                    Text(
                        text = stringResource(
                            R.string.stock_as_needed_left,
                            stockAmountText(info.current, medicine.doseUnit, medicine.stockUnit)
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val low = medicine.lowStockLevel?.takeIf { it > 0.0 }
                    if (low != null) {
                        Text(
                            text = stringResource(
                                R.string.stock_warn_at,
                                stockAmountText(low, medicine.doseUnit, medicine.stockUnit)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    Text(
                        text = stringResource(
                            R.string.stock_left,
                            stockAmountText(info.current, medicine.doseUnit, medicine.stockUnit)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val forecast = stockForecastText(info)
                    if (forecast != null) {
                        Text(
                            text = forecast,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Filled button when stock is low, so it stands out. Outlined otherwise.
            val needsRefill = info != null && info.level != StockLevel.OK
            if (needsRefill) {
                Button(
                    onClick = onRefill,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.refill_button))
                }
            } else {
                OutlinedButton(
                    onClick = onRefill,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.refill_button))
                }
            }
        }
    }
}

@Composable
private fun scheduleSummary(item: MedicineWithSchedule): String {
    if (item.medicine.scheduleMode == ScheduleMode.AS_NEEDED) {
        return stringResource(R.string.as_needed)
    }
    val first = item.phases.firstOrNull() ?: return stringResource(R.string.no_schedule)

    val repeat = when (first.phase.repeatType) {
        RepeatType.DAILY -> stringResource(R.string.schedule_daily)
        RepeatType.WEEKDAYS -> stringResource(R.string.schedule_weekdays)
        RepeatType.EVERY_N_DAYS ->
            stringResource(R.string.schedule_every_n, first.phase.intervalDays)
    }
    val slotCount = first.slots.size
    var summary = repeat + " · " + pluralStringResource(R.plurals.times_count, slotCount, slotCount)
    if (item.phases.size > 1) {
        val phaseCount = item.phases.size
        summary += " · " + pluralStringResource(R.plurals.phases_count, phaseCount, phaseCount)
    }
    return summary
}