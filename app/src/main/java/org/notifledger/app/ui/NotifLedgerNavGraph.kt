package org.notifledger.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun NotifLedgerNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Main.route) {
        composable(Screen.Main.route) { MainDestination(navController) }
        composable(Screen.QuickAdd.route) { QuickAddDestination(navController) }
        composable(Screen.Logs.route) { LogsDestination(navController) }
        composable(Screen.RawJournal.route) { RawJournalDestination(navController) }
        composable(Screen.CategorizationRules.route) { CategorizationRulesDestination(navController) }
        composable(Screen.NotificationSources.route) { NotificationSourcesDestination(navController) }
        composable(Screen.Settings.route) { SettingsDestination(navController) }
        composable(Screen.Statistics.route) { StatisticsDestination(navController) }
    }
}

@Composable
private fun MainDestination(navController: NavHostController) {
    val viewModel: MainViewModel = viewModel()
    MainScreen(
        viewModel = viewModel,
        navController = navController,
        onNavigateToQuickAdd = { navController.navigate(Screen.QuickAdd.route) },
        onNavigateToNotificationSources = { navController.navigate(Screen.NotificationSources.route) },
        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
    )
}

@Composable
private fun QuickAddDestination(navController: NavHostController) {
    val viewModel: QuickAddViewModel = viewModel()
    QuickAddScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
}

@Composable
private fun LogsDestination(navController: NavHostController) {
    val viewModel: LogsViewModel = viewModel()
    LogsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
}

@Composable
private fun RawJournalDestination(navController: NavHostController) {
    val viewModel: RawJournalViewModel = viewModel()
    RawJournalScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
}

@Composable
private fun CategorizationRulesDestination(navController: NavHostController) {
    val viewModel: CategorizationRulesViewModel = viewModel()
    CategorizationRulesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
}

@Composable
private fun NotificationSourcesDestination(navController: NavHostController) {
    val viewModel: NotificationSourcesViewModel = viewModel()
    NotificationSourcesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
}

@Composable
private fun SettingsDestination(navController: NavHostController) {
    val viewModel: SettingsViewModel = viewModel()
    SettingsScreen(
        viewModel = viewModel,
        onBack = { navController.popBackStack() },
        onNavigateToSources = { navController.navigate(Screen.NotificationSources.route) },
    )
}

@Composable
private fun StatisticsDestination(navController: NavHostController) {
    val viewModel: StatisticsViewModel = viewModel()
    StatisticsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
}
