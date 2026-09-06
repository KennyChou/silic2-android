package com.silic2.app.domain.processor

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max

class RainbowRenderer(
    private val nMels: Int = 240,
    private val nFrames: Int = 240,
    private val rainbowBands: Int = 5,
    private val outputWidth: Int = 480,
    private val outputHeight: Int = 480
) {
    // Matplotlib ListedColormap(cm.rainbow(np.linspace((i+1)/5, i/5, 32))) 精確 5 頻段 32 色調色盤
    private val bandsPalette: Array<IntArray> = arrayOf(
        // Band 0: [0.2 -> 0.0]
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
        // Band 1: [0.4 -> 0.2]
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
        // Band 2: [0.6 -> 0.4]
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
        // Band 3: [0.8 -> 0.6]
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
        // Band 4: [1.0 -> 0.8]
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

    /**
     * 將 MelSpectrogram log(log(spec)) 轉為 SILIC 2 彩虹頻譜圖 (480x480 RGB Bitmap)
     */
    fun renderToBitmap(logLogMel: Array<FloatArray>): Bitmap {
        val pixels = IntArray(nFrames * nMels)
        val bandHeight = nMels / rainbowBands // 48 mels per band

        for (band in 0 until rainbowBands) {
            val mStart = band * bandHeight
            val mEnd = (band + 1) * bandHeight

            // 1. 計算該頻段獨立的 min 與 max (對齊 matplotlib pcolormesh 自動縮放)
            var subMin = Float.MAX_VALUE
            var subMax = -Float.MAX_VALUE
            for (m in mStart until mEnd) {
                for (f in 0 until nFrames) {
                    val v = logLogMel[m][f]
                    if (v < subMin) subMin = v
                    if (v > subMax) subMax = v
                }
            }

            val subRange = max(1e-6f, subMax - subMin)
            val palette = bandsPalette[band]

            // 影像 Y 軸：Band 4（最高頻）在最上方 (y = 0..47)，Band 0（最低頻）在最下方 (y = 192..239)
            val yBandStart = (rainbowBands - 1 - band) * bandHeight

            for (row in 0 until bandHeight) {
                val m = mStart + row
                // 頻率越高 row 越大，在圖像中 y 坐標越朝上 (y 越小)
                val y = yBandStart + (bandHeight - 1 - row)

                for (f in 0 until nFrames) {
                    val v = logLogMel[m][f]
                    val normVal = ((v - subMin) / subRange).coerceIn(0f, 1f)
                    val colorIdx = (normVal * 31f).toInt().coerceIn(0, 31)
                    pixels[y * nFrames + f] = palette[colorIdx]
                }
            }
        }

        val baseBitmap = Bitmap.createBitmap(pixels, nFrames, nMels, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(baseBitmap, outputWidth, outputHeight, true)
    }
}
