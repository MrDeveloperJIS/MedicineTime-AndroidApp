package com.mrjis.medicinetime.data.prefs

import android.content.Context
import com.mrjis.medicinetime.domain.dose.DoseRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    BANGLA("bn");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == (tag ?: "") } ?: SYSTEM
    }
}

/**
 * User settings (M6). Kept in SharedPreferences, not DataStore, on purpose:
 * the alarm service, receivers and activities need to read them instantly, with no waiting.
 * Every setter saves and updates the matching flow, so screens update by themselves.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- Theme -----------------------------------------------------------------------------

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null).orEmpty()) }
            .getOrDefault(ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    // ---- Language --------------------------------------------------------------------------

    private val _language = MutableStateFlow(readLanguage(context))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    /** Only saves the choice. [com.mrjis.medicinetime.util.LocaleHelper] applies it. */
    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.tag).apply()
        _language.value = language
    }

    // ---- Snooze ----------------------------------------------------------------------------

    private val _snoozeOptions = MutableStateFlow(readSnoozeOptions())

    /** Snooze buttons on the alarm screen, smallest first. Always 1 to [MAX_SNOOZE_OPTIONS] values. */
    val snoozeOptions: StateFlow<List<Int>> = _snoozeOptions.asStateFlow()

    /** The snooze button on the notification uses the smallest option. */
    val defaultSnoozeMinutes: Int get() = _snoozeOptions.value.first()

    /** Ignored when the list has no valid value, so there is always at least one snooze button. */
    fun setSnoozeOptions(minutes: Collection<Int>) {
        val clean = minutes
            .filter { it in SNOOZE_CHOICES }
            .distinct()
            .sorted()
            .take(MAX_SNOOZE_OPTIONS)
        if (clean.isEmpty()) return
        prefs.edit().putString(KEY_SNOOZE, clean.joinToString(",")).apply()
        _snoozeOptions.value = clean
    }

    private fun readSnoozeOptions(): List<Int> {
        val saved = prefs.getString(KEY_SNOOZE, null)
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { it in SNOOZE_CHOICES }
            ?.distinct()
            ?.sorted()
            .orEmpty()
        return saved.ifEmpty { DEFAULT_SNOOZE_OPTIONS }
    }

    // ---- Missed window ---------------------------------------------------------------------

    private val _missedWindowMinutes = MutableStateFlow(
        prefs.getInt(KEY_MISSED_WINDOW, DEFAULT_MISSED_WINDOW_MINUTES)
            .takeIf { it in MISSED_WINDOW_CHOICES } ?: DEFAULT_MISSED_WINDOW_MINUTES
    )

    /** How long a dose may wait for an answer before it counts as missed. */
    val missedWindowMinutes: StateFlow<Int> = _missedWindowMinutes.asStateFlow()

    fun setMissedWindowMinutes(minutes: Int) {
        if (minutes !in MISSED_WINDOW_CHOICES) return
        prefs.edit().putInt(KEY_MISSED_WINDOW, minutes).apply()
        _missedWindowMinutes.value = minutes
        DoseRules.missedWindowMillis = minutes * 60_000L
    }

    // ---- Ringtone --------------------------------------------------------------------------

    private val _ringtoneUri = MutableStateFlow(prefs.getString(KEY_RINGTONE, null))

    /** Uri string of the chosen alarm sound. null = the system default alarm sound. */
    val ringtoneUri: StateFlow<String?> = _ringtoneUri.asStateFlow()

    fun setRingtoneUri(uri: String?) {
        prefs.edit().apply {
            if (uri == null) remove(KEY_RINGTONE) else putString(KEY_RINGTONE, uri)
        }.apply()
        _ringtoneUri.value = uri
    }

    init {
        // The missed-dose rules read the window from DoseRules, so hand it over at start.
        DoseRules.missedWindowMillis = _missedWindowMinutes.value * 60_000L
    }

    companion object {
        const val FILE = "settings_store"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SNOOZE = "snooze_options"
        private const val KEY_MISSED_WINDOW = "missed_window_minutes"
        private const val KEY_RINGTONE = "ringtone_uri"

        val SNOOZE_CHOICES = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
        val DEFAULT_SNOOZE_OPTIONS = listOf(15, 30, 60)
        const val MAX_SNOOZE_OPTIONS = 4

        val MISSED_WINDOW_CHOICES = listOf(30, 60, 120, 180, 240)
        const val DEFAULT_MISSED_WINDOW_MINUTES = 120

        /**
         * Reads the saved language without a [SettingsStore] instance.
         * Needed very early, in attachBaseContext, before the app container exists.
         */
        fun readLanguage(context: Context): AppLanguage =
            AppLanguage.fromTag(
                context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, "")
            )
    }
}