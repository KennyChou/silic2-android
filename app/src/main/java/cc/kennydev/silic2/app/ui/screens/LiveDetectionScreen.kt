package cc.kennydev.silic2.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import cc.kennydev.silic2.app.ui.MainActivity
import cc.kennydev.silic2.app.ui.components.BackgroundSettingsDialog
import cc.kennydev.silic2.app.ui.components.ConfidenceThresholdDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cc.kennydev.silic2.app.ui.SilicViewModel
import cc.kennydev.silic2.app.ui.components.DetectionCard
import cc.kennydev.silic2.app.ui.components.SpectrogramWaterfallView
import cc.kennydev.silic2.app.ui.components.WaveformVisualizer
import cc.kennydev.silic2.app.ui.theme.DarkForestBg
import cc.kennydev.silic2.app.ui.theme.ForestSurface
import cc.kennydev.silic2.app.ui.theme.SilicGreen
import cc.kennydev.silic2.app.ui.theme.TealAccent
import cc.kennydev.silic2.app.ui.theme.TextPrimary
import cc.kennydev.silic2.app.ui.theme.TextSecondary
import cc.kennydev.silic2.app.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDetectionScreen(
    viewModel: SilicViewModel,
    onNavigateToFilter: () -> Unit,
    onNavigateToRecordings: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val context = LocalContext.current

    var showShareMenu by remember { mutableStateOf(false) }
    var showConfThresholdDialog by remember { mutableStateOf(false) }
    var showBgSettingsDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val started = viewModel.startListening()
            if (!started) {
                Toast.makeText(context, "麥克風初始化失敗，請稍後重試", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "請授予麥克風權限以進行野生動物聲音辨識", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SILIC 2 野生動物識別",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = uiState.statusMessage,
                            fontSize = 11.sp,
                            color = if (uiState.isRecording) SilicGreen else TextTertiary
                        )
                    }
                },
                actions = {
                    // 歷史錄音資料庫按鈕
                    IconButton(onClick = onNavigateToRecordings) {
                        Icon(Icons.Default.Description, contentDescription = "歷史錄音與標記", tint = SilicGreen)
                    }

                    // 物種篩選按鈕
                    IconButton(onClick = onNavigateToFilter) {
                        BadgedBox(
                            badge = {
                                if (uiState.targetClassIds.isNotEmpty()) {
                                    Badge(containerColor = SilicGreen) {
                                        Text("${uiState.targetClassIds.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "篩選目標物種", tint = TextPrimary)
                        }
                    }

                    // 匯出與分享選單
                    Box {
                        IconButton(onClick = { showShareMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多選項", tint = TextPrimary)
                        }

                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("信心分數：${(uiState.confThreshold * 100).toInt()}% (點擊設定)") },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, tint = SilicGreen) },
                                onClick = {
                                    showShareMenu = false
                                    showConfThresholdDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (uiState.enableBackgroundRecording)
                                            "背景聽音：🛡️ 開啟中 (${uiState.maxDurationMinutes}分保護)"
                                        else
                                            "背景聽音：🔒 關閉 (防過熱安全模式)"
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null, tint = if (uiState.enableBackgroundRecording) TealAccent else Color(0xFFFFB74D)) },
                                onClick = {
                                    showShareMenu = false
                                    showBgSettingsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("分享 CSV 偵測紀錄") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = SilicGreen) },
                                onClick = {
                                    showShareMenu = false
                                    val uri = viewModel.exportDetectionsCsv(context)
                                    if (uri != null) {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/csv"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "分享 CSV 紀錄"))
                                    } else {
                                        Toast.makeText(context, "目前尚無偵測紀錄", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("分享 Raven 標籤檔 (.txt)") },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = TealAccent) },
                                onClick = {
                                    showShareMenu = false
                                    val uri = viewModel.exportRavenSelectionTable(context)
                                    if (uri != null) {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "分享 Raven Selection Table"))
                                    } else {
                                        Toast.makeText(context, "目前尚無偵測紀錄", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("分享 WAV 錄音檔") },
                                leadingIcon = { Icon(Icons.Default.AudioFile, contentDescription = null, tint = Color(0xFFFFB74D)) },
                                onClick = {
                                    showShareMenu = false
                                    val uri = viewModel.exportWavAudio(context)
                                    if (uri != null) {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "audio/wav"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "分享 WAV 錄音檔"))
                                    } else {
                                        Toast.makeText(context, "目前尚無錄音檔案", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            if (uiState.detections.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("清空即時辨識清單") },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFFF8A80)) },
                                    onClick = {
                                        showShareMenu = false
                                        viewModel.clearCurrentDetections()
                                        Toast.makeText(context, "已清空即時辨識清單", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("關於 SILIC 2") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = TealAccent) },
                                onClick = {
                                    showShareMenu = false
                                    onNavigateToAbout()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForestSurface,
                    titleContentColor = TextPrimary
                )
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
            Spacer(modifier = Modifier.height(6.dp))

            // 1. 動態滾動瀑布流頻譜圖 (Waterfall Spectrogram View)
            SpectrogramWaterfallView(
                spectrogramBitmap = uiState.spectrogramBitmap,
                detections = uiState.rollingDetections,
                currentAudioTimeMs = uiState.currentAudioTimeMs,
                windowDurationMs = 6000L, // 6 秒視窗
                selectedDetection = uiState.selectedDetection,
                onBoxClick = { det ->
                    viewModel.selectDetection(det)
                    viewModel.playDetectionClip(det)
                }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 頻譜狀態與回放控制工具列
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playbackState.isPlaying) {
                        FilledTonalButton(
                            onClick = { viewModel.stopPlayback() },
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("停止回放", fontSize = 12.sp)
                        }
                    } else if (uiState.spectrogramBitmap != null) {
                        FilledTonalButton(
                            onClick = { viewModel.playFullClip() },
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("回放聲音", fontSize = 12.sp)
                        }
                    }
                }

                if (uiState.latestWavFilePath != null) {
                    Text(
                        text = if (uiState.isRecording) "🔴 WAV 連續存檔中" else "💾 已儲存音檔",
                        fontSize = 11.sp,
                        color = if (uiState.isRecording) Color(0xFFFF5252) else TextTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. 錄音動態波形條
            WaveformVisualizer(
                amplitude = uiState.amplitude,
                isRecording = uiState.isRecording
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. 主操作按鈕：開始聽音 / 停止聽音
            Button(
                onClick = {
                    if (uiState.isRecording) {
                        viewModel.stopListening()
                    } else {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            viewModel.startListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isRecording) Color(0xFFC62828) else SilicGreen,
                    contentColor = if (uiState.isRecording) Color.White else DarkForestBg
                )
            ) {
                Icon(
                    imageVector = if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isRecording) "停止監聽與存檔" else "開始野外即時聽音",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. 辨識結果清單標題與清空按鈕
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "即時辨識清單 (${uiState.detections.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    if (uiState.targetClassIds.isNotEmpty()) {
                        Text(
                            text = "已鎖定 ${uiState.targetClassIds.size} 種",
                            fontSize = 12.sp,
                            color = TealAccent
                        )
                    }
                }

                if (uiState.detections.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            viewModel.clearCurrentDetections()
                            Toast.makeText(context, "已清空即時辨識清單", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "清空清單",
                            tint = Color(0xFFFF8A80),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "清空清單",
                            fontSize = 12.sp,
                            color = Color(0xFFFF8A80),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (uiState.detections.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌿", fontSize = 34.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (uiState.isRecording) "即時頻譜滾動中，等待鳴叫聲..." else "點擊上方按鈕開始野外聽音",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "右側即時收音，聲音累積 3 秒後自動在左側框選標記",
                            fontSize = 11.sp,
                            color = TextTertiary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(uiState.detections) { detection ->
                        DetectionCard(
                            detection = detection,
                            isSelected = uiState.selectedDetection == detection,
                            onCardClick = {
                                viewModel.selectDetection(detection)
                                viewModel.playDetectionClip(detection)
                            },
                            onPlayClip = { viewModel.playDetectionClip(detection) }
                        )
                    }
                }
            }
        }
    }

    if (showConfThresholdDialog) {
        ConfidenceThresholdDialog(
            currentThreshold = uiState.confThreshold,
            onThresholdChanged = { newThreshold ->
                viewModel.setConfThreshold(newThreshold)
            },
            onDismiss = { showConfThresholdDialog = false }
        )
    }

    if (showBgSettingsDialog) {
        BackgroundSettingsDialog(
            enableBackgroundRecording = uiState.enableBackgroundRecording,
            maxDurationMinutes = uiState.maxDurationMinutes,
            autoStopLowBattery = uiState.autoStopLowBattery,
            onSettingsChanged = { enableBg, maxMinutes, lowBattery ->
                viewModel.updateBackgroundSettings(enableBg, maxMinutes, lowBattery)
                if (enableBg) {
                    (context as? MainActivity)?.requestNotificationPermissionIfNeeded()
                }
            },
            onDismiss = { showBgSettingsDialog = false }
        )
    }
}
