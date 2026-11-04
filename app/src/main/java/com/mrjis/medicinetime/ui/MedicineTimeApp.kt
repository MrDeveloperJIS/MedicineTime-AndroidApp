package com.mrjis.medicinetime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mrjis.medicinetime.alarm.SetupStatus
import com.mrjis.medicinetime.data.local.entity.ProfileEntity
import com.mrjis.medicinetime.ui.navigation.Routes
import com.mrjis.medicinetime.ui.navigation.Screen
import com.mrjis.medicinetime.ui.profile.ProfileDraft
import com.mrjis.medicinetime.ui.profile.ProfilesViewModel
import com.mrjis.medicinetime.ui.screens.FirstProfileScreen
import com.mrjis.medicinetime.ui.screens.HistoryScreen
import com.mrjis.medicinetime.ui.screens.MedicineEditScreen
import com.mrjis.medicinetime.ui.screens.MedicinesScreen
import com.mrjis.medicinetime.ui.screens.ProfilesScreen
import com.mrjis.medicinetime.ui.screens.SettingsScreen
import com.mrjis.medicinetime.ui.screens.SetupScreen
import com.mrjis.medicinetime.ui.screens.TodayScreen

@Composable
fun MedicineTimeApp() {
    // Activity-scoped, so every screen shares the same profile state.
    val profilesViewModel: ProfilesViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val profiles by profilesViewModel.profiles.collectAsState()
    val current by profilesViewModel.current.collectAsState()
    val profileList = profiles

    when {
        // Still loading from the database.
        profileList == null -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )

        // First launch: no profile yet.
        profileList.isEmpty() -> FirstProfileScreen(
            onCreate = { name, color -> profilesViewModel.save(null, ProfileDraft(name, color)) }
        )

        else -> MainContent(
            profiles = profileList,
            current = current,
            profilesViewModel = profilesViewModel
        )
    }
}

@Composable
private fun MainContent(
    profiles: List<ProfileEntity>,
    current: ProfileEntity?,
    profilesViewModel: ProfilesViewModel
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = Screen.entries.any { it.route == currentRoute }

    // Once per app start: if reminders cannot work yet, open the setup checklist.
    var setupChecked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!setupChecked) {
            setupChecked = true
            if (!SetupStatus.criticalOk(context)) navController.navigate(Routes.SETUP)
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Screen.entries.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(screen.iconRes),
                                    contentDescription = null
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(screen.labelRes),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Today.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Today.route) {
                TodayScreen(
                    currentProfile = current,
                    profiles = profiles,
                    onSelectProfile = profilesViewModel::select
                )
            }

            composable(Screen.Medicines.route) {
                MedicinesScreen(
                    currentProfile = current,
                    profiles = profiles,
                    onSelectProfile = profilesViewModel::select,
                    onAddMedicine = { navController.navigate(Routes.medicineEdit()) },
                    onOpenMedicine = { id -> navController.navigate(Routes.medicineEdit(id)) }
                )
            }

            composable(Screen.History.route) {
                HistoryScreen(
                    currentProfile = current,
                    profiles = profiles,
                    onSelectProfile = profilesViewModel::select
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onOpenSetup = { navController.navigate(Routes.SETUP) },
                    onOpenProfiles = { navController.navigate(Routes.PROFILES) }
                )
            }

            composable(Routes.SETUP) {
                SetupScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.PROFILES) {
                ProfilesScreen(
                    profiles = profiles,
                    currentId = current?.id,
                    onBack = { navController.popBackStack() },
                    onSelect = profilesViewModel::select,
                    onSave = profilesViewModel::save,
                    onDelete = profilesViewModel::delete
                )
            }

            // Add (no id) or edit (id) a medicine.
            composable(
                route = Routes.MEDICINE_EDIT_PATTERN,
                arguments = listOf(
                    navArgument(Routes.MEDICINE_ID_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                MedicineEditScreen(onClose = { navController.popBackStack() })
            }
        }
    }
}