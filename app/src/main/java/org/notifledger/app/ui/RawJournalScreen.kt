package org.notifledger.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawJournalScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val journalPath by viewModel.journalPath.collectAsState()
    var content by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(journalPath) {
        if (journalPath.isNotBlank()) {
            content = withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(journalPath)
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                } catch (_: Exception) { "" }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Raw journal") },
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
        Text(
            text = content.ifBlank { "No journal file selected or file is empty." },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
}
