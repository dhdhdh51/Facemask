package com.jawlinefit.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

object Routes {
    const val Gate = "gate"
    const val Onboarding = "onboarding"
    const val Login = "login"
    const val Home = "home"
    const val Camera = "camera"
    const val Progress = "progress"
    const val Routines = "routines"
    const val Routine = "routine/{id}"
    const val Session = "session/{id}"
    const val Nuskhe = "nuskhe"
    const val Tips = "tips"
    const val Premium = "premium"
    const val Reminders = "reminders"
    const val Settings = "settings"
    fun routine(id: String) = "routine/$id"
    fun session(id: String) = "session/$id"
}

private data class NavItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val primaryItems = listOf(
    NavItem(Routes.Home, "Home", Icons.Outlined.Home),
    NavItem(Routes.Routines, "Routines", Icons.Outlined.FitnessCenter),
    NavItem(Routes.Camera, "Scan", Icons.Outlined.CameraAlt),
    NavItem(Routes.Progress, "Progress", Icons.Outlined.PhotoLibrary),
    NavItem(Routes.Settings, "Settings", Icons.Outlined.Settings)
)

@Composable fun JawlineFitApp(viewModel: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val showBottom = primaryItems.any { it.route == route }
    LaunchedEffect(signedIn, route) {
        if (!signedIn && route !in setOf(Routes.Gate, Routes.Onboarding, Routes.Login)) nav.replaceAll(Routes.Login)
    }
    Scaffold(
        bottomBar = { if (showBottom) PrimaryNavigation(nav, route) }
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.Gate, modifier = Modifier) {
            composable(Routes.Gate) { GateScreen(viewModel, nav) }
            composable(Routes.Onboarding) { OnboardingScreen { language -> viewModel.completeOnboarding(language); nav.replaceAll(Routes.Login) } }
            composable(Routes.Login) { LoginScreen(viewModel, nav) }
            composable(Routes.Home) { HomeScreen(viewModel, nav, padding) }
            composable(Routes.Camera) { CameraScreen(viewModel, padding) }
            composable(Routes.Progress) { ProgressScreen(viewModel, padding) }
            composable(Routes.Routines) { RoutinesScreen(viewModel, nav, padding) }
            composable(Routes.Routine) { entry -> RoutineDetailScreen(entry.arguments?.getString("id").orEmpty(), viewModel, nav) }
            composable(Routes.Session) { entry -> RoutineSessionScreen(entry.arguments?.getString("id").orEmpty(), viewModel, nav) }
            composable(Routes.Nuskhe) { NuskheScreen(viewModel, nav) }
            composable(Routes.Tips) { TipsScreen(viewModel, nav) }
            composable(Routes.Premium) { PremiumScreen(viewModel, nav) }
            composable(Routes.Reminders) { RemindersScreen(viewModel, nav) }
            composable(Routes.Settings) { SettingsScreen(viewModel, nav, padding) }
        }
    }
}

@Composable private fun GateScreen(viewModel: AppViewModel, nav: NavHostController) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    LaunchedEffect(settings.onboardingComplete, signedIn) {
        val destination = when { !settings.onboardingComplete -> Routes.Onboarding; !signedIn -> Routes.Login; else -> Routes.Home }
        nav.replaceAll(destination)
    }
    FullPageLoading()
}

@Composable private fun PrimaryNavigation(nav: NavHostController, current: String?) {
    NavigationBar {
        primaryItems.forEach { item ->
            NavigationBarItem(
                selected = current == item.route,
                onClick = {
                    nav.navigate(item.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}

fun NavHostController.replaceAll(route: String) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}
