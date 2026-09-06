package com.silic2.app.domain.processor

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
    // 預設採用黑白灰階頻譜 (人眼友善且高對比)，彩色維持送給模型推論
    var isGrayscale: Boolean = true
    private val fft = DoubleFFT_1D(nFft.toLong())
    private val window = DoubleArray(nFft)
    private val numBins = nFft / 2 + 1
    private val melFilterBank = Array(nMels) { DoubleArray(numBins) }

    // 6 秒滾動頻譜緩衝區 [nMels][totalFrames]
    private val rollingLogMel = Array(nMels) { FloatArray(totalFrames) }
    private var writeFrameIndex = 0

    // 彩虹像素緩衝區
    private val bitmapPixels = IntArray(totalFrames * nMels)
    val rollingBitmap: Bitmap = Bitmap.createBitmap(totalFrames, nMels, Bitmap.Config.ARGB_8888)

    // 連續音訊環形緩衝區 (供 FFT 邊界 Hann 窗處理)
    private val audioHistory = ShortArray(sampleRate * 7) // 7 秒歷史
    private var audioHistoryWritePos = 0
    var totalSamplesProcessed: Long = 0L
        private set

    // 5 頻段 32 色 Palette
    private val rainbowRenderer = RainbowRenderer(nMels = nMels, nFrames = totalFrames)
    private val bandsPalette = arrayOf(
        intArrayOf(
            Color.rgb(25, 149, 242), Color.rgb(29, 144, 243), Color.rgb(33, 139, 244), Color.rgb(35, 136, 244),
            Color.rgb(39, 131, 245), Color.rgb(43, 126, 246), Color.rgb(45, 123, 246), Color.rgb(49, 117, 247),
            Color.rgb(53, 112, 248), Color.rgb(55, 109, 248), Color.rgb(59, 103, 249), Color.rgb(61, 100, 249),
            Color.rgb(65, 95, 250), Color.rgb(69, 89, 250), Color.rgb(71, 86, 251), Color.rgb(75, 80, 251),
            Color.rgb(79, 74, 252), Color.rgb(81, 71, 252), Color.rgb(85, 65, 252), Color.rgb(89, 59, 253),
            Color.rgb(91, 56, 253), Color.rgb(95, 49, 253), Color.rgb(99, 43, 254), Color.rgb(101, 40, 254),
            Color.rgb(105, 34, 254), Color.rgb(109, 28, 254), Color.rgb(111, 25, 254), Color.rgb(115, 18, 254),
            Color.rgb(119, 12, 254), Color.rgb(121, 9, 254), Color.rgb(125, 3, 254), Color.rgb(127, 0, 255)
        ),
        intArrayOf(
            Color.rgb(76, 242, 206), Color.rgb(72, 240, 208), Color.rgb(70, 239, 209), Color.rgb(66, 237, 210),
            Color.rgb(62, 234, 212), Color.rgb(60, 233, 213), Color.rgb(56, 230, 215), Color.rgb(52, 228, 216),
            Color.rgb(50, 226, 217), Color.rgb(46, 223, 219), Color.rgb(42, 220, 220), Color.rgb(40, 219, 221),
            Color.rgb(36, 215, 223), Color.rgb(32, 212, 224), Color.rgb(30, 210, 225), Color.rgb(26, 207, 226),
            Color.rgb(22, 203, 228), Color.rgb(20, 201, 228), Color.rgb(16, 197, 230), Color.rgb(14, 195, 230),
            Color.rgb(10, 191, 232), Color.rgb(6, 187, 233), Color.rgb(4, 185, 234), Color.rgb(0, 180, 235),
            Color.rgb(3, 176, 236), Color.rgb(5, 174, 237), Color.rgb(9, 169, 238), Color.rgb(13, 164, 239),
            Color.rgb(15, 162, 239), Color.rgb(19, 157, 241), Color.rgb(23, 152, 242), Color.rgb(25, 149, 242)
        ),
        intArrayOf(
            Color.rgb(178, 242, 149), Color.rgb(174, 244, 152), Color.rgb(172, 245, 153), Color.rgb(168, 246, 156),
            Color.rgb(164, 248, 158), Color.rgb(162, 249, 159), Color.rgb(158, 250, 162), Color.rgb(156, 250, 163),
            Color.rgb(152, 251, 165), Color.rgb(148, 252, 168), Color.rgb(146, 253, 169), Color.rgb(142, 253, 171),
            Color.rgb(138, 254, 174), Color.rgb(136, 254, 175), Color.rgb(132, 254, 177), Color.rgb(128, 254, 179),
            Color.rgb(126, 254, 180), Color.rgb(122, 254, 183), Color.rgb(118, 254, 185), Color.rgb(116, 254, 186),
            Color.rgb(112, 253, 188), Color.rgb(108, 253, 190), Color.rgb(106, 252, 191), Color.rgb(102, 251, 193),
            Color.rgb(98, 250, 195), Color.rgb(96, 250, 196), Color.rgb(92, 249, 198), Color.rgb(90, 248, 199),
            Color.rgb(86, 246, 201), Color.rgb(82, 245, 203), Color.rgb(80, 244, 204), Color.rgb(76, 242, 206)
        ),
        intArrayOf(
            Color.rgb(255, 149, 78), Color.rgb(255, 152, 80), Color.rgb(255, 157, 83), Color.rgb(255, 162, 86),
            Color.rgb(255, 164, 87), Color.rgb(255, 169, 90), Color.rgb(255, 174, 93), Color.rgb(255, 176, 95),
            Color.rgb(254, 180, 97), Color.rgb(250, 185, 100), Color.rgb(248, 187, 102), Color.rgb(244, 191, 105),
            Color.rgb(240, 195, 108), Color.rgb(238, 197, 109), Color.rgb(234, 201, 112), Color.rgb(232, 203, 113),
            Color.rgb(228, 207, 116), Color.rgb(224, 210, 119), Color.rgb(222, 212, 120), Color.rgb(218, 215, 123),
            Color.rgb(214, 219, 126), Color.rgb(212, 220, 127), Color.rgb(208, 223, 130), Color.rgb(204, 226, 132),
            Color.rgb(202, 228, 134), Color.rgb(198, 230, 136), Color.rgb(194, 233, 139), Color.rgb(192, 234, 140),
            Color.rgb(188, 237, 143), Color.rgb(184, 239, 146), Color.rgb(182, 240, 147), Color.rgb(178, 242, 149)
        ),
        intArrayOf(
            Color.rgb(255, 0, 0), Color.rgb(255, 3, 1), Color.rgb(255, 9, 4), Color.rgb(255, 12, 6),
            Color.rgb(255, 18, 9), Color.rgb(255, 25, 12), Color.rgb(255, 28, 14), Color.rgb(255, 34, 17),
            Color.rgb(255, 40, 20), Color.rgb(255, 43, 21), Color.rgb(255, 49, 25), Color.rgb(255, 56, 28),
            Color.rgb(255, 59, 29), Color.rgb(255, 65, 32), Color.rgb(255, 71, 36), Color.rgb(255, 74, 37),
            Color.rgb(255, 80, 40), Color.rgb(255, 86, 43), Color.rgb(255, 89, 45), Color.rgb(255, 95, 48),
            Color.rgb(255, 100, 51), Color.rgb(255, 103, 53), Color.rgb(255, 109, 56), Color.rgb(255, 112, 57),
            Color.rgb(255, 117, 60), Color.rgb(255, 123, 63), Color.rgb(255, 126, 65), Color.rgb(255, 131, 68),
            Color.rgb(255, 136, 71), Color.rgb(255, 139, 72), Color.rgb(255, 144, 75), Color.rgb(255, 149, 78)
        )
    )

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
     * 產出即時 480x240 彩虹頻譜 Bitmap
     */
    @Synchronized
    fun updateBitmap(): Bitmap {
        if (isGrayscale) {
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

            // 底噪基準：40% 處，徹底切除微弱環境風噪與背景噪音
            val dbFloor = minDb + (maxDb - minDb) * 0.40f
            val dbRange = max(1.0f, maxDb - dbFloor)

            for (f in 0 until totalFrames) {
                if (f < startFrame) {
                    // 尚未錄到的留白區域：深沉背景黑底
                    for (m in 0 until nMels) {
                        val y = nMels - 1 - m
                        bitmapPixels[y * totalFrames + f] = 0xFF0E1412.toInt()
                    }
                } else {
                    for (m in 0 until nMels) {
                        val y = nMels - 1 - m
                        val log2 = rollingLogMel[m][f]
                        val log1 = exp(log2.toDouble()).toFloat()
                        val norm = ((log1 - dbFloor) / dbRange).coerceIn(0f, 1f)
                        val gamma = Math.pow(norm.toDouble(), 1.4).toFloat()
                        val gray = (gamma * 255f).toInt().coerceIn(0, 255)
                        bitmapPixels[y * totalFrames + f] = 0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
                    }
                }
            }
        } else {
            // === 彩虹模式 (檢視送給模型的特徵色盤) ===
            val bandHeight = nMels / 5 // 48 bins per band
            for (band in 0 until 5) {
                val mStart = band * bandHeight
                val mEnd = (band + 1) * bandHeight
                val palette = bandsPalette[band]

                var subMin = Float.MAX_VALUE
                var subMax = -Float.MAX_VALUE
                for (m in mStart until mEnd) {
                    for (f in 0 until totalFrames) {
                        val v = rollingLogMel[m][f]
                        if (v < subMin) subMin = v
                        if (v > subMax) subMax = v
                    }
                }

                val subRange = max(1e-6f, subMax - subMin)
                val yBandStart = (4 - band) * bandHeight

                for (row in 0 until bandHeight) {
                    val m = mStart + row
                    val y = yBandStart + (bandHeight - 1 - row)

                    for (f in 0 until totalFrames) {
                        val v = rollingLogMel[m][f]
                        val normVal = ((v - subMin) / subRange).coerceIn(0f, 1f)
                        val colorIdx = (normVal * 31f).toInt().coerceIn(0, 31)
                        bitmapPixels[y * totalFrames + f] = palette[colorIdx]
                    }
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
