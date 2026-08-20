package com.example.aniframeapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private var isRunning = false
    private var startTime = 0L
    private var pauseOffset = 0L

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable

    private lateinit var timelineView: TimelineView
    private lateinit var seekBarZoom: SeekBar
    private lateinit var commaWheel: RecyclerView
    private lateinit var tvSelectedFrames: TextView

    private var selectedCommaDivisor = 1
    private var lastTotalFrames = 0
    private val itemHeightPx by lazy { 50 * resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvFrameResult = findViewById<TextView>(R.id.tvFrameResult)
        val tvTime = findViewById<TextView>(R.id.tvTime)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnPause = findViewById<Button>(R.id.btnPause)
        val btnReset = findViewById<Button>(R.id.btnReset)
        timelineView = findViewById(R.id.timelineView)
        seekBarZoom = findViewById(R.id.seekBarZoom)
        commaWheel = findViewById(R.id.commaWheel)
        tvSelectedFrames = findViewById(R.id.tvSelectedFrames)

        setupCommaWheel()

        timerRunnable = object : Runnable {
            override fun run() {
                val elapsedMillis = SystemClock.uptimeMillis() - startTime

                val seconds = (elapsedMillis / 1000).toInt()
                val minutes = seconds / 60
                val displaySec = seconds % 60
                val millis = ((elapsedMillis % 1000) / 10).toInt()

                tvTime.text = String.format("%02d:%02d.%02d", minutes, displaySec, millis)

                val totalSeconds = elapsedMillis / 1000.0
                val totalFrames = ((elapsedMillis * 24) / 1000).toInt()
                lastTotalFrames = totalFrames

                tvFrameResult.text = String.format("%.2f초", totalSeconds)
                updateSelectedFramesText()

                timelineView.currentFrame = totalFrames
                timelineView.invalidate()

                handler.postDelayed(this, 20)
            }
        }

        btnStart.setOnClickListener {
            if (!isRunning) {
                startTime = SystemClock.uptimeMillis() - pauseOffset
                handler.post(timerRunnable)
                isRunning = true
            }
        }

        btnPause.setOnClickListener {
            if (isRunning) {
                handler.removeCallbacks(timerRunnable)
                pauseOffset = SystemClock.uptimeMillis() - startTime
                isRunning = false
            }
        }

        btnReset.setOnClickListener {
            handler.removeCallbacks(timerRunnable)
            isRunning = false
            startTime = 0L
            pauseOffset = 0L
            lastTotalFrames = 0
            tvTime.text = "00:00.00"
            tvFrameResult.text = "0.00초"
            updateSelectedFramesText()
            timelineView.currentFrame = 0
            timelineView.invalidate()
        }

        seekBarZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = 0.5f + (progress.toFloat() / 100f) * 2.0f
                timelineView.zoomScale = scale
                timelineView.invalidate()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupCommaWheel() {
        val items = listOf("1콤마\n(24fps)", "2콤마\n(12fps)", "3콤마\n(8fps)")
        commaWheel.layoutManager = LinearLayoutManager(this)
        commaWheel.adapter = CommaWheelAdapter(items)

        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(commaWheel)

        commaWheel.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                applyWheelTransform(rv)
            }

            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val layoutManager = rv.layoutManager ?: return
                    val snapView = snapHelper.findSnapView(layoutManager) ?: return
                    val position = layoutManager.getPosition(snapView)
                    selectedCommaDivisor = position + 1
                    updateSelectedFramesText()
                }
            }
        })

        commaWheel.post { applyWheelTransform(commaWheel) }
    }

    private fun applyWheelTransform(rv: RecyclerView) {
        val centerY = rv.height / 2f
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            val childCenterY = (child.top + child.bottom) / 2f
            val offset = (childCenterY - centerY) / itemHeightPx
            val clamped = offset.coerceIn(-2f, 2f)

            child.rotationX = clamped * -35f
            child.alpha = (1f - abs(clamped) * 0.5f).coerceIn(0.25f, 1f)
            child.cameraDistance = 8000f * resources.displayMetrics.density
        }
    }

    private fun updateSelectedFramesText() {
        val label = when (selectedCommaDivisor) {
            1 -> "1콤마 (24fps)"
            2 -> "2콤마 (12fps)"
            else -> "3콤마 (8fps)"
        }
        tvSelectedFrames.text = "$label 총 ${lastTotalFrames / selectedCommaDivisor}프레임"
    }
}