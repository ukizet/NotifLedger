package org.notifledger.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.notifledger.app.R
import org.notifledger.app.journal.JournalEntry
import org.notifledger.app.stats.CategoryTotal
import org.notifledger.app.stats.PeriodTotal
import org.notifledger.app.stats.Statistics
import org.notifledger.app.ui.components.DonutChart
import org.notifledger.app.ui.components.DonutSlice
import org.notifledger.app.ui.components.NotifLedgerTopAppBar
import org.notifledger.app.ui.components.SpendingBarChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val accountOptions by viewModel.accountOptions.collectAsState()

    Scaffold(
        topBar = {
            NotifLedgerTopAppBar(
                title = stringResource(R.string.statistics),
                onBack = onBack,
            )
        },
    ) { padding ->
        StatisticsContent(
            entries = entries,
            accountOptions = accountOptions,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@Composable
private fun StatisticsContent(
    entries: List<JournalEntry>,
    accountOptions: List<String>,
    modifier: Modifier = Modifier,
) {
    var selectedAccounts by remember { mutableStateOf(emptySet<String>()) }
    var daysInput by remember { mutableStateOf("7") }
    var days by remember { mutableStateOf(7) }

    val accounts = selectedAccounts
    val currency = remember(entries, accounts, days) {
        Statistics.dominantCurrency(entries, accounts, days)
    }
    val categories = remember(entries, accounts, days, currency) {
        currency?.let { Statistics.spendingByCategory(entries, accounts, days, currency = it) }.orEmpty()
    }
    val periods = remember(entries, accounts, days, currency) {
        currency?.let { Statistics.spendingByPeriod(entries, accounts, days, currency = it) }.orEmpty()
    }
    val count = remember(entries, accounts, days) {
        Statistics.transactionCount(entries, accounts, days)
    }

    Column(modifier = modifier.padding(16.dp)) {
        AccountDropdown(
            selected = selectedAccounts,
            options = accountOptions,
            onSelectionChange = { selectedAccounts = it },
        )

        Spacer(Modifier.height(12.dp))

        DaysField(
            value = daysInput,
            onValueChange = { digits ->
                daysInput = digits
                digits.toIntOrNull()?.let { if (it >= 1) days = it }
            },
        )

        Spacer(Modifier.height(12.dp))

        StatisticsBody(
            count = count,
            currency = currency,
            categories = categories,
            periods = periods,
        )
    }
}

@Composable
private fun ColumnScope.StatisticsBody(
    count: Int,
    currency: String?,
    categories: List<CategoryTotal>,
    periods: List<PeriodTotal>,
) {
    Text(
        text = pluralStringResource(
            R.plurals.stats_transaction_count,
            count,
            count,
        ),
        style = MaterialTheme.typography.bodyMedium,
    )

    when {
        count == 0 -> HintText(stringResource(R.string.stats_empty))
        currency == null || categories.isEmpty() -> HintText(
            stringResource(R.string.stats_no_expenses),
        )
        else -> {
            HintText(stringResource(R.string.stats_in_currency, currency))
            Spacer(Modifier.height(8.dp))
            ChartsPager(categories = categories, periods = periods, currency = currency)
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnScope.ChartsPager(
    categories: List<CategoryTotal>,
    periods: List<PeriodTotal>,
    currency: String,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth().weight(1f),
    ) { page ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> DonutCard(categories = categories, currency = currency)
                1 -> BarsCard(periods = periods)
            }
        }
    }
    PagerDots(selectedPage = pagerState.currentPage, pageCount = 2)
}

@Composable
private fun PagerDots(
    selectedPage: Int,
    pageCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { page ->
            val color = if (selectedPage == page) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun DaysField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text ->
            onValueChange(text.filter(Char::isDigit).take(5))
        },
        label = { Text(stringResource(R.string.stats_days)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DonutCard(
    categories: List<CategoryTotal>,
    currency: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_spending_by_category),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            DonutChart(
                slices = categories.map {
                    DonutSlice(it.label, it.total, "${it.total.toPlainString()} $currency")
                },
                centerValue = categories.sumOf { it.total }.toPlainString(),
                centerLabel = currency,
                currency = currency,
            )
        }
    }
}

@Composable
private fun BarsCard(
    periods: List<PeriodTotal>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_spending_over_time),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            SpendingBarChart(periods = periods)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    selected: Set<String>,
    options: List<String>,
    onSelectionChange: (Set<String>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val allLabel = stringResource(R.string.stats_all_accounts)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = if (selected.isEmpty()) allLabel else selected.joinToString(", "),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.account)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                leadingIcon = { Checkbox(checked = selected.isEmpty(), onCheckedChange = null) },
                onClick = { onSelectionChange(emptySet()) },
            )
            options.forEach { account ->
                DropdownMenuItem(
                    text = { Text(account) },
                    leadingIcon = { Checkbox(checked = account in selected, onCheckedChange = null) },
                    onClick = { onSelectionChange(toggleAccount(selected, account)) },
                )
            }
        }
    }
}

private fun toggleAccount(selected: Set<String>, account: String): Set<String> =
    if (account in selected) selected - account else selected + account
