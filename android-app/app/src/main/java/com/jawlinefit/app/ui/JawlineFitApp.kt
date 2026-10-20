package com.jawlinefit.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jawlinefit.app.R

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
    fun routine(id: Long) = "routine/$id"
    fun session(id: Long) = "session/$id"
}

private data class NavItem(val route: String, val label: Int, val icon: ImageVector)
private val primaryItems = listOf(
    NavItem(Routes.Home, R.string.home, Icons.Outlined.Home),
    NavItem(Routes.Routines, R.string.routines, Icons.Outlined.FitnessCenter),
    NavItem(Routes.Camera, R.string.scan, Icons.Outlined.CameraAlt),
    NavItem(Routes.Progress, R.string.progress, Icons.Outlined.PhotoLibrary),
    NavItem(Routes.Settings, R.string.settings, Icons.Outlined.Settings)
)

@Composable
fun JawlineFitApp(viewModel: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val showBottom = primaryItems.any { it.route == route }

    LaunchedEffect(signedIn, route) {
        if (!signedIn && route !in setOf(Routes.Gate, Routes.Onboarding, Routes.Login)) {
            nav.replaceAll(Routes.Login)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = { if (showBottom) PrimaryNavigation(nav, route) }
    ) { rootPadding ->
        NavHost(
            navController = nav,
            startDestination = Routes.Gate,
            modifier = Modifier.padding(rootPadding).consumeWindowInsets(rootPadding)
        ) {
            composable(Routes.Gate) { GateScreen(viewModel, nav) }
            composable(Routes.Onboarding) {
                OnboardingScreen { language, age ->
                    viewModel.completeOnboarding(language, age)
                    nav.replaceAll(Routes.Login)
                }
            }
            composable(Routes.Login) { LoginScreen(viewModel, nav) }
            composable(Routes.Home) { HomeScreen(viewModel, nav, PaddingValues(0.dp)) }
            composable(Routes.Camera) { CameraScreen(viewModel, PaddingValues(0.dp)) }
            composable(Routes.Progress) { ProgressScreen(viewModel, PaddingValues(0.dp)) }
            composable(Routes.Routines) { RoutinesScreen(viewModel, nav, PaddingValues(0.dp)) }
            composable(Routes.Routine) { entry ->
                RoutineDetailScreen(entry.arguments?.getString("id")?.toLongOrNull() ?: 0, viewModel, nav)
            }
            composable(Routes.Session) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0
                val authorized = remember(entry) { viewModel.consumeSessionAuthorization(id) }
                if (authorized) {
                    RoutineSessionScreen(id, viewModel, nav)
                } else {
                    LaunchedEffect(id) {
                        if (!nav.popBackStack()) nav.replaceAll(Routes.Routines)
                    }
                    FullPageLoading()
                }
            }
            composable(Routes.Nuskhe) { NuskheScreen(viewModel, nav) }
            composable(Routes.Tips) { TipsScreen(viewModel, nav) }
            composable(Routes.Premium) { PremiumScreen(viewModel, nav) }
            composable(Routes.Reminders) { RemindersScreen(viewModel, nav) }
            composable(Routes.Settings) { SettingsScreen(viewModel, nav, PaddingValues(0.dp)) }
        }
    }
}

@Composable
private fun GateScreen(viewModel: AppViewModel, nav: NavHostController) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    LaunchedEffect(settings.onboardingComplete, signedIn) {
        nav.replaceAll(
            when {
                !settings.onboardingComplete -> Routes.Onboarding
                !signedIn -> Routes.Login
                else -> Routes.Home
            }
        )
    }
    FullPageLoading()
}

@Composable
private fun PrimaryNavigation(nav: NavHostController, current: String?) {
    NavigationBar {
        primaryItems.forEach { item ->
            val label = stringResource(item.label)
            NavigationBarItem(
                selected = current == item.route,
                onClick = {
                    nav.navigate(item.route) {
                        popUpTo(Routes.Home) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, label) },
                label = { Text(label) }
            )
        }
    }
}

fun NavHostController.replaceAll(route: String) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}
