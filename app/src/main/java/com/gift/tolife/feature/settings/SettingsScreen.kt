package com.gift.tolife.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private enum class SettingsPage { MAIN, MODEL_CONFIG, DATA_MANAGE, RECYCLE_BIN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }

    when (currentPage) {
        SettingsPage.MAIN -> SettingsMainPage(onNavigate = { currentPage = it })
        SettingsPage.MODEL_CONFIG -> ModelConfigPage(viewModel, onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.DATA_MANAGE -> DataManagePage(viewModel, onBack = { currentPage = SettingsPage.MAIN })
        SettingsPage.RECYCLE_BIN -> RecycleBinPage(viewModel, onBack = { currentPage = SettingsPage.MAIN })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainPage(onNavigate: (SettingsPage) -> Unit) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SettingsButton("模型配置", onClick = { onNavigate(SettingsPage.MODEL_CONFIG) })
            SettingsButton("数据管理", onClick = { onNavigate(SettingsPage.DATA_MANAGE) })
            SettingsButton("回收站", onClick = { onNavigate(SettingsPage.RECYCLE_BIN) })
        }
    }
}

@Composable
private fun SettingsButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 8.dp)
            .height(52.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
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
                    trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "切换") } },
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
                    modifier = Modifier.align(Alignment.End), shape = RoundedCornerShape(8.dp)) { Text("保存") }
            }

            // 模型
            SettingsCard(title = "模型") {
                ModelRow(tagModel, { tagModel = it }, "标签模型", "轻量模型即可", uiState.testingTag, uiState.testResultTag, viewModel::testTagModel)
                Spacer(modifier = Modifier.height(12.dp))
                ModelRow(summaryModel, { summaryModel = it }, "总结模型", "需要较强文本理解力", uiState.testingSummary, uiState.testResultSummary, viewModel::testSummaryModel)
                Spacer(modifier = Modifier.height(12.dp))
                ModelRow(visionModel, { visionModel = it }, "视觉模型", "用于识别纯图片记录", uiState.testingVision, uiState.testResultVision, viewModel::testVisionModel)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { viewModel.updateTagModel(tagModel); viewModel.updateSummaryModel(summaryModel); viewModel.updateVisionModel(visionModel) },
                    modifier = Modifier.align(Alignment.End), shape = RoundedCornerShape(8.dp)) { Text("保存") }
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
                else Icon(Icons.Filled.CheckCircle, "测试", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun DataManagePage(viewModel: SettingsViewModel, onBack: () -> Unit) {
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground))
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsCard(title = "备份与恢复") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("gift_backup.gtlbackup") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("导出备份") }
                    OutlinedButton(onClick = { showImportConfirm = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("导入恢复") }
                }
            }

            if (showImportConfirm) {
                AlertDialog(onDismissRequest = { showImportConfirm = false },
                    title = { Text("导入数据") }, text = { Text("导入将清空所有现有记录，确定继续？") },
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
                actions = {
                    if (deletedEntries.isNotEmpty()) {
                        TextButton(onClick = { scope.launch { viewModel.permanentlyDeleteAll(); deletedEntries = emptyList() } }) {
                            Text("清空", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onBackground))
        }
    ) { innerPadding ->
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

private fun formatTime(ts: Long): String {
    val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ts))
}
