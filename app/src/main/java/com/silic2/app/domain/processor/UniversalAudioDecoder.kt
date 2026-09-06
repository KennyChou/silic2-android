package com.silic2.app.domain.processor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UniversalAudioDecoder(private val context: Context) {

    /**
     * 從 Uri 解碼任何音訊檔案 (WAV, MP3, M4A, AAC, FLAC, OGG 等)，並重採樣為 32000Hz 單聲道 16-bit PCM ShortArray
     */
    suspend fun decodeTo32kMonoPcm(
        uri: Uri,
        onProgress: ((Float) -> Unit)? = null
    ): ShortArray = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.tmp")
        val extractor = MediaExtractor()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("無法開啟音訊串流: $uri")

            extractor.setDataSource(tempFile.absolutePath)
        } catch (e: Exception) {
            extractor.release()
            tempFile.delete()
            throw IllegalArgumentException("無法讀取音訊來源: ${e.message}", e)
        }

        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = trackFormat
                break
            }
        }

        if (audioTrackIndex == -1 || format == null) {
            extractor.release()
            throw IllegalArgumentException("找不到有效的音訊軌道")
        }

        extractor.selectTrack(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmByteStream = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var isExtractorEOS = false
        var isDecoderEOS = false
        val timeoutUs = 10000L

        try {
            while (!isDecoderEOS) {
                if (!isExtractorEOS) {
                    val inIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inIndex >= 0) {
                        val inBuffer = codec.getInputBuffer(inIndex)
                        if (inBuffer != null) {
                            val sampleSize = extractor.readSampleData(inBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isExtractorEOS = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(inIndex, 0, sampleSize, presentationTimeUs, 0)
                                extractor.advance()
                                if (durationUs > 0 && onProgress != null) {
                                    val prog = (presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f) * 0.5f
                                    onProgress(prog)
                                }
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val chunk = ByteArray(bufferInfo.size)
                        outBuffer.get(chunk)
                        pcmByteStream.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isDecoderEOS = true
                    }
                }
            }
        } finally {
            try { codec.stop() } catch (e: Exception) {}
            try { codec.release() } catch (e: Exception) {}
            try { extractor.release() } catch (e: Exception) {}
            tempFile.delete()
        }

        val rawBytes = pcmByteStream.toByteArray()
        if (rawBytes.isEmpty()) {
            return@withContext ShortArray(0)
        }

        // 轉換 byte[] 為 short[] (16-bit signed PCM, Little Endian)
        val shortCount = rawBytes.size / 2
        val rawShorts = ShortArray(shortCount)
        ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(rawShorts)

        // 聲道轉換：若為多聲道，轉換為單聲道
        val monoShorts: ShortArray = if (channelCount > 1) {
            val frames = shortCount / channelCount
            val mono = ShortArray(frames)
            for (i in 0 until frames) {
                var sum = 0
                for (ch in 0 until channelCount) {
                    sum += rawShorts[i * channelCount + ch]
                }
                mono[i] = (sum / channelCount).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            mono
        } else {
            rawShorts
        }

        // 採樣率轉換：重採樣到 32,000Hz (模型標準)
        val targetSampleRate = 32000
        val finalShorts = if (sampleRate != targetSampleRate && monoShorts.isNotEmpty()) {
            resampleLinear(monoShorts, sampleRate, targetSampleRate)
        } else {
            monoShorts
        }

        onProgress?.invoke(1.0f)
        return@withContext finalShorts
    }

    /**
     * 線性插值高品質快速重採樣
     */
    private fun resampleLinear(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (input.isEmpty()) return ShortArray(0)
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outLength = (input.size / ratio).toInt()
        val output = ShortArray(outLength)

        for (i in 0 until outLength) {
            val origPos = i * ratio
            val index0 = origPos.toInt().coerceIn(0, input.size - 1)
            val index1 = (index0 + 1).coerceIn(0, input.size - 1)
            val frac = (origPos - index0).toFloat()
            val sample0 = input[index0]
            val sample1 = input[index1]
            val interp = sample0 + frac * (sample1 - sample0)
            output[i] = interp.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return output
    }
}
