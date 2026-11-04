package com.mrjis.medicinetime.ui.medicines

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.domain.model.DoseUnit
import com.mrjis.medicinetime.domain.model.StockUnit
import com.mrjis.medicinetime.domain.stock.StockInfo
import com.mrjis.medicinetime.util.formatAmount

/** "12 tablets", "1 tablet", "0.5 tablets", "150 ml". */
@Composable
fun stockAmountText(amount: Double, doseUnit: DoseUnit, stockUnit: StockUnit): String {
    val number = formatAmount(amount)
    if (stockUnit == StockUnit.ML) {
        return stringResource(R.string.stock_amount_ml, number)
    }
    // Only exactly 1 is singular ("1 tablet"); 0.5 and 2 are plural.
    val quantity = if (amount == 1.0) 1 else 2
    val plural = when (doseUnit) {
        DoseUnit.DROP -> R.plurals.stock_count_drop
        DoseUnit.UNIT -> R.plurals.stock_count_unit
        else -> R.plurals.stock_count_tablet
    }
    return pluralStringResource(plural, quantity, number)
}

/**
 * How long a scheduled medicine's stock lasts, as one short line.
 * Returns null when there is no forecast (as-needed or untracked medicines).
 */
@Composable
fun stockForecastText(info: StockInfo): String? {
    val forecast = info.forecast ?: return null
    val days = forecast.daysLeft
    return when {
        info.current <= 0.0 -> stringResource(R.string.stock_out)
        days == null -> stringResource(R.string.stock_lasts_course)
        days < 0 -> stringResource(R.string.stock_not_enough)
        days == 0 -> stringResource(R.string.stock_runs_out_today)
        else -> pluralStringResource(R.plurals.stock_days_left, days, days)
    }
}