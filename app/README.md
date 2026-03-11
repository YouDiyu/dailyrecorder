# DailyMate - 智能日常记录助手

DailyMate是一款Android应用，能够自动记录每日事件并通过AI生成摘要。应用采用Kotlin + Jetpack Compose开发，支持后台持续监听和智能摘要生成。

## 功能特性

### 核心功能
1. **配置主界面（Jetpack Compose）**
   - AI模型选择（Google Speech-to-Text、OpenAI Whisper、本地语音识别）
   - 总结风格配置（简洁、详细、专业、轻松、正式）
   - 存储路径设置
   - 开机自启动选项

2. **后台语音监听服务**
   - 持续监听环境音频（包括息屏状态）
   - 实时语音转文本（使用Android SpeechRecognizer）
   - 按日期自动保存原始记录（格式：yyyy-MM-dd.txt）
   - 前台服务保活机制
   - WakeLock确保后台运行

3. **每日自动摘要生成**
   - 每日23:59自动生成摘要
   - 支持多种摘要风格
   - 可选OpenAI API或本地摘要算法
   - 摘要文件独立存储

## 技术栈

- **语言**: Kotlin
- **UI框架**: Jetpack Compose + Material3
- **最小SDK**: API 26 (Android 8.0)
- **目标SDK**: API 34 (Android 14)
- **架构组件**:
  - ViewModel + StateFlow
  - DataStore (配置持久化)
  - WorkManager (定时任务)
  - Coroutines (异步处理)
- **依赖库**:
  - OkHttp (网络请求)
  - Gson (JSON解析)

## 项目结构

```
app/src/main/java/com/dailymate/app/
├── data/                      # 数据层
│   ├── AppConfig.kt          # 配置数据类
│   └── PreferencesManager.kt # DataStore管理
├── ui/                        # UI层
│   ├── MainScreen.kt         # 主界面Compose UI
│   ├── MainViewModel.kt      # 主界面ViewModel
│   └── theme/                # 主题配置
├── service/                   # 服务层
│   └── VoiceRecordingService.kt  # 语音录制前台服务
├── receiver/                  # 广播接收器
│   └── BootReceiver.kt       # 开机自启动
├── worker/                    # 后台任务
│   └── DailySummaryWorker.kt # 每日摘要生成Worker
├── utils/                     # 工具类
│   ├── FileManager.kt        # 文件管理
│   ├── SummaryGenerator.kt   # 摘要生成器
│   └── WorkManagerHelper.kt  # WorkManager辅助
├── MainActivity.kt            # 主Activity
└── DailyMateApplication.kt   # Application类
```

## 权限说明

应用需要以下权限：

- `RECORD_AUDIO`: 录音权限，用于语音识别
- `FOREGROUND_SERVICE`: 前台服务权限
- `FOREGROUND_SERVICE_MICROPHONE`: 麦克风前台服务类型
- `POST_NOTIFICATIONS`: 通知权限（Android 13+）
- `WAKE_LOCK`: 唤醒锁，保持服务运行
- `RECEIVE_BOOT_COMPLETED`: 开机自启动

## 隐私合规

- ✅ 所有音频数据仅在本地处理，不上传服务器
- ✅ 语音识别使用系统自带的SpeechRecognizer
- ✅ 文件存储在应用专属目录
- ✅ 需要用户显式授权录音权限
- ✅ 前台服务显示持续通知

## 安装与运行

### 前置要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK API 34

### 构建步骤

1. 克隆项目
```bash
git clone <repository-url>
cd daily_recorder
```

2. 在Android Studio中打开项目

3. 同步Gradle依赖

4. 连接Android设备或启动模拟器

5. 运行应用
```bash
./gradlew installDebug
```

## 配置说明

### AI摘要配置（可选）

如需使用OpenAI API生成摘要，请在 `SummaryGenerator.kt` 中配置API密钥：

```kotlin
private const val API_KEY = "your-openai-api-key"
```

**注意**: 如未配置API密钥，应用将自动使用本地摘要算法。

### 存储路径

默认存储路径：
- 记录文件: `/storage/emulated/0/Android/data/com.dailymate.app/files/DailyMate/Records/`
- 摘要文件: `/storage/emulated/0/Android/data/com.dailymate.app/files/DailyMate/Summaries/`

## 使用指南

### 首次使用

1. 启动应用
2. 授予录音和通知权限
3. 配置AI模型和总结风格
4. 点击"启动监听服务"开始记录

### 日常使用

- 应用会在后台持续监听并记录语音
- 每日23:59自动生成摘要
- 可随时点击"生成今日摘要"手动生成
- 点击"查看记录"查看历史记录

### 服务管理

- 服务启动后会显示前台通知
- 可在通知栏或应用内停止服务
- 开启"开机自动启动"后，设备重启会自动启动服务

## 性能优化

### 电池优化
- 使用PARTIAL_WAKE_LOCK而非FULL_WAKE_LOCK
- 前台服务优先级设置为LOW
- 语音识别错误后智能重启

### 内存优化
- 使用协程处理异步任务
- 及时释放资源
- 文件按日期分割存储

## 注意事项

### 语音识别限制
- 依赖设备的语音识别服务
- 需要网络连接（部分设备）
- 识别准确度受环境噪音影响

### 后台运行
- 部分厂商ROM可能限制后台服务
- 建议在电池优化中将应用设为"不限制"
- 华为、小米等设备需额外配置自启动权限

### 存储空间
- 长期使用会占用一定存储空间
- 建议定期清理旧记录
- 可在设置中修改存储路径

## 故障排除

### 服务无法启动
1. 检查是否授予录音权限
2. 检查是否授予通知权限（Android 13+）
3. 查看Logcat日志排查错误

### 语音识别不工作
1. 确认设备支持语音识别
2. 检查网络连接
3. 尝试重启服务

### 摘要生成失败
1. 检查是否有今日记录
2. 如使用OpenAI API，检查API密钥和网络
3. 查看错误提示信息

## 开发计划

- [ ] 实现查看记录界面
- [ ] 添加记录搜索功能
- [ ] 支持导出记录和摘要
- [ ] 添加数据统计和可视化
- [ ] 支持更多AI模型
- [ ] 添加语音播放功能
- [ ] 支持多语言

## 贡献指南

欢迎提交Issue和Pull Request！

## 许可证

本项目采用MIT许可证。

## 联系方式

如有问题或建议，请通过以下方式联系：
- 提交Issue
- 发送邮件至：[your-email]

---

**免责声明**: 本应用仅供学习和个人使用，请遵守当地法律法规，尊重他人隐私。
