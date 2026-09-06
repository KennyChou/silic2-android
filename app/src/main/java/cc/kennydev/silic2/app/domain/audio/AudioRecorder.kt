package cc.kennydev.silic2.app.domain.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class AudioRecorder(
    private val sampleRate: Int = 32000,
    private val clipLengthMs: Int = 3000,
    private val stepMs: Int = 1500,
    private val wavWriter: WavFileWriter? = null,
    private val onAmplitudeChanged: (Float) -> Unit,
    private val onAudioStream: ((ShortArray, Int) -> Unit)? = null,
    private val onAudioClipReady: (ShortArray, Long) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    val clipSamples = (sampleRate * clipLengthMs / 1000)      // 96,000 samples
    val stepSamples = (sampleRate * stepMs / 1000)            // 48,000 samples

    @Volatile
    var isRecording: Boolean = false
        private set

    var totalSamplesRecorded: Long = 0L
        private set

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRecording) return true

        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (minBufSize * 2).coerceAtLeast(sampleRate)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }

            audioRecord?.startRecording()
        } catch (e: Exception) {
            e.printStackTrace()
            audioRecord?.release()
            audioRecord = null
            return false
        }

        isRecording = true
        totalSamplesRecorded = 0L

        recordingJob = scope.launch {
            val fullBuffer = ShortArray(clipSamples)
            var currentBufferFill = 0
            val readChunk = ShortArray(1024)

            while (isActive && isRecording) {
                val read = audioRecord?.read(readChunk, 0, readChunk.size) ?: 0
                if (read > 0) {
                    totalSamplesRecorded += read

                    // 1. 同步寫入 WAV 檔案
                    wavWriter?.writeSamples(readChunk, 0, read)

                    // 2. 即時音訊串流回呼 (供滾動頻譜平滑流動)
                    onAudioStream?.invoke(readChunk, read)

                    // 3. 計算 RMS 振幅
                    var sumSquare = 0.0
                    for (i in 0 until read) {
                        val s = readChunk[i].toDouble() / 32768.0
                        sumSquare += s * s
                    }
                    val rms = sqrt(sumSquare / read).toFloat().coerceIn(0f, 1f)
                    onAmplitudeChanged(rms)

                    // 4. 滑動視窗 (供 3 秒 YOLO 推論)
                    val spaceLeft = clipSamples - currentBufferFill
                    val toCopy = minOf(read, spaceLeft)
                    System.arraycopy(readChunk, 0, fullBuffer, currentBufferFill, toCopy)
                    currentBufferFill += toCopy

                    if (currentBufferFill >= clipSamples) {
                        val timeOffsetMs = (totalSamplesRecorded - clipSamples) * 1000L / sampleRate
                        onAudioClipReady(fullBuffer.clone(), timeOffsetMs)

                        val remainingSamples = clipSamples - stepSamples
                        System.arraycopy(fullBuffer, stepSamples, fullBuffer, 0, remainingSamples)
                        currentBufferFill = remainingSamples

                        if (toCopy < read) {
                            val leftover = read - toCopy
                            val copyLeftover = minOf(leftover, clipSamples - currentBufferFill)
                            System.arraycopy(readChunk, toCopy, fullBuffer, currentBufferFill, copyLeftover)
                            currentBufferFill += copyLeftover
                        }
                    }
                }
            }
        }
        return true
    }

    fun stop() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null

        wavWriter?.close()
    }
}
