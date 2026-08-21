package com.example.aniframeapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.withTranslation
import kotlin.math.abs
import kotlin.math.ceil

class TimelineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply { isAntiAlias = true }
    private val markerPath = Path()

    var currentFrame: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var commaDivisor: Int = 1

    private var zoomScale: Float = 1.5f
    private val minZoom = 0.3f
    private val maxZoom = 6f
    private val baseZoom = 1.5f
    val maxSeconds = 120

    private var scrollOffsetPx = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var gestureDirection = 0
    private val dragSensitivity = 0.6f
    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    var onUserTouch: (() -> Unit)? = null

    private val recordedFrames = mutableListOf<Int>()
    private val maxRecordedFrames = 60

    init {
        isClickable = true
    }

    private fun secondWidth() = 120f * zoomScale
    private fun frameWidth() = secondWidth() / 24f
    private fun contentWidthPx() = secondWidth() * maxSeconds

    private fun edgePaddingPx() = width / 2f
    private fun strictMaxScroll() = (contentWidthPx() - width).coerceAtLeast(0f)
    private fun paddedMinScroll() = -edgePaddingPx()
    private fun paddedMaxScroll() = strictMaxScroll() + edgePaddingPx()

    private fun centerScrollFor(frame: Float): Float {
        return (frame * frameWidth() - width / 2f).coerceIn(paddedMinScroll(), paddedMaxScroll())
    }

    private fun ceilDiv(a: Int, b: Int): Int {
        val div = b.coerceAtLeast(1)
        return (a + div - 1) / div
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scrollOffsetPx = centerScrollFor(0f)
    }

    fun setZoomScale(newScale: Float) {
        val focusFrame = (scrollOffsetPx + width / 2f) / frameWidth()
        zoomScale = newScale.coerceIn(minZoom, maxZoom)
        scrollOffsetPx = centerScrollFor(focusFrame)
        invalidate()
    }

    fun jumpToFrame(frame: Int) {
        scrollOffsetPx = centerScrollFor(frame.toFloat())
        invalidate()
    }

    fun getRecordedFrames(): List<Int> = recordedFrames.toList()

    fun isRecordingBlocked(): Boolean {
        if (recordedFrames.size != 1) return false
        val maxRaw = maxSeconds * 24
        val lastComma = maxRaw / commaDivisor.coerceAtLeast(1)
        val markComma = ceilDiv(recordedFrames[0], commaDivisor)
        return markComma >= lastComma - 1
    }

    fun recordTiming(): Boolean {
        if (isRecordingBlocked()) return false
        val raw = (scrollOffsetPx + width / 2f) / frameWidth()
        val rawFrame = ceil(raw).toInt().coerceIn(0, maxSeconds * 24)
        val divisor = commaDivisor.coerceAtLeast(1)
        val steppedFrame = (ceil(rawFrame.toDouble() / divisor).toInt() * divisor)
            .coerceIn(0, maxSeconds * 24)
        if (recordedFrames.contains(steppedFrame)) {
            invalidate()
            return true
        }
        if (recordedFrames.size >= maxRecordedFrames) return false
        recordedFrames.add(steppedFrame)
        invalidate()
        return true
    }

    fun undoLastTiming() {
        if (recordedFrames.isNotEmpty()) {
            recordedFrames.removeAt(recordedFrames.size - 1)
            invalidate()
        }
    }

    fun clearAllTimings() {
        recordedFrames.clear()
        invalidate()
    }

    fun loadFrames(frames: List<Int>) {
        recordedFrames.clear()
        recordedFrames.addAll(frames.filter { it in 0..(maxSeconds * 24) }.take(maxRecordedFrames))
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                gestureDirection = 0
                onUserTouch?.invoke()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val totalDx = event.x - lastTouchX
                val totalDy = event.y - lastTouchY

                if (gestureDirection == 0) {
                    if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                        gestureDirection = if (abs(totalDx) > abs(totalDy)) 1 else 2
                        if (gestureDirection == 2) {
                            parent?.requestDisallowInterceptTouchEvent(false)
                            return false
                        }
                    }
                }

                if (gestureDirection == 2) {
                    return false
                }

                if (gestureDirection == 1) {
                    val divisor = commaDivisor.coerceAtLeast(1)
                    val zoomFactor = zoomScale / baseZoom
                    val dx = (event.x - lastTouchX) * dragSensitivity * divisor * zoomFactor
                    var newOffset = (scrollOffsetPx - dx).coerceIn(paddedMinScroll(), paddedMaxScroll())

                    if (divisor > 1) {
                        val fw = frameWidth()
                        val centerFrameRaw = (newOffset + width / 2f) / fw
                        val snapped = Math.round(centerFrameRaw / divisor) * divisor
                        newOffset = (snapped * fw - width / 2f).coerceIn(paddedMinScroll(), paddedMaxScroll())
                    }

                    scrollOffsetPx = newOffset
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (gestureDirection != 2) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val fw = frameWidth()
        val sw = secondWidth()

        canvas.withTranslation(-scrollOffsetPx, 0f) {
        val viewportLeft = scrollOffsetPx
        val viewportRight = scrollOffsetPx + width
        val startFrame = ((viewportLeft / fw).toInt() - 2).coerceAtLeast(0)
        val endFrame = ((viewportRight / fw).toInt() + 2).coerceAtMost(maxSeconds * 24)

        paint.color = 0xFF555555.toInt()
        paint.strokeWidth = 2f
        canvas.drawLine(viewportLeft, h / 2, viewportRight, h / 2, paint)

        for (frameIndex in startFrame..endFrame) {
            val x = frameIndex * fw
            when {
                frameIndex % 24 == 0 -> {
                    paint.color = 0xFFFFFFFF.toInt()
                    paint.strokeWidth = 4f
                    canvas.drawLine(x, h / 2 - 25f, x, h / 2 + 25f, paint)
                    paint.textSize = 24f
                    canvas.drawText("${frameIndex / 24}s", x - 15f, h / 2 - 35f, paint)
                }
                frameIndex % 12 == 0 -> {
                    paint.color = 0xFFFFFFFF.toInt()
                    paint.strokeWidth = 3f
                    canvas.drawLine(x, h / 2 - 18f, x, h / 2 + 18f, paint)
                }
                else -> {
                    paint.color = 0xFFAAAAAA.toInt()
                    paint.strokeWidth = 2f
                    canvas.drawLine(x, h / 2 - 12f, x, h / 2 + 12f, paint)
                }
            }
        }

        for (frameIndex in recordedFrames) {
            val x = frameIndex * fw
            if (x in (viewportLeft - fw)..(viewportRight + fw)) {
                paint.color = 0xFFFFFF00.toInt()
                paint.strokeWidth = 4f
                canvas.drawLine(x, h / 2 - 25f, x, h / 2 + 25f, paint)
                paint.textSize = 18f
                val shown = ceilDiv(frameIndex, commaDivisor)
                canvas.drawText("$shown", x - 10f, h / 2 + 45f, paint)
            }
        }

        val currentX = (currentFrame.toFloat() / 24f) * sw
        paint.color = 0xFFFF0000.toInt()
        paint.strokeWidth = 5f
        canvas.drawLine(currentX, 0f, currentX, h, paint)
        }

        val centerFrame = (scrollOffsetPx + width / 2f) / fw
        val cx = width / 2f

        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        markerPath.reset()
        markerPath.moveTo(cx - 12f, 0f)
        markerPath.lineTo(cx + 12f, 0f)
        markerPath.lineTo(cx, 18f)
        markerPath.close()
        canvas.drawPath(markerPath, paint)

        paint.color = 0xFFFF0000.toInt()
        paint.strokeWidth = 2f
        canvas.drawLine(cx, h / 2 - 45f, cx, h / 2 + 45f, paint)

        val shownFrame = centerFrame.toInt().coerceAtLeast(0)
        paint.textSize = 22f
        canvas.drawText("(${shownFrame}프레임)", cx + 16f, 20f, paint)
    }

    fun followPlayhead() {
        val currentX = (currentFrame.toFloat() / 24f) * secondWidth()
        scrollOffsetPx = (currentX - width / 2f).coerceIn(paddedMinScroll(), paddedMaxScroll())
        invalidate()
    }

    fun resetView() {
        scrollOffsetPx = centerScrollFor(0f)
        invalidate()
    }
}