package com.dailymate.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dailymate.app.data.PreferencesManager
import com.dailymate.app.utils.FileManager
import com.dailymate.app.utils.SummaryGenerator
import kotlinx.coroutines.flow.first

/**
 * 每日摘要生成Worker
 * 使用WorkManager在指定时间自动生成摘要
 */
class DailySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "DailySummaryWorker"
        const val WORK_NAME = "daily_summary_work"
    }
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting daily summary generation")
        
        return try {
            val preferencesManager = PreferencesManager(applicationContext)
            val config = preferencesManager.appConfigFlow.first()
            
            if (!config.enableDailySummary) {
                Log.d(TAG, "Daily summary is disabled")
                return Result.success()
            }
            
            val fileManager = FileManager(applicationContext)
            val todayRecord = fileManager.readTodayRecord()
            
            if (todayRecord.isEmpty()) {
                Log.d(TAG, "No records for today, skipping summary")
                return Result.success()
            }
            
            // 生成摘要
            val summaryGenerator = SummaryGenerator(applicationContext)
            val summary = summaryGenerator.generateSummary(
                content = todayRecord,
                style = config.summaryStyle,
                apiBaseUrl = config.apiBaseUrl,
                apiKey = config.apiKey,
                modelName = config.modelName
            )
            
            // 保存摘要
            fileManager.saveTodaySummary(summary)
            
            Log.d(TAG, "Daily summary generated successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating daily summary", e)
            Result.retry()
        }
    }
}
