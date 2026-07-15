package com.gift.tolife.feature.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gift.tolife.R
import com.gift.tolife.core.ui.UiTestTags
import java.util.Calendar

private enum class SettingsPage { MAIN, MODEL_CONFIG, DATA_MANAGE, RECYCLE_BIN, APPEARANCE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }
    val stats by viewModel.stats.collectAsState()
    val dataStats by viewModel.dataStats.collectAsState()

    BackHandler(enabled = currentPage != SettingsPage.MAIN) {
        currentPage = SettingsPage.MAIN
    }

    when (currentPage) {
        SettingsPage.MAIN -> SettingsMainPage(stats = stats, onRefreshStats = viewModel::refreshStats, onNavigate = { currentPage = it })
        SettingsPage.MODEL_CONFIG -> ModelConfigPage(viewModel, onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.DATA_MANAGE -> {
            LaunchedEffect(Unit) { viewModel.refreshDataStats() }
            DataManagePage(viewModel, dataStats = dataStats, onBack = { currentPage = SettingsPage.MAIN })
        }
        SettingsPage.RECYCLE_BIN -> RecycleBinPage(viewModel, onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.APPEARANCE -> AppearancePage(viewModel, onBack = { currentPage = SettingsPage.MAIN })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainPage(
    stats: SettingsViewModel.StatsData,
    onRefreshStats: () -> Unit,
    onNavigate: (SettingsPage) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            item {
                StatsCard(stats = stats, onRefresh = onRefreshStats)
            }
            item {
                SettingsSectionCard(
                    title = "模型配置",
                    description = "API Key、模型选择与连通性测试",
                    painter = painterResource(R.drawable.ic_settings),
                    onClick = { onNavigate(SettingsPage.MODEL_CONFIG) }
                )
            }
            item {
                SettingsSectionCard(
                    title = "数据管理",
                    description = "备份恢复、导入导出",
                    painter = painterResource(R.drawable.ic_edit_note),
                    onClick = { onNavigate(SettingsPage.DATA_MANAGE) }
                )
            }
            item {
                SettingsSectionCard(
                    title = "回收站",
                    description = "恢复或彻底删除已移除的记录",
                    painter = painterResource(R.drawable.ic_refresh),
                    onClick = { onNavigate(SettingsPage.RECYCLE_BIN) }
                )
            }
            item {
                SettingsSectionCard(
                    title = "外观",
                    description = "浅色、深色或跟随系统",
                    painter = painterResource(R.drawable.ic_auto_awesome),
                    onClick = { onNavigate(SettingsPage.APPEARANCE) }
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(title: String, description: String, painter: Painter, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painter, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ===== 模型配置子页 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelConfigPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var apiKey by remember { mutableStateOf(uiState.settings.apiKey) }
    var baseUrl by remember { mutableStateOf(uiState.settings.baseUrl) }
    var tagModel by remember { mutableStateOf(uiState.settings.tagModel) }
    var summaryModel by remember { mutableStateOf(uiState.settings.summaryModel) }
    var visionModel by remember { mutableStateOf(uiState.settings.visionModel) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!initialized) {
            apiKey = uiState.settings.apiKey
            baseUrl = uiState.settings.baseUrl
            tagModel = uiState.settings.tagModel
            summaryModel = uiState.settings.summaryModel
            visionModel = uiState.settings.visionModel
            initialized = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportModelConfig(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importModelConfig(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("模型配置") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI 配置
            SettingsCard(title = "AI 配置") {
                var showKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(painterResource(if (showKey) R.drawable.ic_visibility_off else R.drawable.ic_visibility), "切换") } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = baseUrl, onValueChange = { baseUrl = it },
                    label = { Text("API 地址") },
                    placeholder = { Text("https://api.deepseek.com") },
                    supportingText = { Text("只需填基础地址，程序自动补全 /v1/chat/completions") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { viewModel.updateApiKey(apiKey); viewModel.updateBaseUrl(baseUrl) },
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag(UiTestTags.SETTINGS_SAVE),
                    shape = RoundedCornerShape(8.dp)) { Text("保存") }
            }

            // 模型
            SettingsCard(title = "模型") {
                ModelRow(tagModel, { tagModel = it }, "标签模型", "轻量模型即可", uiState.testingTag, uiState.testResultTag) { viewModel.testTagModel(tagModel) }
                Spacer(modifier = Modifier.height(12.dp))
                ModelRow(summaryModel, { summaryModel = it }, "总结模型", "需要较强文本理解力", uiState.testingSummary, uiState.testResultSummary) { viewModel.testSummaryModel(summaryModel) }
                Spacer(modifier = Modifier.height(12.dp))
                ModelRow(visionModel, { visionModel = it }, "视觉模型", "用于识别纯图片记录", uiState.testingVision, uiState.testResultVision) { viewModel.testVisionModel(visionModel) }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { viewModel.updateTagModel(tagModel); viewModel.updateSummaryModel(summaryModel); viewModel.updateVisionModel(visionModel) },
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag(UiTestTags.SETTINGS_SAVE),
                    shape = RoundedCornerShape(8.dp)) { Text("保存") }
            }

            // 导入导出
            SettingsCard(title = "配置导入导出") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("model_config.json") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("导出配置") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("导入配置") }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(value: String, onValueChange: (String) -> Unit, label: String, supportingText: String,
    testing: Boolean, testResult: String?, onTest: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value, onValueChange, label = { Text(label) }, supportingText = { Text(supportingText) },
            modifier = Modifier.weight(1f), singleLine = true)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onTest, enabled = !testing, modifier = Modifier.padding(top = 4.dp)) {
                if (testing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("测试", style = MaterialTheme.typography.labelSmall)
            }
            if (testResult != null) {
                Text(testResult, style = MaterialTheme.typography.labelSmall,
                    color = if (testResult.startsWith("✓")) Color(0xFF4CAF50) else Color(0xFFE53935))
            }
        }
    }
}

// ===== 数据管理子页 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataManagePage(viewModel: SettingsViewModel, dataStats: SettingsViewModel.DataStats, onBack: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showImportConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) { is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message) }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        uri -> uri?.let { viewModel.exportData(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        uri -> uri?.let { viewModel.importData(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("数据管理") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground))
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // 统计卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("使用统计", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(20.dp))

                    // 第一行（核心数据）
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("${dataStats.totalEntries}", "条记录", isPrimary = true)
                        StatItem("${dataStats.usageDays}", "天使用", isPrimary = true)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    Spacer(Modifier.height(12.dp))

                    // 第二行
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem("${dataStats.imageCount}", "张图片")
                        StatItem("${dataStats.summaryCount}", "篇总结")
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                    Spacer(Modifier.height(12.dp))

                    // 第三行
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatItem(formatSize(dataStats.imageSizeBytes), "图片占用")
                        StatItem(String.format("%.1f 条/天", dataStats.dailyAvg), "日均记录")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard(title = "备份与恢复") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("gift_backup.gtlbackup") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("导出备份") }
                    OutlinedButton(onClick = { showImportConfirm = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("导入恢复") }
                }
            }

            if (showImportConfirm) {
                AlertDialog(onDismissRequest = { showImportConfirm = false },
                    title = { Text("导入数据") }, text = { Text("导入将清空并替换所有现有记录，确定继续？") },
                    confirmButton = { TextButton(onClick = { showImportConfirm = false; importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }) { Text("确定", color = MaterialTheme.colorScheme.error) } },
                    dismissButton = { TextButton(onClick = { showImportConfirm = false }) { Text("取消") } })
            }
        }
    }
}

