package cc.kennydev.silic2.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.kennydev.silic2.app.data.model.SilicDetection
import cc.kennydev.silic2.app.ui.theme.DarkForestBg
import cc.kennydev.silic2.app.ui.theme.ForestSurface
import cc.kennydev.silic2.app.ui.theme.SilicGreen
import cc.kennydev.silic2.app.ui.theme.TealAccent
import cc.kennydev.silic2.app.ui.theme.TextPrimary
import cc.kennydev.silic2.app.ui.theme.TextSecondary
import cc.kennydev.silic2.app.ui.theme.TextTertiary
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

@Composable
fun SpectrogramView(
    spectrogramBitmap: Bitmap?,
    detections: List<SilicDetection>,
    clipStartMs: Long,
    clipLengthMs: Long = 3000L,
    playheadProgress: Float = 0f,
    isPlaying: Boolean = false,
    selectedDetection: SilicDetection? = null,
    onBoxClick: ((SilicDetection) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ForestSurface)
            .border(1.dp, Color(0xFF263238), RoundedCornerShape(12.dp))
    ) {
        if (spectrogramBitmap == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "頻譜圖待命中 (開始聽音或播放範例即可呈現)",
                    fontSize = 13.sp,
                    color = TextTertiary
                )
            }
        } else {
            val imageBitmap = remember(spectrogramBitmap) { spectrogramBitmap.asImageBitmap() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // 1. 繪製頻譜底圖 (拉伸填滿)
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(canvasWidth.toInt(), canvasHeight.toInt())
                )

                // 2. 繪製頻率參考格線 (5kHz, 10kHz)
                val minMel = 1127.0 * ln(1.0 + 100.0 / 700.0)
                val maxMel = 1127.0 * ln(1.0 + 15000.0 / 700.0)
                val deltaMel = maxMel - minMel

                fun freqToY(freqHz: Double): Float {
                    val m = 1127.0 * ln(1.0 + freqHz / 700.0)
                    val norm = ((m - minMel) / deltaMel).coerceIn(0.0, 1.0)
                    return (canvasHeight * (1.0 - norm)).toFloat()
                }

                val gridFreqs = listOf(1000.0, 3000.0, 7000.0, 12000.0)
                for (f in gridFreqs) {
                    val y = freqToY(f)
                    drawLine(
                        color = Color.Black.copy(alpha = 0.15f),
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1f
                    )
                }

                // 3. 繪製 AI 標記邊界框 (Bounding Boxes)
                val fMin = 100.0
                val fMax = 15000.0

                for (det in detections) {
                    val isSelected = selectedDetection == det

                    // 時間映射 (X 軸)
                    val relStartMs = det.timeBeginMs - clipStartMs
                    val relEndMs = det.timeEndMs - clipStartMs

                    val x1 = (relStartMs.toFloat() / clipLengthMs * canvasWidth).coerceIn(0f, canvasWidth)
                    val x2 = (relEndMs.toFloat() / clipLengthMs * canvasWidth).coerceIn(0f, canvasWidth)
                    val boxWidth = max(8f, x2 - x1)

                    // 頻率映射 (Y 軸)
                    val yTop = freqToY(det.freqHighHz.toDouble()).coerceIn(0f, canvasHeight)
                    val yBottom = freqToY(det.freqLowHz.toDouble()).coerceIn(0f, canvasHeight)
                    val boxHeight = max(8f, yBottom - yTop)

                    val boxColor = confidenceToColor(det.confidence)

                    // 框體半透明填充
                    drawRect(
                        color = boxColor.copy(alpha = if (isSelected) 0.35f else 0.18f),
                        topLeft = Offset(x1, yTop),
                        size = Size(boxWidth, boxHeight)
                    )

                    // 框體邊線
                    drawRect(
                        color = if (isSelected) Color.Black else boxColor,
                        topLeft = Offset(x1, yTop),
                        size = Size(boxWidth, boxHeight),
                        style = Stroke(width = if (isSelected) 3f else 1.8f)
                    )

                    // 標籤文字 (使用 nativeCanvas 繪製)
                    drawContext.canvas.nativeCanvas.apply {
                        val paintBg = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(225, 18, 28, 24)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val paintText = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 26f
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        val paintConfLabel = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(190, 180, 215, 205)
                            textSize = 18f
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT
                        }
                        val paintConfScore = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 22f
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }

                        val speciesAndSound = if (det.soundClass.isNotBlank()) "${det.speciesName} ${det.soundClass}" else det.speciesName
                        val confLabel = "信心分數"
                        val confScore = String.format(java.util.Locale.US, "%.2f", det.confidence)

                        val mainWidth = paintText.measureText(speciesAndSound)
                        val confLabelWidth = paintConfLabel.measureText(confLabel)
                        val confScoreWidth = paintConfScore.measureText(confScore)

                        val tagHeight = 36f
                        val tagY = if (yTop - tagHeight < 0f) yTop + tagHeight else yTop
                        val tagTop = tagY - tagHeight

                        val startX = x1 + 8f
                        val confLabelX = startX + mainWidth + 8f
                        val confScoreX = confLabelX + confLabelWidth + 4f
                        val totalTagWidth = (confScoreX + confScoreWidth + 8f) - x1

                        val tagRect = android.graphics.RectF(x1, tagTop, x1 + totalTagWidth, tagY)
                        drawRoundRect(tagRect, 6f, 6f, paintBg)

                        // 繪製物種與聲音類別
                        drawText(speciesAndSound, startX, tagY - 9f, paintText)
                        // 繪製較小字級的信心分數 Label
                        drawText(confLabel, confLabelX, tagY - 9f, paintConfLabel)
                        // 繪製小數點數值
                        drawText(confScore, confScoreX, tagY - 9f, paintConfScore)
                    }
                }

                // 4. 繪製回放指針 (Playhead)
                if (isPlaying && playheadProgress in 0f..1f) {
                    val playX = canvasWidth * playheadProgress
                    // 光暈線
                    drawLine(
                        color = SilicGreen.copy(alpha = 0.5f),
                        start = Offset(playX, 0f),
                        end = Offset(playX, canvasHeight),
                        strokeWidth = 6f
                    )
                    // 核心線
                    drawLine(
                        color = Color.Black,
                        start = Offset(playX, 0f),
                        end = Offset(playX, canvasHeight),
                        strokeWidth = 2f
                    )
                }

                // 5. 繪製頻率參考文字刻度 (左上與左下)
                drawContext.canvas.nativeCanvas.apply {
                    val axisPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(200, 40, 55, 48)
                        textSize = 22f
                        isAntiAlias = true
                    }
                    drawText("15 kHz", 8f, 26f, axisPaint)
                    drawText("7 kHz", 8f, freqToY(7000.0) - 4f, axisPaint)
                    drawText("1 kHz", 8f, freqToY(1000.0) - 4f, axisPaint)
                    drawText("100 Hz", 8f, canvasHeight - 6f, axisPaint)

                    drawText("0.0s", 10f, canvasHeight - 6f, axisPaint)
                    drawText("1.5s", canvasWidth / 2 - 20f, canvasHeight - 6f, axisPaint)
                    drawText("3.0s", canvasWidth - 55f, canvasHeight - 6f, axisPaint)
                }
            }
        }
    }
}

// 依信心分數標記框體顏色：低分紅、中段黃、高分綠
private fun confidenceToColor(confidence: Float): Color {
    val t = confidence.coerceIn(0f, 1f)
    return if (t < 0.5f) {
        lerp(Color(0xFFFF5252), Color(0xFFFFD740), t / 0.5f)
    } else {
        lerp(Color(0xFFFFD740), Color(0xFF00E676), (t - 0.5f) / 0.5f)
    }
}
