package com.dailymate.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailymate.app.data.PreferencesManager
import com.dailymate.app.utils.AudioConcatenator
import com.dailymate.app.utils.FileManager
import com.dailymate.app.utils.SpeechRecognitionHelper
import com.dailymate.app.utils.SummaryGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * 记录查看ViewModel
 */
class RecordsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val fileManager = FileManager(application)
    private val preferencesManager = PreferencesManager(application)
    private val summaryGenerator = SummaryGenerator(application)
    
    // 可用日期列表
    private val _availableDates = MutableStateFlow<List<String>>(emptyList())
    val availableDates: StateFlow<List<String>> = _availableDates.asStateFlow()
    
    // 选中的日期
    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()
    
    // 记录内容
    private val _recordContent = MutableStateFlow("")
    val recordContent: StateFlow<String> = _recordContent.asStateFlow()
    
    // 摘要内容
    private val _summaryContent = MutableStateFlow("")
    val summaryContent: StateFlow<String> = _summaryContent.asStateFlow()
    
    // 音频文件列表
    private val _audioFiles = MutableStateFlow<List<File>>(emptyList())
    val audioFiles: StateFlow<List<File>> = _audioFiles.asStateFlow()
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 重新生成进度
    private val _regenerateProgress = MutableStateFlow(0f)
    val regenerateProgress: StateFlow<Float> = _regenerateProgress.asStateFlow()
    
    // 重新生成状态消息
    private val _regenerateStatus = MutableStateFlow("")
    val regenerateStatus: StateFlow<String> = _regenerateStatus.asStateFlow()
    
    init {
        loadAvailableDates()
    }
    
    /**
     * 加载可用日期列表
     */
    private fun loadAvailableDates() {
        viewModelScope.launch {
            try {
                // 获取所有记录文件
                val recordFiles = fileManager.getAllRecordFiles()
                
                // 提取日期（文件名格式：yyyy-MM-dd.txt）
                val dates = recordFiles.map { file ->
                    file.nameWithoutExtension
                }.sortedDescending() // 最新的在前
                
                _availableDates.value = dates
                
                // 自动选择最新日期
                if (dates.isNotEmpty()) {
                    selectDate(dates.first())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 选择日期
     */
    fun selectDate(date: String) {
        _selectedDate.value = date
        loadDateData(date)
    }
    
    /**
     * 加载指定日期的数据
     */
    private fun loadDateData(date: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // 使用Dispatchers.IO确保在IO线程执行
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // 加载记录
                    val record = fileManager.readRecord(date)
                    _recordContent.value = record
                    
                    // 加载摘要
                    val summaryFile = File(fileManager.getSummariesDirectoryPath(), "$date-summary.txt")
                    val summary = if (summaryFile.exists()) {
                        summaryFile.readText()
                    } else {
                        ""
                    }
                    _summaryContent.value = summary
                    
                    // 加载音频文件（优化：使用更高效的过滤方式）
                    val audioDir = fileManager.getAudioSegmentsDirectory()
                    val datePrefix = "segment_${date.replace("-", "")}"
                    val audioFiles = audioDir.listFiles { _, name ->
                        name.startsWith(datePrefix)
                    }?.sortedBy { it.name } ?: emptyList()
                    
                    _audioFiles.value = audioFiles
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 刷新音频文件列表（不重新加载记录和摘要）
     */
    fun refreshAudioFiles(date: String) {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // 只重新加载音频文件
                    val audioDir = fileManager.getAudioSegmentsDirectory()
                    val datePrefix = "segment_${date.replace("-", "")}"
                    val audioFiles = audioDir.listFiles { _, name ->
                        name.startsWith(datePrefix)
                    }?.sortedBy { it.name } ?: emptyList()
                    
                    _audioFiles.value = audioFiles
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 重新生成摘要
     */
    fun regenerateSummary(date: String) {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // 读取记录内容
                val recordContent = fileManager.readRecord(date)
                
                if (recordContent.isEmpty()) {
                    _summaryContent.value = "无记录内容，无法生成摘要"
                    _isLoading.value = false
                    return@launch
                }
                
                // 获取配置（使用first()获取当前值）
                val config = preferencesManager.appConfigFlow.first()
                
                // 生成摘要
                val summary = summaryGenerator.generateSummary(
                    content = recordContent,
                    style = config.summaryStyle,
                    apiBaseUrl = config.apiBaseUrl,
                    apiKey = config.apiKey,
                    modelName = config.modelName,
                    customPrompt = config.customPrompt
                )
                
                // 保存摘要
                val summaryFile = File(fileManager.getSummariesDirectoryPath(), "$date-summary.txt")
                summaryFile.writeText(summary)
                
                // 更新显示
                _summaryContent.value = summary
                
            } catch (e: Exception) {
                e.printStackTrace()
                _summaryContent.value = "生成摘要失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 重新生成记录
     * 使用贪婪算法拼接音频文件，然后重新识别生成记录
     */
    fun regenerateRecord(date: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _regenerateProgress.value = 0f
            _regenerateStatus.value = "开始处理..."
            
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    android.util.Log.d("RecordsViewModel", "Starting record regeneration for $date")
                    
                    // 1. 获取该日期的所有音频文件 (5%)
                    _regenerateStatus.value = "正在扫描音频文件..."
                    _regenerateProgress.value = 0.05f
                    
                    val audioDir = fileManager.getAudioSegmentsDirectory()
                    val datePrefix = "segment_${date.replace("-", "")}"
                    val audioFiles = audioDir.listFiles { _, name ->
                        name.startsWith(datePrefix) && name.endsWith(".wav")
                    }?.sortedBy { it.name } ?: emptyList()
                    
                    if (audioFiles.isEmpty()) {
                        android.util.Log.w("RecordsViewModel", "No audio files found for $date")
                        _recordContent.value = "没有找到音频文件"
                        _regenerateStatus.value = "未找到音频文件"
                        return@withContext
                    }
                    
                    android.util.Log.d("RecordsViewModel", "Found ${audioFiles.size} audio files")
                    _regenerateStatus.value = "找到 ${audioFiles.size} 个音频文件"
                    _regenerateProgress.value = 0.1f
                    
                    // 2. 清空现有记录 (10%)
                    fileManager.clearRecord(date)
                    _recordContent.value = ""
                    
                    // 3. 使用贪婪算法分组音频文件 (15%)
                    _regenerateStatus.value = "正在分组音频文件..."
                    _regenerateProgress.value = 0.15f
                    
                    val maxDurationMs = 600000L
                    val fileGroups = AudioConcatenator.groupFilesByDuration(audioFiles, maxDurationMs)
                    
                    if (fileGroups.isEmpty()) {
                        android.util.Log.w("RecordsViewModel", "No valid file groups created")
                        _recordContent.value = "无法创建有效的音频片段"
                        _regenerateStatus.value = "分组失败"
                        return@withContext
                    }
                    
                    android.util.Log.d("RecordsViewModel", "Created ${fileGroups.size} file groups")
                    _regenerateStatus.value = "已分成 ${fileGroups.size} 组"
                    _regenerateProgress.value = 0.2f
                    
                    // 4. 创建临时目录用于存放拼接后的文件
                    val tempDir = File(audioDir, "temp_concat")
                    tempDir.mkdirs()
                    
                    val concatenatedFiles = mutableListOf<File>()
                    val originalFiles = mutableListOf<File>()
                    
                    try {
                        // 5. 拼接每组文件 (20%-40%)
                        val concatenateProgressStart = 0.2f
                        val concatenateProgressRange = 0.2f
                        
                        fileGroups.forEachIndexed { index, group ->
                            val progress = concatenateProgressStart + (concatenateProgressRange * (index + 1) / fileGroups.size)
                            _regenerateStatus.value = "正在拼接第 ${index + 1}/${fileGroups.size} 组 (${group.size} 个文件)..."
                            _regenerateProgress.value = progress
                            
                            val outputFileName = group.first().name
                            val outputFile = File(tempDir, outputFileName)
                            
                            android.util.Log.d("RecordsViewModel", "Concatenating group $index with ${group.size} files")
                            
                            if (AudioConcatenator.concatenateWavFiles(group, outputFile)) {
                                concatenatedFiles.add(outputFile)
                                originalFiles.addAll(group)
                            } else {
                                throw Exception("Failed to concatenate group $index")
                            }
                        }
                        
                        // 6. 对每个拼接后的文件进行语音识别 (40%-90%)
                        val recognizeProgressStart = 0.4f
                        val recognizeProgressRange = 0.5f
                        
                        concatenatedFiles.forEachIndexed { index, file ->
                            val progress = recognizeProgressStart + (recognizeProgressRange * index / concatenatedFiles.size)
                            _regenerateStatus.value = "正在识别第 ${index + 1}/${concatenatedFiles.size} 个音频..."
                            _regenerateProgress.value = progress
                            
                            android.util.Log.d("RecordsViewModel", "Recognizing ${file.name}")
                            
                            val recognizedTexts = SpeechRecognitionHelper.recognizeAudioFile(file.absolutePath)
                            
                            if (recognizedTexts != null && recognizedTexts.isNotEmpty()) {
                                // 从文件名提取时间戳
                                val timestampRaw = file.name.removePrefix("segment_").substring(9, 15)
                                val timestampFormatted = timestampRaw.chunked(2).joinToString(":")
                                
                                // 保存识别结果并实时更新显示
                                recognizedTexts.forEach { text ->
                                    val entry = "[$timestampFormatted] $text\n"
                                    fileManager.appendToRecord(date, entry)
                                }
                                
                                // 实时更新记录内容显示
                                val currentRecord = fileManager.readRecord(date)
                                _recordContent.value = currentRecord
                                
                                android.util.Log.d("RecordsViewModel", "Saved ${recognizedTexts.size} recognized texts")
                                
                                // 更新进度
                                val progressAfterRecognition = recognizeProgressStart + (recognizeProgressRange * (index + 1) / concatenatedFiles.size)
                                _regenerateProgress.value = progressAfterRecognition
                            }
                        }
                        
                        // 7. 删除原始文件 (90%-95%)
                        _regenerateStatus.value = "正在清理原始文件..."
                        _regenerateProgress.value = 0.9f
                        
                        originalFiles.forEach { file ->
                            if (file.exists()) {
                                file.delete()
                                android.util.Log.d("RecordsViewModel", "Deleted original file: ${file.name}")
                            }
                        }
                        
                        // 8. 将拼接后的文件移动到音频目录 (95%-98%)
                        _regenerateStatus.value = "正在整理文件..."
                        _regenerateProgress.value = 0.95f
                        
                        concatenatedFiles.forEach { file ->
                            val targetFile = File(audioDir, file.name)
                            file.renameTo(targetFile)
                            android.util.Log.d("RecordsViewModel", "Moved concatenated file: ${file.name}")
                        }
                        
                        // 9. 清理临时目录
                        tempDir.deleteRecursively()
                        
                        // 10. 刷新音频文件列表 (98%-100%)
                        _regenerateStatus.value = "正在刷新数据..."
                        _regenerateProgress.value = 0.98f
                        
                        refreshAudioFiles(date)
                        
                        _regenerateStatus.value = "完成！"
                        _regenerateProgress.value = 1.0f
                        
                        android.util.Log.d("RecordsViewModel", "Record regeneration completed successfully")
                        
                    } catch (e: Exception) {
                        // 回滚：删除临时文件，保留原始文件
                        android.util.Log.e("RecordsViewModel", "Error during regeneration, rolling back", e)
                        _regenerateStatus.value = "处理失败，正在回滚..."
                        tempDir.deleteRecursively()
                        throw e
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("RecordsViewModel", "Failed to regenerate record", e)
                _recordContent.value = "重新生成记录失败: ${e.message}"
                _regenerateStatus.value = "失败: ${e.message}"
            } finally {
                _isLoading.value = false
                // 延迟清除状态消息
                kotlinx.coroutines.delay(2000)
                _regenerateStatus.value = ""
                _regenerateProgress.value = 0f
            }
        }
    }
}
