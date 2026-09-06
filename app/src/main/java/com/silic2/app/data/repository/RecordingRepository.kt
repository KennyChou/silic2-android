package com.silic2.app.data.repository

import android.content.Context
import android.os.Environment
import com.silic2.app.data.model.AnimalCategory
import com.silic2.app.data.model.RecordingSession
import com.silic2.app.data.model.SilicDetection
import java.io.File
import java.util.Locale

class RecordingRepository(private val context: Context) {

    private val musicDir: File
        get() = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir

    /**
     * 讀取所有歷史錄音列表，依時間倒序排列
     */
    fun loadSessions(): List<RecordingSession> {
        val dir = musicDir
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val wavFiles = dir.listFiles { file ->
            file.isFile && file.extension.lowercase(Locale.ROOT) == "wav"
        }?.sortedByDescending { it.lastModified() } ?: return emptyList()

        return wavFiles.map { wav ->
            val baseName = wav.nameWithoutExtension
            val ravenFile = File(dir, "$baseName.txt").takeIf { it.exists() }
            val csvFile = File(dir, "$baseName.csv").takeIf { it.exists() }

            // 計算音訊時長：32kHz, 16-bit (2 bytes), 1 channel
            val dataBytes = (wav.length() - 44).coerceAtLeast(0)
            val durationSec = dataBytes / (32000.0 * 2.0)

            // 解析物種標記 (優先讀 Raven .txt，其次讀 .csv)
            val detections = ravenFile?.let { parseRavenFile(it, wav.lastModified()) }
                ?: csvFile?.let { parseCsvFile(it, wav.lastModified()) }
                ?: emptyList()

            RecordingSession(
                id = baseName,
                wavFile = wav,
                ravenFile = ravenFile,
                csvFile = csvFile,
                timestamp = wav.lastModified(),
                durationSec = durationSec,
                detections = detections
            )
        }
    }

    /**
     * 刪除單一錄音及其標籤檔
     */
    fun deleteSession(session: RecordingSession): Boolean {
        var success = true
        if (session.wavFile.exists()) {
            success = success && session.wavFile.delete()
        }
        session.ravenFile?.let {
            if (it.exists()) success = success && it.delete()
        }
        session.csvFile?.let {
            if (it.exists()) success = success && it.delete()
        }
        return success
    }

    /**
     * 清空所有錄音與標籤檔案
     */
    fun clearAllSessions(): Boolean {
        val dir = musicDir
        if (!dir.exists() || !dir.isDirectory) return true
        val files = dir.listFiles() ?: return true
        var allDeleted = true
        for (f in files) {
            if (f.isFile && (f.extension in listOf("wav", "txt", "csv", "pcm"))) {
                allDeleted = allDeleted && f.delete()
            }
        }
        return allDeleted
    }

    private fun parseRavenFile(file: File, fileTimestamp: Long): List<SilicDetection> {
        val results = mutableListOf<SilicDetection>()
        try {
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                var isHeader = true
                lines.forEach { line ->
                    if (isHeader) {
                        isHeader = false
                    } else {
                        val parts = line.split("\t")
                        if (parts.size >= 10) {
                            val startSec = parts[3].toDoubleOrNull() ?: 0.0
                            val endSec = parts[4].toDoubleOrNull() ?: 0.0
                            val lowFreq = parts[5].toIntOrNull() ?: 0
                            val highFreq = parts[6].toIntOrNull() ?: 15000
                            val species = parts[7]
                            val soundType = parts[8]
                            val score = parts[9].toFloatOrNull() ?: 0.5f

                            results.add(
                                SilicDetection(
                                    soundclassId = 0,
                                    speciesName = species,
                                    soundClass = soundType,
                                    scientificName = "",
                                    confidence = score,
                                    timeBeginMs = (startSec * 1000).toLong(),
                                    timeEndMs = (endSec * 1000).toLong(),
                                    freqLowHz = lowFreq,
                                    freqHighHz = highFreq,
                                    timestamp = fileTimestamp
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun parseCsvFile(file: File, fileTimestamp: Long): List<SilicDetection> {
        val results = mutableListOf<SilicDetection>()
        try {
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                var isHeader = true
                lines.forEach { line ->
                    if (isHeader) {
                        isHeader = false
                    } else {
                        val parts = line.split(",")
                        if (parts.size >= 9) {
                            val species = parts[1]
                            val soundType = parts[2]
                            val scientific = parts[3].trim('"')
                            val score = parts[4].toFloatOrNull() ?: 0.5f
                            val startSec = parts[5].toDoubleOrNull() ?: 0.0
                            val endSec = parts[6].toDoubleOrNull() ?: 0.0
                            val lowFreq = parts[7].toIntOrNull() ?: 0
                            val highFreq = parts[8].toIntOrNull() ?: 15000

                            results.add(
                                SilicDetection(
                                    soundclassId = 0,
                                    speciesName = species,
                                    soundClass = soundType,
                                    scientificName = scientific,
                                    confidence = score,
                                    timeBeginMs = (startSec * 1000).toLong(),
                                    timeEndMs = (endSec * 1000).toLong(),
                                    freqLowHz = lowFreq,
                                    freqHighHz = highFreq,
                                    timestamp = fileTimestamp
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }
}
