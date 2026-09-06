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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.kennydev.silic2.app.data.model.AnimalCategory
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
                        color = Color.White.copy(alpha = 0.15f),
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

                    val boxColor = when (det.category) {
                        AnimalCategory.BIRD -> Color(0xFF00E676)
                        AnimalCategory.FROG -> Color(0xFF00E5FF)
                        AnimalCategory.MAMMAL -> Color(0xFFFFAB40)
                        AnimalCategory.OTHER -> Color(0xFFE040FB)
                        AnimalCategory.ALL -> Color.White
                    }

                    // 框體半透明填充
                    drawRect(
                        color = boxColor.copy(alpha = if (isSelected) 0.35f else 0.18f),
                        topLeft = Offset(x1, yTop),
                        size = Size(boxWidth, boxHeight)
                    )

                    // 框體邊線
                    drawRect(
                        color = if (isSelected) Color.White else boxColor,
                        topLeft = Offset(x1, yTop),
                        size = Size(boxWidth, boxHeight),
                        style = Stroke(width = if (isSelected) 3f else 1.8f)
                    )

                    // 標籤文字 (使用 nativeCanvas 繪製)
                    drawContext.canvas.nativeCanvas.apply {
                        val paintBg = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(220, 20, 30, 25)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val paintText = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 28f
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }

                        val labelText = "${det.speciesName} ${det.soundClass} ${(det.confidence * 100).toInt()}%"
                        val textWidth = paintText.measureText(labelText)
                        val tagHeight = 36f

                        val tagY = if (yTop - tagHeight < 0f) yTop + tagHeight else yTop
                        val tagTop = tagY - tagHeight

                        drawRect(x1, tagTop, x1 + textWidth + 12f, tagY, paintBg)
                        drawText(labelText, x1 + 6f, tagY - 8f, paintText)
                    }
                }

                // 4. 繪製回放指針 (Playhead)
                if (isPlaying && playheadProgress in 0f..1f) {
                    val playX = canvasWidth * playheadProgress
                    // 光暈線
                    drawLine(
                        color = SilicGreen.copy(alpha = 0.4f),
                        start = Offset(playX, 0f),
                        end = Offset(playX, canvasHeight),
                        strokeWidth = 6f
                    )
                    // 核心線
                    drawLine(
                        color = Color.White,
                        start = Offset(playX, 0f),
                        end = Offset(playX, canvasHeight),
                        strokeWidth = 2f
                    )
                }

                // 5. 繪製頻率參考文字刻度 (左上與左下)
                drawContext.canvas.nativeCanvas.apply {
                    val axisPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(180, 200, 220, 210)
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
