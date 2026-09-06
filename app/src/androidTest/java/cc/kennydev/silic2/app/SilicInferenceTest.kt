package cc.kennydev.silic2.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.kennydev.silic2.app.data.model.AnimalCategory
import cc.kennydev.silic2.app.data.repository.SoundClassRepository
import cc.kennydev.silic2.app.domain.audio.WavFileWriter
import cc.kennydev.silic2.app.domain.inference.SilicDetector
import cc.kennydev.silic2.app.domain.processor.MelSpectrogramConverter
import cc.kennydev.silic2.app.domain.processor.RainbowRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class SilicInferenceTest {

    @Test
    fun testWavFileWriter() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val testWav = File(appContext.cacheDir, "test_output.wav")
        if (testWav.exists()) testWav.delete()

        val writer = WavFileWriter(testWav, sampleRate = 32000, channels = 1, bitsPerSample = 16)
        val dummyPcm = ShortArray(32000) { (it % 1000).toShort() } // 1 秒音訊
        writer.writeSamples(dummyPcm)
        writer.close()

        assertTrue("WAV file should exist", testWav.exists())
        assertEquals("WAV file size should be 44 header + 64000 PCM bytes", 44 + 64000L, testWav.length())

        RandomAccessFile(testWav, "r").use { raf ->
            val riff = ByteArray(4)
            raf.read(riff)
            assertEquals("RIFF", String(riff))

            raf.seek(8)
            val wave = ByteArray(4)
            raf.read(wave)
            assertEquals("WAVE", String(wave))

            raf.seek(12)
            val fmt = ByteArray(4)
            raf.read(fmt)
            assertEquals("fmt ", String(fmt))
        }
    }

    @Test
    fun testCategoryClassification() {
        assertEquals(AnimalCategory.BIRD, AnimalCategory.fromSpecies("鵂鶹"))
        assertEquals(AnimalCategory.BIRD, AnimalCategory.fromSpecies("黃嘴角鴞"))
        assertEquals(AnimalCategory.FROG, AnimalCategory.fromSpecies("梭德氏赤蛙"))
        assertEquals(AnimalCategory.MAMMAL, AnimalCategory.fromSpecies("山羌"))
        assertEquals(AnimalCategory.OTHER, AnimalCategory.fromSpecies("疣尾蝎虎"))

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val allList = SoundClassRepository.getAllList(appContext)
        val birds = allList.count { it.category == AnimalCategory.BIRD }
        val frogs = allList.count { it.category == AnimalCategory.FROG }
        val mammals = allList.count { it.category == AnimalCategory.MAMMAL }

        assertTrue("Birds should be majority (~339)", birds > 300)
        assertTrue("Frogs should be ~40", frogs == 40)
        assertTrue("Mammals should be ~22", mammals == 22)
    }

    @Test
    fun testRealDeviceInferenceOnSampleOwl() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val detector = SilicDetector(appContext)
        assertTrue("Model should be loaded successfully", detector.isModelLoaded)

        val melConverter = MelSpectrogramConverter()
        val rainbowRenderer = RainbowRenderer()

        val bytes = appContext.assets.open("sample_owl.pcm").use { it.readBytes() }
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

        val melSpec = melConverter.computeMelSpectrogram(shorts)
        val bitmap = rainbowRenderer.renderToBitmap(melSpec)

        val results = detector.detect(
            bitmap = bitmap,
            clipStartMs = 0L,
            confThreshold = 0.15f
        )

        assertTrue("Should detect at least 1 bird sound", results.isNotEmpty())
        val hasOwl = results.any { it.speciesName.contains("鴞") || it.speciesName.contains("鵂鶹") }
        assertTrue("Should detect either Collared Owlet or Mountain Scops Owl", hasOwl)

        detector.close()
    }
}
