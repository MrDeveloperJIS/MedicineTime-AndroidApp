package com.mrjis.medicinetime.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrjis.medicinetime.AppContainer
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.domain.model.Meal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfilesViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.profileRepository
    private val provider = container.currentProfileProvider
    private val scheduler = container.alarmScheduler

    /** null until the first load finishes. */
    val profiles: StateFlow<List<ProfileEntity>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val current: StateFlow<ProfileEntity?> = provider.current
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(id: String) {
        viewModelScope.launch { provider.select(id) }
    }

    /** Creates a new profile (existing == null) or updates one. A new profile becomes the current one. */
    fun save(existing: ProfileEntity?, draft: ProfileDraft) {
        container.appScope.launch {
            if (existing == null) {
                val created = repository.create(draft.name, draft.avatarColor)
                repository.update(created.withDraft(draft))
                provider.select(created.id)
            } else {
                repository.update(existing.withDraft(draft))
                // Meal times may have changed, which moves meal-linked alarms.
                scheduler.scheduleNextById(existing.id)
            }
        }
    }

    /** The last remaining profile cannot be deleted. */
    fun delete(id: String) {
        if ((profiles.value?.size ?: 0) <= 1) return
        container.appScope.launch {
            repository.delete(id)
            // The profile is gone, so this cancels its dose and snooze alarms.
            scheduler.scheduleNextById(id)
        }
    }

    private fun ProfileEntity.withDraft(draft: ProfileDraft) = copy(
        name = draft.name,
        avatarColor = draft.avatarColor,
        breakfastTime = draft.mealTimes.getValue(Meal.BREAKFAST),
        lunchTime = draft.mealTimes.getValue(Meal.LUNCH),
        snacksTime = draft.mealTimes.getValue(Meal.SNACKS),
        dinnerTime = draft.mealTimes.getValue(Meal.DINNER)
    )
}