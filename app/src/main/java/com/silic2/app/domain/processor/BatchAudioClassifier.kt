package com.silic2.app.domain.processor

import com.silic2.app.data.model.SilicDetection
import com.silic2.app.domain.inference.SilicDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class BatchAudioClassifier(
    private val melConverter: MelSpectrogramConverter,
    private val rainbowRenderer: RainbowRenderer,
    private val detector: SilicDetector
) {

    companion object {
        const val SAMPLE_RATE = 32000
        const val CHUNK_SECONDS = 3.0f
        const val STEP_SECONDS = 1.5f
        const val CHUNK_SAMPLES = (SAMPLE_RATE * CHUNK_SECONDS).toInt() // 96,000
        const val STEP_SAMPLES = (SAMPLE_RATE * STEP_SECONDS).toInt()   // 48,000
    }

    suspend fun classifyFullAudio(
        audioSamples: ShortArray,
        confThreshold: Float = 0.20f,
        targetClassIds: Set<Int>? = null,
        onProgress: ((currentChunk: Int, totalChunks: Int) -> Unit)? = null
    ): List<SilicDetection> = withContext(Dispatchers.Default) {
        if (audioSamples.isEmpty()) return@withContext emptyList()

        val allDetections = mutableListOf<SilicDetection>()
        val totalSamples = audioSamples.size

        if (totalSamples <= CHUNK_SAMPLES) {
            // 短於 3 秒：補 0 進行單次辨識
            val padded = ShortArray(CHUNK_SAMPLES)
            System.arraycopy(audioSamples, 0, padded, 0, totalSamples)
            val mel = melConverter.computeMelSpectrogram(padded)
            val bmp = rainbowRenderer.renderToBitmap(mel)
            val results = detector.detect(
                bitmap = bmp,
                clipStartMs = 0L,
                confThreshold = confThreshold,
                targetClassIds = targetClassIds
            )
            onProgress?.invoke(1, 1)
            return@withContext results
        }

        // 計算滑動總片段數
        val totalChunks = max(1, ((totalSamples - CHUNK_SAMPLES) / STEP_SAMPLES) + 1)
        var chunkIndex = 0
        var offset = 0

        while (offset < totalSamples) {
            val chunkStartMs = (offset.toDouble() / SAMPLE_RATE * 1000.0).toLong()
            val chunk = ShortArray(CHUNK_SAMPLES)
            val available = min(CHUNK_SAMPLES, totalSamples - offset)
            System.arraycopy(audioSamples, offset, chunk, 0, available)

            val mel = melConverter.computeMelSpectrogram(chunk)
            val bmp = rainbowRenderer.renderToBitmap(mel)
            val results = detector.detect(
                bitmap = bmp,
                clipStartMs = chunkStartMs,
                confThreshold = confThreshold,
                targetClassIds = targetClassIds
            )

            allDetections.addAll(results)
            chunkIndex++
            onProgress?.invoke(chunkIndex, totalChunks)

            if (offset + CHUNK_SAMPLES >= totalSamples) {
                break
            }
            offset += STEP_SAMPLES
        }

        // 重疊去重 (Non-Maximum Suppression / Merging)
        return@withContext mergeOverlappingDetections(allDetections)
    }

    /**
     * 合併相鄰窗格重疊檢測到的同物種鳴叫
     */
    private fun mergeOverlappingDetections(raw: List<SilicDetection>): List<SilicDetection> {
        if (raw.size <= 1) return raw

        val sorted = raw.sortedWith(compareBy({ it.soundclassId }, { it.timeBeginMs }))
        val merged = mutableListOf<SilicDetection>()

        var current = sorted[0]
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            val isSameClass = current.soundclassId == next.soundclassId
            // 若同一物種且時間有重疊 (允許 300ms 容許間距)
            val isOverlapping = next.timeBeginMs <= current.timeEndMs + 300

            if (isSameClass && isOverlapping) {
                // 合併為更寬的時間範圍，信心度取最大值
                current = current.copy(
                    timeBeginMs = min(current.timeBeginMs, next.timeBeginMs),
                    timeEndMs = max(current.timeEndMs, next.timeEndMs),
                    freqLowHz = min(current.freqLowHz, next.freqLowHz),
                    freqHighHz = max(current.freqHighHz, next.freqHighHz),
                    confidence = max(current.confidence, next.confidence)
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }
}
