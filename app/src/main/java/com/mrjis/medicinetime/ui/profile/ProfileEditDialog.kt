package com.mrjis.medicinetime.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.domain.model.Meal
import com.mrjis.medicinetime.ui.components.AppTimePickerDialog
import com.mrjis.medicinetime.ui.components.labelRes
import com.mrjis.medicinetime.util.formatTime
import java.time.LocalTime

/** Add (initial == null) or edit a profile: name, avatar color and meal times. */
@Composable
fun ProfileEditDialog(
    initial: ProfileEntity?,
    onDismiss: () -> Unit,
    onSave: (ProfileDraft) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var color by rememberSaveable { mutableIntStateOf(initial?.avatarColor ?: ProfileColors.default) }
    val times = remember {
        mutableStateMapOf<Meal, LocalTime>().apply {
            val defaults = defaultMealTimes()
            Meal.entries.forEach { meal ->
                put(meal, initial?.mealTime(meal) ?: defaults.getValue(meal))
            }
        }
    }
    var pickingMeal by remember { mutableStateOf<Meal?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (initial == null) R.string.add_profile else R.string.edit_profile
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.profile_color),
                    style = MaterialTheme.typography.titleSmall
                )
                ColorPicker(selected = color, onSelect = { color = it })

                Text(
                    text = stringResource(R.string.meal_times),
                    style = MaterialTheme.typography.titleSmall
                )
                Meal.entries.forEach { meal ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(meal.labelRes()),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        OutlinedButton(
                            onClick = { pickingMeal = meal },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(formatTime(times.getValue(meal)))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(ProfileDraft(name.trim(), color, times.toMap())) }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    pickingMeal?.let { meal ->
        AppTimePickerDialog(
            initial = times.getValue(meal),
            onDismiss = { pickingMeal = null },
            onConfirm = {
                times[meal] = it
                pickingMeal = null
            }
        )
    }
}