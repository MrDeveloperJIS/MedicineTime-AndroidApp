package com.mrjis.medicinetime.ui.medicines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.domain.model.DoseUnit
import com.mrjis.medicinetime.domain.model.StockUnit
import com.mrjis.medicinetime.ui.components.labelRes

/**
 * "I bought more". [amount] is in stock units: tablets, drops, units, or ml for syrup
 * (the size of the bottle, not teaspoons). The dialog shows the new total before confirming.
 */
@Composable
fun RefillDialog(
    medicineName: String,
    doseUnit: DoseUnit,
    stockUnit: StockUnit,
    current: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val amount = parseAmount(text)?.takeIf { it > 0.0 }
    val showError = text.isNotBlank() && amount == null

    val suffix = stringResource(
        if (stockUnit == StockUnit.ML) R.string.unit_ml else doseUnit.labelRes()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.refill_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.refill_message, medicineName),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(
                        R.string.refill_current,
                        stockAmountText(current, doseUnit, stockUnit)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NumberField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.refill_amount_label),
                    suffix = suffix,
                    errorText = if (showError) stringResource(R.string.refill_invalid) else null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (amount != null) {
                    Text(
                        text = stringResource(
                            R.string.refill_new_total,
                            stockAmountText(current + amount, doseUnit, stockUnit)
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (amount != null) onConfirm(amount) },
                enabled = amount != null
            ) {
                Text(stringResource(R.string.refill_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}