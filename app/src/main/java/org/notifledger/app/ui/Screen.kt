package org.notifledger.app.ui

/**
 * Represents a screen in the app's navigation.
 * Design §4 — each screen maps to a section of the design doc.
 */
sealed class Screen(val route: String) {
    data object Main : Screen("main")
    data object QuickAdd : Screen("quick_add")
    data object RawJournal : Screen("raw_journal")
    data object CategorizationRules : Screen("categorization_rules")
    data object NotificationSources : Screen("notification_sources")
    data object Logs : Screen("logs")
    data object Settings : Screen("settings")
}
