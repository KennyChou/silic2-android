package cc.kennydev.silic2.app.domain.processor

import android.graphics.Bitmap
import android.graphics.Color
import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * 6 秒滾動彩虹頻譜處理器 (Waterfall Spectrogram Processor)
 * 寬度 480 幀 (每幀 400 samples = 12.5ms，480 * 12.5ms = 6000ms = 6 秒)
 * 高度 240 bins (100Hz ~ 15000Hz)
 */
class RollingSpectrogramProcessor(
    private val sampleRate: Int = 32000,
    private val nFft: Int = 1600,
    private val hopLength: Int = 400,
    private val nMels: Int = 240,
    val totalFrames: Int = 480 // 6 秒寬度
) {
    // 固定採用黑白灰階頻譜 (人眼友善且高對比)
    val isGrayscale: Boolean = true
    private val fft = DoubleFFT_1D(nFft.toLong())
    private val window = DoubleArray(nFft)
    private val numBins = nFft / 2 + 1
    private val melFilterBank = Array(nMels) { DoubleArray(numBins) }

    // 6 秒滾動頻譜緩衝區 [nMels][totalFrames]
    private val rollingLogMel = Array(nMels) { FloatArray(totalFrames) }
    private var writeFrameIndex = 0

    // 黑白像素緩衝區
    private val bitmapPixels = IntArray(totalFrames * nMels)
    val rollingBitmap: Bitmap = Bitmap.createBitmap(totalFrames, nMels, Bitmap.Config.ARGB_8888)

    // 連續音訊環形緩衝區 (供 FFT 邊界 Hann 窗處理)
    private val audioHistory = ShortArray(sampleRate * 7) // 7 秒歷史
    private var audioHistoryWritePos = 0
    var totalSamplesProcessed: Long = 0L
        private set

    private var pendingSamples = ShortArray(hopLength)
    private var pendingCount = 0

    init {
        for (i in 0 until nFft) {
            window[i] = 0.5 * (1.0 - cos(2.0 * PI * i / nFft))
        }

        val fMin = 100.0
        val fMax = 15000.0
        val melMin = 1127.0 * ln(1.0 + fMin / 700.0)
        val melMax = 1127.0 * ln(1.0 + fMax / 700.0)
        val melPoints = DoubleArray(nMels + 2)
        val deltaMel = (melMax - melMin) / (nMels + 1)
        for (i in 0 until nMels + 2) {
            melPoints[i] = melMin + i * deltaMel
        }
        val hzPoints = DoubleArray(nMels + 2) { 700.0 * (exp(melPoints[it] / 1127.0) - 1.0) }
        val binPoints = DoubleArray(nMels + 2) { hzPoints[it] * nFft / sampleRate }

        for (m in 0 until nMels) {
            val left = binPoints[m]
            val center = binPoints[m + 1]
            val right = binPoints[m + 2]
            for (k in 0 until numBins) {
                val weight = when {
                    k < left -> 0.0
                    k <= center -> if (center > left) (k - left) / (center - left) else 0.0
                    k <= right -> if (right > center) (right - k) / (right - center) else 0.0
                    else -> 0.0
                }
                val enorm = 2.0 / (hzPoints[m + 2] - hzPoints[m])
                melFilterBank[m][k] = weight * enorm
            }
        }
    }

    /**
     * 寫入新的音訊片段，每滿 400 samples 計算一幀頻譜並向左滾動
     */
    @Synchronized
    fun pushSamples(samples: ShortArray, offset: Int, length: Int) {
        var srcPos = offset
        var remaining = length

        while (remaining > 0) {
            val needed = hopLength - pendingCount
            val toCopy = min(needed, remaining)

            System.arraycopy(samples, srcPos, pendingSamples, pendingCount, toCopy)
            pendingCount += toCopy
            srcPos += toCopy
            remaining -= toCopy

            // 儲存至歷史環形緩衝區
            for (i in 0 until toCopy) {
                audioHistory[audioHistoryWritePos] = samples[srcPos - toCopy + i]
                audioHistoryWritePos = (audioHistoryWritePos + 1) % audioHistory.size
            }
            totalSamplesProcessed += toCopy

            if (pendingCount >= hopLength) {
                computeAndScrollFrame()
                pendingCount = 0
            }
        }
    }

    private val fftBuffer = DoubleArray(nFft)
    private val powerSpectrum = DoubleArray(numBins)

    private fun computeAndScrollFrame() {
        // 從音訊歷史中取出最近 nFft (1600) 個 samples
        var histPos = (audioHistoryWritePos - nFft + audioHistory.size) % audioHistory.size
        for (i in 0 until nFft) {
            val sampleVal = audioHistory[histPos].toDouble()
            fftBuffer[i] = sampleVal * window[i]
            histPos = (histPos + 1) % audioHistory.size
        }

        fft.realForward(fftBuffer)

        powerSpectrum[0] = fftBuffer[0] * fftBuffer[0]
        if (numBins > 1) {
            powerSpectrum[numBins - 1] = fftBuffer[1] * fftBuffer[1]
        }
        for (k in 1 until numBins - 1) {
            val re = fftBuffer[2 * k]
            val im = fftBuffer[2 * k + 1]
            powerSpectrum[k] = re * re + im * im
        }

        // 1. 矩陣左移一列 (滾動)
        for (m in 0 until nMels) {
            System.arraycopy(rollingLogMel[m], 1, rollingLogMel[m], 0, totalFrames - 1)
        }

        // 2. 填入最右側新的一列
        val rightCol = totalFrames - 1
        for (m in 0 until nMels) {
            var energy = 0.0
            val filter = melFilterBank[m]
            for (k in 0 until numBins) {
                energy += powerSpectrum[k] * filter[k]
            }
            val safeEnergy = max(1.0001, energy)
            val log1 = ln(safeEnergy)
            val log2 = ln(max(1e-5, log1)).toFloat()
            rollingLogMel[m][rightCol] = log2
        }

        if (writeFrameIndex < totalFrames) {
            writeFrameIndex++
        }
    }

    /**
     * 產出即時 480x240 黑白高對比頻譜 Bitmap
     */
    @Synchronized
    fun updateBitmap(): Bitmap {
        // === 黑白/灰階聲景模式 (人眼友善、高對比、還原單重 log 能量分貝) ===
        val activeFrames = min(totalFrames, max(1, writeFrameIndex))
        val startFrame = totalFrames - activeFrames // 目前有音訊資料的起始欄位

        var minDb = Float.MAX_VALUE
        var maxDb = -Float.MAX_VALUE

        // 1. 統計目前有資料區域的真實能量分貝 (單重 log) 極值
        for (f in startFrame until totalFrames) {
            for (m in 0 until nMels) {
                val log2 = rollingLogMel[m][f]
                val log1 = exp(log2.toDouble()).toFloat()
                if (log1 < minDb) minDb = log1
                if (log1 > maxDb) maxDb = log1
            }
        }

        if (minDb >= maxDb) {
            minDb = 10f
            maxDb = 30f
        }

        // 底噪基準：65% 處，白色背景僅留明顯叫聲呈黑，傳統聲譜圖風格
        val dbFloor = minDb + (maxDb - minDb) * 0.65f
        val dbRange = max(1.0f, maxDb - dbFloor)

        for (f in 0 until totalFrames) {
            if (f < startFrame) {
                // 尚未錄到的留白區域：淺灰底
                for (m in 0 until nMels) {
                    val y = nMels - 1 - m
                    bitmapPixels[y * totalFrames + f] = 0xFFE8E6E0.toInt()
                }
            } else {
                for (m in 0 until nMels) {
                    val y = nMels - 1 - m
                    val log2 = rollingLogMel[m][f]
                    val log1 = exp(log2.toDouble()).toFloat()
                    val norm = ((log1 - dbFloor) / dbRange).coerceIn(0f, 1f)
                    val gamma = Math.pow(norm.toDouble(), 1.8).toFloat()
                    val gray = (255f - gamma * 255f).toInt().coerceIn(0, 255)
                    bitmapPixels[y * totalFrames + f] = 0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
                }
            }
        }

        rollingBitmap.setPixels(bitmapPixels, 0, totalFrames, 0, 0, totalFrames, nMels)
        return rollingBitmap
    }

    /**
     * 提取過去 3 秒 (96,000 samples) 音訊供 YOLO LiteRT 進行精確推論
     */
    @Synchronized
    fun extractPast3Seconds(): Pair<ShortArray, Long>? {
        val clipSamples = 32000 * 3
        if (totalSamplesProcessed < clipSamples) return null

        val result = ShortArray(clipSamples)
        var startHistPos = (audioHistoryWritePos - clipSamples + audioHistory.size) % audioHistory.size
        for (i in 0 until clipSamples) {
            result[i] = audioHistory[startHistPos]
            startHistPos = (startHistPos + 1) % audioHistory.size
        }

        // 推論窗口的時間戳記 (毫秒)
        val clipStartMs = (totalSamplesProcessed - clipSamples) * 1000L / sampleRate
        return Pair(result, clipStartMs)
    }

    @Synchronized
    fun reset() {
        writeFrameIndex = 0
        audioHistoryWritePos = 0
        totalSamplesProcessed = 0L
        pendingCount = 0
        for (m in 0 until nMels) {
            rollingLogMel[m].fill(0f)
        }
        bitmapPixels.fill(0)
        rollingBitmap.eraseColor(Color.TRANSPARENT)
    }
}
