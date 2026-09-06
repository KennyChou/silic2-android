package cc.kennydev.silic2.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.kennydev.silic2.app.data.model.AnimalCategory
import cc.kennydev.silic2.app.data.model.RecordingSession
import cc.kennydev.silic2.app.data.model.SilicDetection
import cc.kennydev.silic2.app.ui.SilicViewModel
import cc.kennydev.silic2.app.ui.components.DetectionCard
import cc.kennydev.silic2.app.ui.theme.DarkForestBg
import cc.kennydev.silic2.app.ui.theme.ForestSurface
import cc.kennydev.silic2.app.ui.theme.SilicGreen
import cc.kennydev.silic2.app.ui.theme.TealAccent
import cc.kennydev.silic2.app.ui.theme.TextPrimary
import cc.kennydev.silic2.app.ui.theme.TextSecondary
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailScreen(
    session: RecordingSession,
    viewModel: SilicViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playbackState by viewModel.playbackState.collectAsState()
    val spectrogramBitmap by viewModel.detailSpectrogramBitmap.collectAsState()
    val isLoading by viewModel.detailIsLoading.collectAsState()
    var selectedDet by remember { mutableStateOf<SilicDetection?>(null) }
    var showShareMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPlayback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = session.id,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${session.formattedDate} · ${session.formattedDuration}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "刪除此錄音", tint = Color(0xFFFF5252))
                    }
                    Box {
                        IconButton(onClick = { showShareMenu = true }) {
                            Icon(Icons.Default.Share, contentDescription = "分享檔案", tint = TealAccent)
                        }
                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("打包分享全部檔案 (.wav + 標記)") },
                                leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null, tint = SilicGreen) },
                                onClick = {
                                    showShareMenu = false
                                    viewModel.shareAllSessionFiles(context, session)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("分享無損音訊檔 (.wav)") },
                                leadingIcon = { Icon(Icons.Default.Audiotrack, contentDescription = null, tint = TealAccent) },
                                onClick = {
                                    showShareMenu = false
                                    viewModel.shareFile(context, session.wavFile, "audio/wav", "分享 WAV 錄音檔")
                                }
                            )
                            if (session.ravenFile != null) {
                                DropdownMenuItem(
                                    text = { Text("分享 Raven 標籤檔 (.txt)") },
                                    leadingIcon = { Icon(Icons.Default.Label, contentDescription = null, tint = Color(0xFFFFD54F)) },
                                    onClick = {
                                        showShareMenu = false
                                        viewModel.shareFile(context, session.ravenFile, "text/plain", "分享 Raven 標籤檔")
                                    }
                                )
                            }
                            if (session.csvFile != null) {
                                DropdownMenuItem(
                                    text = { Text("分享 CSV 辨識表 (.csv)") },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF81C784)) },
                                    onClick = {
                                        showShareMenu = false
                                        viewModel.shareFile(context, session.csvFile, "text/csv", "分享 CSV 辨識表")
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkForestBg)
            )
        },
        containerColor = DarkForestBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // 1. 歷史全段頻譜圖 (Fixed 固定在頂部，不隨下方清單滾動)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F0D))
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SilicGreen)
                    }
                } else if (spectrogramBitmap != null) {
                    HistoricalSpectrogramView(
                        bitmap = spectrogramBitmap!!,
                        durationMs = (session.durationSec * 1000).toLong(),
                        detections = session.detections,
                        currentPlayPosMs = playbackState.currentPositionMs,
                        isPlaying = playbackState.isPlaying,
                        selectedDetection = selectedDet,
                        onSelectDetection = { det ->
                            selectedDet = det
                            viewModel.playSessionClip(session, det)
                        },
                        onSeekTime = { seekMs ->
                            if (!playbackState.isPlaying) {
                                viewModel.playSession(session)
                            }
                            viewModel.seekPlayback(seekMs)
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("無頻譜資訊", color = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. 音訊播放控制器 (Fixed 固定在頂部)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16211D))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val curSec = (playbackState.currentPositionMs / 1000).toInt()
                        val totSec = session.durationSec.toInt()
                        Text(
                            text = String.format(java.util.Locale.getDefault(), "%02d:%02d / %02d:%02d", curSec / 60, curSec % 60, totSec / 60, totSec % 60),
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )

                        Button(
                            onClick = {
                                if (playbackState.isPlaying) {
                                    viewModel.stopPlayback()
                                } else {
                                    viewModel.playSession(session)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (playbackState.isPlaying) Color(0xFFCF6679) else SilicGreen
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (playbackState.isPlaying) "暫停" else "播放全曲", fontSize = 12.sp)
                        }
                    }

                    // 互動進度條 (支援拖曳與點選跳轉)
                    Slider(
                        value = playbackState.progress.coerceIn(0f, 1f),
                        onValueChange = { newProg ->
                            val targetMs = (newProg * session.durationSec * 1000).toLong()
                            if (!playbackState.isPlaying) {
                                viewModel.playSession(session)
                            }
                            viewModel.seekPlayback(targetMs)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = SilicGreen,
                            activeTrackColor = SilicGreen,
                            inactiveTrackColor = Color(0xFF22382E)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. 物種紀錄標題
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "物種辨識紀錄 (${session.detections.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "點擊「▶」試聽並高亮頻譜",
                    fontSize = 11.sp,
                    color = TealAccent
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4. 物種卡片清單 (獨立在下方滑動，不受頂部頻譜圖影響)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (session.detections.isEmpty()) {
                    item {
                        Text(
                            text = "此錄音無高於門檻之物種辨識紀錄",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(session.detections) { det ->
                        DetectionCard(
                            detection = det,
                            onPlayClip = {
                                selectedDet = det
                                viewModel.playSessionClip(session, det)
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("刪除此筆錄音？", color = TextPrimary) },
            text = { Text("確定要刪除「${session.id}」的所有音訊與標籤檔案嗎？此操作無法復原。", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteRecording(session)
                        onBack()
                    }
                ) {
                    Text("確定刪除", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF1E2824)
        )
    }
}

@Composable
private fun HistoricalSpectrogramView(
    bitmap: Bitmap,
    durationMs: Long,
    detections: List<SilicDetection>,
    currentPlayPosMs: Long,
    isPlaying: Boolean,
    selectedDetection: SilicDetection?,
    onSelectDetection: (SilicDetection) -> Unit,
    onSeekTime: (Long) -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    val fMin = 100.0
    val fMax = 15000.0
    val melMin = 1127.0 * ln(1.0 + fMin / 700.0)
    val melMax = 1127.0 * ln(1.0 + fMax / 700.0)

    fun freqToY(freqHz: Double, height: Float): Float {
        val safeFreq = freqHz.coerceIn(fMin, fMax)
        val mel = 1127.0 * ln(1.0 + safeFreq / 700.0)
        val norm = ((mel - melMin) / (melMax - melMin)).toFloat().coerceIn(0f, 1f)
        return (1f - norm) * height
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(detections, durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs <= 0) return@detectTapGestures
                        val clickedDet = detections.find { det ->
                            val x1 = (det.timeBeginMs.toFloat() / durationMs) * size.width
                            val x2 = (det.timeEndMs.toFloat() / durationMs) * size.width
                            val yTop = freqToY(det.freqHighHz.toDouble(), size.height.toFloat())
                            val yBottom = freqToY(det.freqLowHz.toDouble(), size.height.toFloat())
                            offset.x in (x1 - 10f)..(x2 + 10f) && offset.y in yTop..yBottom
                        }
                        if (clickedDet != null) {
                            onSelectDetection(clickedDet)
                        } else {
                            val targetMs = (offset.x / size.width * durationMs).toLong().coerceIn(0L, durationMs)
                            onSeekTime(targetMs)
                        }
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. 繪製全段黑白頻譜圖
            drawImage(
                image = imageBitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(canvasWidth.toInt(), canvasHeight.toInt())
            )

            // 2. 繪製所有 AI 標記方框
            if (durationMs > 0) {
                for (det in detections) {
                    val isSelected = selectedDetection == det
                    val x1 = ((det.timeBeginMs.toFloat() / durationMs) * canvasWidth).coerceIn(0f, canvasWidth)
                    val x2 = ((det.timeEndMs.toFloat() / durationMs) * canvasWidth).coerceIn(0f, canvasWidth)
                    val boxWidth = max(8f, x2 - x1)

                    val yTop = freqToY(det.freqHighHz.toDouble(), canvasHeight).coerceIn(0f, canvasHeight)
                    val yBottom = freqToY(det.freqLowHz.toDouble(), canvasHeight).coerceIn(0f, canvasHeight)
                    val boxHeight = max(8f, yBottom - yTop)

                    val boxColor = when (det.category) {
                        AnimalCategory.BIRD -> Color(0xFF00E676)
                        AnimalCategory.FROG -> Color(0xFF00E5FF)
                        AnimalCategory.MAMMAL -> Color(0xFFFFAB40)
                        AnimalCategory.OTHER -> Color(0xFFE040FB)
                        AnimalCategory.ALL -> Color.White
                    }

                    // 框體半透明填滿
                    drawRect(
                        color = boxColor.copy(alpha = if (isSelected) 0.45f else 0.20f),
                        topLeft = Offset(x1, yTop),
                        size = Size(boxWidth, boxHeight)
                    )

                    // 框體邊線
                    drawRect(
                        color = if (isSelected) Color.White else boxColor,
                        topLeft = Offset(x1, yTop),
                        size = Size(boxWidth, boxHeight),
                        style = Stroke(width = if (isSelected) 3.5f else 1.8f)
                    )

                    // 標記小圓角標籤
                    drawContext.canvas.nativeCanvas.apply {
                        val paintBg = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(220, 15, 25, 20)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val paintText = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 24f
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        val label = "${det.speciesName} ${(det.confidence * 100).toInt()}%"
                        val tw = paintText.measureText(label)
                        val th = 32f
                        val tagY = if (yTop - th < 0f) yTop + th else yTop
                        val tagTop = tagY - th

                        val rectF = android.graphics.RectF(x1, tagTop, x1 + tw + 12f, tagY)
                        drawRoundRect(rectF, 4f, 4f, paintBg)
                        drawText(label, x1 + 6f, tagY - 8f, paintText)
                    }
                }
            }

            // 3. 發光播放時間軸指針 (Playhead Line - 無論播放中或暫停都清晰可見)
            if (durationMs > 0 && (isPlaying || currentPlayPosMs > 0)) {
                val playX = (currentPlayPosMs.toFloat() / durationMs * canvasWidth).coerceIn(0f, canvasWidth)
                // 外部微光外暈
                drawLine(
                    color = SilicGreen.copy(alpha = if (isPlaying) 0.50f else 0.25f),
                    start = Offset(playX, 0f),
                    end = Offset(playX, canvasHeight),
                    strokeWidth = 6f
                )
                // 核心指示線
                drawLine(
                    color = if (isPlaying) Color.White else SilicGreen,
                    start = Offset(playX, 0f),
                    end = Offset(playX, canvasHeight),
                    strokeWidth = 2.5f
                )
                // 指針頂部圓球標記
                drawCircle(
                    color = if (isPlaying) Color.White else SilicGreen,
                    radius = 5.5f,
                    center = Offset(playX, 7f)
                )
            }

            // 4. 軸線刻度標示
            drawContext.canvas.nativeCanvas.apply {
                val axisPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(170, 180, 210, 200)
                    textSize = 20f
                    isAntiAlias = true
                }
                drawText("15k", 6f, 20f, axisPaint)
                drawText("7k", 6f, freqToY(7000.0, canvasHeight) - 2f, axisPaint)
                drawText("1k", 6f, freqToY(1000.0, canvasHeight) - 2f, axisPaint)
                drawText("100Hz", 6f, canvasHeight - 6f, axisPaint)
            }
        }
    }
}
