package com.dailymate.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.dailymate.app.utils.WorkManagerHelper

/**
 * Application类
 * 初始化应用级别的配置
 */
class DailyMateApplication : Application() {
    
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "voice_recording_channel"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleDailySummary()
    }
    
    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 调度每日摘要任务
     */
    private fun scheduleDailySummary() {
        WorkManagerHelper.scheduleDailySummary(this)
    }
}
