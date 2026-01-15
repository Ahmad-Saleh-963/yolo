package com.example.yolo.yolo.data.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OnnxTensor
import com.example.yolo.yolo.model.Detection
import java.nio.FloatBuffer
import java.util.Collections

class YoloDetector(context: Context) {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val labels: List<String>

    // إعدادات النموذج (YOLOv8n افتراضياً 640x640)
    private val inputWidth = 640
    private val inputHeight = 640

    init {
        val modelBytes = context.assets.open("yolov8n.onnx").readBytes()
        session = env.createSession(modelBytes)
        labels = context.assets.open("labels.txt").bufferedReader().readLines()
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        // 1. Pre-processing: Resize and Normalize
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        val floatBuffer = allocateFloatBuffer(resizedBitmap)
        
        val inputName = session.inputNames.iterator().next()
        val shape = longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)

        // 2. Inference
        val results = session.run(Collections.singletonMap(inputName, inputTensor))
        val output = results[0].value as Array<Array<FloatArray>> // [1][84][8400]
        
        // 3. Post-processing (Parsing YOLOv8 output)
        return parseV8Output(output[0], bitmap.width.toFloat(), bitmap.height.toFloat())
    }

    private fun allocateFloatBuffer(bitmap: Bitmap): FloatBuffer {
        val buffer = FloatBuffer.allocate(1 * 3 * inputWidth * inputHeight)
        val pixels = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        // تحويل من ARGB إلى RGB Normalization (0-1)
        for (i in 0 until inputWidth * inputHeight) {
            val p = pixels[i]
            buffer.put(i, ((p shr 16 and 0xFF) / 255f)) // R
            buffer.put(i + inputWidth * inputHeight, ((p shr 8 and 0xFF) / 255f)) // G
            buffer.put(i + 2 * inputWidth * inputHeight, ((p and 0xFF) / 255f)) // B
        }
        return buffer
    }

    private fun parseV8Output(output: Array<FloatArray>, imgW: Float, imgH: Float): List<Detection> {
        val detections = mutableListOf<Detection>()
        val numElements = output[0].size // 8400
        val numChannels = output.size    // 84 (4 boxes + 80 classes)

        for (i in 0 until numElements) {
            var maxConf = -1f
            var classId = -1
            
            // البحث عن أعلى نتيجة ثقة بين الـ 80 فئة
            for (c in 4 until numChannels) {
                if (output[c][i] > maxConf) {
                    maxConf = output[c][i]
                    classId = c - 4
                }
            }

            if (maxConf > 0.5f) { // Threshold
                val cx = output[0][i] * (imgW / inputWidth)
                val cy = output[1][i] * (imgH / inputHeight)
                val w = output[2][i] * (imgW / inputWidth)
                val h = output[3][i] * (imgH / inputHeight)
                
                detections.add(Detection(
                    classId = classId,
                    label = labels.getOrElse(classId) { "Unknown" },
                    score = maxConf,
                    box = RectF(cx - w/2, cy - h/2, cx + w/2, cy + h/2)
                ))
            }
        }
        // تطبيق NMS لتقليل المربعات المتداخلة (مبسط)
        return detections.sortedByDescending { it.score }.take(5)
    }
}
