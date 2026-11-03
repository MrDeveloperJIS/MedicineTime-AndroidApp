package com.mrjis.medicinetime.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.stockWarningDataStore by preferencesDataStore(name = "stock_warnings")

/**
 * Remembers the most serious stock warning already sent for each medicine, so a threshold
 * fires once per refill. It is tied to the stock baseline time: a refill or a stock edit moves
 * that time, which makes the old state count as "nothing sent yet".
 * Kept on this phone only (not in Room), so no database change is needed.
 */
class StockWarningStore(private val context: Context) {

    data class State(val baselineAt: Long, val rank: Int)

    private fun key(medicineId: String) = stringPreferencesKey("warned_$medicineId")

    suspend fun get(medicineId: String): State? {
        val raw = context.stockWarningDataStore.data.first()[key(medicineId)] ?: return null
        val parts = raw.split(':')
        val baselineAt = parts.getOrNull(0)?.toLongOrNull() ?: return null
        val rank = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return State(baselineAt, rank)
    }

    suspend fun set(medicineId: String, baselineAt: Long, rank: Int) {
        context.stockWarningDataStore.edit { it[key(medicineId)] = "$baselineAt:$rank" }
    }

    suspend fun clear(medicineId: String) {
        context.stockWarningDataStore.edit { it.remove(key(medicineId)) }
    }
}