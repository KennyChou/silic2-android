package cc.kennydev.silic2.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
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
import cc.kennydev.silic2.app.ui.theme.TextTertiary
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

@Composable
fun SpectrogramWaterfallView(
    spectrogramBitmap: Bitmap?,
    detections: List<SilicDetection>,
    currentAudioTimeMs: Long,
    windowDurationMs: Long = 6000L, // 6 秒寬度
    selectedDetection: SilicDetection? = null,
    onBoxClick: ((SilicDetection) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ForestSurface)
            .border(1.dp, Color(0xFF263238), RoundedCornerShape(12.dp))
    ) {
        if (spectrogramBitmap == null || currentAudioTimeMs <= 0L) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "頻譜滾動瀑布流待命中 (點擊「開始野外即時聽音」即刻啟動)",
                    fontSize = 13.sp,
                    color = TextTertiary
                )
            }
        } else {
            val imageBitmap = remember(spectrogramBitmap) { spectrogramBitmap.asImageBitmap() }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(detections, currentAudioTimeMs) {
                        detectTapGestures { tapOffset ->
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val windowStartMs = max(0L, currentAudioTimeMs - windowDurationMs)

                            val minMel = 1127.0 * ln(1.0 + 100.0 / 700.0)
                            val maxMel = 1127.0 * ln(1.0 + 15000.0 / 700.0)
                            val deltaMel = maxMel - minMel

                            fun freqToY(freqHz: Double): Float {
                                val m = 1127.0 * ln(1.0 + freqHz / 700.0)
                                val norm = ((m - minMel) / deltaMel).coerceIn(0.0, 1.0)
                                return (canvasHeight * (1.0 - norm)).toFloat()
                            }

                            for (det in detections) {
                                val x1 = ((det.timeBeginMs - windowStartMs).toFloat() / windowDurationMs * canvasWidth)
                                val x2 = ((det.timeEndMs - windowStartMs).toFloat() / windowDurationMs * canvasWidth)
                                val yTop = freqToY(det.freqHighHz.toDouble())
                                val yBottom = freqToY(det.freqLowHz.toDouble())

                                if (tapOffset.x in x1..x2 && tapOffset.y in yTop..yBottom) {
                                    onBoxClick?.invoke(det)
                                    break
                                }
                            }
                        }
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // 1. 繪製 6 秒滾動頻譜底圖
                drawImage(
                    image = imageBitmap,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(canvasWidth.toInt(), canvasHeight.toInt())
                )

                val minMel = 1127.0 * ln(1.0 + 100.0 / 700.0)
                val maxMel = 1127.0 * ln(1.0 + 15000.0 / 700.0)
                val deltaMel = maxMel - minMel

                fun freqToY(freqHz: Double): Float {
                    val m = 1127.0 * ln(1.0 + freqHz / 700.0)
                    val norm = ((m - minMel) / deltaMel).coerceIn(0.0, 1.0)
                    return (canvasHeight * (1.0 - norm)).toFloat()
                }

                // 2. 頻率參考線 (1k, 3k, 7k, 12k)
                val gridFreqs = listOf(1000.0, 3000.0, 7000.0, 12000.0)
                for (f in gridFreqs) {
                    val y = freqToY(f)
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1f
                    )
                }

                // 3. -3s AI 判定點垂直虛線 (正中央)
                val midX = canvasWidth / 2f
                drawLine(
                    color = TealAccent.copy(alpha = 0.6f),
                    start = Offset(midX, 0f),
                    end = Offset(midX, canvasHeight),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // 4. 動態標記邊界框 (時間錨定，隨頻譜一起向左游動)
                val windowStartMs = max(0L, currentAudioTimeMs - windowDurationMs)

                for (det in detections) {
                    // 若方框已游出最左側 (> 6 秒前)，或在未來，則跳過
                    if (det.timeEndMs < windowStartMs || det.timeBeginMs > currentAudioTimeMs) continue

                    val isSelected = selectedDetection == det

                    val x1 = ((det.timeBeginMs - windowStartMs).toFloat() / windowDurationMs * canvasWidth).coerceIn(0f, canvasWidth)
                    val x2 = ((det.timeEndMs - windowStartMs).toFloat() / windowDurationMs * canvasWidth).coerceIn(0f, canvasWidth)
                    val boxWidth = max(10f, x2 - x1)

                    val yTop = freqToY(det.freqHighHz.toDouble()).coerceIn(0f, canvasHeight)
                    val yBottom = freqToY(det.freqLowHz.toDouble()).coerceIn(0f, canvasHeight)
                    val boxHeight = max(10f, yBottom - yTop)

                    val boxColor = when (det.category) {
                        AnimalCategory.BIRD -> Color(0xFF00E676)
                        AnimalCategory.FROG -> Color(0xFF00E5FF)
                        AnimalCategory.MAMMAL -> Color(0xFFFFAB40)
                        AnimalCategory.OTHER -> Color(0xFFE040FB)
                        AnimalCategory.ALL -> Color.White
                    }

                    // 框體半透明發光填充
                    drawRect(
                        color = boxColor.copy(alpha = if (isSelected) 0.38f else 0.20f),
                        topLeft = Offset(x1, yTop),
                        size = Size(boxWidth, boxHeight)
                    )

                    // 框體邊線
                    drawRect(
                        color = if (isSelected) Color.White else boxColor,
                        topLeft = Offset(x1, yTop),
                        size = Size(boxWidth, boxHeight),
                        style = Stroke(width = if (isSelected) 3.5f else 2f)
                    )

                    // 標記文字標籤
                    drawContext.canvas.nativeCanvas.apply {
                        val paintBg = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(225, 18, 28, 24)
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

                        val tagRect = android.graphics.RectF(x1, tagTop, x1 + textWidth + 14f, tagY)
                        drawRoundRect(tagRect, 6f, 6f, paintBg)
                        // 類別微型彩色圓點指示
                        val paintDot = android.graphics.Paint().apply {
                            color = boxColor.hashCode()
                            style = android.graphics.Paint.Style.FILL
                        }
                        drawCircle(x1 + 8f, tagTop + tagHeight / 2f, 4f, paintDot)
                        drawText(labelText, x1 + 16f, tagY - 9f, paintText)
                    }
                }

                // 5. 軸線標示與即時區域提示
                drawContext.canvas.nativeCanvas.apply {
                    val axisPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(190, 200, 225, 215)
                        textSize = 22f
                        isAntiAlias = true
                    }
                    val hintPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(160, 100, 240, 200)
                        textSize = 20f
                        isAntiAlias = true
                    }

                    // 頻率刻度 (左側標記)
                    drawText("15k", 8f, 22f, axisPaint)
                    drawText("7k", 8f, freqToY(7000.0) - 4f, axisPaint)
                    drawText("1k", 8f, freqToY(1000.0) - 4f, axisPaint)
                    drawText("100Hz", 8f, canvasHeight - 8f, axisPaint)

                    // 時間刻度 (頂部與底部分流，避免重疊)
                    drawText("◀ 歷史標記區", 55f, 22f, hintPaint)
                    drawText("-3s [AI 判定]", midX - 50f, 22f, hintPaint)
                    drawText("即時收音 [0s]", canvasWidth - 130f, 22f, hintPaint)

                    drawText("-6s", 10f, canvasHeight - 6f, axisPaint)
                    drawText("-3s", midX - 15f, canvasHeight - 6f, axisPaint)
                    drawText("現在", canvasWidth - 50f, canvasHeight - 6f, axisPaint)
                }
            }
        }
    }
}
