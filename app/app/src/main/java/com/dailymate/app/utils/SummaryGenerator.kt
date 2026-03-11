package com.dailymate.app.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 摘要生成器
 * 使用AI API生成每日摘要
 */
class SummaryGenerator(private val context: Context) {
    
    companion object {
        private const val TAG = "SummaryGenerator"
        private const val TIMEOUT_SECONDS = 300L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    /**
     * 生成摘要
     */
    suspend fun generateSummary(
        content: String,
        style: String,
        apiBaseUrl: String,
        apiKey: String,
        modelName: String,
        customPrompt: String = ""
    ): String = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(content, style, customPrompt)
        Log.d(TAG, prompt)
        
        val jsonBody = JSONObject().apply {
            put("model", modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "你是一个专业的日记助手，擅长从日常记录中分析相应场景提取关键信息并为用户生成准确的日常记录")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.7)
        }
        
        val requestBody = jsonBody.toString()
            .toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(apiBaseUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorMessage = when (response.code) {
                    401 -> "API密钥无效或已过期"
                    429 -> "API请求频率超限，请稍后再试"
                    500, 502, 503 -> "服务器错误，请稍后再试"
                    else -> "API请求失败: ${response.code}"
                }
                Log.e(TAG, "API Error: ${response.code} - ${response.message}")
                throw Exception(errorMessage)
            }
            
            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            val jsonResponse = JSONObject(responseBody)
            
            jsonResponse
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }
    }
    
    /**
     * 构建提示词
     */
    private fun buildPrompt(content: String, style: String, customPrompt: String = ""): String {
        val dateString = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA).format(Date())
        
        // 如果是自定义风格且有自定义提示词，使用自定义提示词
        if (style == "自定义" && customPrompt.isNotEmpty()) {
            return """
                $customPrompt
                
                以下是${dateString}的日常记录：
                
                $content
            """.trimIndent()
        }
        
        return when (style) {
            "简洁" -> """
                请为以下${dateString}的日常记录生成一个简洁且完整的摘要：
                
                $content
                
                要求：
                1. 按事件对记录进行划分
                2. 使用简洁的语言概括每条事件
                3. 按时间顺序组织
            """.trimIndent()
            
            "详细" -> """
                请为以下${dateString}的日常记录生成一个详细的摘要：
                
                $content
                
                要求：
                1 .按事件对记录进行划分 
                2. 详细描述主要活动和事件
                2. 包含关键信息
                3. 按时间顺序分段组织，逻辑清晰
            """.trimIndent()
            
            "专业" -> """
                请为以下${dateString}的日常记录生成一个专业的工作总结：
                
                $content
                
                要求：
                1. 使用专业术语
                2. 突出工作成果和进展
                3. 结构化呈现（如：完成事项、进行中事项、待办事项）
            """.trimIndent()
            
            "轻松" -> """
                请为以下${dateString}的日常记录生成一个轻松有趣的摘要：
                
                $content
                
                要求：
                1. 使用轻松活泼的语言
                2. 可以加入适当的emoji
                3. 突出有趣的细节
            """.trimIndent()
            
            "正式" -> """
                请为以下${dateString}的日常记录生成一个正式的日志摘要：
                
                $content
                
                要求：
                1. 使用正式、规范的语言
                2. 客观描述事实
                3. 结构严谨，条理清晰
            """.trimIndent()
            
            else -> """
                请为以下${dateString}的日常记录生成摘要：
                
                $content
            """.trimIndent()
        }
    }
    
    /**
     * 生成本地简单摘要（不依赖API）
     */
    private fun generateLocalSummary(content: String, style: String): String {
        val dateString = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA).format(Date())
        val lines = content.split("\n").filter { it.isNotBlank() }
        
        if (lines.isEmpty()) {
            return "今日暂无记录"
        }
        
        val summary = StringBuilder()
        summary.append("【${dateString}摘要】\n\n")
        
        when (style) {
            "简洁" -> {
                summary.append("今日共记录 ${lines.size} 条事件\n\n")
                summary.append("主要活动：\n")
                lines.take(5).forEach { line ->
                    summary.append("• ${line.trim()}\n")
                }
            }
            
            "详细" -> {
                summary.append("今日详细记录（共 ${lines.size} 条）：\n\n")
                lines.forEachIndexed { index, line ->
                    summary.append("${index + 1}. ${line.trim()}\n")
                }
            }
            
            else -> {
                summary.append("今日记录（共 ${lines.size} 条）：\n\n")
                lines.forEach { line ->
                    summary.append("• ${line.trim()}\n")
                }
            }
        }
        
        summary.append("\n生成时间：${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}")
        
        return summary.toString()
    }
}
