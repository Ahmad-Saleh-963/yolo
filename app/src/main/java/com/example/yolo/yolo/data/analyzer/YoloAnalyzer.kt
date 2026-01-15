package com.example.yolo.yolo.data.analyzer

import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.yolo.yolo.data.detector.YoloDetector
import com.example.yolo.yolo.model.Detection
import com.example.yolo.yolo.util.toBitmap

class YoloAnalyzer(
    private val detector: YoloDetector,
    private val onResults: (List<Detection>, Size) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        val bitmap = image.toBitmap()
        val results = detector.detect(bitmap)

        onResults(results, Size(bitmap.width, bitmap.height))
        image.close()
    }
}
