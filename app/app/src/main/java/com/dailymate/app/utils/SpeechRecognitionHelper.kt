package com.dailymate.app.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 语音识别辅助类
 * 提供通过Web API进行语音识别的功能
 */
object SpeechRecognitionHelper {
    
    private const val TAG = "SpeechRecognitionHelper"
    private const val API_URL = "https://www.joyou.vip:9204/speech_to_text"
    private const val API_TIMEOUT = 120L // API超时时间（秒）- 增加到5分钟以处理长音频
    private const val API_TOKEN = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjI1NDgzMTMwLWQxYzAtNGZlNS05ZjJlLWRmNjU3OTFkMDJlNSJ9.eyJpc3MiOiJodHRwczovL2FwaS5jb3plLmNuIiwiYXVkIjpbInNrb3ZRZzRKSTJqa1FLWGJzQ1FiNUhKWHJkbmw0ZDRXIl0sImV4cCI6ODIxMDI2Njg3Njc5OSwiaWF0IjoxNzY3OTQ1NzYxLCJzdWIiOiJzcGlmZmU6Ly9hcGkuY296ZS5jbi93b3JrbG9hZF9pZGVudGl0eS9pZDo3NTkzMjQ5MzczNzY5Njk1MjUxIiwic3JjIjoiaW5ib3VuZF9hdXRoX2FjY2Vzc190b2tlbl9pZDo3NTkzMjY5MjI1MDMzMDM5OTE0In0.S13jgBz--jxJmtz-QZe1QFZOCMimCmVgh_zx1dcjvaGAuTWiKY0-cIOAJuh8TcdQxxZOP4l-HdtS3QAbATW2woP9KPmEhpq6QYi9csq9sfcCfE1DHgRb7ZruyALtwM1HzVEu9c8piAmer6aD2OjfduHYSmd3UK01eTeUFrTvryJQnzO745cx_M_NseGNNITjpCXmekv30WQC4mezdviKMwfpznhdPAaqclZQDRUVjil_flyWR0FDUZZtpso_aaSWrNqZ_6nlKSK3IlpP2QO20wI-DzrKjr7kIRnP-stQ4viZnPiNxCcqdgqQ-H3uAmd1U2uWQm7ZhowCcpmvxjcp5w"
    private const val USER_ID = "dailymate_user"
    
    private val httpClient: OkHttpClient by lazy {
        createUnsafeOkHttpClient()
    }
    
    /**
     * 通过Web API识别音频文件
     * 
     * @param audioFilePath 音频文件的绝对路径
     * @return 识别结果文本列表，失败返回null
     */
    suspend fun recognizeAudioFile(audioFilePath: String?): List<String>? {
        return withContext(Dispatchers.IO) {
            try {
                val audioFile = File(audioFilePath)
                if (!audioFile.exists()) {
                    Log.e(TAG, "Audio file does not exist: $audioFilePath")
                    return@withContext null
                }

                // 创建请求
                val requestBody = okhttp3.MultipartBody.Builder()
                    .setType(okhttp3.MultipartBody.FORM)
                    .addFormDataPart("audio_file", audioFile.name,
                        audioFile.asRequestBody("audio/wav".toMediaType()))
                    .addFormDataPart("uid", USER_ID)
                    .build()

                val request = Request.Builder()
                    .url(API_URL)
                    .header("Authorization", "Bearer $API_TOKEN")
                    .post(requestBody)
                    .build()

                Log.d(TAG, "Sending audio file recognition request to API...")

                // 发送请求
                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "API Response: $responseBody")

                    // 解析响应
                    val responseJson = JSONObject(responseBody ?: "{}")
                    val textField = responseJson.opt("text")

                    if (textField is JSONArray) {
                        val resultList = mutableListOf<String>()
                        for (i in 0 until textField.length()) {
                            val item = textField.optJSONObject(i)
                            if (item != null) {
                                val keyValue = item.optString("text")
                                if (keyValue.isNotEmpty()) {
                                    resultList.add(keyValue)
                                }
                            }
                        }
                        Log.d(TAG, "Recognition successful: $resultList")
                        return@withContext resultList
                    } else {
                        Log.w(TAG, "API returned unexpected text format")
                        return@withContext null
                    }
                } else {
                    Log.e(TAG, "API request failed: ${response.code} ${response.toString()}")
                    return@withContext null
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error recognizing audio file via API", e)
                return@withContext null
            }
        }
    }

    /**
     * 创建安全的OkHttpClient（生产环境推荐）
     * 使用系统默认的证书验证
     */
    fun createSecureOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(API_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(API_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(API_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }
    
    /**
     * 创建不进行SSL验证的OkHttpClient
     * ⚠️ 警告：仅用于开发/测试环境
     */
    fun createUnsafeOkHttpClient(): OkHttpClient {
        return try {
            Log.w(TAG, "⚠️ 使用不安全的SSL配置！仅用于开发/测试环境")
            
            // 创建信任所有证书的TrustManager
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    @Throws(java.security.cert.CertificateException::class)
                    override fun checkClientTrusted(
                        chain: Array<java.security.cert.X509Certificate>,
                        authType: String
                    ) {
                    }

                    @Throws(java.security.cert.CertificateException::class)
                    override fun checkServerTrusted(
                        chain: Array<java.security.cert.X509Certificate>,
                        authType: String
                    ) {
                    }

                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> {
                        return arrayOf()
                    }
                }
            )

            // 安装信任所有证书的TrustManager
            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())

            // 创建SSLSocketFactory
            val sslSocketFactory = sslContext.socketFactory

            // 创建信任所有主机名的HostnameVerifier
            val hostnameVerifier = javax.net.ssl.HostnameVerifier { hostname, _ ->
                Log.d(TAG, "Accepting hostname: $hostname")
                true
            }

            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                .hostnameVerifier(hostnameVerifier)
                .connectTimeout(API_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(API_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(API_TIMEOUT, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create unsafe OkHttpClient", e)
            throw RuntimeException("Failed to create OkHttpClient: ${e.message}", e)
        }
    }
}
