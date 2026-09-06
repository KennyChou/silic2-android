package com.silic2.app.domain.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
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
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
     * 播放記憶體中的 ShortArray 音訊片段，可選指定 [startMs] 與 [endMs]
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
                    .setBufferSizeInBytes(max(minBufferSize, playLength * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                val track = audioTrack ?: return@launch
                track.play()

                val clipDurationMs = safeEndMs - safeStartMs
                _playbackState.value = PlaybackState(
                    isPlaying = true,
                    currentPositionMs = safeStartMs,
                    totalDurationMs = totalDurationMs,
                    progress = if (totalDurationMs > 0) safeStartMs.toFloat() / totalDurationMs else 0f
                )

                // 啟動進度更新迴圈
                val progressJob = launch {
                    val startTime = System.currentTimeMillis()
                    while (isActive && _playbackState.value.isPlaying) {
                        val elapsed = System.currentTimeMillis() - startTime
                        val curMs = min(safeEndMs, safeStartMs + elapsed)
                        val prog = if (totalDurationMs > 0) curMs.toFloat() / totalDurationMs else 0f
                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = curMs,
                            progress = prog
                        )
                        delay(30)
                    }
                }

                // 寫入 PCM 數據
                track.write(playSamples, 0, playLength)

                // 等待播放完畢
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
     * 播放 WAV 檔案，可選指定 [startMs] 與 [endMs]
     */
    fun playWavFile(file: File, startMs: Long = 0L, endMs: Long? = null) {
        if (!file.exists()) return
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = file.readBytes()
                if (bytes.size <= 44) return@launch
                // 跳過 44 位元組標頭
                val pcmBytes = bytes.copyOfRange(44, bytes.size)
                val shorts = ShortArray(pcmBytes.size / 2)
                ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                val totalMs = shorts.size * 1000L / sampleRate
                playShorts(shorts, startMs, endMs ?: totalMs)
            } catch (t: Throwable) {
                Log.e("AudioPlaybackManager", "Failed to read WAV file: ${t.message}", t)
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
                release()
            }
        } catch (_: Exception) {}
        audioTrack = null
        _playbackState.value = PlaybackState(isPlaying = false, progress = 0f)
    }
}
