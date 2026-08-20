package com.example.aniframeapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class TimelineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        isAntiAlias = true // 선과 글자를 부드럽게 그려주는 설정
    }

    var currentFrame: Int = 0
    var zoomScale: Float = 1.0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        val secondWidth = 120f * zoomScale
        val frameWidth = secondWidth / 24f

        paint.color = 0xFF555555.toInt()
        paint.strokeWidth = 2f
        canvas.drawLine(0f, height / 2, width, height / 2, paint)

        var x = 0f
        var frameIndex = 0
        while (x < width + secondWidth) {
            val isSecond = (frameIndex % 24 == 0)
            if (isSecond) {
                paint.color = 0xFFFFFFFF.toInt()
                paint.strokeWidth = 4f
                canvas.drawLine(x, height / 2 - 25f, x, height / 2 + 25f, paint)

                paint.textSize = 24f
                canvas.drawText("${frameIndex / 24}s", x - 15f, height / 2 - 35f, paint)
            } else {
                paint.color = 0xFFAAAAAA.toInt()
                paint.strokeWidth = 2f
                canvas.drawLine(x, height / 2 - 12f, x, height / 2 + 12f, paint)
            }
            x += frameWidth
            frameIndex++
        }

        val currentX = (currentFrame.toFloat() / 24f) * secondWidth
        paint.color = 0xFFFF0000.toInt()
        paint.strokeWidth = 5f
        canvas.drawLine(currentX, 0f, currentX, height, paint)
    }
}