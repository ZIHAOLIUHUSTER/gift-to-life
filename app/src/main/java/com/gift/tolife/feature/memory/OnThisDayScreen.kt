package com.gift.tolife.feature.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gift.tolife.core.model.Entry
import com.gift.tolife.feature.record.EntryPreviewSheet
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnThisDayScreen(onBack: () -> Unit, viewModel: OnThisDayViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var previewEntry by remember { mutableStateOf<Entry?>(null) }

    val month = Calendar.getInstance().get(Calendar.MONTH) + 1
    val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("那年今日 · ${month}月${day}日") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        if (state.yearEntries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("这一天还没有记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.yearEntries.entries.sortedByDescending { it.key }.forEach { (year, entries) ->
                    item(key = "year_$year") {
                        Text(
                            "${year}年",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(entries, key = { it.id }) { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { previewEntry = entry },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(entry.content, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    formatTime(entry.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (previewEntry != null) {
        EntryPreviewSheet(
            entry = previewEntry!!,
            tags = emptyList(),
            onEdit = { previewEntry = null },
            onDismiss = { previewEntry = null },
            onDelete = {
                viewModel.deleteEntry(previewEntry!!.id)
                previewEntry = null
            }
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}