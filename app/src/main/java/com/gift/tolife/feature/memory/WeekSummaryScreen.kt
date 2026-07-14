package com.gift.tolife.feature.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gift.tolife.core.common.TimeUtil
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.model.EntryType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekSummaryScreen(
    onBack: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MemoryEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val weekSummaries = uiState.summaryEntries
        .filter { it.summaryStart != null && it.summaryEnd != null }
        .filter { it.summaryEnd!! - it.summaryStart!! < 8L * 24 * 60 * 60 * 1000 }
        .sortedByDescending { it.summaryStart }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("周总结") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.canGenerateWeek) {
                item(key = "generate") {
                    Button(
                        onClick = viewModel::generateWeekSummary,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isGeneratingSummary,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (uiState.isGeneratingSummary) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("总结上周")
                    }
                }
            }

            if (weekSummaries.isEmpty()) {
                item(key = "empty") {
                    Text("暂无周总结", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(weekSummaries, key = { it.id }) { entry ->
                SummaryCard(entry = entry)
            }
        }
    }
}
