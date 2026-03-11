package com.dailymate.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailymate.app.data.AppConfig
import com.dailymate.app.data.InputHistoryManager
import com.dailymate.app.data.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主界面ViewModel
 * 管理应用配置和服务状态
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesManager = PreferencesManager(application)
    private val historyManager = InputHistoryManager(application)
    
    // 应用配置状态
    private val _appConfig = MutableStateFlow(AppConfig())
    val appConfig: StateFlow<AppConfig> = _appConfig.asStateFlow()
    
    // 历史记录状态
    val apiBaseUrlHistory = historyManager.apiBaseUrlHistory
    val apiKeyHistory = historyManager.apiKeyHistory
    val modelNameHistory = historyManager.modelNameHistory
    
    // 服务运行状态
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    
    // UI状态
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    init {
        loadConfig()
    }
    
    /**
     * 加载配置
     */
    private fun loadConfig() {
        viewModelScope.launch {
            preferencesManager.appConfigFlow.collect { config ->
                _appConfig.value = config
            }
        }
    }
    
    /**
     * 更新API基础URL
     */
    fun updateApiBaseUrl(baseUrl: String) {
        viewModelScope.launch {
            try {
                preferencesManager.updateApiBaseUrl(baseUrl)
                historyManager.addApiBaseUrlHistory(baseUrl)
                _uiState.value = UiState.Success("API地址已更新")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("更新失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新API密钥
     */
    fun updateApiKey(apiKey: String) {
        viewModelScope.launch {
            try {
                preferencesManager.updateApiKey(apiKey)
                historyManager.addApiKeyHistory(apiKey)
                _uiState.value = UiState.Success("API密钥已更新")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("更新失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新模型名称
     */
    fun updateModelName(modelName: String) {
        viewModelScope.launch {
            try {
                preferencesManager.updateModelName(modelName)
                historyManager.addModelNameHistory(modelName)
                _uiState.value = UiState.Success("模型名称已更新")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("更新失败: ${e.message}")
            }
        }
    }
    
    /**
     * 清除API地址历史记录
     */
    fun clearApiBaseUrlHistory() {
        viewModelScope.launch {
            try {
                historyManager.clearApiBaseUrlHistory()
                _uiState.value = UiState.Success("历史记录已清除")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("清除失败: ${e.message}")
            }
        }
    }
    
    /**
     * 清除API密钥历史记录
     */
    fun clearApiKeyHistory() {
        viewModelScope.launch {
            try {
                historyManager.clearApiKeyHistory()
                _uiState.value = UiState.Success("历史记录已清除")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("清除失败: ${e.message}")
            }
        }
    }
    
    /**
     * 清除模型名称历史记录
     */
    fun clearModelNameHistory() {
        viewModelScope.launch {
            try {
                historyManager.clearModelNameHistory()
                _uiState.value = UiState.Success("历史记录已清除")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("清除失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新总结风格
     */
    fun updateSummaryStyle(style: String) {
        viewModelScope.launch {
            try {
                preferencesManager.updateSummaryStyle(style)
                _uiState.value = UiState.Success("总结风格已更新")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("更新失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新自定义提示词
     */
    fun updateCustomPrompt(prompt: String) {
        viewModelScope.launch {
            try {
                preferencesManager.updateCustomPrompt(prompt)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("更新失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新存储路径
     */
    fun updateStoragePath(path: String) {
        viewModelScope.launch {
            try {
                preferencesManager.updateStoragePath(path)
                _uiState.value = UiState.Success("存储路径已更新")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("更新失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新自动启动服务
     */
    fun updateAutoStartService(enabled: Boolean) {
        viewModelScope.launch {
            try {
                preferencesManager.updateAutoStartService(enabled)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("更新失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新语音识别语言
     */
    fun updateRecognitionLanguage(language: String) {
        viewModelScope.launch {
            try {
                preferencesManager.updateRecognitionLanguage(language)
                _uiState.value = UiState.Success("识别语言已更新")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("更新失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新录制模式
     */
    fun updateRecordingMode(mode: String) {
        viewModelScope.launch {
            try {
                preferencesManager.updateRecordingMode(mode)
                _uiState.value = UiState.Success("录制模式已更新")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("更新失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新服务运行状态
     */
    fun updateServiceRunningState(isRunning: Boolean) {
        _isServiceRunning.value = isRunning
    }
    
    /**
     * 重置UI状态
     */
    fun resetUiState() {
        _uiState.value = UiState.Idle
    }
    
    /**
     * UI状态密封类
     */
    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val message: String) : UiState()
        data class Error(val message: String) : UiState()
    }
}
