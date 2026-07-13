package org.notifledger.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.notifledger.app.model.Posting
import org.notifledger.app.model.Source
import org.notifledger.app.model.Transaction
import org.notifledger.app.ui.components.TransactionForm
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val defaultAccount by viewModel.defaultAccount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick add") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        TransactionForm(
            initial = Transaction(
                date = LocalDate.now().toString(),
                payee = "",
                postings = listOf(
                    Posting(account = "expenses:unknown", amount = "", currency = "NOK"),
                    Posting(account = defaultAccount, amount = "", currency = "NOK"),
                ),
                source = Source.Manual,
            ),
            onSave = { tx ->
                viewModel.addTransaction(tx)
                onBack()
            },
            onCancel = onBack,
            modifier = Modifier.padding(padding),
        )
    }
}
