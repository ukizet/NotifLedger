package org.notifledger.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun NotifLedgerNavGraph(
    navController: NavHostController,
    viewModel: MainViewModel,
) {
    NavHost(navController = navController, startDestination = Screen.Main.route) {
        composable(Screen.Main.route) {
            MainScreen(
                viewModel = viewModel,
                onNavigateToQuickAdd = { navController.navigate(Screen.QuickAdd.route) },
                onNavigateToRawJournal = { navController.navigate(Screen.RawJournal.route) },
                onNavigateToParserRules = { navController.navigate(Screen.ParserRules.route) },
                onNavigateToCategorizationRules = { navController.navigate(Screen.CategorizationRules.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(Screen.QuickAdd.route) {
            QuickAddScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.RawJournal.route) {
            RawJournalScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.ParserRules.route) {
            ParserRulesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.CategorizationRules.route) {
            CategorizationRulesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
