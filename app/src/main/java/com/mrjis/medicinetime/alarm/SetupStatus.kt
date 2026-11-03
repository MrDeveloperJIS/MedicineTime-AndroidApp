package com.mrjis.medicinetime.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi

/** Checks the settings that decide whether alarms can really ring, and opens the screens that fix them. */
object SetupStatus {

    /** Permission granted and notifications not switched off for the app. */
    fun notificationsOk(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    /** Always true on Android 13+ (USE_EXACT_ALARM is granted automatically). */
    fun exactAlarmsOk(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Only Android 14+ can deny the full-screen alarm screen. */
    fun fullScreenOk(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 34 ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun batteryOk(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Without these two, no reminder can ring at all. */
    fun criticalOk(context: Context): Boolean = notificationsOk(context) && exactAlarmsOk(context)

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun exactAlarmSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context))

    @RequiresApi(34)
    fun fullScreenSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context))

    fun batteryIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))

    /** Starts [intent]; if this phone has no such screen, opens the app's own settings page instead. */
    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
            } catch (_: Exception) {
                // Nothing more we can do.
            }
        }
    }

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")
}