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
    private lateinit var tv1Comma: TextView
    private lateinit var tv2Comma: TextView
    private lateinit var tv3Comma: TextView

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
        tv1Comma = findViewById(R.id.tv1Comma)
        tv2Comma = findViewById(R.id.tv2Comma)
        tv3Comma = findViewById(R.id.tv3Comma)

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

                tvFrameResult.text = String.format("%.2f초 (총 %d프레임)", totalSeconds, totalFrames)

                tv1Comma.text = "1콤마 (24fps): ${totalFrames} 프레임"
                tv2Comma.text = "2콤마 (12fps): ${totalFrames / 2} 프레임"
                tv3Comma.text = "3콤마 (8fps): ${totalFrames / 3} 프레임"

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
            tvTime.text = "00:00.00"
            tvFrameResult.text = "0.00초 (총 0프레임)"
            tv1Comma.text = "1콤마 (24fps): 0 프레임"
            tv2Comma.text = "2콤마 (12fps): 0 프레임"
            tv3Comma.text = "3콤마 (8fps): 0 프레임"
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