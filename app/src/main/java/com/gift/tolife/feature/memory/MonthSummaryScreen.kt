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
import com.gift.tolife.core.model.Entry
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSummaryScreen(
    onBack: () -> Unit,
    summaryVM: SummaryViewModel = hiltViewModel()
) {
    val state by summaryVM.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        summaryVM.events.collect { event ->
            when (event) {
                is SummaryEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val monthSummaries = state.monthSummaries
        .filter { it.summaryStart != null && it.summaryEnd != null }
        .filter { it.summaryEnd!! - it.summaryStart!! > 25L * 24 * 60 * 60 * 1000 }
        .sortedByDescending { it.summaryStart }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("月总结") },
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
            if (state.canGenerateMonth) {
                item(key = "generate") {
                    Button(
                        onClick = summaryVM::generateMonthSummary,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isGenerating,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (state.isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("总结上月")
                    }
                }
            }

            if (monthSummaries.isEmpty()) {
                item(key = "empty") {
                    Text("暂无月总结", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            items(monthSummaries, key = { it.id }) { entry ->
                SummaryCard(entry = entry)
            }
        }
    }
}
