package com.silic2.app.domain.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavFileWriter(
    private val outputFile: File,
    private val sampleRate: Int = 32000,
    private val channels: Int = 1,
    private val bitsPerSample: Int = 16
) {
    private var fos: FileOutputStream? = null
    private var totalAudioBytes: Long = 0

    init {
        outputFile.parentFile?.mkdirs()
        fos = FileOutputStream(outputFile)
        // 預先寫入 44 位元組空白標頭，結束時再回填
        val emptyHeader = ByteArray(44)
        fos?.write(emptyHeader)
    }

    @Synchronized
    fun writeSamples(samples: ShortArray, offset: Int = 0, length: Int = samples.size) {
        val stream = fos ?: return
        val byteBuffer = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in offset until offset + length) {
            byteBuffer.putShort(samples[i])
        }
        val bytes = byteBuffer.array()
        stream.write(bytes)
        totalAudioBytes += bytes.size
    }

    @Synchronized
    fun close() {
        fos?.flush()
        fos?.close()
        fos = null

        // 回填 44 位元組 RIFF/WAVE 標頭
        if (outputFile.exists()) {
            RandomAccessFile(outputFile, "rw").use { raf ->
                raf.seek(0)
                writeWavHeader(raf, totalAudioBytes, sampleRate, channels, bitsPerSample)
            }
        }
    }

    companion object {
        fun writeWavHeader(
            raf: RandomAccessFile,
            totalAudioLen: Long,
            sampleRate: Int,
            channels: Int,
            bitsPerSample: Int
        ) {
            val totalDataLen = totalAudioLen + 36
            val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
            val blockAlign = (channels * bitsPerSample / 8).toShort()

            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(totalDataLen.toInt())
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1.toShort()) // AudioFormat 1 = PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate.toInt())
            header.putShort(blockAlign)
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(totalAudioLen.toInt())

            raf.write(header.array())
        }

        fun createWavFromPcm(
            pcmFile: File,
            wavFile: File,
            sampleRate: Int = 32000,
            channels: Int = 1,
            bitsPerSample: Int = 16
        ) {
            wavFile.parentFile?.mkdirs()
            val pcmSize = pcmFile.length()
            RandomAccessFile(wavFile, "rw").use { raf ->
                writeWavHeader(raf, pcmSize, sampleRate, channels, bitsPerSample)
                pcmFile.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } > 0) {
                        raf.write(buffer, 0, read)
                    }
                }
            }
        }
        fun createWavFromShortArray(
            wavFile: File,
            samples: ShortArray,
            sampleRate: Int = 32000,
            channels: Int = 1,
            bitsPerSample: Int = 16
        ) {
            wavFile.parentFile?.mkdirs()
            val totalAudioLen = (samples.size * 2).toLong()
            RandomAccessFile(wavFile, "rw").use { raf ->
                writeWavHeader(raf, totalAudioLen, sampleRate, channels, bitsPerSample)
                val chunkSize = 4096
                val byteBuffer = ByteBuffer.allocate(chunkSize * 2).order(ByteOrder.LITTLE_ENDIAN)
                var idx = 0
                while (idx < samples.size) {
                    val count = Math.min(chunkSize, samples.size - idx)
                    byteBuffer.clear()
                    for (i in 0 until count) {
                        byteBuffer.putShort(samples[idx + i])
                    }
                    raf.write(byteBuffer.array(), 0, count * 2)
                    idx += count
                }
            }
        }
    }
}
