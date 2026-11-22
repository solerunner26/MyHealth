package com.solerunner.myhealth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.solerunner.myhealth.R
import com.solerunner.myhealth.design.theme.MyHealthTokens
import com.solerunner.myhealth.di.AppContainer
import com.solerunner.myhealth.domain.model.Profile
import com.solerunner.myhealth.ui.dashboard.DashboardScreen
import com.solerunner.myhealth.ui.doctor.DoctorSummaryScreen
import com.solerunner.myhealth.ui.importdoc.ImportScreen
import com.solerunner.myhealth.ui.insights.InsightsScreen
import com.solerunner.myhealth.ui.medicines.MedicinesScreen
import com.solerunner.myhealth.ui.navigation.Routes
import com.solerunner.myhealth.ui.navigation.topDestinations
import com.solerunner.myhealth.ui.profile.ProfileScreen
import com.solerunner.myhealth.ui.records.RecordsScreen
import com.solerunner.myhealth.ui.records.ReportDetailScreen
import com.solerunner.myhealth.ui.settings.SettingsScreen
import com.solerunner.myhealth.ui.treatments.TreatmentsScreen
import com.solerunner.myhealth.ui.trends.TrendDetailScreen
import com.solerunner.myhealth.ui.trends.TrendsScreen
import com.solerunner.myhealth.ui.vitals.VitalsScreen

/**
 * The signed-in shell: a bottom bar on phones, a navigation rail on wide screens, and one NavHost.
 * Detail routes (a report, a trend, import, settings…) hide the bar so the content owns the screen.
 */
@Composable
fun MainScaffold(container: AppContainer, profile: Profile) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBars = topDestinations.any { it.route == currentRoute }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Scaffold(
            bottomBar = {
                if (!wide && showBars) {
                    NavigationBar {
                        topDestinations.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = { navController.navigateTop(destination.route) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    NavigationRail {
                        Spacer(Modifier.height(MyHealthTokens.spacing.md))
                        topDestinations.forEach { destination ->
                            NavigationRailItem(
                                selected = currentRoute == destination.route,
                                onClick = { navController.navigateTop(destination.route) },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    }
                }
                AppNavHost(
                    container = container,
                    profile = profile,
                    navController = navController,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun NavHostController.navigateTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(container: AppContainer, profile: Profile, navController: NavHostController, modifier: Modifier) {
    NavHost(navController = navController, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            DashboardScreen(
                container = container,
                profile = profile,
                onNavigate = { navController.navigate(it) },
                onOpenReport = { id -> navController.navigate(Routes.reportDetail(id)) },
            )
        }
        composable(Routes.RECORDS) {
            RecordsScreen(
                container = container,
                onOpenReport = { id -> navController.navigate(Routes.reportDetail(id)) },
                onImport = { navController.navigate(Routes.IMPORT) },
            )
        }
        composable(
            Routes.REPORT_DETAIL,
            arguments = listOf(navArgument("reportId") { type = NavType.LongType }),
        ) { entry ->
            ReportDetailScreen(
                container = container,
                reportId = entry.arguments?.getLong("reportId") ?: 0L,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
            )
        }
        composable(Routes.IMPORT) {
            ImportScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.popBackStack()
                    navController.navigate(Routes.reportDetail(id))
                },
            )
        }
        composable(Routes.TRENDS) {
            TrendsScreen(container = container, onOpenTrend = { key -> navController.navigate(Routes.trendDetail(key)) })
        }
        composable(Routes.TREND_DETAIL) { entry ->
            TrendDetailScreen(
                container = container,
                analyteKey = entry.arguments?.getString("analyteKey") ?: "",
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.VITALS) { VitalsScreen(container = container) }
        composable(Routes.MEDICINES) { MedicinesScreen(container = container) }
        composable(Routes.TREATMENTS) { TreatmentsScreen(container = container) }
        composable(Routes.INSIGHTS) {
            InsightsScreen(
                container = container,
                onOpenReport = { id -> navController.navigate(Routes.reportDetail(id)) },
            )
        }
        composable(Routes.PROFILE) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = MyHealthTokens.spacing.screenPadding)) {
                    TextButton(onClick = { navController.popBackStack() }) { Text(stringResource(R.string.common_back)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Text(stringResource(R.string.nav_settings))
                    }
                }
                ProfileScreen(container = container)
            }
        }
        composable(Routes.SETTINGS) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = MyHealthTokens.spacing.screenPadding)) {
                    TextButton(onClick = { navController.popBackStack() }) { Text(stringResource(R.string.common_back)) }
                }
                SettingsScreen(container = container)
            }
        }
        composable(Routes.DOCTOR_SUMMARY) {
            DoctorSummaryScreen(container = container, onBack = { navController.popBackStack() })
        }
    }
}
