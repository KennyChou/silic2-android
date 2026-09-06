package com.silic2.app.domain.inference

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.silic2.app.data.model.SilicDetection
import com.silic2.app.data.model.SoundClass
import com.silic2.app.data.repository.SoundClassRepository
import org.json.JSONArray
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class SilicDetector(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val soundClasses: Map<Int, SoundClass> = SoundClassRepository.getSoundClasses(context)

    // YOLO 模型 403 類別索引映射 (0..402 -> soundclass_id)
    private val classMap: List<Int> = loadModelClasses()

    val isModelLoaded: Boolean
        get() = interpreter != null

    init {
        loadModel()
    }

    private fun loadModelClasses(): List<Int> {
        return try {
            context.assets.open("model_classes.json").bufferedReader().use { reader ->
                val jsonArray = JSONArray(reader.readText())
                val list = mutableListOf<Int>()
                for (i in 0 until jsonArray.length()) {
                    list.add(jsonArray.getInt(i))
                }
                list
            }
        } catch (t: Throwable) {
            Log.e("SilicDetector", "Failed to load model_classes.json: ${t.message}", t)
            emptyList()
        }
    }

    private fun loadModel() {
        try {
            val modelBuffer = FileUtil.loadMappedFile(context, "best_float32.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.i("SilicDetector", "Model best_float32.tflite loaded successfully (4 threads CPU)!")
        } catch (t: Throwable) {
            Log.e("SilicDetector", "Cannot load best_float32.tflite: ${t.message}", t)
            interpreter = null
        }
    }

    private fun melToFreq(melNorm: Float, fMin: Double = 100.0, fMax: Double = 15000.0): Int {
        if (melNorm <= 0f) return fMin.toInt()
        val minMel = 1127.0 * ln(1.0 + fMin / 700.0)
        val maxMel = 1127.0 * ln(1.0 + fMax / 700.0)
        val mel = melNorm * (maxMel - minMel) + minMel
        val freq = 700.0 * (exp(mel / 1127.0) - 1.0)
        return freq.toInt().coerceIn(fMin.toInt(), fMax.toInt())
    }

    fun detect(
        bitmap: Bitmap,
        clipStartMs: Long,
        confThreshold: Float = 0.20f,
        targetClassIds: Set<Int>? = null
    ): List<SilicDetection> {
        val tflite = interpreter ?: return emptyList()

        try {
            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(480, 480, ResizeOp.ResizeMethod.BILINEAR))
                .build()

            var tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)
            tensorImage = imageProcessor.process(tensorImage)

            val inputBuffer = ByteBuffer.allocateDirect(1 * 480 * 480 * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }
            val pixels = tensorImage.tensorBuffer.floatArray
            for (p in pixels) {
                inputBuffer.putFloat(p / 255.0f)
            }
            inputBuffer.rewind()

            val outputTensor = tflite.getOutputTensor(0)
            val outputShape = outputTensor.shape() // [1, 300, 6]
            val clipLengthMs = 3000

            val results = mutableListOf<SilicDetection>()

            if (outputShape.size == 3 && outputShape[2] == 6) {
                val numBoxes = outputShape[1]
                val outputBuffer = Array(1) { Array(numBoxes) { FloatArray(6) } }
                tflite.run(inputBuffer, outputBuffer)

                val boxes = outputBuffer[0]
                for (i in 0 until numBoxes) {
                    val score = boxes[i][4]
                    if (score >= confThreshold) {
                        val rawClassIdx = boxes[i][5].toInt()
                        val soundclassId = if (rawClassIdx in classMap.indices) {
                            classMap[rawClassIdx]
                        } else {
                            rawClassIdx
                        }

                        if (targetClassIds == null || soundclassId in targetClassIds) {
                            val x1 = boxes[i][0] / 480.0f
                            val y1 = boxes[i][1] / 480.0f
                            val x2 = boxes[i][2] / 480.0f
                            val y2 = boxes[i][3] / 480.0f

                            val xMin = min(x1, x2).coerceIn(0f, 1f)
                            val xMax = max(x1, x2).coerceIn(0f, 1f)
                            val yMin = min(y1, y2).coerceIn(0f, 1f)
                            val yMax = max(y1, y2).coerceIn(0f, 1f)

                            val timeBegin = clipStartMs + (xMin * clipLengthMs).toLong()
                            val timeEnd = clipStartMs + (xMax * clipLengthMs).toLong()

                            // Mel 逆轉換換算真實頻率 (y=0 頂部為高頻，y=1 底部為低頻)
                            val freqLow = melToFreq(1f - yMax)
                            val freqHigh = melToFreq(1f - yMin)

                            val soundClass = soundClasses[soundclassId]
                            results.add(
                                SilicDetection(
                                    soundclassId = soundclassId,
                                    speciesName = soundClass?.speciesName ?: "物種 #$soundclassId",
                                    soundClass = soundClass?.soundClass ?: "聲音",
                                    scientificName = soundClass?.scientificName ?: "",
                                    confidence = score,
                                    timeBeginMs = timeBegin,
                                    timeEndMs = max(timeBegin + 50L, timeEnd),
                                    freqLowHz = min(freqLow, freqHigh),
                                    freqHighHz = max(freqLow, freqHigh)
                                )
                            )
                        }
                    }
                }
            }

            return results
        } catch (t: Throwable) {
            Log.e("SilicDetector", "Inference error: ${t.message}", t)
            return emptyList()
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
