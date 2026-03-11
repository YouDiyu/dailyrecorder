package com.dailymate.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dailymate.app.DailyMateApplication
import com.dailymate.app.MainActivity
import com.dailymate.app.data.PreferencesManager
import com.dailymate.app.utils.FileManager
import com.dailymate.app.utils.SpeechRecognitionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * 连续语音录制服务
 * 使用AudioRecord持续录制音频，只保存有效语音片段
 * 通过Web API进行语音识别
 */
class ContinuousVoiceRecordingService : Service() {
    
    companion object {
        private const val TAG = "ContinuousVoiceService"
        private const val NOTIFICATION_ID = 1002
        
        // 音频参数
        private const val SAMPLE_RATE = 16000 // 采样率
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // 录制参数
        private const val MAX_SEGMENT_DURATION_MS = 600000L // 最大段长度600秒
        private const val NO_SOUND_TIMEOUT_MS = 10000L // 10秒无有效音频则保存
        private const val MIN_AUDIO_LEVEL = 100 // 最小音频电平（用于检测有效语音）
        
        @Volatile
        var isServiceRunning = false
            private set
    }
    
    private lateinit var audioRecord: AudioRecord
    private lateinit var fileManager: FileManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var httpClient: OkHttpClient
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isRecording = AtomicBoolean(false)

    private var currentLanguage = "zh-CN"
    private var S2TapiToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjI1NDgzMTMwLWQxYzAtNGZlNS05ZjJlLWRmNjU3OTFkMDJlNSJ9.eyJpc3MiOiJodHRwczovL2FwaS5jb3plLmNuIiwiYXVkIjpbInNrb3ZRZzRKSTJqa1FLWGJzQ1FiNUhKWHJkbmw0ZDRXIl0sImV4cCI6ODIxMDI2Njg3Njc5OSwiaWF0IjoxNzY3OTQ1NzYxLCJzdWIiOiJzcGlmZmU6Ly9hcGkuY296ZS5jbi93b3JrbG9hZF9pZGVudGl0eS9pZDo3NTkzMjQ5MzczNzY5Njk1MjUxIiwic3JjIjoiaW5ib3VuZF9hdXRoX2FjY2Vzc190b2tlbl9pZDo3NTkzMjY5MjI1MDMzMDM5OTE0In0.S13jgBz--jxJmtz-QZe1QFZOCMimCmVgh_zx1dcjvaGAuTWiKY0-cIOAJuh8TcdQxxZOP4l-HdtS3QAbATW2woP9KPmEhpq6QYi9csq9sfcCfE1DHgRb7ZruyALtwM1HzVEu9c8piAmer6aD2OjfduHYSmd3UK01eTeUFrTvryJQnzO745cx_M_NseGNNITjpCXmekv30WQC4mezdviKMwfpznhdPAaqclZQDRUVjil_flyWR0FDUZZtpso_aaSWrNqZ_6nlKSK3IlpP2QO20wI-DzrKjr7kIRnP-stQ4viZnPiNxCcqdgqQ-H3uAmd1U2uWQm7ZhowCcpmvxjcp5w" // API Token
    private var userId = "dailymate_user" // 用户ID
    
