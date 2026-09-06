package cc.kennydev.silic2.app.domain.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val progress: Float = 0f
)

class AudioPlaybackManager(
    private val sampleRate: Int = 32000,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var playJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val minBufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    /**
     * 播放本機 WAV 檔案，支援全曲播放與任意片段播放 ([startMs] 至 [endMs])
     * 使用 Android 系統原生 MediaPlayer，硬體穩定、精準同步、支援長音訊
     */
    fun playWavFile(file: File, startMs: Long = 0L, endMs: Long? = null) {
        if (!file.exists() || file.length() <= 44) return
        stop()

        scope.launch(Dispatchers.Main) {
            try {
                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    prepare()
                }
                mediaPlayer = mp

                val totalDurationMs = mp.duration.toLong().coerceAtLeast(1L)
                val safeStartMs = startMs.coerceIn(0L, totalDurationMs)
                val targetEndMs = endMs?.coerceIn(safeStartMs + 100L, totalDurationMs)

                if (safeStartMs > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mp.seekTo(safeStartMs, MediaPlayer.SEEK_CLOSEST)
                    } else {
                        mp.seekTo(safeStartMs.toInt())
                    }
                }

                mp.setOnCompletionListener {
                    stop()
                }

                mp.setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlaybackManager", "MediaPlayer error: what=$what, extra=$extra")
                    stop()
                    true
                }

                mp.start()

                _playbackState.value = PlaybackState(
                    isPlaying = true,
                    currentPositionMs = safeStartMs,
                    totalDurationMs = totalDurationMs,
                    progress = safeStartMs.toFloat() / totalDurationMs
                )

                // 啟動高頻率即時進度更新迴圈 (約 30fps)
                playJob = scope.launch(Dispatchers.Default) {
                    while (isActive && _playbackState.value.isPlaying) {
                        val player = mediaPlayer ?: break
                        if (!player.isPlaying) {
                            delay(30)
                            continue
                        }
                        val currentMs = player.currentPosition.toLong().coerceIn(0L, totalDurationMs)

                        // 若有指定片段結束時間，到達時自動停止
                        if (targetEndMs != null && currentMs >= targetEndMs) {
                            stop()
                            break
                        }

                        val prog = (currentMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                        _playbackState.value = PlaybackState(
                            isPlaying = true,
                            currentPositionMs = currentMs,
                            totalDurationMs = totalDurationMs,
                            progress = prog
                        )
                        delay(33)
                    }
                }
            } catch (t: Throwable) {
                Log.e("AudioPlaybackManager", "Failed to play WAV file: ${t.message}", t)
                stop()
            }
        }
    }

    /**
     * 播放記憶體中的 ShortArray 音訊片段 (供即時監聽畫面最後 3 秒試聽)
     */
    fun playShorts(
        samples: ShortArray,
        startMs: Long = 0L,
        endMs: Long = (samples.size * 1000L / sampleRate)
    ) {
        stop()

        val totalDurationMs = samples.size * 1000L / sampleRate
        val safeStartMs = max(0L, startMs)
        val safeEndMs = min(totalDurationMs, max(safeStartMs + 100L, endMs))

        val startSample = (safeStartMs * sampleRate / 1000L).toInt().coerceIn(0, samples.size)
        val endSample = (safeEndMs * sampleRate / 1000L).toInt().coerceIn(startSample, samples.size)
        val playLength = endSample - startSample

        if (playLength <= 0) return

        val playSamples = ShortArray(playLength)
        System.arraycopy(samples, startSample, playSamples, 0, playLength)

        playJob = scope.launch(Dispatchers.IO) {
            try {
                val bufferSize = max(minBufferSize * 4, 8192)
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                val track = audioTrack ?: return@launch
                track.play()

                _playbackState.value = PlaybackState(
                    isPlaying = true,
                    currentPositionMs = safeStartMs,
                    totalDurationMs = totalDurationMs,
                    progress = if (totalDurationMs > 0) safeStartMs.toFloat() / totalDurationMs else 0f
                )

                // 啟動進度更新迴圈
                val progressJob = launch {
                    val startTime = System.currentTimeMillis()
                    val clipDurationMs = safeEndMs - safeStartMs
                    while (isActive && _playbackState.value.isPlaying) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val curMs = min(safeEndMs, safeStartMs + elapsed)
                        val prog = if (totalDurationMs > 0) curMs.toFloat() / totalDurationMs else 0f
                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = curMs,
                            progress = prog
                        )
                        delay(33)
                    }
                }

                // 以合理 chunk 分批寫入 PCM 數據，防 HAL 溢出
                var written = 0
                val chunkSize = 2048
                while (written < playLength && isActive) {
                    val count = min(chunkSize, playLength - written)
                    val res = track.write(playSamples, written, count)
                    if (res <= 0) break
                    written += res
                }

                val playDurationMs = playLength * 1000L / sampleRate
                delay(playDurationMs + 50)

                progressJob.cancel()
                track.stop()
                track.release()
            } catch (t: Throwable) {
                Log.e("AudioPlaybackManager", "Playback error: ${t.message}", t)
            } finally {
                audioTrack = null
                _playbackState.value = PlaybackState(
                    isPlaying = false,
                    currentPositionMs = 0L,
                    totalDurationMs = totalDurationMs,
                    progress = 0f
                )
            }
        }
    }

    /**
     * 跳轉至指定時間 (毫秒)
     */
    fun seekTo(positionMs: Long) {
        val mp = mediaPlayer
        if (mp != null) {
            val total = mp.duration.toLong()
            val target = positionMs.coerceIn(0L, total)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mp.seekTo(target, MediaPlayer.SEEK_CLOSEST)
            } else {
                mp.seekTo(target.toInt())
            }
            _playbackState.value = _playbackState.value.copy(
                currentPositionMs = target,
                progress = if (total > 0) target.toFloat() / total else 0f
            )
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null

        scope.launch(Dispatchers.Main) {
            try {
                mediaPlayer?.apply {
                    if (isPlaying) {
                        stop()
                    }
                    reset()
                    release()
                }
            } catch (_: Exception) {}
            mediaPlayer = null
        }

        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (_: Exception) {}
        audioTrack = null

        _playbackState.value = _playbackState.value.copy(isPlaying = false)
    }
}
