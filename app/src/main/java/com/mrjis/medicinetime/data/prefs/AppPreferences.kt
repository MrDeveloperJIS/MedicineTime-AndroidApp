package com.mrjis.medicinetime.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Small app settings kept in DataStore. More (theme, language, snooze) arrive in M6. */
class AppPreferences(private val context: Context) {

    private val currentProfileKey = stringPreferencesKey("current_profile_id")

    val currentProfileId: Flow<String?> =
        context.settingsDataStore.data.map { it[currentProfileKey] }

    suspend fun setCurrentProfileId(id: String) {
        context.settingsDataStore.edit { it[currentProfileKey] = id }
    }
}