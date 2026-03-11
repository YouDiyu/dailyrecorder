package com.dailymate.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dailymate.app.DailyMateApplication
import com.dailymate.app.MainActivity
import com.dailymate.app.R
import com.dailymate.app.data.PreferencesManager
import com.dailymate.app.utils.FileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音录制前台服务
 * 持续监听环境音频并转换为文本
 * 针对Xiaomi设备优化
 */
class VoiceRecordingService : Service() {
    
    companion object {
        private const val TAG = "VoiceRecordingService"
        private const val NOTIFICATION_ID = 1001
        private const val RESTART_DELAY_NORMAL = 50L // 正常重启延迟（毫秒）
        private const val RESTART_DELAY_ERROR = 2000L // 错误后重启延迟（毫秒）
        private const val RESTART_DELAY_XIAOMI = 1500L // Xiaomi设备重启延迟
        private const val REINIT_INTERVAL = 10 // 每10次识别后重新初始化
        private const val READY_TIMEOUT = 5000L // Ready状态超时（毫秒）
        
        @Volatile
        var isServiceRunning = false
            private set
    }
    
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var fileManager: FileManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var preferencesManager: PreferencesManager
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val isListening = AtomicBoolean(false)
    private val isRestarting = AtomicBoolean(false)
    private var currentLanguage = "zh-CN" // 当前识别语言
    private var consecutiveErrors = 0 // 连续错误计数
    private var recognitionCount = 0 // 识别次数计数
    private var lastReadyTime = 0L // 最后Ready时间
    private val isXiaomiDevice = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate (Device: ${Build.MANUFACTURER} ${Build.MODEL})")
        
        fileManager = FileManager(this)
        preferencesManager = PreferencesManager(this)
        
        // 加载语言配置
        serviceScope.launch {
            preferencesManager.appConfigFlow.collect { config ->
                if (currentLanguage != config.recognitionLanguage) {
                    currentLanguage = config.recognitionLanguage
                    Log.d(TAG, "Language changed to: $currentLanguage")
                    // 重新初始化识别器以应用新语言
                    if (isListening.get()) {
                        stopListening()
                        serviceScope.launch {
                            delay(RESTART_DELAY_XIAOMI)
                            releaseSpeechRecognizer()
                            initializeSpeechRecognizer()
                            startListening()
                        }
                    }
                }
            }
        }
        
        initializeSpeechRecognizer()
        acquireWakeLock()
        
        isServiceRunning = true
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 开始语音识别
        startListening()
        
        // 返回START_STICKY确保服务被系统杀死后会重启
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        
        stopListening()
        releaseSpeechRecognizer()
        releaseWakeLock()
        serviceScope.cancel()
        