    // 音频缓冲区
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    ) * 2
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        fileManager = FileManager(this)
        preferencesManager = PreferencesManager(this)
        
        // ✅ 使用SpeechRecognitionHelper提供的HTTP客户端
        httpClient = SpeechRecognitionHelper.createUnsafeOkHttpClient()
        
        // 加载配置
        serviceScope.launch {
            preferencesManager.appConfigFlow.collect { config ->
                currentLanguage = config.recognitionLanguage
                // apiToken = config.apiKey
                Log.d(TAG, "Config updated - Language: $currentLanguage")
            }
        }
        
        initializeAudioRecord()
        acquireWakeLock()
        
        isServiceRunning = true
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        
        startForeground(NOTIFICATION_ID, createNotification())
        startRecording()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        
        stopRecording()
        releaseAudioRecord()
        releaseWakeLock()
        serviceScope.cancel()
        
        isServiceRunning = false
    }
    
    /**
     * 初始化AudioRecord
     */
    private fun initializeAudioRecord() {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
            } else {
                Log.d(TAG, "AudioRecord initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioRecord", e)
        }
    }
    
    /**
     * 开始录制
     */
    private fun startRecording() {
        if (isRecording.compareAndSet(false, true)) {
            serviceScope.launch {
                try {
                    audioRecord.startRecording()
                    Log.d(TAG, "Recording started")
                    recordingLoop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting recording", e)
                    isRecording.set(false)
                }
            }
        }
    }
    
    /**
     * 停止录制
     */
    private fun stopRecording() {
        if (isRecording.compareAndSet(true, false)) {
            try {
                audioRecord.stop()
                Log.d(TAG, "Recording stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording", e)
            }
        }
    }
    
    /**
     * 录制循环 - 只保存有效音频
     */
    private suspend fun recordingLoop() {
        val segmentBuffer = mutableListOf<Short>() // 只包含有效音频
        val readBuffer = ShortArray(bufferSize / 2)
        var segmentStartTime = 0L // segment开始时间（首次有效音频时）
        var lastValidSoundTime = 0L // 最后一次检测到有效音频的时间
        
        while (isRecording.get()) {
            try {
                // 读取音频数据
                val readCount = audioRecord.read(readBuffer, 0, readBuffer.size)
                
                if (readCount > 0) {
                    // 检测音频电平
                    val audioLevel = calculateAudioLevel(readBuffer, readCount)
                    val hasValidSound = audioLevel > MIN_AUDIO_LEVEL
                    
                    if (hasValidSound) {
                        val currentTime = System.currentTimeMillis()
                        
                        // 如果是segment的第一个有效音频，记录开始时间
                        if (segmentBuffer.isEmpty()) {
                            segmentStartTime = currentTime
                            Log.d(TAG, "Started new audio segment")
                        }
                        
                        // 添加有效音频到segment
                        for (i in 0 until readCount) {
                            segmentBuffer.add(readBuffer[i])
                        }
                        
                        lastValidSoundTime = currentTime
                    }
                    
                    // 检查是否需要保存segment
                    if (segmentBuffer.isNotEmpty()) {
                        val currentTime = System.currentTimeMillis()
                        val segmentDuration = currentTime - segmentStartTime
                        val timeSinceLastSound = currentTime - lastValidSoundTime
                        
                        // 触发条件：超过30秒 或 超过10秒无有效音频
                        if (segmentDuration >= MAX_SEGMENT_DURATION_MS || 
                            timeSinceLastSound >= NO_SOUND_TIMEOUT_MS) {
                            
                            Log.d(TAG, "Segment complete - Duration: ${segmentDuration}ms, " +
                                      "Size: ${segmentBuffer.size} samples, " +
                                      "Reason: ${if (segmentDuration >= MAX_SEGMENT_DURATION_MS) "max duration" else "no sound timeout"}")
                            
                            // 处理音频段
                            processAudioSegment(segmentBuffer.toShortArray())
                            
                            // 清空segment，准备下一段
                            segmentBuffer.clear()
                            segmentStartTime = 0L
                            lastValidSoundTime = 0L
                        }
                    }
                }
                
                // 短暂延迟避免CPU占用过高
                delay(10)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in recording loop", e)
                delay(1000)
            }
        }
    }
    
    /**
     * 计算音频电平
     */
    private fun calculateAudioLevel(buffer: ShortArray, count: Int): Int {
        var sum = 0L
        for (i in 0 until count) {
            sum += abs(buffer[i].toInt())
        }
        return (sum / count).toInt()
    }
    
    /**
     * 处理音频段
     */
    private suspend fun processAudioSegment(audioData: ShortArray) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing audio segment with ${audioData.size} samples")
                
                // 保存为PCM和WAV格式
                val wavFile = saveAudioSegmentAsWAV(audioData)
                
                Log.d(TAG, "Saved audio files - WAV: ${wavFile?.name}")
                
                // ✅ 使用SpeechRecognitionHelper进行语音识别
                val recognizedTexts = SpeechRecognitionHelper.recognizeAudioFile(wavFile?.absolutePath)

                if (recognizedTexts != null && recognizedTexts.isNotEmpty()) {
                    // 保存识别结果
                    saveRecognizedTexts(wavFile?.name ?: "", recognizedTexts)
                    Log.d(TAG, "Recognition successful and saved")
                } else {
                    Log.w(TAG, "Recognition failed or returned empty result")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing audio segment", e)
            }
        }
    }
    
    /**
     * 保存音频段为PCM格式
     */
    private fun saveAudioSegmentAsPCM(audioData: ShortArray): File? {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val audioDir = fileManager.getAudioSegmentsDirectory()
            val audioFile = File(audioDir, "segment_$timestamp.pcm")
            
            FileOutputStream(audioFile).use { fos ->
                val byteBuffer = ByteArray(audioData.size * 2)
                for (i in audioData.indices) {
                    byteBuffer[i * 2] = (audioData[i].toInt() and 0xFF).toByte()
                    byteBuffer[i * 2 + 1] = ((audioData[i].toInt() shr 8) and 0xFF).toByte()
                }
                fos.write(byteBuffer)
            }
            
            Log.d(TAG, "PCM audio saved: ${audioFile.name}, size: ${audioFile.length()} bytes")
            return audioFile
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving PCM audio", e)
            return null
        }
    }
    
    /**
     * 保存音频段为WAV格式
     */
    private fun saveAudioSegmentAsWAV(audioData: ShortArray): File? {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val audioDir = fileManager.getAudioSegmentsDirectory()
            val audioFile = File(audioDir, "segment_$timestamp.wav")
            
            FileOutputStream(audioFile).use { fos ->
                // 计算数据大小
                val dataSize = audioData.size * 2 // 每个Short是2字节
                val fileSize = 36 + dataSize // WAV文件头36字节 + 数据
                
                // 写入WAV文件头
                // RIFF chunk
                fos.write("RIFF".toByteArray())
                fos.write(intToByteArray(fileSize), 0, 4) // 文件大小-8
                fos.write("WAVE".toByteArray())
                
                // fmt chunk
                fos.write("fmt ".toByteArray())
                fos.write(intToByteArray(16), 0, 4) // fmt chunk大小
                fos.write(shortToByteArray(1), 0, 2) // 音频格式 (1 = PCM)
                fos.write(shortToByteArray(1), 0, 2) // 声道数 (1 = 单声道)
                fos.write(intToByteArray(SAMPLE_RATE), 0, 4) // 采样率
                fos.write(intToByteArray(SAMPLE_RATE * 2), 0, 4) // 字节率 (采样率 * 声道数 * 位深度/8)
                fos.write(shortToByteArray(2), 0, 2) // 块对齐 (声道数 * 位深度/8)
                fos.write(shortToByteArray(16), 0, 2) // 位深度
                
                // data chunk
                fos.write("data".toByteArray())
                fos.write(intToByteArray(dataSize), 0, 4) // 数据大小
                
                // 写入PCM数据
                for (sample in audioData) {
                    fos.write(shortToByteArray(sample.toInt()), 0, 2)
                }
            }
            
            Log.d(TAG, "WAV audio saved: ${audioFile.name}, size: ${audioFile.length()} bytes")
            return audioFile
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving WAV audio", e)
            return null
        }
    }
    
    /**
     * 将Int转换为小端字节数组
     */
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
    
    /**
     * 将Short转换为小端字节数组
     */
    private fun shortToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte()
        )
    }
    
    /**
     * 将PCM音频数据转换为WAV格式的字节数组
     */
    private fun convertPCMToWAVBytes(audioData: ShortArray): ByteArray {
        val dataSize = audioData.size * 2 // 每个Short是2字节
        val fileSize = 36 + dataSize // WAV文件头36字节 + 数据
        val totalSize = 44 + dataSize // 完整WAV文件大小
        
        val wavBytes = ByteArray(totalSize)
        var offset = 0
        
        // RIFF chunk
        System.arraycopy("RIFF".toByteArray(), 0, wavBytes, offset, 4)
        offset += 4
        System.arraycopy(intToByteArray(fileSize), 0, wavBytes, offset, 4)
        offset += 4
        System.arraycopy("WAVE".toByteArray(), 0, wavBytes, offset, 4)
        offset += 4
        
        // fmt chunk
        System.arraycopy("fmt ".toByteArray(), 0, wavBytes, offset, 4)
        offset += 4
        System.arraycopy(intToByteArray(16), 0, wavBytes, offset, 4) // fmt chunk大小
        offset += 4
        System.arraycopy(shortToByteArray(1), 0, wavBytes, offset, 2) // 音频格式 (1 = PCM)
        offset += 2
        System.arraycopy(shortToByteArray(1), 0, wavBytes, offset, 2) // 声道数 (1 = 单声道)
        offset += 2
        System.arraycopy(intToByteArray(SAMPLE_RATE), 0, wavBytes, offset, 4) // 采样率
        offset += 4
        System.arraycopy(intToByteArray(SAMPLE_RATE * 2), 0, wavBytes, offset, 4) // 字节率
        offset += 4
        System.arraycopy(shortToByteArray(2), 0, wavBytes, offset, 2) // 块对齐
        offset += 2
        System.arraycopy(shortToByteArray(16), 0, wavBytes, offset, 2) // 位深度
        offset += 2
        
        // data chunk
        System.arraycopy("data".toByteArray(), 0, wavBytes, offset, 4)
        offset += 4
        System.arraycopy(intToByteArray(dataSize), 0, wavBytes, offset, 4)
        offset += 4
        
        // PCM数据
        for (sample in audioData) {
            val sampleBytes = shortToByteArray(sample.toInt())
            wavBytes[offset++] = sampleBytes[0]
            wavBytes[offset++] = sampleBytes[1]
        }
        
        return wavBytes
    }
    
    /**
     * 保存识别的文本
     */
    private suspend fun saveRecognizedText(text: String) {
        withContext(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val entry = "[$timestamp] $text\n"
                fileManager.appendToTodayRecord(entry)
                Log.d(TAG, "Saved recognized text: $entry")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving recognized text", e)
            }
        }
    }

    /**
     * 保存识别的文本列表
     */
    private suspend fun saveRecognizedTexts(wavFileName: String, recognizedTexts: List<String>) {
        withContext(Dispatchers.IO) {
            try {
                // 去掉文件后缀和前缀 "segment_"
                val timestampRaw = wavFileName.removePrefix("segment_").substring(9, 15)

                // 转换时间格式 HHmmss -> HH:mm:ss
                val timestampFormatted = timestampRaw.chunked(2).joinToString(":")

                // 遍历 recognizedTexts 并拼接时间
                val entries = recognizedTexts.map { text -> "[$timestampFormatted] $text\n" }

                // 保存到 TodayRecord
                entries.forEach { entry ->
                    fileManager.appendToTodayRecord(entry)
                }

                Log.d(TAG, "Saved recognized texts with timestamp: $timestampFormatted")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving recognized texts", e)
            }
        }
    }
    
    /**
     * 释放AudioRecord
     */
    private fun releaseAudioRecord() {
        try {
            if (::audioRecord.isInitialized) {
                audioRecord.release()
                Log.d(TAG, "AudioRecord released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
    }
    
    /**
     * 获取唤醒锁
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DailyMate::ContinuousRecordingWakeLock"
        )
        wakeLock.acquire(10 * 60 * 60 * 1000L)
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
            .setContentTitle("DailyMate 连续录制")
            .setContentText("正在智能录制并识别音频...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

}
