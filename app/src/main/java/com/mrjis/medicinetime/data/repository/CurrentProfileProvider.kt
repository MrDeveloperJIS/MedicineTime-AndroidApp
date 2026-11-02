package com.mrjis.medicinetime.data.repository

import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.data.prefs.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The profile currently shown in the app. Falls back to the first profile if the saved one
 * was deleted, and is null only when no profile exists.
 */
class CurrentProfileProvider(
    profiles: ProfileRepository,
    private val prefs: AppPreferences
) {
    val current: Flow<ProfileEntity?> =
        combine(profiles.observeAll(), prefs.currentProfileId) { list, savedId ->
            list.firstOrNull { it.id == savedId } ?: list.firstOrNull()
        }

    suspend fun select(id: String) = prefs.setCurrentProfileId(id)
}