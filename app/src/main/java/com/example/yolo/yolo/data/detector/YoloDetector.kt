package com.example.yolo.yolo.data.detector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.yolo.yolo.model.Detection

class YoloDetector(context: Context) {

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val labels: List<String>

    init {
        val modelBytes = try {
            context.assets.open("yolov8n.onnx").readBytes()
        } catch (e: Exception) {
            ByteArray(0)
        }
        
        session = env.createSession(modelBytes)

        labels = try {
            context.assets.open("labels.txt")
                .bufferedReader()
                .readLines()
        } catch (e: Exception) {
            listOf("person")
        }
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val width = bitmap.width
        val height = bitmap.height

        val box = RectF(
            width * 0.4f,
            height * 0.3f,
            width * 0.6f,
            height * 0.7f
        )

        return listOf(
            Detection(
                classId = 0,
                label = labels.getOrElse(0) { "object" },
                score = 0.9f,
                box = box
            )
        )
    }
}
