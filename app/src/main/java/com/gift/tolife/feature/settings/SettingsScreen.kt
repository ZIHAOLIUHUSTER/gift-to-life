package com.gift.tolife.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 本地编辑状态（不在 ViewModel 中，避免每次键入都触发更新）
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var tagModel by remember { mutableStateOf("") }
    var summaryModel by remember { mutableStateOf("") }
    var visionModel by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apiKey = uiState.settings.apiKey
        baseUrl = uiState.settings.baseUrl
        tagModel = uiState.settings.tagModel
        summaryModel = uiState.settings.summaryModel
        visionModel = uiState.settings.visionModel
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            snackbarHostState.showSnackbar("已保存")
            viewModel.clearSavedFlag()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI 配置卡片
            SettingsCard(title = "AI 配置") {
                var showKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "切换可见性"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API 地址") },
                    placeholder = { Text("https://api.deepseek.com") },
                    supportingText = { Text("只需填基础地址，程序自动补全 /v1/chat/completions") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel.updateApiKey(apiKey)
                        viewModel.updateBaseUrl(baseUrl)
                    },
                    modifier = Modifier.align(Alignment.End),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("保存")
                }
            }

            // 模型配置卡片
            SettingsCard(title = "模型") {
                // 标签模型
                ModelRow(
                    value = tagModel,
                    onValueChange = { tagModel = it },
                    label = "标签模型",
                    supportingText = "轻量模型即可，仅需文本分类",
                    testing = uiState.testingTag,
                    testResult = uiState.testResultTag,
                    onTest = viewModel::testTagModel
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 总结模型
                ModelRow(
                    value = summaryModel,
                    onValueChange = { summaryModel = it },
                    label = "总结模型",
                    supportingText = "需要较强文本理解力，建议推理模型",
                    testing = uiState.testingSummary,
                    testResult = uiState.testResultSummary,
                    onTest = viewModel::testSummaryModel
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 视觉模型
                ModelRow(
                    value = visionModel,
                    onValueChange = { visionModel = it },
                    label = "视觉模型",
                    supportingText = "用于识别纯图片记录",
                    testing = uiState.testingVision,
                    testResult = uiState.testResultVision,
                    onTest = viewModel::testVisionModel
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel.updateTagModel(tagModel)
                        viewModel.updateSummaryModel(summaryModel)
                        viewModel.updateVisionModel(visionModel)
                    },
                    modifier = Modifier.align(Alignment.End),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("保存")
                }
            }

            // 安全卡片（占位）
            SettingsCard(title = "安全") {
                Text(
                    "更多安全设置将在后续版本中添加",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ModelRow(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    testing: Boolean,
    testResult: String?,
    onTest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            supportingText = { Text(supportingText) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onTest,
                enabled = !testing,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "测试",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 测试结果显示在按钮下方
            if (testResult != null) {
                val isSuccess = testResult.startsWith("✓")
                Text(
                    testResult,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFE53935)
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
