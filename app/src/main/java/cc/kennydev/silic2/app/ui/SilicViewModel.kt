package cc.kennydev.silic2.app.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.content.Intent
import cc.kennydev.silic2.app.data.model.RecordingSession
import cc.kennydev.silic2.app.data.repository.RecordingRepository
import cc.kennydev.silic2.app.domain.processor.WavSpectrogramGenerator
import cc.kennydev.silic2.app.data.model.AnimalCategory
import cc.kennydev.silic2.app.data.model.SilicDetection
import cc.kennydev.silic2.app.data.model.SoundClass
import cc.kennydev.silic2.app.data.repository.SoundClassRepository
import cc.kennydev.silic2.app.domain.audio.AudioPlaybackManager
import cc.kennydev.silic2.app.domain.audio.AudioRecorder
import cc.kennydev.silic2.app.domain.audio.PlaybackState
import cc.kennydev.silic2.app.domain.audio.WavFileWriter
import cc.kennydev.silic2.app.domain.inference.SilicDetector
import cc.kennydev.silic2.app.domain.processor.MelSpectrogramConverter
import cc.kennydev.silic2.app.domain.processor.RainbowRenderer
import cc.kennydev.silic2.app.domain.processor.RollingSpectrogramProcessor
import cc.kennydev.silic2.app.domain.processor.UniversalAudioDecoder
import cc.kennydev.silic2.app.domain.processor.BatchAudioClassifier
import kotlinx.coroutines.withContext

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import cc.kennydev.silic2.app.service.RecordingForegroundService
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class SilicUiState(
    val isRecording: Boolean = false,
    val amplitude: Float = 0f,
    val detections: List<SilicDetection> = emptyList(),
    val targetClassIds: Set<Int> = emptySet(),
    val confThreshold: Float = 0.20f,
    val isModelLoaded: Boolean = false,
    val statusMessage: String = "就緒",
    val spectrogramBitmap: Bitmap? = null,
    val currentAudioTimeMs: Long = 0L,
    val rollingDetections: List<SilicDetection> = emptyList(),
    val selectedDetection: SilicDetection? = null,
    val latestWavFilePath: String? = null,
    val isGrayscale: Boolean = true,
    val enableBackgroundRecording: Boolean = false,
    val maxDurationMinutes: Int = 60,
    val autoStopLowBattery: Boolean = true
)

class SilicViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SilicUiState())
    val uiState: StateFlow<SilicUiState> = _uiState.asStateFlow()

    fun toggleSpectrogramColorMode() {
        val next = !rollingProcessor.isGrayscale
        rollingProcessor.isGrayscale = next
        _uiState.update { it.copy(isGrayscale = next) }
    }

    private val melConverter = MelSpectrogramConverter()
    private val rainbowRenderer = RainbowRenderer()
    private val detector = SilicDetector(application.applicationContext)
    private val playbackManager = AudioPlaybackManager(sampleRate = 32000, scope = viewModelScope)

    private val recordingRepository = RecordingRepository(application.applicationContext)
    private val wavSpectrogramGenerator = WavSpectrogramGenerator()
    private val audioDecoder = UniversalAudioDecoder(application.applicationContext)
    private val batchClassifier = BatchAudioClassifier(melConverter, rainbowRenderer, detector)

    private val _isAnalyzingAudio = MutableStateFlow<Boolean>(false)
    val isAnalyzingAudio: StateFlow<Boolean> = _isAnalyzingAudio.asStateFlow()

    private val _analysisProgressMessage = MutableStateFlow<String>("")
    val analysisProgressMessage: StateFlow<String> = _analysisProgressMessage.asStateFlow()


    private val _recordingSessions = MutableStateFlow<List<RecordingSession>>(emptyList())
    val recordingSessions: StateFlow<List<RecordingSession>> = _recordingSessions.asStateFlow()

    private val _currentDetailSession = MutableStateFlow<RecordingSession?>(null)
    val currentDetailSession: StateFlow<RecordingSession?> = _currentDetailSession.asStateFlow()

    private val _detailSpectrogramBitmap = MutableStateFlow<Bitmap?>(null)
    val detailSpectrogramBitmap: StateFlow<Bitmap?> = _detailSpectrogramBitmap.asStateFlow()

    private val _detailIsLoading = MutableStateFlow<Boolean>(false)
    val detailIsLoading: StateFlow<Boolean> = _detailIsLoading.asStateFlow()

    private val rollingProcessor = RollingSpectrogramProcessor(sampleRate = 32000)

    val playbackState: StateFlow<PlaybackState> = playbackManager.playbackState

    val allSoundClasses: List<SoundClass> by lazy {
        SoundClassRepository.getAllList(application.applicationContext)
    }

    private var audioRecorder: AudioRecorder? = null
    private var currentClipSamples: ShortArray? = null
    private var currentWavFile: File? = null

    private val autoStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RecordingForegroundService.BROADCAST_AUTO_STOPPED) {
                val reason = intent.getStringExtra(RecordingForegroundService.EXTRA_STOP_REASON) ?: "安全保護自動停止"
                stopListening(reason)
            }
        }
    }

    init {
        _uiState.update {
            it.copy(
                isModelLoaded = detector.isModelLoaded,
                statusMessage = if (detector.isModelLoaded) "模型已載入，支援 398 類聲音" else "模型載入中..."
            )
        }

        // 註冊服務逾時/低電量自動停止廣播
        val filter = IntentFilter(RecordingForegroundService.BROADCAST_AUTO_STOPPED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(autoStopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(autoStopReceiver, filter)
        }

        // 啟動 30fps 滾動頻譜刷新迴圈
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                if (_uiState.value.isRecording || _uiState.value.currentAudioTimeMs > 0) {
                    val bitmap = rollingProcessor.updateBitmap()
                    val curTimeMs = rollingProcessor.totalSamplesProcessed * 1000L / 32000L
                    _uiState.update { current ->
                        current.copy(
                            spectrogramBitmap = bitmap,
                            currentAudioTimeMs = curTimeMs
                        )
                    }
                }
                delay(33) // ~30 fps
            }
        }
    }

    fun updateBackgroundSettings(enableBg: Boolean, maxMinutes: Int, lowBattery: Boolean) {
        _uiState.update {
            it.copy(
                enableBackgroundRecording = enableBg,
                maxDurationMinutes = maxMinutes,
                autoStopLowBattery = lowBattery
            )
        }
        if (_uiState.value.isRecording) {
            val app = getApplication<Application>()
            if (enableBg) {
                RecordingForegroundService.start(app, maxMinutes, lowBattery)
            } else {
                RecordingForegroundService.stop(app)
            }
        }
    }

    fun startListening(): Boolean {
        if (_uiState.value.isRecording) return true
        stopPlayback()
        rollingProcessor.reset()

        // 建立 WAV 儲存檔
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: getApplication<Application>().filesDir
        val wavFile = File(dir, "SILIC2_$timeStamp.wav")
        currentWavFile = wavFile
        val wavWriter = WavFileWriter(wavFile, sampleRate = 32000)

        audioRecorder = AudioRecorder(
            sampleRate = 32000,
            clipLengthMs = 3000,
            stepMs = 1500,
            wavWriter = wavWriter,
            onAmplitudeChanged = { amp ->
                _uiState.update { it.copy(amplitude = amp) }
            },
            onAudioStream = { chunk, count ->
                rollingProcessor.pushSamples(chunk, 0, count)
            },
            onAudioClipReady = { pcmSamples, offsetMs ->
                currentClipSamples = pcmSamples
                processAudioClip(pcmSamples, offsetMs)
            }
        )

        val success = audioRecorder?.start() ?: false
        if (success) {
            if (_uiState.value.enableBackgroundRecording) {
                RecordingForegroundService.start(
                    getApplication(),
                    _uiState.value.maxDurationMinutes,
                    _uiState.value.autoStopLowBattery
                )
            }
            _uiState.update {
                it.copy(
                    isRecording = true,
                    statusMessage = "野外即時聽音與滾動頻譜分析中...",
                    latestWavFilePath = wavFile.absolutePath,
                    rollingDetections = emptyList()
                )
            }
        } else {
            audioRecorder = null
            currentWavFile = null
            _uiState.update { it.copy(isRecording = false, statusMessage = "麥克風啟動失敗，請檢查錄音權限") }
        }
        return success
    }

    fun stopListening(stopReason: String? = null) {
        RecordingForegroundService.stop(getApplication())
        audioRecorder?.stop()
        audioRecorder = null
        val wav = currentWavFile
        val list = _uiState.value.detections
        if (wav != null && list.isNotEmpty()) {
            try {
                val base = wav.nameWithoutExtension
                val pDir = wav.parentFile ?: getApplication<Application>().filesDir
                writeRavenFile(File(pDir, "$base.txt"), list)
                writeCsvFile(File(pDir, "$base.csv"), list)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _uiState.update {
            it.copy(
                isRecording = false,
                amplitude = 0f,
                statusMessage = stopReason ?: if (wav != null) "已儲存錄音與標記: ${wav.nameWithoutExtension} (.wav / .txt / .csv)" else "監聽已停止"
            )
        }
    }

    private fun processAudioClip(pcmSamples: ShortArray, offsetMs: Long) {
        viewModelScope.launch(Dispatchers.Default) {
            val melSpec = melConverter.computeMelSpectrogram(pcmSamples)
            val bitmap = rainbowRenderer.renderToBitmap(melSpec)

            val targets = if (_uiState.value.targetClassIds.isEmpty()) null else _uiState.value.targetClassIds
            val results = detector.detect(
                bitmap = bitmap,
                clipStartMs = offsetMs,
                confThreshold = _uiState.value.confThreshold,
                targetClassIds = targets
            )

            if (results.isNotEmpty()) {
                _uiState.update { current ->
                    current.copy(
                        detections = results + current.detections,
                        rollingDetections = current.rollingDetections + results,
                        statusMessage = "偵測到 ${results.size} 個聲音：${results.first().speciesName}"
                    )
                }
            }
        }
    }

    /**
     * 讀取 sample_owl.pcm 執行範例測試 (鵂鶹與黃嘴角鴞)
     */
    fun runSampleTest() {
        stopListening()
        stopPlayback()
        rollingProcessor.reset()

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(statusMessage = "正在執行範例鳥鳴辨識驗證...") }
            try {
                val bytes = getApplication<Application>().assets.open("sample_owl.pcm").use {
                    it.readBytes()
                }
                val shorts = ShortArray(bytes.size / 2)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                currentClipSamples = shorts

                // 注入滾動處理器以繪製頻譜
                rollingProcessor.pushSamples(shorts, 0, shorts.size)
                val bitmap = rollingProcessor.updateBitmap()

                // 儲存範例音檔至磁碟供回放
                val dir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?: getApplication<Application>().filesDir
                val sampleWav = File(dir, "sample_owl.wav")
                WavFileWriter.createWavFromPcm(
                    pcmFile = File(getApplication<Application>().cacheDir, "temp.pcm").apply {
                        writeBytes(bytes)
                    },
                    wavFile = sampleWav
                )
                currentWavFile = sampleWav

                val melSpec = melConverter.computeMelSpectrogram(shorts)
                val melBitmap = rainbowRenderer.renderToBitmap(melSpec)

                val targets = if (_uiState.value.targetClassIds.isEmpty()) null else _uiState.value.targetClassIds
                val results = detector.detect(
                    bitmap = melBitmap,
                    clipStartMs = 0L,
                    confThreshold = _uiState.value.confThreshold,
                    targetClassIds = targets
                )

                _uiState.update { current ->
                    val newDetections = if (results.isNotEmpty()) results + current.detections else current.detections
                    current.copy(
                        spectrogramBitmap = bitmap,
                        currentAudioTimeMs = 3000L,
                        detections = newDetections,
                        rollingDetections = results,
                        latestWavFilePath = sampleWav.absolutePath,
                        statusMessage = if (results.isNotEmpty()) {
                            "範例驗證成功！辨識出 ${results.size} 筆：${results.joinToString { it.speciesName }}"
                        } else {
                            "範例測試未達門檻"
                        }
                    )
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(statusMessage = "範例測試失敗: ${t.message}") }
            }
        }
    }

    // === 回放控制 ===

    fun playFullClip() {
        val samples = currentClipSamples ?: return
        playbackManager.playShorts(samples, 0L, (samples.size * 1000L / 32000L))
    }

    fun playDetectionClip(detection: SilicDetection) {
        _uiState.update { it.copy(selectedDetection = detection) }
        val samples = currentClipSamples
        if (samples != null) {
            val relStart = max(0L, detection.timeBeginMs % 3000L)
            val relEnd = min(3000L, max(relStart + 100L, detection.timeEndMs % 3000L))
            playbackManager.playShorts(samples, relStart, relEnd)
        } else {
            currentWavFile?.let { wav ->
                playbackManager.playWavFile(wav, detection.timeBeginMs, detection.timeEndMs)
            }
        }
    }

    fun stopPlayback() {
        playbackManager.stop()
        _uiState.update { it.copy(selectedDetection = null) }
    }

    fun selectDetection(detection: SilicDetection?) {
        _uiState.update { it.copy(selectedDetection = detection) }
    }

    fun setConfThreshold(threshold: Float) {
        _uiState.update { it.copy(confThreshold = threshold) }
    }

    fun toggleTargetClass(classId: Int) {
        _uiState.update { current ->
            val set = current.targetClassIds.toMutableSet()
            if (set.contains(classId)) {
                set.remove(classId)
            } else {
                set.add(classId)
            }
            current.copy(targetClassIds = set)
        }
    }

    fun selectMultipleClasses(classIds: Collection<Int>) {
        _uiState.update { current ->
            current.copy(targetClassIds = current.targetClassIds + classIds)
        }
    }

    fun deselectMultipleClasses(classIds: Collection<Int>) {
        _uiState.update { current ->
            current.copy(targetClassIds = current.targetClassIds - classIds.toSet())
        }
    }

    fun setTargetClasses(classIds: Collection<Int>) {
        _uiState.update { current ->
            current.copy(targetClassIds = classIds.toSet())
        }
    }

    fun clearTargetClasses() {
        _uiState.update { it.copy(targetClassIds = emptySet()) }
    }

    fun clearDetections() {
        _uiState.update {
            it.copy(
                detections = emptyList(),
                rollingDetections = emptyList(),
                selectedDetection = null
            )
        }
    }

    // === 匯出格式支援 ===

    private fun writeRavenFile(targetFile: File, list: List<SilicDetection>) {
        targetFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("Selection\tView\tChannel\tBegin Time (s)\tEnd Time (s)\tLow Freq (Hz)\tHigh Freq (Hz)\tSpecies\tSound Type\tScore\n")
            list.forEachIndexed { idx, d ->
                val sel = idx + 1
                val startSec = String.format(Locale.US, "%.3f", d.timeBeginMs / 1000.0)
                val endSec = String.format(Locale.US, "%.3f", d.timeEndMs / 1000.0)
                writer.write("$sel\tSpectrogram 1\t1\t$startSec\t$endSec\t${d.freqLowHz}\t${d.freqHighHz}\t${d.speciesName}\t${d.soundClass}\t${d.confidence}\n")
            }
        }
    }

    private fun writeCsvFile(targetFile: File, list: List<SilicDetection>) {
        targetFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            writer.write("大類,物種中文名,聲音型態,學名,信心度,起始時間(秒),結束時間(秒),最低頻率(Hz),最高頻率(Hz),紀錄時間\n")
            list.forEach { d ->
                val recordTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(d.timestamp))
                val startSec = d.timeBeginMs / 1000.0
                val endSec = d.timeEndMs / 1000.0
                writer.write("${d.category.displayName},${d.speciesName},${d.soundClass},\"${d.scientificName}\",${d.confidence},$startSec,$endSec,${d.freqLowHz},${d.freqHighHz},$recordTime\n")
            }
        }
    }

    fun exportDetectionsCsv(context: Context): Uri? {
        val list = _uiState.value.detections
        if (list.isEmpty()) return null

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "SILIC2_detections_$timeStamp.csv"
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val file = File(dir, fileName)
        writeCsvFile(file, list)

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun exportRavenSelectionTable(context: Context): Uri? {
        val list = _uiState.value.detections
        if (list.isEmpty()) return null

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "SILIC2_Raven_SelectionTable_$timeStamp.txt"
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val file = File(dir, fileName)
        writeRavenFile(file, list)

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun exportWavAudio(context: Context): Uri? {
        val file = currentWavFile ?: return null
        if (!file.exists()) return null

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }


    // === 歷史錄音管理與清空功能 ===

    fun clearCurrentDetections() {
        _uiState.update {
            it.copy(
                detections = emptyList(),
                rollingDetections = emptyList(),
                selectedDetection = null,
                statusMessage = "已清空即時辨識清單"
            )
        }
    }

    fun loadRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = recordingRepository.loadSessions()
            _recordingSessions.value = list
        }
    }

    fun deleteRecording(session: RecordingSession) {
        viewModelScope.launch(Dispatchers.IO) {
            recordingRepository.deleteSession(session)
            loadRecordings()
            if (_currentDetailSession.value?.id == session.id) {
                _currentDetailSession.value = null
                _detailSpectrogramBitmap.value = null
            }
        }
    }

    fun clearAllRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            recordingRepository.clearAllSessions()
            loadRecordings()
            _currentDetailSession.value = null
            _detailSpectrogramBitmap.value = null
        }
    }

    fun selectSessionForDetail(session: RecordingSession) {
        _currentDetailSession.value = session
        _detailSpectrogramBitmap.value = null
        _detailIsLoading.value = true
        stopPlayback()

        viewModelScope.launch(Dispatchers.Default) {
            val bmp = wavSpectrogramGenerator.generateGrayscaleSpectrogram(session.wavFile)
            _detailSpectrogramBitmap.value = bmp
            _detailIsLoading.value = false
        }
    }

    fun playSession(session: RecordingSession) {
        playbackManager.playWavFile(session.wavFile)
    }

    fun playSessionClip(session: RecordingSession, detection: SilicDetection) {
        playbackManager.playWavFile(
            file = session.wavFile,
            startMs = detection.timeBeginMs,
            endMs = detection.timeEndMs
        )
    }

    fun shareFile(context: Context, file: java.io.File, mimeType: String, chooserTitle: String) {
        if (!file.exists()) return
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shareAllSessionFiles(context: Context, session: RecordingSession) {
        val uris = ArrayList<android.net.Uri>()
        try {
            if (session.wavFile.exists()) {
                uris.add(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", session.wavFile))
            }
            session.ravenFile?.takeIf { it.exists() }?.let {
                uris.add(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it))
            }
            session.csvFile?.takeIf { it.exists() }?.let {
                uris.add(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it))
            }
            if (uris.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "分享錄音與所有標記檔"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    // === 外部/本機音訊匯入與批次推論 ===

    fun importAndAnalyzeAudio(
        uri: Uri,
        onCompleted: ((RecordingSession) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (_isAnalyzingAudio.value) return
        stopListening()
        stopPlayback()

        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzingAudio.value = true
            _analysisProgressMessage.value = "正在解碼音訊檔案 (支援 WAV/M4A/MP3/AAC/FLAC)..."
            try {
                val pcmSamples = audioDecoder.decodeTo32kMonoPcm(uri) { prog ->
                    val pct = (prog * 100).toInt()
                    _analysisProgressMessage.value = "正在解碼音訊檔案... $pct%"
                }

                if (pcmSamples.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _isAnalyzingAudio.value = false
                        _analysisProgressMessage.value = ""
                        onError?.invoke("無法解碼該音訊檔案，格式可能不受支援或檔案已損毀")
                    }
                    return@launch
                }

                val durationSec = pcmSamples.size.toDouble() / 32000.0

                // 儲存為 32kHz 標準 WAV 至 Music 目錄
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val dir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?: getApplication<Application>().filesDir
                val wavFile = File(dir, "SILIC2_IMPORT_$timeStamp.wav")

                _analysisProgressMessage.value = "儲存標準 32kHz 音訊 (長度: ${String.format(Locale.US, "%.1f", durationSec)} 秒)..."
                WavFileWriter.createWavFromShortArray(wavFile, pcmSamples, 32000)

                _analysisProgressMessage.value = "開始 AI 滑動窗批次辨識..."
                val targets = if (_uiState.value.targetClassIds.isEmpty()) null else _uiState.value.targetClassIds
                val detections = batchClassifier.classifyFullAudio(
                    audioSamples = pcmSamples,
                    confThreshold = _uiState.value.confThreshold,
                    targetClassIds = targets
                ) { currentChunk, totalChunks ->
                    val currentSec = (currentChunk * 1.5).coerceAtMost(durationSec)
                    _analysisProgressMessage.value = "AI 批次辨識中: 片段 $currentChunk/$totalChunks (${String.format(Locale.US, "%.1f", currentSec)}s / ${String.format(Locale.US, "%.1f", durationSec)}s)"
                }

                // 產生科研標記表 (Raven Selection Table 與 CSV)
                val baseName = wavFile.nameWithoutExtension
                val ravenFile = File(dir, "$baseName.txt")
                val csvFile = File(dir, "$baseName.csv")

                writeRavenFile(ravenFile, detections)
                writeCsvFile(csvFile, detections)

                // 重新載入歷史清單並同步至 UI
                val sessions = recordingRepository.loadSessions()
                _recordingSessions.value = sessions

                val newSession = sessions.find { it.id == baseName } ?: RecordingSession(
                    id = baseName,
                    wavFile = wavFile,
                    ravenFile = ravenFile,
                    csvFile = csvFile,
                    timestamp = System.currentTimeMillis(),
                    durationSec = durationSec,
                    detections = detections
                )

                withContext(Dispatchers.Main) {
                    _isAnalyzingAudio.value = false
                    _analysisProgressMessage.value = ""
                    selectSessionForDetail(newSession)
                    onCompleted?.invoke(newSession)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _isAnalyzingAudio.value = false
                    _analysisProgressMessage.value = ""
                    onError?.invoke("音訊匯入失敗: ${e.localizedMessage ?: "未知錯誤"}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(autoStopReceiver)
        } catch (_: Exception) { }
        stopListening()
        stopPlayback()
        detector.close()
    }
}
