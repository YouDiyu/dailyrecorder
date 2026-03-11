package com.dailymate.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

/**
 * DataStore扩展属性
 */
private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(name = "input_history")

/**
 * 输入历史记录管理器
 * 管理API地址、API密钥和模型名称的历史记录
 */
class InputHistoryManager(private val context: Context) {
    
    companion object {
        private val API_BASE_URL_HISTORY = stringPreferencesKey("api_base_url_history")
        private val API_KEY_HISTORY = stringPreferencesKey("api_key_history")
        private val MODEL_NAME_HISTORY = stringPreferencesKey("model_name_history")
        private const val MAX_HISTORY_SIZE = 5
    }
    
    /**
     * 获取API地址历史记录
     */
    val apiBaseUrlHistory: Flow<List<String>> = context.historyDataStore.data.map { preferences ->
        parseHistory(preferences[API_BASE_URL_HISTORY] ?: "")
    }
    
    /**
     * 获取API密钥历史记录
     */
    val apiKeyHistory: Flow<List<String>> = context.historyDataStore.data.map { preferences ->
        parseHistory(preferences[API_KEY_HISTORY] ?: "")
    }
    
    /**
     * 获取模型名称历史记录
     */
    val modelNameHistory: Flow<List<String>> = context.historyDataStore.data.map { preferences ->
        parseHistory(preferences[MODEL_NAME_HISTORY] ?: "")
    }
    
    /**
     * 添加API地址到历史记录
     */
    suspend fun addApiBaseUrlHistory(url: String) {
        if (url.isBlank()) return
        context.historyDataStore.edit { preferences ->
            val history = parseHistory(preferences[API_BASE_URL_HISTORY] ?: "")
            val newHistory = addToHistory(history, url)
            preferences[API_BASE_URL_HISTORY] = serializeHistory(newHistory)
        }
    }
    
    /**
     * 添加API密钥到历史记录
     */
    suspend fun addApiKeyHistory(key: String) {
        if (key.isBlank()) return
        context.historyDataStore.edit { preferences ->
            val history = parseHistory(preferences[API_KEY_HISTORY] ?: "")
            val newHistory = addToHistory(history, key)
            preferences[API_KEY_HISTORY] = serializeHistory(newHistory)
        }
    }
    
    /**
     * 添加模型名称到历史记录
     */
    suspend fun addModelNameHistory(name: String) {
        if (name.isBlank()) return
        context.historyDataStore.edit { preferences ->
            val history = parseHistory(preferences[MODEL_NAME_HISTORY] ?: "")
            val newHistory = addToHistory(history, name)
            preferences[MODEL_NAME_HISTORY] = serializeHistory(newHistory)
        }
    }
    
    /**
     * 清除API地址历史记录
     */
    suspend fun clearApiBaseUrlHistory() {
        context.historyDataStore.edit { preferences ->
            preferences.remove(API_BASE_URL_HISTORY)
        }
    }
    
    /**
     * 清除API密钥历史记录
     */
    suspend fun clearApiKeyHistory() {
        context.historyDataStore.edit { preferences ->
            preferences.remove(API_KEY_HISTORY)
        }
    }
    
    /**
     * 清除模型名称历史记录
     */
    suspend fun clearModelNameHistory() {
        context.historyDataStore.edit { preferences ->
            preferences.remove(MODEL_NAME_HISTORY)
        }
    }
    
    /**
     * 添加项到历史记录
     * 保持最近5条唯一记录，按时间倒序
     */
    private fun addToHistory(history: List<String>, newItem: String): List<String> {
        val mutableHistory = history.toMutableList()
        // 移除已存在的相同项
        mutableHistory.remove(newItem)
        // 添加到开头
        mutableHistory.add(0, newItem)
        // 保持最多5条记录
        return mutableHistory.take(MAX_HISTORY_SIZE)
    }
    
    /**
     * 解析历史记录JSON
     */
    private fun parseHistory(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            List(jsonArray.length()) { i -> jsonArray.getString(i) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 序列化历史记录为JSON
     */
    private fun serializeHistory(history: List<String>): String {
        val jsonArray = JSONArray()
        history.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }
}

/**
 * 脱敏显示API密钥
 * 仅显示末4位，其余用*替代
 */
fun maskApiKey(key: String): String {
    if (key.length <= 4) return key
    val visiblePart = key.takeLast(4)
    val maskedPart = "*".repeat(key.length - 4)
    return maskedPart + visiblePart
}

/**
 * 截断长文本
 * 超过指定长度时截断并添加省略号
 */
fun truncateText(text: String, maxLength: Int = 50): String {
    return if (text.length > maxLength) {
        text.take(maxLength) + "..."
    } else {
        text
    }
}
