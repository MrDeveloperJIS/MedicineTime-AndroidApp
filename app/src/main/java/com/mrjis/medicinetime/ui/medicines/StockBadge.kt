package com.mrjis.medicinetime.ui.medicines

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.domain.stock.StockInfo
import com.mrjis.medicinetime.domain.stock.StockLevel
import com.mrjis.medicinetime.ui.theme.extraColors

/** Gray = fine, amber = low, red = very low or out. The word is always shown, not only the colour. */
@Composable
fun StockBadge(info: StockInfo, modifier: Modifier = Modifier) {
    val extra = MaterialTheme.extraColors
    val colors = MaterialTheme.colorScheme

    val container = when (info.level) {
        StockLevel.OK -> colors.surfaceContainerHigh
        StockLevel.LOW -> extra.warning
        StockLevel.CRITICAL -> colors.error
    }
    val content = when (info.level) {
        StockLevel.OK -> colors.onSurfaceVariant
        StockLevel.LOW -> extra.onWarning
        StockLevel.CRITICAL -> colors.onError
    }
    val label = when (info.level) {
        StockLevel.OK -> stringResource(R.string.stock_badge_ok)
        StockLevel.LOW -> stringResource(R.string.stock_badge_low)
        StockLevel.CRITICAL ->
            if (info.current <= 0.0) stringResource(R.string.stock_badge_out)
            else stringResource(R.string.stock_badge_very_low)
    }

    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}