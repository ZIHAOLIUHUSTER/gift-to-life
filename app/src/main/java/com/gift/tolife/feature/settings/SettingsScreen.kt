package com.gift.tolife.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
                var apiKey by remember(uiState.settings.apiKey) {
                    mutableStateOf(uiState.settings.apiKey)
                }
                var showKey by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                viewModel.updateApiKey(apiKey)
                            }
                        },
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

                var baseUrl by remember(uiState.settings.baseUrl) {
                    mutableStateOf(uiState.settings.baseUrl)
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API 地址") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                viewModel.updateBaseUrl(baseUrl)
                            }
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            }

            // 模型配置卡片
            SettingsCard(title = "模型") {
                var localTagModel by remember(uiState.settings.tagModel) {
                    mutableStateOf(uiState.settings.tagModel)
                }
                OutlinedTextField(
                    value = localTagModel,
                    onValueChange = { localTagModel = it },
                    label = { Text("标签模型") },
                    supportingText = { Text("用于自动生成标签") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                viewModel.updateTagModel(localTagModel)
                            }
                        },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                var localSummaryModel by remember(uiState.settings.summaryModel) {
                    mutableStateOf(uiState.settings.summaryModel)
                }
                OutlinedTextField(
                    value = localSummaryModel,
                    onValueChange = { localSummaryModel = it },
                    label = { Text("总结模型") },
                    supportingText = { Text("用于生成周/月总结") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                viewModel.updateSummaryModel(localSummaryModel)
                            }
                        },
                    singleLine = true
                )
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
