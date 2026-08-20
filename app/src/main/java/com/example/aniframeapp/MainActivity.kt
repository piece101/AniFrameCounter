package com.example.aniframeapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var isRunning = false
    private var startTime = 0L
    private var pauseOffset = 0L

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable

    private lateinit var timelineView: TimelineView
    private lateinit var seekBarZoom: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvFrameResult = findViewById<TextView>(R.id.tvFrameResult)
        val tvTime = findViewById<TextView>(R.id.tvTime)
        val btnStartPause = findViewById<Button>(R.id.btnStartPause)
        val btnReset = findViewById<Button>(R.id.btnReset)
        timelineView = findViewById(R.id.timelineView)
        seekBarZoom = findViewById(R.id.seekBarZoom)

        timerRunnable = object : Runnable {
            override fun run() {
                val elapsedMillis = SystemClock.uptimeMillis() - startTime

                val seconds = (elapsedMillis / 1000).toInt()
                val minutes = seconds / 60
                val displaySec = seconds % 60
                val millis = ((elapsedMillis % 1000) / 10).toInt()

                tvTime.text = String.format("%02d:%02d.%02d", minutes, displaySec, millis)

                val totalFrames = ((elapsedMillis * 24) / 1000).toInt()
                val currentFrameInSec = totalFrames % 24

                tvFrameResult.text = "${displaySec}초 ${currentFrameInSec}프레임\n(총 ${totalFrames}프레임)"

                timelineView.currentFrame = totalFrames
                timelineView.invalidate()

                handler.postDelayed(this, 20)
            }
        }

        btnStartPause.setOnClickListener {
            if (isRunning) {
                handler.removeCallbacks(timerRunnable)
                pauseOffset = SystemClock.uptimeMillis() - startTime
                btnStartPause.text = "시작"
                isRunning = false
            } else {
                startTime = SystemClock.uptimeMillis() - pauseOffset
                handler.post(timerRunnable)
                btnStartPause.text = "정지"
                isRunning = true
            }
        }

        btnReset.setOnClickListener {
            handler.removeCallbacks(timerRunnable)
            isRunning = false
            startTime = 0L
            pauseOffset = 0L
            btnStartPause.text = "시작"
            tvTime.text = "00:00.00"
            tvFrameResult.text = "0초 00프레임\n(총 0프레임)"
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
}