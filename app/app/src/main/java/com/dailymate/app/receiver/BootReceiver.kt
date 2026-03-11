package com.dailymate.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.dailymate.app.data.PreferencesManager
import com.dailymate.app.service.VoiceRecordingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机自启动接收器
 * 在设备启动完成后自动启动服务（如果用户启用了该选项）
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, checking auto-start setting")
            
            scope.launch {
                try {
                    val preferencesManager = PreferencesManager(context)
                    val config = preferencesManager.appConfigFlow.first()
                    
                    if (config.autoStartService) {
                        Log.d(TAG, "Auto-start enabled, starting service")
                        startVoiceRecordingService(context)
                    } else {
                        Log.d(TAG, "Auto-start disabled")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking auto-start setting", e)
                }
            }
        }
    }
    
    /**
     * 启动语音录制服务
     */
    private fun startVoiceRecordingService(context: Context) {
        try {
            val serviceIntent = Intent(context, VoiceRecordingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "Service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service", e)
        }
    }
}
