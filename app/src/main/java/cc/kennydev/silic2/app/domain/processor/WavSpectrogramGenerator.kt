package cc.kennydev.silic2.app.domain.processor

import android.graphics.Bitmap
import android.graphics.Color
import org.jtransforms.fft.DoubleFFT_1D
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class WavSpectrogramGenerator(
    private val sampleRate: Int = 32000,
    private val nFft: Int = 1600,
    private val hopLength: Int = 400,
    private val nMels: Int = 240
) {
    private val fft = DoubleFFT_1D(nFft.toLong())
    private val window = DoubleArray(nFft) { 0.5 * (1.0 - cos(2.0 * PI * it / nFft)) }
    private val numBins = nFft / 2 + 1
    private val melFilterBank = Array(nMels) { DoubleArray(numBins) }

    init {
        val fMin = 100.0
        val fMax = 15000.0
        val melMin = 1127.0 * ln(1.0 + fMin / 700.0)
        val melMax = 1127.0 * ln(1.0 + fMax / 700.0)
        val melPoints = DoubleArray(nMels + 2) { melMin + it * (melMax - melMin) / (nMels + 1) }
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
     * 從 WAV 檔案生成完整的黑白高對比聲圖 Bitmap
     */
    fun generateGrayscaleSpectrogram(wavFile: File, targetWidth: Int = 800): Bitmap? {
        if (!wavFile.exists()) return null
        val bytes = wavFile.readBytes()
        if (bytes.size <= 44) return null

        val pcmBytes = bytes.copyOfRange(44, bytes.size)
        val shorts = ShortArray(pcmBytes.size / 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

        val totalSamples = shorts.size
        if (totalSamples < nFft) return null

        // 自適應時間步長：螢幕寬度約 800~1000 像素，限制最大 1000 幀以確保 0.05 秒內極速渲染
        val maxFrames = 1000
        val effectiveHop = max(hopLength, (totalSamples - nFft) / maxFrames)
        val totalFrames = max(1, (totalSamples - nFft) / effectiveHop + 1)
        val logMelMatrix = Array(nMels) { FloatArray(totalFrames) }

        val fftBuffer = DoubleArray(nFft)
        val powerSpectrum = DoubleArray(numBins)

        var minDb = Float.MAX_VALUE
        var maxDb = -Float.MAX_VALUE

        for (f in 0 until totalFrames) {
            val offset = f * effectiveHop
            for (i in 0 until nFft) {
                fftBuffer[i] = shorts[offset + i].toDouble() * window[i]
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

            for (m in 0 until nMels) {
                var energy = 0.0
                val filter = melFilterBank[m]
                for (k in 0 until numBins) {
                    energy += powerSpectrum[k] * filter[k]
                }
                val safeEnergy = max(1.0001, energy)
                val log1 = ln(safeEnergy).toFloat() // 單重 log (能量分貝)
                logMelMatrix[m][f] = log1
                if (log1 < minDb) minDb = log1
                if (log1 > maxDb) maxDb = log1
            }
        }

        if (minDb >= maxDb) {
            minDb = 10f
            maxDb = 30f
        }

        // 底噪壓暗基準 (40%)
        val dbFloor = minDb + (maxDb - minDb) * 0.40f
        val dbRange = max(1.0f, maxDb - dbFloor)

        val bitmap = Bitmap.createBitmap(totalFrames, nMels, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(totalFrames * nMels)

        for (f in 0 until totalFrames) {
            for (m in 0 until nMels) {
                val y = nMels - 1 - m // 高頻在頂，低頻在底
                val log1 = logMelMatrix[m][f]
                val norm = ((log1 - dbFloor) / dbRange).coerceIn(0f, 1f)
                val gamma = Math.pow(norm.toDouble(), 1.4).toFloat()
                val gray = (gamma * 255f).toInt().coerceIn(0, 255)
                pixels[y * totalFrames + f] = 0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
            }
        }

        bitmap.setPixels(pixels, 0, totalFrames, 0, 0, totalFrames, nMels)
        return bitmap
    }
}
