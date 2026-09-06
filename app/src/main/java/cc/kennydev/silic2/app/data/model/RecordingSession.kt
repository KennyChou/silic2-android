package cc.kennydev.silic2.app.data.model

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 代表一次完整的野外錄音歷史紀錄
 */
data class RecordingSession(
    val id: String,
    val wavFile: File,
    val ravenFile: File? = null,
    val csvFile: File? = null,
    val timestamp: Long,
    val durationSec: Double,
    val detections: List<SilicDetection> = emptyList()
) {
    val formattedDate: String
        get() = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    val formattedDuration: String
        get() {
            val totalSeconds = durationSec.toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }

    val fileSizeBytes: Long
        get() = wavFile.length()

    val formattedSize: String
        get() {
            val kb = fileSizeBytes / 1024.0
            return if (kb > 1024) {
                String.format(Locale.getDefault(), "%.1f MB", kb / 1024.0)
            } else {
                String.format(Locale.getDefault(), "%.0f KB", kb)
            }
        }

    val speciesSummary: String
        get() {
            if (detections.isEmpty()) return "無辨識紀錄"
            val distinctSpecies = detections.map { it.speciesName }.distinct()
            return if (distinctSpecies.size <= 2) {
                distinctSpecies.joinToString("、")
            } else {
                "${distinctSpecies.take(2).joinToString("、")} 等 ${distinctSpecies.size} 種"
            }
        }
}
