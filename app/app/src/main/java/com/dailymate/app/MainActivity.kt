package com.dailymate.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import com.dailymate.app.data.RecordingModes
import com.dailymate.app.service.ContinuousVoiceRecordingService
import com.dailymate.app.service.VoiceRecordingService
import com.dailymate.app.ui.MainScreen
import com.dailymate.app.ui.MainViewModel
import com.dailymate.app.ui.theme.DailyMateTheme
import com.dailymate.app.utils.FileManager
import com.dailymate.app.utils.SummaryGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 主Activity
 * 处理权限请求和服务控制
 */
class MainActivity : ComponentActivity() {
    
    private val viewModel: MainViewModel by viewModels()
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 权限请求启动器
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startVoiceRecordingService()
        } else {
            Toast.makeText(
                this,
                getString(R.string.error_permission_denied),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            DailyMateTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        viewModel = viewModel,
                        onStartService = { requestPermissionsAndStartService() },
                        onStopService = { stopVoiceRecordingService() },
                        onViewRecords = { viewRecords() },
                        onGenerateSummary = { generateSummary() }
                    )
                }
            }
        }
        
        // 检查服务运行状态
        checkServiceStatus()
    }
    
    override fun onResume() {
        super.onResume()
        checkServiceStatus()
    }
    
    /**
     * 检查服务运行状态
     */
    private fun checkServiceStatus() {
        val isRunning = VoiceRecordingService.isServiceRunning || 
                       ContinuousVoiceRecordingService.isServiceRunning
        viewModel.updateServiceRunningState(isRunning)
    }
    
    /**
     * 请求权限并启动服务
     */
    private fun requestPermissionsAndStartService() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )
        
        // Android 13+ 需要通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // 检查权限
        val needRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needRequest) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            startVoiceRecordingService()
        }
    }
    
    /**
     * 启动语音录制服务（根据配置选择模式）
     */
    private fun startVoiceRecordingService() {
        try {
            val config = viewModel.appConfig.value
            val serviceClass = when (config.recordingMode) {
                RecordingModes.CONTINUOUS -> ContinuousVoiceRecordingService::class.java
                else -> VoiceRecordingService::class.java
            }
            
            val modeName = RecordingModes.options[config.recordingMode] ?: "标准模式"
            
            val intent = Intent(this, serviceClass)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            viewModel.updateServiceRunningState(true)
            Toast.makeText(this, "服务已启动 ($modeName)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.error_service_start_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * 停止语音录制服务（停止所有可能运行的服务）
     */
    private fun stopVoiceRecordingService() {
        // 停止标准模式服务
        val standardIntent = Intent(this, VoiceRecordingService::class.java)
        stopService(standardIntent)
        
        // 停止连续模式服务
        val continuousIntent = Intent(this, ContinuousVoiceRecordingService::class.java)
        stopService(continuousIntent)
        
        viewModel.updateServiceRunningState(false)
        Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 查看记录
     */
    private fun viewRecords() {
        val intent = Intent(this, RecordsActivity::class.java)
        startActivity(intent)
    }
    
    /**
     * 生成摘要
     */
    private fun generateSummary() {
        Toast.makeText(this, "正在生成摘要...", Toast.LENGTH_SHORT).show()
        
        activityScope.launch {
            try {
                val fileManager = FileManager(this@MainActivity)
                val todayRecord = fileManager.readTodayRecord()
                
                if (todayRecord.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "今日暂无记录",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                
                // 获取当前配置
                val config = viewModel.appConfig.value
                val summaryGenerator = SummaryGenerator(this@MainActivity)
                
                val summary = summaryGenerator.generateSummary(
                    content = todayRecord,
                    style = config.summaryStyle,
                    apiBaseUrl = config.apiBaseUrl,
                    apiKey = config.apiKey,
                    modelName = config.modelName
                )
                
                fileManager.saveTodaySummary(summary)
                
                Toast.makeText(
                    this@MainActivity,
                    "摘要生成成功！",
                    Toast.LENGTH_SHORT
                ).show()
                
                // 可以在这里显示摘要内容
                // 例如：打开一个新的Activity或Dialog显示摘要
                
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "生成摘要失败: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 取消所有协程
        activityScope.coroutineContext.cancel()
    }
}
