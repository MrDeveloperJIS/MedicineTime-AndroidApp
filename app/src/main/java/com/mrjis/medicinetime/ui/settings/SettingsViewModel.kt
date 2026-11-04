package com.mrjis.medicinetime.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrjis.medicinetime.AppContainer
import com.mrjis.medicinetime.data.prefs.AppLanguage
import com.mrjis.medicinetime.data.prefs.ThemeMode
import com.mrjis.medicinetime.update.UpdateResult
import com.mrjis.medicinetime.util.LocaleHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val version: String, val pageUrl: String) : UpdateUiState
    data object Failed : UpdateUiState
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val store = container.settingsStore

    val themeMode: StateFlow<ThemeMode> = store.themeMode
    val language: StateFlow<AppLanguage> = store.language
    val snoozeOptions: StateFlow<List<Int>> = store.snoozeOptions
    val missedWindowMinutes: StateFlow<Int> = store.missedWindowMinutes
    val ringtoneUri: StateFlow<String?> = store.ringtoneUri

    val currentVersion: String = container.updateChecker.currentVersion()

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = store.setThemeMode(mode)

    /** Saves the language and applies it. On Android 12 the screen must also be recreated by the caller. */
    fun setLanguage(language: AppLanguage) {
        store.setLanguage(language)
        LocaleHelper.apply(container.appContext, language)
    }

    fun setSnoozeOptions(minutes: Collection<Int>) = store.setSnoozeOptions(minutes)

    fun setMissedWindowMinutes(minutes: Int) = store.setMissedWindowMinutes(minutes)

    /** null = the system default alarm sound. */
    fun setRingtoneUri(uri: String?) = store.setRingtoneUri(uri)

    fun checkForUpdate() {
        if (_updateState.value == UpdateUiState.Checking) return
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            _updateState.value = when (val result = container.updateChecker.check()) {
                UpdateResult.UpToDate -> UpdateUiState.UpToDate
                UpdateResult.Failed -> UpdateUiState.Failed
                is UpdateResult.Available -> UpdateUiState.Available(result.version, result.pageUrl)
            }
        }
    }
}