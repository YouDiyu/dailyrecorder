package com.dailymate.app.data

/**
 * 应用配置数据类
 * 用于存储用户的所有配置选项
 */
data class AppConfig(
    val summaryStyle: String = "简洁",
    val customPrompt: String = "", // 自定义提示词
    val storagePath: String = "DailyMate/Records",
    val autoStartService: Boolean = false,
    val enableDailySummary: Boolean = true,
    val summaryTime: String = "23:59", // 每日摘要生成时间
    val apiBaseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", // API基础URL
    val apiKey: String = "sk-9489f803554d448e9adff444c41e3654", // API密钥
    val modelName: String = "qwen-plus", // 模型名称
    val recognitionLanguage: String = "zh-CN", // 语音识别语言
    val recordingMode: String = "standard" // 录制模式：standard(标准) 或 continuous(连续)
)

/**
 * 录制模式选项
 */
object RecordingModes {
    const val STANDARD = "standard"
    const val CONTINUOUS = "continuous"
    
    val options = mapOf(
        STANDARD to "标准模式（低功耗）",
        CONTINUOUS to "连续模式（零漏记）"
    )
    
    val descriptions = mapOf(
        STANDARD to "使用SpeechRecognizer实时识别，功耗低，适合日常使用",
        CONTINUOUS to "持续录制音频分段识别，零漏记录，适合重要场景"
    )
}

/**
 * 总结风格选项
 */
object SummaryStyles {
    const val CUSTOM = "自定义"
    
    val options = listOf(
        "简洁",
        "详细",
        "专业",
        "轻松",
        "正式",
        CUSTOM
    )
}

/**
 * 语音识别语言选项
 */
object RecognitionLanguages {
    val options = mapOf(
        "zh-CN" to "中文（简体）",
        "zh-TW" to "中文（繁体）",
        "en-US" to "英语（美国）",
        "en-GB" to "英语（英国）",
        "ja-JP" to "日语",
        "ko-KR" to "韩语",
        "fr-FR" to "法语",
        "de-DE" to "德语",
        "es-ES" to "西班牙语",
        "ru-RU" to "俄语"
    )
}
