package com.dailymate.app.utils

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dailymate.app.worker.DailySummaryWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager辅助类
 * 管理定时任务的调度
 */
object WorkManagerHelper {
    
    /**
     * 调度每日摘要任务
     * @param context 上下文
     * @param hour 小时（0-23）
     * @param minute 分钟（0-59）
     */
    fun scheduleDailySummary(context: Context, hour: Int = 23, minute: Int = 59) {
        // 计算到下次执行的延迟时间
        val currentTime = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            
            // 如果目标时间已过，设置为明天
            if (before(currentTime)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        val initialDelay = targetTime.timeInMillis - currentTime.timeInMillis
        
        // 创建每日重复的工作请求
        val dailySummaryWork = PeriodicWorkRequestBuilder<DailySummaryWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        
        // 使用唯一名称调度工作，如果已存在则替换
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DailySummaryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            dailySummaryWork
        )
    }
    
    /**
     * 取消每日摘要任务
     */
    fun cancelDailySummary(context: Context) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(DailySummaryWorker.WORK_NAME)
    }
}
