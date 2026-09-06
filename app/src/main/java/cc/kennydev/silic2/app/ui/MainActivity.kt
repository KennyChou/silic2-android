package cc.kennydev.silic2.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import cc.kennydev.silic2.app.ui.screens.AboutScreen
import cc.kennydev.silic2.app.ui.screens.LiveDetectionScreen
import cc.kennydev.silic2.app.ui.screens.RecordingDetailScreen
import cc.kennydev.silic2.app.ui.screens.RecordingsListScreen
import cc.kennydev.silic2.app.ui.screens.SpeciesFilterScreen
import cc.kennydev.silic2.app.ui.theme.DarkForestBg
import cc.kennydev.silic2.app.ui.theme.ForestSurface
import cc.kennydev.silic2.app.ui.theme.SilicGreen
import cc.kennydev.silic2.app.ui.theme.Silic2Theme
import cc.kennydev.silic2.app.ui.theme.TextPrimary
import cc.kennydev.silic2.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val viewModel: SilicViewModel by viewModels()
    private val requestedScreen = MutableStateFlow<Screen?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.startListening()
        } else {
            Toast.makeText(this, "需要麥克風錄音權限以進行聲音辨識", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 保持螢幕常亮，避免野外監聽與分析時手機自動休眠關閉螢幕
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            Silic2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkForestBg
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.Live) }
                    val uiState by viewModel.uiState.collectAsState()
                    val detailSession by viewModel.currentDetailSession.collectAsState()
                    val reqScreen by requestedScreen.collectAsState()
                    val isAnalyzingAudio by viewModel.isAnalyzingAudio.collectAsState()
                    val progressMessage by viewModel.analysisProgressMessage.collectAsState()

                    // 處理外部 Intent 或跳轉請求
                    LaunchedEffect(reqScreen) {
                        reqScreen?.let {
                            currentScreen = it
                            requestedScreen.value = null
                        }
                    }

                    // 返回鍵處理
                    BackHandler(enabled = currentScreen != Screen.Live) {
                        when (currentScreen) {
                            Screen.Filter -> currentScreen = Screen.Live
                            Screen.Recordings -> currentScreen = Screen.Live
                            Screen.Detail -> currentScreen = Screen.Recordings
                            Screen.About -> currentScreen = Screen.Live
                            Screen.Live -> { }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedContent(targetState = currentScreen, label = "ScreenTransition") { screen ->
                            when (screen) {
                                Screen.Live -> {
                                    LiveDetectionScreen(
                                        viewModel = viewModel,
                                        onNavigateToFilter = { currentScreen = Screen.Filter },
                                        onNavigateToRecordings = { currentScreen = Screen.Recordings },
                                        onNavigateToAbout = { currentScreen = Screen.About }
                                    )
                                }
                                Screen.Filter -> {
                                    SpeciesFilterScreen(
                                        allClasses = viewModel.allSoundClasses,
                                        selectedIds = uiState.targetClassIds,
                                        confThreshold = uiState.confThreshold,
                                        onSetConfThreshold = { thresh -> viewModel.setConfThreshold(thresh) },
                                        onToggleClass = { id -> viewModel.toggleTargetClass(id) },
                                        onSelectMultiple = { ids -> viewModel.selectMultipleClasses(ids) },
                                        onDeselectMultiple = { ids -> viewModel.deselectMultipleClasses(ids) },
                                        onSetSelection = { ids -> viewModel.setTargetClasses(ids) },
                                        onClearSelection = { viewModel.clearTargetClasses() },
                                        onBack = { currentScreen = Screen.Live }
                                    )
                                }
                                Screen.Recordings -> {
                                    RecordingsListScreen(
                                        viewModel = viewModel,
                                        onSelectSession = { session ->
                                            viewModel.selectSessionForDetail(session)
                                            currentScreen = Screen.Detail
                                        },
                                        onBack = { currentScreen = Screen.Live }
                                    )
                                }
                                Screen.Detail -> {
                                    val session = detailSession
                                    if (session != null) {
                                        RecordingDetailScreen(
                                            session = session,
                                            viewModel = viewModel,
                                            onBack = { currentScreen = Screen.Recordings }
                                        )
                                    } else {
                                        currentScreen = Screen.Recordings
                                    }
                                }
                                Screen.About -> {
                                    AboutScreen(onBack = { currentScreen = Screen.Live })
                                }
                            }
                        }

                        // 外部音訊匯入與 AI 批次辨識進度提示 Dialog
                        if (isAnalyzingAudio) {
                            Dialog(
                                onDismissRequest = { },
                                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = ForestSurface,
                                    tonalElevation = 8.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(24.dp)
                                            .fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator(
                                            color = SilicGreen,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Text(
                                            text = "SILIC 2 音訊解析與辨識",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (progressMessage.isNotEmpty()) progressMessage else "正在處理音訊...",
                                            fontSize = 13.sp,
                                            color = TextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 處理隨 Intent 啟動的外部音訊
        handleIncomingAudioIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingAudioIntent(intent)
    }

    private fun handleIncomingAudioIntent(intent: Intent?) {
        val uri = extractAudioUri(intent) ?: return
        viewModel.importAndAnalyzeAudio(
            uri = uri,
            onCompleted = {
                requestedScreen.value = Screen.Detail
            },
            onError = { errorMsg ->
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun extractAudioUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    ?: intent.clipData?.getItemAt(0)?.uri
                    ?: intent.data
            }
            Intent.ACTION_VIEW -> {
                intent.data ?: intent.clipData?.getItemAt(0)?.uri
            }
            else -> null
        }
    }

    fun checkAndStartRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startListening()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onStop() {
        super.onStop()
        val state = viewModel.uiState.value
        // 若未開啟背景聽音，退出到背景或按電源鍵時自動安全存檔並停止，防手機在背景過熱
        if (state.isRecording && !state.enableBackgroundRecording) {
            viewModel.stopListening("已退到背景，自動安全存檔以防耗電與過熱")
        }
    }

    private enum class Screen {
        Live, Filter, Recordings, Detail, About
    }
}
