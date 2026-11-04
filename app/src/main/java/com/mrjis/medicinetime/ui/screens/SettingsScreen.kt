package com.mrjis.medicinetime.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.data.prefs.AppLanguage
import com.mrjis.medicinetime.data.prefs.SettingsStore
import com.mrjis.medicinetime.data.prefs.ThemeMode
import com.mrjis.medicinetime.ui.AppViewModelProvider
import com.mrjis.medicinetime.ui.settings.SettingsViewModel
import com.mrjis.medicinetime.ui.settings.UpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class SettingsDialog { THEME, LANGUAGE, SNOOZE, MISSED }

@Composable
fun SettingsScreen(
    onOpenSetup: () -> Unit,
    onOpenProfiles: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    val themeMode by viewModel.themeMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val snoozeOptions by viewModel.snoozeOptions.collectAsState()
    val missedWindow by viewModel.missedWindowMinutes.collectAsState()
    val ringtoneUri by viewModel.ringtoneUri.collectAsState()
    val updateState by viewModel.updateState.collectAsState()

    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            // "Default" (or nothing) means the system default alarm sound.
            viewModel.setRingtoneUri(
                if (picked == null || picked == Settings.System.DEFAULT_ALARM_ALERT_URI) null else picked.toString()
            )
        }
    }
    val pickerTitle = stringResource(R.string.ringtone_pick_title)

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )

        // ---- Appearance ----
        SectionTitle(R.string.settings_section_appearance)
        SettingsCard {
            SettingRow(
                title = stringResource(R.string.settings_theme),
                value = stringResource(themeMode.labelRes()),
                onClick = { dialog = SettingsDialog.THEME }
            )
            HorizontalDivider()
            SettingRow(
                title = stringResource(R.string.settings_language),
                value = stringResource(language.labelRes()),
                onClick = { dialog = SettingsDialog.LANGUAGE }
            )
        }

        // ---- Alarms ----
        SectionTitle(R.string.settings_section_alarms)
        SettingsCard {
            SettingRow(
                title = stringResource(R.string.settings_ringtone),
                value = ringtoneName(ringtoneUri),
                onClick = {
                    val existing = ringtoneUri?.let { Uri.parse(it) } ?: Settings.System.DEFAULT_ALARM_ALERT_URI
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, pickerTitle)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                    }
                    ringtonePicker.launch(intent)
                }
            )
            HorizontalDivider()
            SettingRow(
                title = stringResource(R.string.settings_snooze),
                value = snoozeOptions.map { durationLabel(it) }.joinToString(", "),
                onClick = { dialog = SettingsDialog.SNOOZE }
            )
            HorizontalDivider()
            SettingRow(
                title = stringResource(R.string.settings_missed),
                value = durationLabel(missedWindow),
                onClick = { dialog = SettingsDialog.MISSED }
            )
        }

        // ---- Family and reminders ----
        SectionTitle(R.string.settings_section_general)
        SettingsCard {
            SettingRow(
                title = stringResource(R.string.settings_profiles_title),
                value = stringResource(R.string.settings_profiles_hint),
                onClick = onOpenProfiles
            )
            HorizontalDivider()
            SettingRow(
                title = stringResource(R.string.setup_row_title),
                value = stringResource(R.string.setup_row_hint),
                onClick = onOpenSetup
            )
        }

        // ---- Updates ----
        SectionTitle(R.string.settings_section_updates)
        SettingsCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_version, viewModel.currentVersion),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val statusText = when (val state = updateState) {
                    UpdateUiState.Idle -> null
                    UpdateUiState.Checking -> stringResource(R.string.update_checking)
                    UpdateUiState.UpToDate -> stringResource(R.string.update_up_to_date)
                    UpdateUiState.Failed -> stringResource(R.string.update_failed)
                    is UpdateUiState.Available -> stringResource(R.string.update_available, state.version)
                }
                if (statusText != null) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = viewModel::checkForUpdate,
                    enabled = updateState != UpdateUiState.Checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.update_check))
                }

                (updateState as? UpdateUiState.Available)?.let { available ->
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(available.pageUrl)))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.update_download))
                    }
                }
            }
        }
    }

    // ---- Dialogs ----
    when (dialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = themeMode,
            label = { stringResource(it.labelRes()) },
            onSelect = { viewModel.setThemeMode(it) },
            onDismiss = { dialog = null }
        )

        SettingsDialog.LANGUAGE -> ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = AppLanguage.entries,
            selected = language,
            label = { stringResource(it.labelRes()) },
            onSelect = {
                if (it != language) {
                    viewModel.setLanguage(it)
                    // Android 13+ restarts the screens by itself. Android 12 needs a nudge.
                    if (Build.VERSION.SDK_INT < 33) activity?.recreate()
                }
            },
            onDismiss = { dialog = null }
        )

        SettingsDialog.MISSED -> ChoiceDialog(
            title = stringResource(R.string.settings_missed),
            options = SettingsStore.MISSED_WINDOW_CHOICES,
            selected = missedWindow,
            label = { durationLabel(it) },
            onSelect = { viewModel.setMissedWindowMinutes(it) },
            onDismiss = { dialog = null }
        )

        SettingsDialog.SNOOZE -> SnoozeDialog(
            selected = snoozeOptions,
            onSave = { viewModel.setSnoozeOptions(it) },
            onDismiss = { dialog = null }
        )

        null -> Unit
    }
}

// ---- Building blocks ---------------------------------------------------------------------------

@Composable
private fun SectionTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column { content() }
    }
}

@Composable
private fun SettingRow(title: String, value: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** A list of radio buttons in a dialog. Picking one applies it and closes the dialog. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(option)
                                    onDismiss()
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/** Pick 1 to 4 snooze choices. Save is disabled with nothing ticked; at 4 the rest are locked. */
@Composable
private fun SnoozeDialog(
    selected: List<Int>,
    onSave: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by remember { mutableStateOf(selected.toSet()) }
    val atMax = chosen.size >= SettingsStore.MAX_SNOOZE_OPTIONS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_snooze)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.snooze_dialog_hint, SettingsStore.MAX_SNOOZE_OPTIONS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SettingsStore.SNOOZE_CHOICES.forEach { minutes ->
                    val isChecked = minutes in chosen
                    val enabled = isChecked || !atMax
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(
                                value = isChecked,
                                enabled = enabled,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    chosen = if (checked) chosen + minutes else chosen - minutes
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isChecked, onCheckedChange = null, enabled = enabled)
                        Text(
                            text = durationLabel(minutes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(chosen.toList())
                    onDismiss()
                },
                enabled = chosen.isNotEmpty()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ---- Labels ------------------------------------------------------------------------------------

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@StringRes
private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.BANGLA -> R.string.language_bangla
}

/** 15 -> "15 min", 120 -> "2 hours". */
@Composable
private fun durationLabel(minutes: Int): String =
    if (minutes % 60 == 0) {
        val hours = minutes / 60
        pluralStringResource(R.plurals.alarm_snooze_hours, hours, hours)
    } else {
        stringResource(R.string.alarm_snooze_minutes, minutes)
    }

/** The name of the chosen alarm sound. null = the system default. */
@Composable
private fun ringtoneName(uriString: String?): String {
    val context = LocalContext.current
    val defaultName = stringResource(R.string.ringtone_default)
    val customName = stringResource(R.string.ringtone_custom)
    if (uriString == null) return defaultName
    val title by produceState(initialValue = customName, uriString) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                RingtoneManager.getRingtone(context, Uri.parse(uriString))?.getTitle(context)
            }.getOrNull()
        }?.takeIf { it.isNotBlank() } ?: customName
    }
    return title
}