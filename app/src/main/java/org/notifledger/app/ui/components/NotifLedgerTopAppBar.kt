package org.notifledger.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import org.notifledger.app.R

/**
 * Standard top bar for secondary screens: a title slot plus a back button using the
 * Lucide ArrowLeft. Extracted because six screens had the same navigationIcon block.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotifLedgerTopAppBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.back))
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier,
    )
}
