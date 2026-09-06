package com.silic2.app.domain.processor

import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * SILIC 2 專用 Mel 頻譜轉換器
 * 參數完全對齊 nnAudio.features.MelSpectrogram:
 * sr=32000, n_fft=1600, hop_length=400, n_mels=240, fmin=100, fmax=15000, norm=1
 */
class MelSpectrogramConverter(
    private val sampleRate: Int = 32000,
    private val nFft: Int = 1600,
    private val hopLength: Int = 400,
    private val nMels: Int = 240,
    private val fMin: Double = 100.0,
    private val fMax: Double = 15000.0
) {
    private val fft = DoubleFFT_1D(nFft.toLong())
    private val window = DoubleArray(nFft)
    private val numBins = nFft / 2 + 1 // 801 bins
    private val melFilterBank = Array(nMels) { DoubleArray(numBins) }

    init {
        // 1. 初始化 Hann 窗 (對齊 torch.hann_window periodic=True: 2*PI*i / N)
        for (i in 0 until nFft) {
            window[i] = 0.5 * (1.0 - cos(2.0 * PI * i / nFft))
        }

        // 2. 初始化 Mel Filter Bank (對齊 Slaney norm=1)
        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)
        val melPoints = DoubleArray(nMels + 2)
        val deltaMel = (melMax - melMin) / (nMels + 1)
        for (i in 0 until nMels + 2) {
            melPoints[i] = melMin + i * deltaMel
        }

        val hzPoints = DoubleArray(nMels + 2) { melToHz(melPoints[it]) }
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
                // 面積歸一化 (norm = 1: 2.0 / (hz_right - hz_left))
                val enorm = 2.0 / (hzPoints[m + 2] - hzPoints[m])
                melFilterBank[m][k] = weight * enorm
            }
        }
    }

    private fun hzToMel(f: Double): Double = 1127.0 * ln(1.0 + f / 700.0)
    private fun melToHz(m: Double): Double = 700.0 * (exp(m / 1127.0) - 1.0)

    /**
     * 輸入 3 秒音訊 (96,000 samples)，輸出 [nMels (240)][targetFrames (240)] 的 log(log(spec)) 數值
     */
    fun computeMelSpectrogram(pcmSamples: ShortArray): Array<FloatArray> {
        val targetFrames = 240
        val numSamples = pcmSamples.size

        // 1. 峰值正規化 (Peak Normalization): 將最大絕對振幅放大至 32000 (對齊 pydub.effects.normalize)
        var maxAmp = 0
        for (s in pcmSamples) {
            val absVal = abs(s.toInt())
            if (absVal > maxAmp) maxAmp = absVal
        }

        val scale = if (maxAmp > 50) 32000.0 / maxAmp else 1.0
        val normalizedSamples = DoubleArray(numSamples) { i ->
            pcmSamples[i] * scale
        }

        // 2. 100 Hz 高通濾波 (對齊 silic2.py 的 high_pass_filter(100))
        val fc = 100.0
        val dt = 1.0 / sampleRate
        val rc = 1.0 / (2.0 * PI * fc)
        val alpha = rc / (rc + dt)

        val filteredSamples = DoubleArray(numSamples)
        if (numSamples > 0) {
            filteredSamples[0] = normalizedSamples[0]
            for (i in 1 until numSamples) {
                filteredSamples[i] = alpha * (filteredSamples[i - 1] + normalizedSamples[i] - normalizedSamples[i - 1])
            }
        }

        val logLogMel = Array(nMels) { FloatArray(targetFrames) }
        val fftBuffer = DoubleArray(nFft)

        // 3. 逐幀計算 STFT 與 Mel 濾波
        for (frame in 0 until targetFrames) {
            val centerSample = frame * hopLength
            val startSample = centerSample - nFft / 2

            // 加 Hann 窗 (使用 reflect padding 處理邊界)
            for (i in 0 until nFft) {
                val srcIdx = startSample + i
                val sampleVal = when {
                    srcIdx in 0 until numSamples -> filteredSamples[srcIdx]
                    srcIdx < 0 -> {
                        val refIdx = -srcIdx
                        if (refIdx < numSamples) filteredSamples[refIdx] else 0.0
                    }
                    else -> {
                        val refIdx = 2 * numSamples - 2 - srcIdx
                        if (refIdx in 0 until numSamples) filteredSamples[refIdx] else 0.0
                    }
                }
                fftBuffer[i] = sampleVal * window[i]
            }

            // 執行實數 FFT
            fft.realForward(fftBuffer)

            // 計算功率譜 (Power Spectrum: |X|^2)
            val powerSpectrum = DoubleArray(numBins)
            powerSpectrum[0] = fftBuffer[0] * fftBuffer[0]
            if (numBins > 1) {
                powerSpectrum[numBins - 1] = fftBuffer[1] * fftBuffer[1]
            }
            for (k in 1 until numBins - 1) {
                val re = fftBuffer[2 * k]
                val im = fftBuffer[2 * k + 1]
                powerSpectrum[k] = re * re + im * im
            }

            // 乘上 Mel 濾波器矩陣
            for (m in 0 until nMels) {
                var melEnergy = 0.0
                val filter = melFilterBank[m]
                for (k in 0 until numBins) {
                    melEnergy += powerSpectrum[k] * filter[k]
                }
                // 對齊 Python: data = torch.log(torch.log(spec[0] + 1e-6))
                val safeEnergy = max(1.0001, melEnergy)
                val log1 = ln(safeEnergy)
                val log2 = ln(max(1e-5, log1)).toFloat()
                logLogMel[m][frame] = log2
            }
        }

        return logLogMel
    }
}
