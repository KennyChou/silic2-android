package cc.kennydev.silic2.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.kennydev.silic2.app.data.model.AnimalCategory
import cc.kennydev.silic2.app.data.model.RecordingSession
import cc.kennydev.silic2.app.ui.SilicViewModel
import cc.kennydev.silic2.app.ui.theme.DarkForestBg
import cc.kennydev.silic2.app.ui.theme.ForestSurface
import cc.kennydev.silic2.app.ui.theme.SilicGreen
import cc.kennydev.silic2.app.ui.theme.TealAccent
import cc.kennydev.silic2.app.ui.theme.TextPrimary
import cc.kennydev.silic2.app.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsListScreen(
    viewModel: SilicViewModel,
    onSelectSession: (RecordingSession) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sessions by viewModel.recordingSessions.collectAsState()

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importAndAnalyzeAudio(
                uri = it,
                onCompleted = { session ->
                    onSelectSession(session)
                },
                onError = { errMsg ->
                    Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<RecordingSession?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadRecordings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "野外歷史錄音與標記",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "共 ${sessions.size} 筆紀錄 · 自動儲存 WAV 與 Raven/CSV",
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
                    IconButton(
                        onClick = { audioPickerLauncher.launch("audio/*") }
                    ) {
                        Icon(
                            Icons.Default.FileOpen,
                            contentDescription = "匯入音訊檔",
                            tint = SilicGreen
                        )
                    }

                    if (sessions.isNotEmpty()) {
                        TextButton(
                            onClick = { showClearConfirmDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "清空全部",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "清空全部",
                                color = Color(0xFFFF5252),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkForestBg)
            )
        },
        containerColor = DarkForestBg
    ) { paddingValues ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = ForestSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("目前尚無野外錄音存檔", fontSize = 16.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("在即時聽音時結束錄音，將自動儲存於此", fontSize = 12.sp, color = ForestSurface)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { audioPickerLauncher.launch("audio/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = SilicGreen)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("匯入外部音訊辨識", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(sessions, key = { it.id }) { session ->
                    RecordingSessionCard(
                        session = session,
                        onClick = { onSelectSession(session) },
                        onShare = { mode ->
                            when (mode) {
                                ShareMode.ALL -> viewModel.shareAllSessionFiles(context, session)
                                ShareMode.WAV -> viewModel.shareFile(context, session.wavFile, "audio/wav", "分享 WAV 錄音檔")
                                ShareMode.RAVEN -> session.ravenFile?.let {
                                    viewModel.shareFile(context, it, "text/plain", "分享 Raven 標籤檔")
                                }
                                ShareMode.CSV -> session.csvFile?.let {
                                    viewModel.shareFile(context, it, "text/csv", "分享 CSV 辨識表")
                                }
                            }
                        },
                        onDelete = {
                            sessionToDelete = session
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("清空全部歷史錄音？", color = TextPrimary) },
            text = { Text("這將刪除手機儲存的所有 ${sessions.size} 筆 WAV 音訊、Raven 標籤檔與 CSV 報表，無法復原。", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllRecordings()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("確定清空全部", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF1E2824)
        )
    }

    sessionToDelete?.let { targetSession ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("刪除此筆錄音？", color = TextPrimary) },
            text = { Text("確定要刪除「${targetSession.id}」嗎？包含 WAV 音訊、Raven 標籤檔與 CSV 辨識表。", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRecording(targetSession)
                        sessionToDelete = null
                    }
                ) {
                    Text("確定刪除", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF1E2824)
        )
    }
}

private enum class ShareMode { ALL, WAV, RAVEN, CSV }

@Composable
private fun RecordingSessionCard(
    session: RecordingSession,
    onClick: () -> Unit,
    onShare: (ShareMode) -> Unit,
    onDelete: () -> Unit
) {
    var showShareMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16211D))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AudioFile,
                        contentDescription = null,
                        tint = SilicGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = session.id,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(onClick = { showShareMenu = true }) {
                            Icon(Icons.Default.Share, contentDescription = "分享", tint = TealAccent, modifier = Modifier.size(20.dp))
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
                                    onShare(ShareMode.ALL)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("分享無損音訊檔 (.wav)") },
                                leadingIcon = { Icon(Icons.Default.Audiotrack, contentDescription = null, tint = TealAccent) },
                                onClick = {
                                    showShareMenu = false
                                    onShare(ShareMode.WAV)
                                }
                            )
                            if (session.ravenFile != null) {
                                DropdownMenuItem(
                                    text = { Text("分享 Raven 標籤檔 (.txt)") },
                                    leadingIcon = { Icon(Icons.Default.Label, contentDescription = null, tint = Color(0xFFFFD54F)) },
                                    onClick = {
                                        showShareMenu = false
                                        onShare(ShareMode.RAVEN)
                                    }
                                )
                            }
                            if (session.csvFile != null) {
                                DropdownMenuItem(
                                    text = { Text("分享 CSV 辨識表 (.csv)") },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF81C784)) },
                                    onClick = {
                                        showShareMenu = false
                                        onShare(ShareMode.CSV)
                                    }
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "刪除", tint = Color(0xFFFF7043), modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 時間、長度與檔案大小
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = session.formattedDate, fontSize = 12.sp, color = TextSecondary)
                Text(text = "·", fontSize = 12.sp, color = TextSecondary)
                Text(text = "時長 ${session.formattedDuration}", fontSize = 12.sp, color = TextSecondary)
                Text(text = "·", fontSize = 12.sp, color = TextSecondary)
                Text(text = session.formattedSize, fontSize = 12.sp, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 辨識物種摘要標籤
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF22382E),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${session.detections.size} 個標記",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SilicGreen,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = session.speciesSummary,
                        fontSize = 12.sp,
                        color = Color(0xFFE0E0E0),
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("查看頻譜與回放", fontSize = 11.sp, color = TealAccent)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
