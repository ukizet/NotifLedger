package org.notifledger.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.ChartColumn
import com.composables.icons.lucide.List
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ScrollText
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Tags
import org.notifledger.app.R
import org.notifledger.app.ui.Screen

@Composable
fun MainBottomBar(
    navController: NavController,
    onSimulate: () -> Unit,
) {
    val currentRoute by navController.currentBackStackEntryAsState()
    val route = currentRoute?.destination?.route

    NavigationBar {
        BottomNavItem(
            icon = Lucide.Tags,
            label = stringResource(R.string.nav_rules),
            selected = route == Screen.CategorizationRules.route,
            onClick = { navController.navigate(Screen.CategorizationRules.route) },
        )
        BottomNavItem(
            icon = Lucide.List,
            label = stringResource(R.string.nav_journal),
            selected = route == Screen.RawJournal.route,
            onClick = { navController.navigate(Screen.RawJournal.route) },
        )
        BottomNavItem(
            icon = Lucide.ChartColumn,
            label = stringResource(R.string.nav_statistics),
            selected = route == Screen.Statistics.route,
            onClick = { navController.navigate(Screen.Statistics.route) },
        )
        BottomNavItem(
            icon = Lucide.ScrollText,
            label = stringResource(R.string.logs),
            selected = route == Screen.Logs.route,
            onClick = { navController.navigate(Screen.Logs.route) },
        )
        BottomNavItem(
            icon = Lucide.Bot,
            label = stringResource(R.string.nav_test),
            selected = false,
            onClick = onSimulate,
        )
        BottomNavItem(
            icon = Lucide.Settings,
            label = stringResource(R.string.settings),
            selected = route == Screen.Settings.route,
            onClick = { navController.navigate(Screen.Settings.route) },
        )
    }
}

@Composable
private fun RowScope.BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
        selected = selected,
        onClick = onClick,
    )
}
