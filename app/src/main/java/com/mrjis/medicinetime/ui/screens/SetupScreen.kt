package com.mrjis.medicinetime.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.alarm.SetupStatus

private data class SetupItem(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    @StringRes val buttonRes: Int,
    /** null = this one cannot be checked by the app (phone brand settings). */
    val ok: Boolean?,
    val onClick: () -> Unit
)

/** Checklist that makes sure alarms can ring: notifications, exact alarms, lock screen, battery. */
@Composable
fun SetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity

    // Bumped every time the user returns from a system settings page, so the ticks refresh.
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(activity) {
        val lifecycle = activity?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refresh++
        // Denied (or denied for good earlier): send the user to the settings page instead.
        if (!granted) SetupStatus.open(context, SetupStatus.notificationSettingsIntent(context))
    }

    val notificationsOk = remember(refresh) { SetupStatus.notificationsOk(context) }
    val exactOk = remember(refresh) { SetupStatus.exactAlarmsOk(context) }
    val fullScreenOk = remember(refresh) { SetupStatus.fullScreenOk(context) }
    val batteryOk = remember(refresh) { SetupStatus.batteryOk(context) }

    val items = listOf(
        SetupItem(
            R.string.setup_notifications_title,
            R.string.setup_notifications_desc,
            R.string.setup_notifications_button,
            notificationsOk
        ) {
            val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
            if (needsPermission) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                SetupStatus.open(context, SetupStatus.notificationSettingsIntent(context))
            }
        },
        SetupItem(
            R.string.setup_exact_title,
            R.string.setup_exact_desc,
            R.string.setup_exact_button,
            exactOk
        ) {
            SetupStatus.open(context, SetupStatus.exactAlarmSettingsIntent(context))
        },
        SetupItem(
            R.string.setup_fullscreen_title,
            R.string.setup_fullscreen_desc,
            R.string.setup_fullscreen_button,
            fullScreenOk
        ) {
            if (Build.VERSION.SDK_INT >= 34) {
                SetupStatus.open(context, SetupStatus.fullScreenSettingsIntent(context))
            }
        },
        SetupItem(
            R.string.setup_battery_title,
            R.string.setup_battery_desc,
            R.string.setup_battery_button,
            batteryOk
        ) {
            SetupStatus.open(context, SetupStatus.batteryIntent(context))
        },
        SetupItem(
            R.string.setup_oem_title,
            R.string.setup_oem_desc,
            R.string.setup_oem_button,
            null
        ) {
            SetupStatus.open(
                context,
                Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com"))
            )
        }
    )

    val allGood = items.all { it.ok != false }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.back))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.setup_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    if (allGood) R.string.setup_all_good else R.string.setup_needs_attention
                ),
                style = MaterialTheme.typography.titleMedium,
                color = if (allGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            items.forEach { SetupCard(it) }

            Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun SetupCard(item: SetupItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(item.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                when (item.ok) {
                    true -> Text(
                        text = stringResource(R.string.setup_status_ok),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    false -> Text(
                        text = stringResource(R.string.setup_status_todo),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    null -> Unit
                }
            }
            Text(
                text = stringResource(item.descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.ok != true) {
                Button(
                    onClick = item.onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(item.buttonRes))
                }
            }
        }
    }
}