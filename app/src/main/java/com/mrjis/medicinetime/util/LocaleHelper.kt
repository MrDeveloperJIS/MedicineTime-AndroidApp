package com.mrjis.medicinetime.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.mrjis.medicinetime.data.prefs.AppLanguage
import com.mrjis.medicinetime.data.prefs.SettingsStore
import java.util.Locale

/**
 * Applies the in-app language (English / Bangla / follow the system).
 *
 *  - Android 13+ : the system keeps the app language ([LocaleManager]), so the choice also shows up in
 *                  the phone's own "App languages" settings. Nothing needs wrapping.
 *  - Android 12  : the saved choice is applied by wrapping the context in attachBaseContext.
 */
object LocaleHelper {

    /** Call from attachBaseContext of the Application, activities and services. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val language = SettingsStore.readLanguage(base)
        if (language == AppLanguage.SYSTEM) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag(language.tag)))
        return base.createConfigurationContext(configuration)
    }

    /**
     * Android 13+: hands the choice to the system, which restarts the screens in the new language.
     * Android 12: does nothing here. The caller recreates the activity, and [wrap] does the rest.
     */
    fun apply(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT < 33) return
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        manager.applicationLocales =
            if (language == AppLanguage.SYSTEM) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(language.tag)
    }

    /**
     * Android 13+: the language may also be changed in the phone's settings.
     * The system is the truth, so copy it into the app's setting.
     */
    fun syncFromSystem(context: Context, store: SettingsStore) {
        if (Build.VERSION.SDK_INT < 33) return
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        val locales = manager.applicationLocales
        val language = if (locales.isEmpty) {
            AppLanguage.SYSTEM
        } else {
            val tag = locales[0].language
            AppLanguage.entries.firstOrNull { it != AppLanguage.SYSTEM && it.tag == tag } ?: AppLanguage.SYSTEM
        }
        if (language != store.language.value) store.setLanguage(language)
    }
}