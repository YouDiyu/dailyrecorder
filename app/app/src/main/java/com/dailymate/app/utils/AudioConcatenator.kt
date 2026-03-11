package com.dailymate.app.utils

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 音频拼接工具类
 * 负责将多个WAV文件拼接成更大的片段
 */
object AudioConcatenator {
    
    private const val TAG = "AudioConcatenator"
    private const val WAV_HEADER_SIZE = 44
    
    /**
     * 获取WAV文件的音频数据大小（不包括头部）
     */
    private fun getAudioDataSize(file: File): Long {
        return if (file.exists() && file.length() > WAV_HEADER_SIZE) {
            file.length() - WAV_HEADER_SIZE
        } else {
            0
        }
    }
    
    /**
     * 获取WAV文件的持续时间（毫秒）
     * 假设：16000 Hz采样率，16位，单声道
     */
    fun getAudioDuration(file: File): Long {
        val dataSize = getAudioDataSize(file)
        // 16000 Hz * 2 bytes = 32000 bytes/second
        return (dataSize * 1000) / 32000
    }
    
    /**
     * 拼接多个WAV文件
     * @param files 要拼接的文件列表（按顺序）
     * @param outputFile 输出文件
     * @return 是否成功
     */
    fun concatenateWavFiles(files: List<File>, outputFile: File): Boolean {
        if (files.isEmpty()) {
            Log.w(TAG, "No files to concatenate")
            return false
        }
        
        try {
            // 计算总数据大小
            var totalDataSize = 0L
            files.forEach { file ->
                totalDataSize += getAudioDataSize(file)
            }
            
            Log.d(TAG, "Concatenating ${files.size} files, total data size: $totalDataSize bytes")
            
            // 创建输出文件
            FileOutputStream(outputFile).use { fos ->
                // 写入WAV头部（使用第一个文件的头部作为模板）
                FileInputStream(files[0]).use { fis ->
                    val header = ByteArray(WAV_HEADER_SIZE)
                    fis.read(header)
                    
                    // 更新数据大小字段
                    val fileSize = (36 + totalDataSize).toInt()
                    header[4] = (fileSize and 0xFF).toByte()
                    header[5] = ((fileSize shr 8) and 0xFF).toByte()
                    header[6] = ((fileSize shr 16) and 0xFF).toByte()
                    header[7] = ((fileSize shr 24) and 0xFF).toByte()
                    
                    val dataSize = totalDataSize.toInt()
                    header[40] = (dataSize and 0xFF).toByte()
                    header[41] = ((dataSize shr 8) and 0xFF).toByte()
                    header[42] = ((dataSize shr 16) and 0xFF).toByte()
                    header[43] = ((dataSize shr 24) and 0xFF).toByte()
                    
                    fos.write(header)
                }
                
                // 拼接所有文件的音频数据
                val buffer = ByteArray(8192)
                files.forEach { file ->
                    FileInputStream(file).use { fis ->
                        // 跳过WAV头部
                        fis.skip(WAV_HEADER_SIZE.toLong())
                        
                        // 复制音频数据
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
            
            Log.d(TAG, "Successfully concatenated to ${outputFile.name}, size: ${outputFile.length()} bytes")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error concatenating WAV files", e)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            return false
        }
    }
    
    /**
     * 使用贪婪算法将音频文件分组
     * @param files 原始音频文件列表（已按时间排序）
     * @param maxDurationMs 每个片段的最大持续时间（毫秒）
     * @return 分组后的文件列表
     */
    fun groupFilesByDuration(files: List<File>, maxDurationMs: Long): List<List<File>> {
        if (files.isEmpty()) {
            return emptyList()
        }
        
        val groups = mutableListOf<List<File>>()
        var currentGroup = mutableListOf<File>()
        var currentDuration = 0L
        
        for (file in files) {
            val fileDuration = getAudioDuration(file)
            
            // 如果单个文件就超过最大时长，跳过该文件
            if (fileDuration > maxDurationMs) {
                Log.w(TAG, "File ${file.name} duration ($fileDuration ms) exceeds max duration ($maxDurationMs ms), skipping")
                continue
            }
            
            // 如果添加这个文件会超过最大时长，开始新的组
            if (currentDuration + fileDuration > maxDurationMs && currentGroup.isNotEmpty()) {
                groups.add(currentGroup.toList())
                currentGroup = mutableListOf()
                currentDuration = 0L
            }
            
            // 添加文件到当前组
            currentGroup.add(file)
            currentDuration += fileDuration
        }
        
        // 添加最后一组
        if (currentGroup.isNotEmpty()) {
            groups.add(currentGroup)
        }
        
        Log.d(TAG, "Grouped ${files.size} files into ${groups.size} segments")
        return groups
    }
}