        isServiceRunning = false
    }
    
    /**
     * 初始化语音识别器
     */
    private fun initializeSpeechRecognizer() {
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer.setRecognitionListener(createRecognitionListener())
            
            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguage)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, currentLanguage)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                
                // Xiaomi设备优化：增加静音超时时间
                if (isXiaomiDevice) {
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 15000L)
                } else {
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
                }
            }
            
            Log.d(TAG, "SpeechRecognizer initialized with language: $currentLanguage (Xiaomi: $isXiaomiDevice)")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing speech recognizer", e)
        }
    }
    
    /**
     * 创建识别监听器
     */
    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {
            lastReadyTime = System.currentTimeMillis()
            Log.d(TAG, "Ready for speech (count: $recognitionCount)")
            isListening.set(true)
            isRestarting.set(false)
            consecutiveErrors = 0 // 重置错误计数
            
            // 启动监听有效性检测
            scheduleReadyCheck()
        }
        
        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech")
        }
        
        override fun onRmsChanged(rmsdB: Float) {
            // 音量变化，可用于UI显示
        }
        
        override fun onBufferReceived(buffer: ByteArray?) {
            // 接收到音频缓冲区
        }
        
        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech")
            isListening.set(false)
        }
        
        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                SpeechRecognizer.ERROR_AUDIO -> "音频错误"
                SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "语音超时"
                SpeechRecognizer.ERROR_NO_MATCH -> "无匹配"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                else -> "未知错误"
            }
            Log.e(TAG, "Recognition error: $error ($errorMessage), consecutive: $consecutiveErrors")
            isListening.set(false)
            
            // 增加连续错误计数
            consecutiveErrors++
            
            // 错误后重新启动识别
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> {
                    // 无匹配：继续监听，不算作错误
                    Log.d(TAG, "No match detected, continuing to listen...")
                    consecutiveErrors = 0 // 重置错误计数
                    scheduleRestart(RESTART_DELAY_NORMAL)
                }
                
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // 语音超时：正常情况，继续监听
                    Log.d(TAG, "Speech timeout, continuing to listen...")
                    consecutiveErrors = 0 // 重置错误计数
                    scheduleRestart(RESTART_DELAY_NORMAL)
                }
                
                SpeechRecognizer.ERROR_CLIENT -> {
                    // 客户端错误：Xiaomi设备常见，需要更长延迟
                    val delay = if (isXiaomiDevice) RESTART_DELAY_XIAOMI else RESTART_DELAY_ERROR
                    Log.w(TAG, "Client error on ${if (isXiaomiDevice) "Xiaomi" else "other"} device, waiting ${delay}ms")
                    scheduleRestart(delay)
                }
                
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    // 识别器忙：等待后重试
                    Log.w(TAG, "Recognizer busy, waiting...")
                    scheduleRestart(RESTART_DELAY_ERROR)
                }
                
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    // 网络错误：等待较长时间后重试
                    Log.w(TAG, "Network error, waiting longer...")
                    scheduleRestart(RESTART_DELAY_ERROR * 3)
                }
                
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    // 权限不足：停止服务
                    Log.e(TAG, "Insufficient permissions, stopping service")
                    stopSelf()
                }
                
                else -> {
                    // 其他错误：重新初始化识别器
                    Log.w(TAG, "Unknown error, reinitializing...")
                    scheduleReinitialize()
                }
            }
            
            // 如果连续错误过多，重新初始化
            if (consecutiveErrors >= 5) {
                Log.e(TAG, "Too many consecutive errors ($consecutiveErrors), reinitializing...")
                consecutiveErrors = 0
                scheduleReinitialize()
            }
        }
        
        override fun onResults(results: android.os.Bundle?) {
            Log.d(TAG, "onResults called")
            isListening.set(false) // 确保状态正确
            recognitionCount++ // 增加识别计数
            
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                if (matches.isNotEmpty()) {
                    val text = matches[0]
                    Log.d(TAG, "Recognized: $text (count: $recognitionCount)")
                    saveRecognizedText(text)
                    consecutiveErrors = 0 // 重置错误计数
                }
            }
            
            // 每N次识别后重新初始化，防止"假就绪"
            if (recognitionCount >= REINIT_INTERVAL) {
                Log.d(TAG, "Reached $REINIT_INTERVAL recognitions, reinitializing...")
                recognitionCount = 0
                scheduleReinitialize()
            } else {
                // 继续监听
                Log.d(TAG, "Scheduling restart after recognition")
                scheduleRestart(RESTART_DELAY_NORMAL)
            }
        }
        
        override fun onPartialResults(partialResults: android.os.Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                if (matches.isNotEmpty()) {
                    Log.d(TAG, "Partial: ${matches[0]}")
                }
            }
        }
        
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {
            // 其他事件
        }
    }
    
    /**
     * 开始监听
     */
    private fun startListening() {
        if (!isListening.get() && !isRestarting.get()) {
            try {
                speechRecognizer.startListening(recognizerIntent)
                Log.d(TAG, "Started listening")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting listening", e)
                // 启动失败，稍后重试
                scheduleRestart(RESTART_DELAY_ERROR)
            }
        } else {
            Log.w(TAG, "Already listening or restarting, skipping start")
        }
    }
    
    /**
     * 停止监听
     */
    private fun stopListening() {
        if (isListening.get()) {
            try {
                speechRecognizer.stopListening()
                isListening.set(false)
                Log.d(TAG, "Stopped listening")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping listening", e)
            }
        }
    }
    
    /**
     * 计划重启监听
     */
    private fun scheduleRestart(delayMillis: Long) {
        if (isRestarting.compareAndSet(false, true)) {
            Log.d(TAG, "Scheduling restart in ${delayMillis}ms")
            serviceScope.launch {
                delay(delayMillis)
                if (isServiceRunning) {
                    if (!isListening.get()) {
                        Log.d(TAG, "Executing scheduled restart")
                        isRestarting.set(false)
                        startListening()
                    } else {
                        Log.w(TAG, "Already listening, canceling restart")
                        isRestarting.set(false)
                    }
                } else {
                    Log.w(TAG, "Service not running, canceling restart")
                    isRestarting.set(false)
                }
            }
        } else {
            Log.d(TAG, "Restart already scheduled, skipping")
        }
    }
    
    /**
     * 计划重新初始化
     */
    private fun scheduleReinitialize() {
        if (isRestarting.compareAndSet(false, true)) {
            Log.d(TAG, "Scheduling reinitialization...")
            serviceScope.launch {
                delay(RESTART_DELAY_ERROR)
                if (isServiceRunning) {
                    try {
                        Log.d(TAG, "Reinitializing speech recognizer...")
                        releaseSpeechRecognizer()
                        delay(500) // 等待释放完成
                        initializeSpeechRecognizer()
                        isRestarting.set(false)
                        startListening()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reinitializing", e)
                        isRestarting.set(false)
                    }
                } else {
                    isRestarting.set(false)
                }
            }
        }
    }
    
    /**
     * 计划Ready状态检测
     * 检测是否进入"假就绪"状态
     */
    private fun scheduleReadyCheck() {
        serviceScope.launch {
            delay(READY_TIMEOUT)
            // 如果Ready后超时仍无语音输入，可能是假就绪
            val timeSinceReady = System.currentTimeMillis() - lastReadyTime
            if (isListening.get() && timeSinceReady >= READY_TIMEOUT) {
                Log.w(TAG, "Possible fake ready state detected (${timeSinceReady}ms), reinitializing...")
                // 强制重新初始化
                stopListening()
                scheduleReinitialize()
            }
        }
    }
    
    /**
     * 保存识别的文本
     */
    private fun saveRecognizedText(text: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val entry = "[$timestamp] $text\n"
                fileManager.appendToTodayRecord(entry)
                Log.d(TAG, "Saved: $entry")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving text", e)
            }
        }
    }
    
    /**
     * 释放语音识别器
     */
    private fun releaseSpeechRecognizer() {
        try {
            if (::speechRecognizer.isInitialized) {
                speechRecognizer.destroy()
                Log.d(TAG, "Speech recognizer released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing speech recognizer", e)
        }
    }
    
    /**
     * 获取唤醒锁
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DailyMate::VoiceRecordingWakeLock"
        )
        wakeLock.acquire(10 * 60 * 60 * 1000L) // 10小时
        Log.d(TAG, "WakeLock acquired")
    }
    
    /**
     * 释放唤醒锁
     */
    private fun releaseWakeLock() {
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
            Log.d(TAG, "WakeLock released")
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        return NotificationCompat.Builder(this, DailyMateApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
