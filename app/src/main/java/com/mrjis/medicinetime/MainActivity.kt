package com.mrjis.medicinetime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.mrjis.medicinetime.alarm.AlarmNotifications
import com.mrjis.medicinetime.alarm.StockNotifications
import com.mrjis.medicinetime.ui.MedicineTimeApp
import com.mrjis.medicinetime.ui.theme.SettingsTheme
import com.mrjis.medicinetime.util.LocaleHelper

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MedicineTimeApplication).container
        // The language may have been changed in the phone's settings (Android 13+).
        LocaleHelper.syncFromSystem(this, container.settingsStore)
        // Re-creating a channel only refreshes its name and description, which follow the language.
        AlarmNotifications.createChannels(this)
        StockNotifications.createChannel(this)

        requestNotificationPermissionIfNeeded()
        setContent {
            SettingsTheme(container.settingsStore) {
                MedicineTimeApp()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}