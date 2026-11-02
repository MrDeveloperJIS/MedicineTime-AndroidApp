package com.mrjis.medicinetime.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mrjis.medicinetime.R
import com.mrjis.medicinetime.ui.components.ScreenPlaceholder

@Composable
fun MedicinesScreen(modifier: Modifier = Modifier) {
    ScreenPlaceholder(title = stringResource(R.string.nav_medicines), modifier = modifier)
}