// ===== 回收站子页 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecycleBinPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    var deletedEntries by remember { mutableStateOf<List<com.gift.tolife.core.model.Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        deletedEntries = viewModel.getDeletedEntries()
        loading = false
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) { is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("回收站") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "返回") } },
                actions = {
                    if (deletedEntries.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.testTag(UiTestTags.RECYCLE_CLEAR)
                        ) {
                            Text("清空", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground))
        }
    ) { innerPadding ->
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("清空回收站") },
                text = { Text("将永久删除 ${deletedEntries.size} 条记录，不可恢复。确定清空？") },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            viewModel.permanentlyDeleteAll()
                            deletedEntries = emptyList()
                            showClearConfirm = false
                        }
                    }) { Text("清空", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
                }
            )
        }

        if (deletedEntries.isEmpty() && !loading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("回收站为空", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(innerPadding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(deletedEntries, key = { it.id }) { entry ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.content.take(40) + if (entry.content.length > 40) "..." else "", style = MaterialTheme.typography.bodySmall)
                                Text(formatTime(entry.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { scope.launch { viewModel.restoreEntry(entry.id); deletedEntries = deletedEntries.filter { it.id != entry.id } } }) { Text("恢复") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ===== 外观子页 =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearancePage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTheme = uiState.settings.themeMode

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "system" to "跟随系统",
                "light" to "浅色",
                "dark" to "深色"
            ).forEach { (value, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateThemeMode(value) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentTheme == value,
                        onClick = { viewModel.updateThemeMode(value) }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: SettingsViewModel.StatsData, onRefresh: () -> Unit) {
    LaunchedEffect(Unit) { onRefresh() }

    val cal = java.util.Calendar.getInstance()
    val monthLabel = "${cal.get(java.util.Calendar.MONTH) + 1}月统计"

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(monthLabel, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Spacer(Modifier.height(16.dp))

            // 数据行
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem("${stats.monthlyCount}", "条记录")
                StatItem("${stats.streakCount}", "连续天数")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
            Spacer(Modifier.height(16.dp))

            // 热力图（多排）
            if (stats.dailyCounts.isNotEmpty()) {
                val daysInMonth = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                val firstDayCal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.DAY_OF_MONTH, 1)
                }
                val firstDayOfWeek = firstDayCal.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 周日=0
                val totalCells = firstDayOfWeek + daysInMonth
                val cols = 7
                val rows = (totalCells + cols - 1) / cols

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (row in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            for (col in 0 until cols) {
                                val cellIndex = row * cols + col
                                val day = cellIndex - firstDayOfWeek + 1
                                if (day in 1..daysInMonth) {
                                    val count = stats.dailyCounts[day] ?: 0
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(18.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(
                                                when {
                                                    count == 0 -> MaterialTheme.colorScheme.surface
                                                    count == 1 -> Color(0xFFC8E6C9)
                                                    count in 2..3 -> Color(0xFF81C784)
                                                    count in 4..6 -> Color(0xFF4CAF50)
                                                    else -> Color(0xFF2E7D32)
                                                }
                                            )
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, isPrimary: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = if (isPrimary) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(ts: Long): String {
    val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ts))
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
