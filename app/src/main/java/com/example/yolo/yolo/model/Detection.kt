package com.example.yolo.yolo.model

import android.graphics.RectF

data class Detection(
    val classId: Int,
    val label: String,
    val score: Float,
    val box: RectF
)
