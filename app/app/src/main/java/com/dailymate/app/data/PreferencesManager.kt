package com.dailymate.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore扩展属性
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

/**
 * 偏好设置管理器
 * 使用DataStore进行持久化存储
 */
class PreferencesManager(private val context: Context) {
    
    companion object {
        private val SUMMARY_STYLE = stringPreferencesKey("summary_style")
        private val CUSTOM_PROMPT = stringPreferencesKey("custom_prompt")
        private val STORAGE_PATH = stringPreferencesKey("storage_path")
        private val AUTO_START_SERVICE = booleanPreferencesKey("auto_start_service")
        private val ENABLE_DAILY_SUMMARY = booleanPreferencesKey("enable_daily_summary")
        private val SUMMARY_TIME = stringPreferencesKey("summary_time")
        private val API_BASE_URL = stringPreferencesKey("api_base_url")
        private val API_KEY = stringPreferencesKey("api_key")
        private val MODEL_NAME = stringPreferencesKey("model_name")
        private val RECOGNITION_LANGUAGE = stringPreferencesKey("recognition_language")
        private val RECORDING_MODE = stringPreferencesKey("recording_mode")
    }
    
    /**
     * 获取应用配置Flow
     */
    val appConfigFlow: Flow<AppConfig> = context.dataStore.data.map { preferences ->
        AppConfig(
            summaryStyle = preferences[SUMMARY_STYLE] ?: "简洁",
            customPrompt = preferences[CUSTOM_PROMPT] ?: "",
            storagePath = preferences[STORAGE_PATH] ?: "DailyMate/Records",
            autoStartService = preferences[AUTO_START_SERVICE] ?: false,
            enableDailySummary = preferences[ENABLE_DAILY_SUMMARY] ?: true,
            summaryTime = preferences[SUMMARY_TIME] ?: "23:59",
            apiBaseUrl = preferences[API_BASE_URL] ?: "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            apiKey = preferences[API_KEY] ?: "sk-9489f803554d448e9adff444c41e3654",
            modelName = preferences[MODEL_NAME] ?: "qwen-plus",
            recognitionLanguage = preferences[RECOGNITION_LANGUAGE] ?: "zh-CN",
            recordingMode = preferences[RECORDING_MODE] ?: "standard"
        )
    }
    
    /**
     * 保存应用配置
     */
    suspend fun saveAppConfig(config: AppConfig) {
        context.dataStore.edit { preferences ->
            preferences[SUMMARY_STYLE] = config.summaryStyle
            preferences[CUSTOM_PROMPT] = config.customPrompt
            preferences[STORAGE_PATH] = config.storagePath
            preferences[AUTO_START_SERVICE] = config.autoStartService
            preferences[ENABLE_DAILY_SUMMARY] = config.enableDailySummary
            preferences[SUMMARY_TIME] = config.summaryTime
            preferences[API_BASE_URL] = config.apiBaseUrl
            preferences[API_KEY] = config.apiKey
            preferences[MODEL_NAME] = config.modelName
            preferences[RECOGNITION_LANGUAGE] = config.recognitionLanguage
            preferences[RECORDING_MODE] = config.recordingMode
        }
    }
    
    /**
     * 更新API基础URL
     */
    suspend fun updateApiBaseUrl(baseUrl: String) {
        context.dataStore.edit { preferences ->
            preferences[API_BASE_URL] = baseUrl
        }
    }
    
    /**
     * 更新API密钥
     */
    suspend fun updateApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
        }
    }
    
    /**
     * 更新模型名称
     */
    suspend fun updateModelName(modelName: String) {
        context.dataStore.edit { preferences ->
            preferences[MODEL_NAME] = modelName
        }
    }
    
    /**
     * 更新总结风格
     */
    suspend fun updateSummaryStyle(style: String) {
        context.dataStore.edit { preferences ->
            preferences[SUMMARY_STYLE] = style
        }
    }
    
    /**
     * 更新自定义提示词
     */
    suspend fun updateCustomPrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_PROMPT] = prompt
        }
    }
    
    /**
     * 更新存储路径
     */
    suspend fun updateStoragePath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[STORAGE_PATH] = path
        }
    }
    
    /**
     * 更新自动启动服务设置
     */
    suspend fun updateAutoStartService(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_START_SERVICE] = enabled
        }
    }
    
    /**
     * 更新语音识别语言
     */
    suspend fun updateRecognitionLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[RECOGNITION_LANGUAGE] = language
        }
    }
    
    /**
     * 更新录制模式
     */
    suspend fun updateRecordingMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[RECORDING_MODE] = mode
        }
    }
}
