package com.dailymate.app.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件管理器
 * 负责管理日记录文件的读写
 */
class FileManager(private val context: Context) {
    
    companion object {
        private const val TAG = "FileManager"
        private const val RECORDS_DIR = "DailyMate/Records"
        private const val SUMMARIES_DIR = "DailyMate/Summaries"
        private const val AUDIO_SEGMENTS_DIR = "DailyMate/Audio_segments"
        private const val DATE_FORMAT = "yyyy-MM-dd"
    }
    
    private val recordsDir: File
    private val summariesDir: File
    private val audioSegmentsDir: File
    
    init {
        // 使用应用专属目录
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        recordsDir = File(baseDir, RECORDS_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        summariesDir = File(baseDir, SUMMARIES_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        audioSegmentsDir = File(baseDir, AUDIO_SEGMENTS_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        
        Log.d(TAG, "Records directory: ${recordsDir.absolutePath}")
        Log.d(TAG, "Summaries directory: ${summariesDir.absolutePath}")
        Log.d(TAG, "Audio segments directory: ${audioSegmentsDir.absolutePath}")
    }
    
    /**
     * 获取今日日期字符串
     */
    private fun getTodayDateString(): String {
        return SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Date())
    }
    
    /**
     * 获取今日记录文件
     */
    fun getTodayRecordFile(): File {
        val dateString = getTodayDateString()
        return File(recordsDir, "$dateString.txt")
    }
    
    /**
     * 追加内容到今日记录
     */
    fun appendToTodayRecord(content: String) {
        try {
            val file = getTodayRecordFile()
            // 如果文件不存在，先创建空文件
            if (!file.exists()) {
                file.createNewFile()
                Log.d(TAG, "Created new record file: ${file.name}")
            }
            file.appendText(content)
            Log.d(TAG, "Appended to ${file.name}: $content")
        } catch (e: Exception) {
            Log.e(TAG, "Error appending to today's record", e)
            throw e
        }
    }
    
    /**
     * 读取今日记录
     */
    fun readTodayRecord(): String {
        return try {
            val file = getTodayRecordFile()
            if (!file.exists()) {
                // 如果文件不存在，先创建空文件
                file.createNewFile()
                Log.d(TAG, "Created new record file: ${file.name}")
                ""
            } else {
                file.readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading today's record", e)
            ""
        }
    }
    
    /**
     * 读取指定日期的记录
     */
    fun readRecord(date: String): String {
        return try {
            val file = File(recordsDir, "$date.txt")
            if (file.exists()) {
                file.readText()
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading record for $date", e)
            ""
        }
    }
    
    /**
     * 保存今日摘要
     */
    fun saveTodaySummary(summary: String) {
        try {
            val dateString = getTodayDateString()
            val file = File(summariesDir, "$dateString-summary.txt")
            file.writeText(summary)
            Log.d(TAG, "Saved summary to ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving summary", e)
            throw e
        }
    }
    
    /**
     * 读取今日摘要
     */
    fun readTodaySummary(): String {
        return try {
            val dateString = getTodayDateString()
            val file = File(summariesDir, "$dateString-summary.txt")
            if (file.exists()) {
                file.readText()
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading today's summary", e)
            ""
        }
    }
    
    /**
     * 获取所有记录文件列表
     */
    fun getAllRecordFiles(): List<File> {
        return try {
            recordsDir.listFiles()?.filter { it.isFile && it.extension == "txt" }
                ?.sortedByDescending { it.name } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting record files", e)
            emptyList()
        }
    }
    
    /**
     * 获取所有摘要文件列表
     */
    fun getAllSummaryFiles(): List<File> {
        return try {
            summariesDir.listFiles()?.filter { it.isFile && it.extension == "txt" }
                ?.sortedByDescending { it.name } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting summary files", e)
            emptyList()
        }
    }
    
    /**
     * 删除指定日期的记录
     */
    fun deleteRecord(date: String): Boolean {
        return try {
            val file = File(recordsDir, "$date.txt")
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting record for $date", e)
            false
        }
    }
    
    /**
     * 清空指定日期的记录内容
     */
    fun clearRecord(date: String) {
        try {
            val file = File(recordsDir, "$date.txt")
            file.writeText("")
            Log.d(TAG, "Cleared record for $date")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing record for $date", e)
            throw e
        }
    }
    
    /**
     * 追加内容到指定日期的记录
     */
    fun appendToRecord(date: String, content: String) {
        try {
            val file = File(recordsDir, "$date.txt")
            file.appendText(content)
            Log.d(TAG, "Appended to $date record: $content")
        } catch (e: Exception) {
            Log.e(TAG, "Error appending to record for $date", e)
            throw e
        }
    }
    
    /**
     * 获取记录目录路径
     */
    fun getRecordsDirectoryPath(): String {
        return recordsDir.absolutePath
    }
    
    /**
     * 获取摘要目录路径
     */
    fun getSummariesDirectoryPath(): String {
        return summariesDir.absolutePath
    }
    
    /**
     * 获取音频段目录
     */
    fun getAudioSegmentsDirectory(): File {
        return audioSegmentsDir
    }
    
    /**
     * 获取音频段目录路径
     */
    fun getAudioSegmentsDirectoryPath(): String {
        return audioSegmentsDir.absolutePath
    }
    
    /**
     * 清理旧的音频段文件（保留最近N小时的）
     */
    fun cleanupOldAudioSegments(hoursToKeep: Int = 1) {
        try {
            val cutoffTime = System.currentTimeMillis() - (hoursToKeep * 60 * 60 * 1000L)
            audioSegmentsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoffTime) {
                    file.delete()
                    Log.d(TAG, "Deleted old audio segment: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up audio segments", e)
        }
    }
}
