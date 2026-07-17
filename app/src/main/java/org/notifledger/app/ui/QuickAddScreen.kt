package org.notifledger.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.notifledger.app.R
import org.notifledger.app.model.Posting
import org.notifledger.app.model.Source
import org.notifledger.app.model.Transaction
import org.notifledger.app.ui.components.NotifLedgerTopAppBar
import org.notifledger.app.ui.components.TransactionForm
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val defaultAccount by viewModel.defaultAccount.collectAsState()
    val defaultCurrency by viewModel.defaultCurrency.collectAsState()
    val payeeSuggestions by viewModel.existingPayees.collectAsState()
    val accountSuggestions by viewModel.existingAccounts.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            NotifLedgerTopAppBar(
                title = stringResource(R.string.quick_add),
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        TransactionForm(
            initial = Transaction(
                date = LocalDate.now().toString(),
                payee = "",
                postings = listOf(
                    Posting(account = "expenses:unknown", amount = "", currency = defaultCurrency),
                    Posting(account = defaultAccount, amount = "", currency = defaultCurrency),
                ),
                source = Source.Manual,
            ),
            payeeSuggestions = payeeSuggestions,
            accountSuggestions = accountSuggestions,
            defaultCurrency = defaultCurrency,
            onSave = { tx ->
                viewModel.addTransaction(tx)
                onBack()
            },
            onCancel = onBack,
            modifier = Modifier.padding(padding),
        )
    }
}
