package com.example.yolo.yolo.data.analyzer

import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.yolo.yolo.data.detector.YoloDetector
import com.example.yolo.yolo.model.Detection

class YoloAnalyzer(
    private val detector: YoloDetector,
    private val onResult: (Detection?, Size) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        val bitmap = image.toBitmap()
        val results = detector.detect(bitmap)

        onResult(results.firstOrNull(), Size(bitmap.width, bitmap.height))
        image.close()
    }
}
