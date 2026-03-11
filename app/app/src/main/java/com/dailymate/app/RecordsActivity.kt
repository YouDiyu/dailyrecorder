package com.dailymate.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.dailymate.app.ui.theme.DailyMateTheme
import java.io.File

/**
 * 记录查看Activity
 */
class RecordsActivity : ComponentActivity() {
    
    private val viewModel: RecordsViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            DailyMateTheme {
                RecordsScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onPlayAudio = { file -> playAudioFile(file) },
                    onRegenerateSummary = { date -> regenerateSummary(date) },
                    onRegenerateRecord = { date -> regenerateRecord(date) },
                    onDeleteAudio = { file -> deleteAudioFile(file) }
                )
            }
        }
    }
    
    /**
     * 播放音频文件
     */
    private fun playAudioFile(file: File) {
        try {
            if (!file.exists()) {
                Toast.makeText(this, "音频文件不存在", Toast.LENGTH_SHORT).show()
                return
            }
            
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(intent, "播放音频"))
        } catch (e: Exception) {
            Toast.makeText(this, "无法播放音频: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 重新生成摘要
     */
    private fun regenerateSummary(date: String) {
        viewModel.regenerateSummary(date)
        Toast.makeText(this, "正在重新生成摘要...", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 重新生成记录
     */
    private fun regenerateRecord(date: String) {
        viewModel.regenerateRecord(date)
        Toast.makeText(this, "正在重新生成记录...", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 删除音频文件
     */
    private fun deleteAudioFile(file: File) {
        try {
            if (file.exists() && file.delete()) {
                Toast.makeText(this, "音频文件已删除", Toast.LENGTH_SHORT).show()
                // 只刷新音频文件列表，不重新加载记录和摘要
                viewModel.selectedDate.value?.let { date ->
                    viewModel.refreshAudioFiles(date)
                }
            } else {
                Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * 记录查看界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    viewModel: RecordsViewModel,
    onBack: () -> Unit,
    onPlayAudio: (File) -> Unit,
    onRegenerateSummary: (String) -> Unit,
    onRegenerateRecord: (String) -> Unit,
    onDeleteAudio: (File) -> Unit
) {
    val dates by viewModel.availableDates.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val recordContent by viewModel.recordContent.collectAsState()
    val summaryContent by viewModel.summaryContent.collectAsState()
    val audioFiles by viewModel.audioFiles.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val regenerateProgress by viewModel.regenerateProgress.collectAsState()
    val regenerateStatus by viewModel.regenerateStatus.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("查看记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 日期选择
            DateSelector(
                dates = dates,
                selectedDate = selectedDate,
                onDateSelected = { viewModel.selectDate(it) }
            )
            
            if (isLoading && regenerateProgress == 0f) {
                // 只在普通加载时显示"加载中..."
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("加载中...")
                }
            } else if (selectedDate != null) {
                // 进度指示器和状态消息（在内容上方）
                if (regenerateProgress > 0f && regenerateStatus.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = regenerateStatus,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            
                            LinearProgressIndicator(
                                progress = regenerateProgress,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            
                            Text(
                                text = "${(regenerateProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 记录内容
                    item(key = "record_$selectedDate") {
                        RecordCard(
                            title = "当日记录",
                            content = recordContent,
                            onRegenerate = { onRegenerateRecord(selectedDate!!) }
                        )
                    }
                    
                    // 摘要内容
                    item(key = "summary_$selectedDate") {
                        SummaryCard(
                            title = "AI摘要",
                            content = summaryContent,
                            onRegenerate = { onRegenerateSummary(selectedDate!!) }
                        )
                    }
                    
                    // 音频文件列表
                    item(key = "audio_$selectedDate") {
                        AudioFilesCard(
                            audioFiles = audioFiles,
                            onPlayAudio = onPlayAudio,
                            onDeleteAudio = onDeleteAudio,
                            onDeleteAll = {
                                // 批量删除所有音频文件（优化：删除完后只刷新一次）
                                var deleteCount = 0
                                audioFiles.forEach { file ->
                                    try {
                                        if (file.exists() && file.delete()) {
                                            deleteCount++
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                // 所有文件删除完后只刷新一次
                                if (deleteCount > 0) {
                                    viewModel.refreshAudioFiles(selectedDate!!)
                                }
                            }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("请选择日期查看记录")
                }
            }
        }
    }
}

/**
 * 日期选择器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelector(
    dates: List<String>,
    selectedDate: String?,
    onDateSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedDate ?: "选择日期",
            onValueChange = {},
            readOnly = true,
            label = { Text("日期") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            dates.forEach { date ->
                DropdownMenuItem(
                    text = { Text(date) },
                    onClick = {
                        onDateSelected(date)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 记录卡片
 */
@Composable
fun RecordCard(
    title: String,
    content: String,
    onRegenerate: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxCardHeight = screenHeight * 0.4f // 最大高度为屏幕高度的40%
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                
                IconButton(onClick = onRegenerate) {
                    Icon(Icons.Default.Refresh, "重新生成")
                }
            }
            
            if (content.isEmpty()) {
                Text(
                    text = "暂无记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // 使用Box限制高度并添加滚动
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxCardHeight)
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}

/**
 * 摘要卡片
 */
@Composable
fun SummaryCard(
    title: String,
    content: String,
    onRegenerate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                
                IconButton(onClick = onRegenerate) {
                    Icon(Icons.Default.Refresh, "重新生成")
                }
            }
            
            if (content.isEmpty()) {
                Text(
                    text = "暂无摘要",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * 音频文件卡片
 */
@Composable
fun AudioFilesCard(
    audioFiles: List<File>,
    onPlayAudio: (File) -> Unit,
    onDeleteAudio: (File) -> Unit,
    onDeleteAll: () -> Unit
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "音频片段 (${audioFiles.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                
                // 批量删除按钮（仅在有文件时显示）
                if (audioFiles.isNotEmpty()) {
                    IconButton(onClick = { showDeleteAllDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除全部",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            if (audioFiles.isEmpty()) {
                Text(
                    text = "暂无音频文件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                audioFiles.forEach { file ->
                    AudioFileItem(
                        file = file,
                        onPlay = { onPlayAudio(file) },
                        onDelete = { onDeleteAudio(file) }
                    )
                }
            }
        }
    }
    
    // 批量删除确认对话框
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("确认删除全部") },
            text = { Text("确定要删除该日期的所有音频文件吗？\n共 ${audioFiles.size} 个文件") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        onDeleteAll()
                    }
                ) {
                    Text("删除全部", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 音频文件项
 */
@Composable
fun AudioFileItem(
    file: File,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onPlay)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${file.length() / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "播放",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onPlay)
                )
                
                IconButton(
                    onClick = { showDeleteDialog = true }
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这个音频文件吗？\n${file.name}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
