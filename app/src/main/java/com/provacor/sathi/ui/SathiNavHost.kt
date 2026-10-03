package com.provacor.sathi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.provacor.sathi.ui.home.HomeScreen
import com.provacor.sathi.ui.permissions.PermissionsScreen
import com.provacor.sathi.ui.settings.SettingsScreen
import com.provacor.sathi.ui.settings.SettingsViewModel

private object Routes {
    const val HOME = "home"
    const val PERMISSIONS = "permissions"
    const val SETTINGS = "settings"
}

@Composable
fun SathiNavHost(settingsVm: SettingsViewModel = viewModel()) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val loaded = settings
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Wait for stored settings so first-run onboarding is decided once, without a flash.
        if (loaded != null) AppNav(settingsVm, startAtHome = loaded.onboardingDone)
    }
}

@Composable
private fun AppNav(settingsVm: SettingsViewModel, startAtHome: Boolean) {
    val nav = rememberNavController()
    val start = remember { if (startAtHome) Routes.HOME else Routes.PERMISSIONS }
    NavHost(navController = nav, startDestination = start) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
            )
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(settingsVm, onDone = {
                settingsVm.setOnboardingDone()
                nav.leavePermissions()
            })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(settingsVm, onBack = { nav.popBackStack() })
        }
    }
}

private fun NavHostController.leavePermissions() {
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(Routes.HOME) { popUpTo(Routes.PERMISSIONS) { inclusive = true } }
    }
}
