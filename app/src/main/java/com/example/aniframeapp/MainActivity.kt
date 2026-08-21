package com.example.aniframeapp

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private var isRunning = false
    private var startTime = 0L
    private var pauseOffset = 0L

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable

    private lateinit var timelineView: TimelineView
    private lateinit var commaWheel: RecyclerView
    private lateinit var tvSelectedFrames: TextView
    private lateinit var tvCommaLockMessage: TextView
    private lateinit var seekBarZoom: SeekBar
    private lateinit var btnPlayPause: Button
    private lateinit var btnRecordTiming: Button
    private lateinit var btnClearTiming: Button
    private lateinit var btnSaveTiming: Button
    private lateinit var topSlot: RecyclerView
    private lateinit var bottomSlot: RecyclerView
    private lateinit var tvFramesNeeded: TextView
    private lateinit var topSnapHelper: LinearSnapHelper
    private lateinit var bottomSnapHelper: LinearSnapHelper

    private lateinit var slideDrawer: View
    private lateinit var menuToolbar: View
    private lateinit var drawerToggleIcon: View
    private lateinit var drawerScrim: View
    private lateinit var drawerBackButton: View
    private lateinit var drawerMenuList: View
    private lateinit var savedTimingsContainer: View
    private lateinit var savedTimingsList: RecyclerView
    private lateinit var scrollThumb: View
    private lateinit var tvEmptyState: TextView
    private lateinit var itemSavedTimings: TextView
    private lateinit var itemImportTiming: TextView
    private lateinit var drawerHeader: View
    private lateinit var selectionToolbar: View
    private lateinit var selectionBackButton: View
    private lateinit var cbSelectAll: CheckBox
    private lateinit var btnExportSelected: View

    private var selectedCommaDivisor = 1
    private var lastTotalFrames = 0
    private var hasMovedFromReset = false
    private val lockMessageHandler = Handler(Looper.getMainLooper())

    private val maxMillis = 120_000L

    private var sortedMarks: List<Int> = emptyList()
    private var topOptions: List<Int> = listOf(0)
    private var bottomOptions: List<Int> = emptyList()
    private var topSelectedIndex = 0
    private var bottomSelectedIndex = 0
    private var topSlotLocked = false
    private var bottomSlotLocked = false

    private var savedFiles: List<File> = emptyList()
    private var savedTimingAdapter: SavedTimingAdapter? = null
    private var backPressedTime = 0L

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importTimingFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        window.statusBarColor = 0xFF1E1E1E.toInt()

        requestStoragePermissionOnFirstLaunch()

        val tvTime = findViewById<TextView>(R.id.tvTime)
        val btnReset = findViewById<Button>(R.id.btnReset)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        timelineView = findViewById(R.id.timelineView)
        commaWheel = findViewById(R.id.commaWheel)
        tvSelectedFrames = findViewById(R.id.tvSelectedFrames)
        tvCommaLockMessage = findViewById(R.id.tvCommaLockMessage)
        seekBarZoom = findViewById(R.id.seekBarZoom)
        btnRecordTiming = findViewById(R.id.btnRecordTiming)
        btnClearTiming = findViewById(R.id.btnClearTiming)
        btnSaveTiming = findViewById(R.id.btnSaveTiming)
        topSlot = findViewById(R.id.topSlot)
        bottomSlot = findViewById(R.id.bottomSlot)
        tvFramesNeeded = findViewById(R.id.tvFramesNeeded)

        slideDrawer = findViewById(R.id.slideDrawer)
        menuToolbar = findViewById(R.id.menuToolbar)
        drawerToggleIcon = findViewById(R.id.drawerToggleIcon)
        drawerScrim = findViewById(R.id.drawerScrim)
        drawerBackButton = findViewById(R.id.drawerBackButton)
        drawerMenuList = findViewById(R.id.drawerMenuList)
        savedTimingsContainer = findViewById(R.id.savedTimingsContainer)
        savedTimingsList = findViewById(R.id.savedTimingsList)
        scrollThumb = findViewById(R.id.scrollThumb)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        itemSavedTimings = findViewById(R.id.itemSavedTimings)
        itemSavedTimings.setOnClickListener { showSavedTimingsList() }
        itemImportTiming = findViewById(R.id.itemImportTiming)
        itemImportTiming.setOnClickListener {
            importLauncher.launch(arrayOf("text/plain"))
        }
        drawerHeader = findViewById(R.id.drawerHeader)
        selectionToolbar = findViewById(R.id.selectionToolbar)
        selectionBackButton = findViewById(R.id.selectionBackButton)
        cbSelectAll = findViewById(R.id.cbSelectAll)
        btnExportSelected = findViewById(R.id.btnExportSelected)
        selectionBackButton.setOnClickListener {
            savedTimingAdapter?.deselectAll()
        }
        btnExportSelected.setOnClickListener {
            val selected = savedTimingAdapter?.getSelectedPositions() ?: emptySet()
            exportSelectedFiles(selected)
        }
        drawerBackButton.setOnClickListener {
            if (savedTimingsContainer.isVisible) {
                savedTimingsContainer.isVisible = false
                drawerMenuList.isVisible = true
                savedTimingAdapter?.deselectAll()
            } else {
                closeDrawer()
            }
        }

        setupCommaWheel()
        setupTimingSlots()
        setupDrawer()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerScrim.isVisible) {
                    closeDrawer()
                    return
                }
                val now = System.currentTimeMillis()
                if (now - backPressedTime < 2000) {
                    finish()
                } else {
                    backPressedTime = now
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.toast_press_back_again),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        timelineView.onUserTouch = {
            hasMovedFromReset = true
            if (isRunning) {
                handler.removeCallbacks(timerRunnable)
                pauseOffset = SystemClock.uptimeMillis() - startTime
                isRunning = false
                btnPlayPause.text = getString(R.string.btn_play)
            }
        }

        timerRunnable = object : Runnable {
            override fun run() {
                var elapsedMillis = SystemClock.uptimeMillis() - startTime
                val reachedLimit = elapsedMillis >= maxMillis
                if (reachedLimit) elapsedMillis = maxMillis

                val seconds = (elapsedMillis / 1000).toInt()
                val minutes = seconds / 60
                val displaySec = seconds % 60
                val millis = ((elapsedMillis % 1000) / 10).toInt()

                tvTime.text = String.format(Locale.getDefault(), "%02d:%02d.%02ds", minutes, displaySec, millis)

                val totalFrames = ((elapsedMillis * 24) / 1000).toInt()
                lastTotalFrames = totalFrames

                updateSelectedFramesText()

                val steppedFrame = (totalFrames / selectedCommaDivisor) * selectedCommaDivisor
                timelineView.currentFrame = steppedFrame
                timelineView.followPlayhead()

                if (reachedLimit) {
                    isRunning = false
                    btnPlayPause.text = getString(R.string.btn_play)
                } else {
                    handler.postDelayed(this, 20)
                }
            }
        }

        btnPlayPause.setOnClickListener {
            if (isRunning) {
                handler.removeCallbacks(timerRunnable)
                pauseOffset = SystemClock.uptimeMillis() - startTime
                isRunning = false
                btnPlayPause.text = getString(R.string.btn_play)
            } else {
                hasMovedFromReset = true
                startTime = SystemClock.uptimeMillis() - pauseOffset
                handler.post(timerRunnable)
                isRunning = true
                btnPlayPause.text = getString(R.string.btn_pause)
            }
        }

        btnReset.setOnClickListener {
            handler.removeCallbacks(timerRunnable)
            isRunning = false
            startTime = 0L
            pauseOffset = 0L
            lastTotalFrames = 0
            hasMovedFromReset = false
            tvTime.text = getString(R.string.time_placeholder)
            updateSelectedFramesText()
            timelineView.currentFrame = 0
            timelineView.resetView()
            btnPlayPause.text = getString(R.string.btn_play)
            timelineView.clearAllTimings()
            rebuildTimingSlots()
        }

        seekBarZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = 0.3f + (progress.toFloat() / 100f) * 5.7f
                timelineView.setZoomScale(scale)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnRecordTiming.setOnClickListener {
            timelineView.recordTiming()
            rebuildTimingSlots()
        }

        btnClearTiming.setOnClickListener {
            timelineView.undoLastTiming()
            rebuildTimingSlots()
        }

        btnSaveTiming.setOnClickListener {
            showSaveTimingDialog()
        }

        rebuildTimingSlots()
    }

    private fun setupCommaWheel() {
        val items = listOf(
            getString(R.string.comma_1x),
            getString(R.string.comma_2x),
            getString(R.string.comma_3x)
        )
        commaWheel.layoutManager = LinearLayoutManager(this)
        commaWheel.adapter = CommaWheelAdapter(items, R.layout.item_comma_picker_large)

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
                    timelineView.commaDivisor = selectedCommaDivisor
                    updateSelectedFramesText()
                    rebuildTimingSlots()
                }
            }
        })

        commaWheel.post { applyWheelTransform(commaWheel) }

        commaWheel.setOnTouchListener { _, _ ->
            val locked = isRunning || hasMovedFromReset
            if (locked) {
                showCommaLockMessage()
            }
            locked
        }
    }

    private fun showCommaLockMessage() {
        lockMessageHandler.removeCallbacksAndMessages(null)
        tvCommaLockMessage.isVisible = true
        lockMessageHandler.postDelayed({
            tvCommaLockMessage.isVisible = false
        }, 700)
    }

    private fun setupTimingSlots() {
        topSlot.layoutManager = LinearLayoutManager(this)
        topSnapHelper = LinearSnapHelper()
        topSnapHelper.attachToRecyclerView(topSlot)
        topSlot.isClickable = true
        topSlot.setOnTouchListener { _, _ -> topSlotLocked }
        topSlot.setOnClickListener {
            timelineView.jumpToFrame(topOptions.getOrElse(topSelectedIndex) { 0 })
        }
        topSlot.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                applyWheelTransform(rv)
            }
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                bottomSlotLocked = newState != RecyclerView.SCROLL_STATE_IDLE
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lm = rv.layoutManager ?: return
                    val snapView = topSnapHelper.findSnapView(lm) ?: return
                    val position = lm.getPosition(snapView)
                    topSelectedIndex = position
                    timelineView.jumpToFrame(topOptions.getOrElse(position) { 0 })
                    rebuildBottomSlot()
                    updateFramesNeededText()
                }
            }
        })

        bottomSlot.layoutManager = LinearLayoutManager(this)
        bottomSnapHelper = LinearSnapHelper()
        bottomSnapHelper.attachToRecyclerView(bottomSlot)
        bottomSlot.isClickable = true
        bottomSlot.setOnTouchListener { _, _ -> bottomSlotLocked }
        bottomSlot.setOnClickListener {
            timelineView.jumpToFrame(bottomOptions.getOrElse(bottomSelectedIndex) { 0 })
        }
        bottomSlot.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                applyWheelTransform(rv)
            }
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                topSlotLocked = newState != RecyclerView.SCROLL_STATE_IDLE
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val lm = rv.layoutManager ?: return
                    val snapView = bottomSnapHelper.findSnapView(lm) ?: return
                    val position = lm.getPosition(snapView)
                    bottomSelectedIndex = position
                    timelineView.jumpToFrame(bottomOptions.getOrElse(position) { 0 })
                    updateFramesNeededText()
                }
            }
        })
    }

    private fun applyWheelTransform(rv: RecyclerView) {
        val centerY = rv.height / 2f
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i)
            val childCenterY = (child.top + child.bottom) / 2f
            val childHeight = child.height.toFloat().coerceAtLeast(1f)
            val offset = (childCenterY - centerY) / childHeight
            val clamped = offset.coerceIn(-2f, 2f)

            child.rotationX = clamped * -35f
            child.alpha = (1f - abs(clamped) * 0.5f).coerceIn(0.25f, 1f)
            child.cameraDistance = 8000f * resources.displayMetrics.density
        }
    }

    private fun updateSelectedFramesText() {
        val label = when (selectedCommaDivisor) {
            1 -> getString(R.string.comma_1x)
            2 -> getString(R.string.comma_2x)
            else -> getString(R.string.comma_3x)
        }
        tvSelectedFrames.text = getString(
            R.string.drawing_no_format,
            label,
            lastTotalFrames / selectedCommaDivisor
        )
    }

    private fun ceilDivLocal(a: Int, b: Int): Int {
        val div = b.coerceAtLeast(1)
        return (a + div - 1) / div
    }

    private fun topLabel(value: Int, marks: List<Int>): String {
        return if (value == 0) {
            if (marks.size == 1) {
                val commaVal = ceilDivLocal(marks[0], selectedCommaDivisor)
                if (commaVal <= 1) getString(R.string.record_label_zero) else getString(R.string.record_label_from_zero)
            } else {
                getString(R.string.record_label_from_zero)
            }
        } else {
            val idx = marks.indexOf(value)
            val commaVal = ceilDivLocal(value, selectedCommaDivisor)
            getString(R.string.record_label_format, idx + 1, commaVal)
        }
    }

    private fun bottomLabel(value: Int): String {
        val idx = sortedMarks.indexOf(value)
        val commaVal = ceilDivLocal(value, selectedCommaDivisor)
        return getString(R.string.record_label_format, idx + 1, commaVal)
    }

    private fun rebuildTimingSlots() {
        sortedMarks = timelineView.getRecordedFrames().sorted()
        topOptions = listOf(0) + sortedMarks.dropLast(1)
        topSelectedIndex = 0

        val topLabels = topOptions.map { topLabel(it, sortedMarks) }
        topSlot.adapter = CommaWheelAdapter(topLabels, R.layout.item_comma_picker_medium)
        topSlot.scrollToPosition(0)
        topSlot.post { applyWheelTransform(topSlot) }

        rebuildBottomSlot()
        updateRecordButtonState()
    }

    private fun rebuildBottomSlot() {
        val topValue = topOptions.getOrElse(topSelectedIndex) { 0 }
        bottomOptions = sortedMarks.filter { it > topValue }
        bottomSelectedIndex = 0

        if (bottomOptions.isEmpty()) {
            bottomSlot.isVisible = false
            updateFramesNeededText()
            return
        }
        bottomSlot.isVisible = true

        val bottomLabels = bottomOptions.map { bottomLabel(it) }
        bottomSlot.adapter = CommaWheelAdapter(bottomLabels, R.layout.item_comma_picker_medium)
        bottomSlot.scrollToPosition(0)
        bottomSlot.post { applyWheelTransform(bottomSlot) }
        updateFramesNeededText()
    }

    private fun updateFramesNeededText() {
        val topValue = topOptions.getOrElse(topSelectedIndex) { 0 }
        val bottomValue = bottomOptions.getOrElse(bottomSelectedIndex) { -1 }
        if (bottomValue < 0) {
            tvFramesNeeded.text = getString(R.string.frames_needed_placeholder)
            return
        }
        val divisor = selectedCommaDivisor.coerceAtLeast(1)
        val needed = (bottomValue - topValue) / divisor
        tvFramesNeeded.text = getString(R.string.frames_needed_format, needed)
    }

    private fun updateRecordButtonState() {
        val blocked = timelineView.isRecordingBlocked()
        btnRecordTiming.isEnabled = !blocked
        btnRecordTiming.backgroundTintList =
            ColorStateList.valueOf(if (blocked) 0xFF888800.toInt() else 0xFFFFFF00.toInt())
    }

    private fun showSaveTimingDialog() {
        val defaultName = getString(R.string.dialog_save_default_name)
        val editText = EditText(this)
        editText.setText(defaultName)
        editText.setTextColor(0xFFFFFFFF.toInt())
        editText.setHintTextColor(0xFFAAAAAA.toInt())
        editText.setSelection(0, editText.text.length)

        val titleView = TextView(this).apply {
            text = getString(R.string.dialog_save_title)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setPadding(48, 40, 48, 16)
        }

        AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setCustomTitle(titleView)
            .setView(editText)
            .setPositiveButton(R.string.dialog_save_confirm) { _, _ ->
                val fileName = editText.text.toString().ifBlank { defaultName }
                saveTimingToFile(fileName)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun saveTimingToFile(fileName: String) {
        val sorted = timelineView.getRecordedFrames().sorted()
        val sb = StringBuilder()
        sb.append("ANIFRAME_TIMING_V1\n")
        sb.append("comma=$selectedCommaDivisor\n")
        sorted.forEach { frame ->
            sb.append("$frame\n")
        }
        val actualFileName = "${fileName}__c${selectedCommaDivisor}"
        try {
            openFileOutput("$actualFileName.txt", MODE_PRIVATE).use { fos ->
                OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                    writer.write(sb.toString())
                }
            }
            Toast.makeText(this, getString(R.string.toast_saved, fileName), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_save_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDrawer() {
        slideDrawer.post {
            val screenHeight = resources.displayMetrics.heightPixels
            val lp = slideDrawer.layoutParams
            lp.height = screenHeight / 4
            slideDrawer.layoutParams = lp
            slideDrawer.translationY = 0f
            slideDrawer.translationX = resources.displayMetrics.widthPixels.toFloat()
        }

        menuToolbar.setOnClickListener { openDrawer() }
        drawerToggleIcon.setOnClickListener { closeDrawer() }
        drawerScrim.setOnClickListener { closeDrawer() }

        var dragStartX = 0f
        slideDrawer.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = (event.rawX - dragStartX).coerceAtLeast(0f)
                    v.translationX = delta
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (v.translationX > v.width * 0.3f) {
                        closeDrawer()
                    } else {
                        openDrawer()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun openDrawer() {
        slideDrawer.translationY = 0f
        slideDrawer.animate().translationX(0f).translationY(0f).setDuration(200).start()
        drawerScrim.isVisible = true
        drawerScrim.alpha = 0f
        drawerScrim.animate().alpha(1f).setDuration(200).start()
        drawerBackButton.isVisible = true
    }

    private fun closeDrawer() {
        val width = if (slideDrawer.width > 0) slideDrawer.width else resources.displayMetrics.widthPixels
        slideDrawer.translationY = 0f
        slideDrawer.animate().translationX(width.toFloat()).translationY(0f).setDuration(200).start()
        drawerScrim.animate().alpha(0f).setDuration(200).withEndAction {
            drawerScrim.isVisible = false
        }.start()
        savedTimingsContainer.isVisible = false
        drawerMenuList.isVisible = true
        drawerBackButton.isVisible = false
        selectionToolbar.isVisible = false
        drawerHeader.isVisible = true
    }

    private fun showSavedTimingsList() {
        savedFiles = filesDir.listFiles { f -> f.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

        val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일 HH시mm분", Locale.getDefault())
        val commaSuffixRegex = Regex("__c(\\d+)$")
        val items = savedFiles.map { file ->
            val nameWithoutExt = file.name.removeSuffix(".txt")
            val match = commaSuffixRegex.find(nameWithoutExt)
            val commaUsed = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val title = if (match != null) nameWithoutExt.removeSuffix(match.value) else nameWithoutExt
            val dateStr = dateFormat.format(file.lastModified())
            title to getString(R.string.saved_entry_format, commaUsed, dateStr)
        }

        drawerMenuList.isVisible = false
        savedTimingsContainer.isVisible = true
        drawerBackButton.isVisible = true

        if (items.isEmpty()) {
            savedTimingsList.isVisible = false
            scrollThumb.isVisible = false
            tvEmptyState.isVisible = true
        } else {
            tvEmptyState.isVisible = false
            savedTimingsList.isVisible = true
            savedTimingsList.layoutManager = LinearLayoutManager(this)
            savedTimingAdapter = SavedTimingAdapter(
                items,
                onDelete = { position -> confirmDeleteTiming(position) },
                onSelectionChanged = { selected -> updateSelectionToolbar(selected) }
            )
            savedTimingsList.adapter = savedTimingAdapter
            setupFastScroll(savedTimingsList, scrollThumb)
        }
        updateSelectionToolbar(emptySet())
    }

    private fun updateSelectionToolbar(selected: Set<Int>) {
        if (selected.isEmpty()) {
            selectionToolbar.isVisible = false
            drawerHeader.isVisible = true
        } else {
            selectionToolbar.isVisible = true
            drawerHeader.isVisible = false
        }
        val itemCount = savedTimingAdapter?.itemCount ?: 0
        cbSelectAll.setOnCheckedChangeListener(null)
        cbSelectAll.isChecked = itemCount > 0 && selected.size == itemCount
        cbSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) savedTimingAdapter?.selectAll() else savedTimingAdapter?.deselectAll()
        }
    }

    private fun exportSelectedFiles(selectedPositions: Set<Int>) {
        val filesToExport = selectedPositions.mapNotNull { savedFiles.getOrNull(it) }
        if (filesToExport.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_select_export), Toast.LENGTH_SHORT).show()
            return
        }
        val uris = ArrayList<Uri>()
        for (file in filesToExport) {
            uris.add(FileProvider.getUriForFile(this, "$packageName.fileprovider", file))
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.export_chooser_title)))
    }

    private fun requestStoragePermissionOnFirstLaunch() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("asked_storage_permission", false)) return
        prefs.edit { putBoolean("asked_storage_permission", true) }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    private fun importTimingFromUri(uri: Uri) {
        try {
            val lines = contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.readLines()

            if (lines == null || lines.isEmpty() || lines[0] != "ANIFRAME_TIMING_V1") {
                Toast.makeText(this, getString(R.string.toast_unsupported_format), Toast.LENGTH_SHORT).show()
                return
            }

            var comma = 1
            val frames = mutableListOf<Int>()
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line.startsWith("comma=")) {
                    comma = line.removePrefix("comma=").toIntOrNull() ?: 1
                } else {
                    line.toIntOrNull()?.let { frames.add(it) }
                }
            }

            selectedCommaDivisor = comma.coerceIn(1, 3)
            timelineView.commaDivisor = selectedCommaDivisor
            timelineView.loadFrames(frames)
            hasMovedFromReset = true
            updateSelectedFramesText()
            rebuildTimingSlots()

            commaWheel.scrollToPosition(selectedCommaDivisor - 1)
            commaWheel.post { applyWheelTransform(commaWheel) }

            closeDrawer()
            Toast.makeText(this, getString(R.string.toast_import_complete), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_import_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteTiming(position: Int) {
        val file = savedFiles.getOrNull(position) ?: return
        val nameWithoutExt = file.name.removeSuffix(".txt")
        val match = Regex("__c(\\d+)$").find(nameWithoutExt)
        val title = if (match != null) nameWithoutExt.removeSuffix(match.value) else nameWithoutExt
        savedTimingAdapter?.setPendingDelete(position)

        val titleView = TextView(this).apply {
            text = getString(R.string.dialog_delete_title)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setPadding(48, 40, 48, 16)
        }

        val dialog = AlertDialog.Builder(this, R.style.DarkDialogTheme)
            .setCustomTitle(titleView)
            .setMessage(getString(R.string.dialog_delete_message, title))
            .setPositiveButton(R.string.dialog_delete_confirm) { _, _ ->
                file.delete()
                showSavedTimingsList()
            }
            .setNegativeButton(R.string.dialog_cancel) { _, _ ->
                savedTimingAdapter?.setPendingDelete(-1)
            }
            .setOnCancelListener {
                savedTimingAdapter?.setPendingDelete(-1)
            }
            .create()

        dialog.setOnShowListener {
            val window = dialog.window ?: return@setOnShowListener
            val screenHeight = resources.displayMetrics.heightPixels
            val targetCenterY = screenHeight / 6
            val dialogHeight = window.decorView.height
            val lp = window.attributes
            lp.gravity = Gravity.TOP
            lp.y = targetCenterY - dialogHeight / 2
            window.attributes = lp
        }
        dialog.show()
    }

    private fun setupFastScroll(recyclerView: RecyclerView, thumb: View) {
        recyclerView.post {
            val range = recyclerView.computeVerticalScrollRange()
            val extent = recyclerView.computeVerticalScrollExtent()
            if (range > extent * 2 && extent > 0) {
                val screenHeight = resources.displayMetrics.heightPixels
                val thumbHeight = (screenHeight / 9).coerceAtMost(recyclerView.height)
                val lp = thumb.layoutParams
                lp.height = thumbHeight
                thumb.layoutParams = lp
                thumb.translationY = ((recyclerView.height - thumbHeight) / 2f)
                thumb.isVisible = true
            } else {
                thumb.isVisible = false
            }
        }

        var dragStartY = 0f
        var thumbStartY = 0f
        thumb.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartY = event.rawY
                    thumbStartY = v.translationY
                    v.alpha = 0.5f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxThumbY = (recyclerView.height - v.height).toFloat().coerceAtLeast(0f)
                    val newY = (thumbStartY + (event.rawY - dragStartY)).coerceIn(0f, maxThumbY)
                    v.translationY = newY

                    val range = recyclerView.computeVerticalScrollRange()
                    val extent = recyclerView.computeVerticalScrollExtent()
                    val maxScroll = (range - extent).coerceAtLeast(1)
                    val ratio = if (maxThumbY > 0) newY / maxThumbY else 0f
                    val targetScroll = (ratio * maxScroll).toInt()
                    val currentScroll = recyclerView.computeVerticalScrollOffset()
                    recyclerView.scrollBy(0, targetScroll - currentScroll)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    true
                }
                else -> false
            }
        }
    }
}
