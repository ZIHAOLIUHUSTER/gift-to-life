package com.gift.tolife.feature.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gift.tolife.R
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.ui.component.AppEmptyState
import com.gift.tolife.feature.record.EntryPreviewSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekSummaryScreen(
    onBack: () -> Unit,
    summaryVM: SummaryViewModel = hiltViewModel()
) {
    val state by summaryVM.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var previewEntry by remember { mutableStateOf<Entry?>(null) }

    LaunchedEffect(Unit) {
        summaryVM.events.collect { event ->
            when (event) {
                is SummaryEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val weekSummaries = state.weekSummaries
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
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回")
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
            item(key = "generate") {
                Button(
                    onClick = summaryVM::generateWeekSummary,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isGenerating,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (state.isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("总结上周")
                }
            }

            if (weekSummaries.isEmpty()) {
                item(key = "empty") {
                    AppEmptyState(
                        title = "暂无周总结",
                        description = "点击上方按钮生成上周总结"
                    )
                }
            }

            items(weekSummaries, key = { it.id }) { entry ->
                SummaryCard(entry = entry, onClick = { previewEntry = entry })
            }
        }
    }

    if (previewEntry != null) {
        EntryPreviewSheet(
            entry = previewEntry!!,
            tags = emptyList(),
            onDismiss = { previewEntry = null },
            onDelete = {
                summaryVM.deleteSummary(previewEntry!!.id)
                previewEntry = null
            }
        )
    }
}
