package com.dailymate.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailymate.app.data.RecognitionLanguages
import com.dailymate.app.data.RecordingModes
import com.dailymate.app.data.SummaryStyles

/**
 * 主界面Compose UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onViewRecords: () -> Unit,
    onGenerateSummary: () -> Unit
) {
    val appConfig by viewModel.appConfig.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    // 显示Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is MainViewModel.UiState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetUiState()
            }
            is MainViewModel.UiState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetUiState()
            }
            else -> {}
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("DailyMate") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 服务状态卡片
            ServiceStatusCard(
                isRunning = isServiceRunning,
                onStartService = onStartService,
                onStopService = onStopService
            )
            
            // 模型配置卡片
            ModelConfigCard(
                appConfig = appConfig,
                viewModel = viewModel,
                onBaseUrlChange = { viewModel.updateApiBaseUrl(it) },
                onApiKeyChange = { viewModel.updateApiKey(it) },
                onModelNameChange = { viewModel.updateModelName(it) }
            )
            
            // 配置设置卡片
            ConfigurationCard(
                appConfig = appConfig,
                viewModel = viewModel,
                onSummaryStyleChange = { viewModel.updateSummaryStyle(it) },
                onStoragePathChange = { viewModel.updateStoragePath(it) },
                onAutoStartChange = { viewModel.updateAutoStartService(it) }
            )
            
            // 操作按钮卡片
            ActionButtonsCard(
                onViewRecords = onViewRecords,
                onGenerateSummary = onGenerateSummary
            )
        }
    }
}

/**
 * 服务状态卡片
 */
@Composable
fun ServiceStatusCard(
    isRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "服务控制",
                style = MaterialTheme.typography.titleMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRunning) "服务运行中" else "服务已停止",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                if (isRunning) {
                    FilledTonalButton(onClick = onStopService) {
                        Text("停止监听")
                    }
                } else {
                    Button(onClick = onStartService) {
                        Text("启动监听")
                    }
                }
            }
        }
    }
}

/**
 * 模型配置卡片
 */
@Composable
fun ModelConfigCard(
    appConfig: com.dailymate.app.data.AppConfig,
    viewModel: MainViewModel,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelNameChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "模型配置",
                style = MaterialTheme.typography.titleMedium
            )
            
            // API基础URL输入
            OutlinedTextField(
                value = appConfig.apiBaseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text("API地址") },
                placeholder = { Text("https://api.openai.com/v1/chat/completions") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("API的完整URL地址") }
            )
            
            // API密钥输入
            OutlinedTextField(
                value = appConfig.apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API密钥") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("用于身份验证") }
            )
            
            // 模型名称输入
            OutlinedTextField(
                value = appConfig.modelName,
                onValueChange = onModelNameChange,
                label = { Text("模型名称") },
                placeholder = { Text("gpt-3.5-turbo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text("如：gpt-3.5-turbo, gpt-4等") }
            )
        }
    }
}

/**
 * 配置设置卡片
 */
@Composable
fun ConfigurationCard(
    appConfig: com.dailymate.app.data.AppConfig,
    viewModel: MainViewModel,
    onSummaryStyleChange: (String) -> Unit,
    onStoragePathChange: (String) -> Unit,
    onAutoStartChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "应用设置",
                style = MaterialTheme.typography.titleMedium
            )
            
            // 录制模式选择
            RecordingModeDropdown(
                selectedMode = appConfig.recordingMode,
                onModeSelected = { viewModel.updateRecordingMode(it) }
            )
            
            // 语音识别语言选择
            RecognitionLanguageDropdown(
                selectedLanguage = appConfig.recognitionLanguage,
                onLanguageSelected = { viewModel.updateRecognitionLanguage(it) }
            )
            
            // 总结风格选择
            SummaryStyleDropdown(
                selectedStyle = appConfig.summaryStyle,
                onStyleSelected = onSummaryStyleChange
            )
            
            // 自定义提示词输入框（仅在选择"自定义"时显示）
            if (appConfig.summaryStyle == SummaryStyles.CUSTOM) {
                OutlinedTextField(
                    value = appConfig.customPrompt,
                    onValueChange = { viewModel.updateCustomPrompt(it) },
                    label = { Text("自定义提示词") },
                    placeholder = { Text("请输入自定义的总结提示词...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    supportingText = { 
                        Text("例如：请用幽默风趣的语言总结今天的活动，重点关注有趣的细节") 
                    }
                )
            }
            
            // 存储路径输入
            OutlinedTextField(
                value = appConfig.storagePath,
                onValueChange = onStoragePathChange,
                label = { Text("存储路径") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // 自动启动开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("开机自动启动服务")
                Switch(
                    checked = appConfig.autoStartService,
                    onCheckedChange = onAutoStartChange
                )
            }
        }
    }
}


/**
 * 录制模式下拉菜单
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingModeDropdown(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val modeName = RecordingModes.options[selectedMode] ?: selectedMode
    val modeDescription = RecordingModes.descriptions[selectedMode] ?: ""
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = modeName,
            onValueChange = {},
            readOnly = true,
            label = { Text("录制模式") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            supportingText = { Text(modeDescription) }
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            RecordingModes.options.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { 
                        Column {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                RecordingModes.descriptions[code] ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onModeSelected(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 语音识别语言下拉菜单
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionLanguageDropdown(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val languageName = RecognitionLanguages.options[selectedLanguage] ?: selectedLanguage
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = languageName,
            onValueChange = {},
            readOnly = true,
            label = { Text("识别语言") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            supportingText = { Text("语音识别使用的语言") }
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            RecognitionLanguages.options.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onLanguageSelected(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 总结风格下拉菜单
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryStyleDropdown(
    selectedStyle: String,
    onStyleSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedStyle,
            onValueChange = {},
            readOnly = true,
            label = { Text("总结风格") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SummaryStyles.options.forEach { style ->
                DropdownMenuItem(
                    text = { Text(style) },
                    onClick = {
                        onStyleSelected(style)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 操作按钮卡片
 */
@Composable
fun ActionButtonsCard(
    onViewRecords: () -> Unit,
    onGenerateSummary: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "快捷操作",
                style = MaterialTheme.typography.titleMedium
            )
            
            OutlinedButton(
                onClick = onViewRecords,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("查看记录")
            }
            
            Button(
                onClick = onGenerateSummary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("生成今日摘要")
            }
        }
    }
}
