package com.gift.tolife.feature.memory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gift.tolife.core.model.Entry
import com.gift.tolife.core.ui.component.AppEmptyState
import com.gift.tolife.core.ui.component.PageHeader
import com.gift.tolife.feature.record.EntryCard
import com.gift.tolife.feature.record.EntryPreviewSheet
import java.util.Calendar
import kotlinx.coroutines.flow.collectLatest
import com.gift.tolife.core.ui.theme.PageGutter

/**
 * 那年今日。
 *
 * 2026-09-27：列表与记录页统一为「连续时间流」（透明行 + 发丝线），
 * 不再每条一张白卡；空状态改用统一的 AppEmptyState。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnThisDayScreen(onBack: () -> Unit, viewModel: OnThisDayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var previewEntry by remember { mutableStateOf<Entry?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is MemoryEntryEvent.Deleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "已移至回收站",
                        actionLabel = "撤销",
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreEntry(event.entryId)
                }
                is MemoryEntryEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    val month = Calendar.getInstance().get(Calendar.MONTH) + 1
    val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PageHeader(
                title = "那年今日",
                eyebrow = "${month}月${day}日",
                onBack = onBack,
                showDivider = true,
            )
        }
    ) { innerPadding ->
        if (state.yearEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                AppEmptyState(
                    title = "这一天还没有记录",
                    description = "明年今日再来看"
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                state.yearEntries.entries.sortedByDescending { it.key }.forEach { (year, entries) ->
                    item(key = "year_$year") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = PageGutter, end = PageGutter, top = 24.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = "$year",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = " 年",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            HorizontalDivider(
                                modifier = Modifier.weight(1f).padding(bottom = 10.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            )
                        }
                    }
                    items(entries, key = { it.id }) { entry ->
                        EntryCard(
                            entry = entry,
                            tags = emptyList(),
                            onClick = { previewEntry = entry },
                        )
                    }
                }
            }
        }
    }

    if (previewEntry != null) {
        EntryPreviewSheet(
            entry = previewEntry!!,
            tags = emptyList(),
            onDismiss = { previewEntry = null },
            onDelete = {
                previewEntry?.let { viewModel.deleteEntry(it.id) }
                previewEntry = null
            }
        )
    }
}
