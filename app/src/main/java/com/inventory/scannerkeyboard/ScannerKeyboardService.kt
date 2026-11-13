package com.inventory.scannerkeyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Size
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Keyboard Scan - SPX: bàn phím Android có camera quét mã ngay trong bàn phím.
 *  - Tab QUÉT: camera quét liên tục, mã được chèn vào ô đang chọn rồi gửi Enter.
 *  - Tab GÕ PHÍM: bàn phím đầy đủ chữ / số / ký hiệu.
 */
class ScannerKeyboardService : InputMethodService(), LifecycleOwner {

    // CameraX cần LifecycleOwner, InputMethodService không có sẵn nên tự tạo.
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    private lateinit var prefs: Prefs
    private var root: View? = null

    private lateinit var previewView: PreviewView
    private lateinit var overlay: ScanOverlayView
    private lateinit var cameraBox: View
    private lateinit var tvStatus: TextView
    private lateinit var tvHint: TextView
    private lateinit var tabScan: TextView
    private lateinit var tabType: TextView
    private lateinit var panelScan: View
    private lateinit var panelType: LinearLayout
    private lateinit var btnCam: TextView
    private lateinit var chipFlash: TextView
    private lateinit var chipKeepOn: TextView
    private lateinit var chipDim: TextView
    private lateinit var chipAutoEnter: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analyzer = BarcodeAnalyzer { code -> onCode(code) }

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var starting = false
    private var scanning = false
    private var torchOn = false

    private val lastSeen = HashMap<String, Long>() // chống trùng: chỉ chứa các mã đã được nhận
    private var busyUntil = 0L                      // đang rung / chờ độ nhạy: chưa nhận mã mới
    private var tone: ToneGenerator? = null

    private var mode = 0        // 0 = QUÉT, 1 = GÕ PHÍM
    private var layer = 0       // 0 chữ, 1 ký hiệu, 2 ký hiệu phụ
    private var shift = 0       // 0 thường, 1 hoa một lần, 2 CAPS LOCK
    private val letterKeys = ArrayList<Pair<TextView, Char>>()
    private var shiftKey: TextView? = null

    private var repeatAction: (() -> Unit)? = null
    private val repeatRunnable = object : Runnable {
        override fun run() { repeatAction?.invoke(); handler.postDelayed(this, 55) }
    }
    private val hideHint = Runnable {
        if (::tvHint.isInitialized) tvHint.animate().alpha(0f).setDuration(180).withEndAction { tvHint.visibility = View.GONE }.start()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        registry.currentState = Lifecycle.State.CREATED
        prefs = Prefs(this)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // =====================================================================
    //  GIAO DIỆN
    // =====================================================================
    override fun onCreateInputView(): View {
        val v = layoutInflater.inflate(R.layout.keyboard_view, null)
        root = v
        previewView = v.findViewById(R.id.previewView)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        overlay = v.findViewById(R.id.overlay)
        cameraBox = v.findViewById(R.id.cameraBox)
        tvStatus = v.findViewById(R.id.tvStatus)
        tvHint = v.findViewById(R.id.tvHint)
        tabScan = v.findViewById(R.id.tabScan)
        tabType = v.findViewById(R.id.tabType)
        panelScan = v.findViewById(R.id.panelScan)
        panelType = v.findViewById(R.id.panelType)
        btnCam = v.findViewById(R.id.btnCam)
        chipFlash = v.findViewById(R.id.chipFlash)
        chipKeepOn = v.findViewById(R.id.chipKeepOn)
        chipDim = v.findViewById(R.id.chipDim)
        chipAutoEnter = v.findViewById(R.id.chipAutoEnter)

        cameraBox.layoutParams.height = dp(prefs.cameraHeightDp.coerceIn(120, 420))

        // --- Các nút (chạm: thực hiện, giữ lâu: hiện hướng dẫn) ---
        bind(tabScan, "QUÉT: dùng camera quét mã QR / mã vạch, mã tự nhập vào ô đang chọn.") { setMode(0) }
        bind(tabType, "GÕ PHÍM: bàn phím đầy đủ để gõ chữ, số, ký hiệu. Camera tự tắt để tiết kiệm pin.") { setMode(1) }
        bind(v.findViewById(R.id.btnHide), "ẨN BÀN PHÍM: thu gọn bàn phím lại.") { requestHideSelf(0) }
        bind(btnCam, "BẬT / TẮT CAMERA: bật hoặc tắt camera quét. Nên tắt khi không dùng để tiết kiệm pin.") {
            if (scanning) stopCamera() else startCamera()
        }
        val btnDel = v.findViewById<TextView>(R.id.btnDel)
        bindRepeat(btnDel) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
        btnDel.setOnLongClickListener(null)
        bind(v.findViewById(R.id.btnEnter), "ENTER (↵): gửi phím Enter thủ công vào ô đang chọn.") { sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }
        bind(v.findViewById(R.id.btnSwitch), "ĐỔI BÀN PHÍM (🌐): chuyển sang bàn phím khác, ví dụ để gõ tiếng Việt có dấu.") { showPicker() }

        bind(chipFlash, "FLASH: bật / tắt đèn pin khi quét nơi thiếu sáng. Chỉ dùng được khi camera đang bật.") { toggleTorch() }
        bind(chipKeepOn, "LUÔN SÁNG: giữ màn hình không tự tắt khi bàn phím đang hiện.") {
            prefs.keepScreenOn = !prefs.keepScreenOn; applyScreenFlags()
            say(if (prefs.keepScreenOn) "Màn hình luôn sáng: BẬT" else "Màn hình luôn sáng: TẮT")
        }
        bind(chipDim, "GIẢM SÁNG: hạ độ sáng màn hình xuống mức đã đặt (mặc định 50%). Chỉnh mức trong ứng dụng.") {
            prefs.dimScreen = !prefs.dimScreen; applyScreenFlags()
            say(if (prefs.dimScreen) "Giảm sáng: ${prefs.dimPercent}%" else "Giảm sáng: TẮT")
        }
        bind(chipAutoEnter, "TỰ ENTER: tự gửi Enter sau mỗi mã quét được. Tắt nếu chỉ muốn chèn mã.") {
            prefs.sendEnter = !prefs.sendEnter; updateUi()
            say(if (prefs.sendEnter) "Tự Enter: BẬT" else "Tự Enter: TẮT")
        }

        setupDragHandle(v.findViewById(R.id.dragHandle))
        buildTypePanel()
        Fonts.apply(v)
        applyScreenFlags()
        setMode(mode)
        updateUi()
        return v
    }

    /** Nút thường: chạm thực hiện, giữ lâu hiện hướng dẫn, có hiệu ứng nhún nhẹ. */
    private fun bind(view: View, hint: String, action: () -> Unit) {
        view.setOnClickListener { action() }
        view.setOnLongClickListener { showHint(hint); true }
        pressEffect(view)
    }

    private fun pressEffect(view: View) {
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
            }
            false
        }
    }

    /** Nút giữ để lặp lại (phím xóa). */
    private fun bindRepeat(view: View, action: () -> Unit) {
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).start()
                    action()
                    repeatAction = action
                    handler.postDelayed(repeatRunnable, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                    handler.removeCallbacks(repeatRunnable)
                    repeatAction = null
                }
            }
            true
        }
    }

    private fun showHint(text: String) {
        if (!::tvHint.isInitialized) return
        handler.removeCallbacks(hideHint)
        tvHint.text = text
        tvHint.alpha = 0f
        tvHint.visibility = View.VISIBLE
        tvHint.animate().alpha(1f).setDuration(150).start()
        handler.postDelayed(hideHint, 3800)
    }

    private fun say(text: String) { if (::tvStatus.isInitialized) tvStatus.text = text }

    /** Kéo thanh nhỏ dưới khung camera: kéo lên = cao hơn, kéo xuống = thấp hơn. */
    private fun setupDragHandle(handle: View) {
        var startY = 0f
        var startH = 0
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY; startH = cameraBox.height
                    showHint("Kéo lên để tăng, kéo xuống để giảm chiều cao khung quét.")
                }
                MotionEvent.ACTION_MOVE -> {
                    val h = (startH + (startY - e.rawY)).toInt().coerceIn(dp(120), dp(420))
                    cameraBox.layoutParams.height = h
                    cameraBox.requestLayout()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    prefs.cameraHeightDp = (cameraBox.height / resources.displayMetrics.density).toInt().coerceIn(120, 420)
                }
            }
            true
        }
    }

    private fun updateUi() {
        if (!::btnCam.isInitialized) return
        btnCam.isSelected = scanning
        btnCam.text = if (scanning) "TẮT CAMERA" else "BẬT CAMERA"
        chipFlash.isSelected = torchOn
        chipKeepOn.isSelected = prefs.keepScreenOn
        chipDim.isSelected = prefs.dimScreen
        chipAutoEnter.isSelected = prefs.sendEnter
        tabScan.isSelected = mode == 0
        tabType.isSelected = mode == 1
        overlay.active = scanning
    }

    /** Màn hình luôn sáng + giảm độ sáng (áp dụng khi bàn phím đang hiện). */
    private fun applyScreenFlags(visible: Boolean = true) {
        root?.keepScreenOn = visible && prefs.keepScreenOn
        try {
            val w = window?.window
            if (w != null) {
                val lp = w.attributes
                lp.screenBrightness = if (visible && prefs.dimScreen) prefs.dimPercent.coerceIn(5, 100) / 100f
                else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        } catch (_: Throwable) { }
        updateUi()
    }

    private fun setMode(m: Int) {
        mode = m
        panelScan.visibility = if (m == 0) View.VISIBLE else View.GONE
        panelType.visibility = if (m == 1) View.VISIBLE else View.GONE
        if (m == 1) stopCamera() else if (prefs.autoStart && !scanning) startCamera()
        updateUi()
    }

    private fun showPicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    // =====================================================================
    //  VÒNG ĐỜI BÀN PHÍM
    // =====================================================================
    override fun onWindowShown() {
        super.onWindowShown()
        registry.currentState = Lifecycle.State.RESUMED
        lastSeen.clear()
        applyScreenFlags(true)
        if (mode == 0 && prefs.autoStart && !scanning && root != null) startCamera()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        stopCamera()
        applyScreenFlags(false)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onDestroy() {
        stopCamera()
        registry.currentState = Lifecycle.State.DESTROYED
        analyzer.close()
        executor.shutdown()
        tone?.release()
        super.onDestroy()
    }

    // =====================================================================
    //  CAMERA
    // =====================================================================
    private fun startCamera() {
        if (starting || scanning || root == null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            say("Chưa có quyền camera. Chạm để mở ứng dụng cấp quyền.")
            tvStatus.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        starting = true
        say("Đang mở camera…")
        if (!registry.currentState.isAtLeast(Lifecycle.State.STARTED)) registry.currentState = Lifecycle.State.RESUMED
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                p.unbindAll()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder().setResolutionStrategy(
                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                        ).build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, analyzer)
                camera = p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                scanning = true
                say("Đang quét… đưa mã vào khung")
            } catch (e: Exception) {
                scanning = false
                say("Không mở được camera: ${e.message ?: "lỗi không xác định"}")
            } finally {
                starting = false
                updateUi()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        try { camera?.cameraControl?.enableTorch(false) } catch (_: Exception) { }
        try { provider?.unbindAll() } catch (_: Exception) { }
        camera = null
        scanning = false
        torchOn = false
        if (::tvStatus.isInitialized) say("Camera tắt")
        updateUi()
    }

    private fun toggleTorch() {
        val cam = camera
        if (cam == null) { say("Hãy bật camera trước."); return }
        if (!cam.cameraInfo.hasFlashUnit()) { say("Thiết bị không hỗ trợ đèn flash."); return }
        torchOn = !torchOn
        cam.cameraControl.enableTorch(torchOn)
        updateUi()
    }

    // =====================================================================
    //  XỬ LÝ MÃ QUÉT
    // =====================================================================
    private fun onCode(code: String) {
        val now = SystemClock.elapsedRealtime()

        // Mã đã nhận mà vẫn còn trong khung: làm mới mốc thời gian (chống trùng, không khóa vĩnh viễn).
        val last = lastSeen[code]
        if (last != null) lastSeen[code] = now

        // Đang rung / đang chờ độ nhạy: chưa nhận mã mới (camera vẫn chạy, quét liên tục).
        if (now < busyUntil) return
        if (last != null && now - last < prefs.debounceMs) return

        val ic = currentInputConnection
        if (ic == null) { say("Không có ô nhập nào đang được chọn."); return }

        lastSeen[code] = now
        if (lastSeen.size > 200) lastSeen.entries.removeAll { now - it.value > 30_000 }

        val vibMs = if (prefs.vibrate) prefs.vibrateMs.coerceIn(0, 2000) else 0
        val enterDelay = if (prefs.sendEnter) prefs.enterDelayMs.coerceIn(0, 1000) else 0
        // Chỉ quét tiếp sau khi: rung xong + Enter đã gửi + thêm thời gian "độ nhạy".
        busyUntil = now + maxOf(vibMs, enterDelay + 100) + prefs.scanDelayMs.coerceIn(0, 5000)

        ic.commitText(code, 1)
        if (prefs.sendEnter) {
            handler.postDelayed({ sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }, enterDelay.toLong()) // đúng 1 Enter cho mỗi mã
        }
        feedback(vibMs)
        overlay.flashSuccess()
        say("✔ $code")
    }

    private fun feedback(vibMs: Int) {
        if (prefs.beep) {
            try {
                if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            } catch (_: Exception) { }
        }
        if (vibMs > 0) {
            try {
                getSystemService(Vibrator::class.java)
                    ?.vibrate(VibrationEffect.createOneShot(vibMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) { }
        }
    }

    // =====================================================================
    //  BÀN PHÍM GÕ (chữ / số / ký hiệu)
    // =====================================================================
    private enum class KS { NORMAL, SPECIAL, ACCENT }

    private fun buildTypePanel() {
        panelType.removeAllViews()
        letterKeys.clear()
        shiftKey = null

        when (layer) {
            0 -> {
                panelType.addView(charRow("1234567890", false, false))
                panelType.addView(charRow("qwertyuiop", true, false))
                panelType.addView(charRow("asdfghjkl", true, true))
                val r = newRow()
                shiftKey = addKey(r, "⇧", 1.5f, KS.SPECIAL) { toggleShift() }
                "zxcvbnm".forEach { addChar(r, it, true) }
                addRepeatKey(r, "⌫", 1.5f)
                panelType.addView(r)
            }
            1 -> {
                panelType.addView(charRow("1234567890", false, false))
                panelType.addView(charRow("@#$%&-+()/", false, false))
                val r = newRow()
                addKey(r, "=\\<", 1.5f, KS.SPECIAL) { layer = 2; buildTypePanel() }
                "*\"':;!?".forEach { addChar(r, it, false) }
                addRepeatKey(r, "⌫", 1.5f)
                panelType.addView(r)
            }
            else -> {
                panelType.addView(charRow("[]{}<>^~|\\", false, false))
                panelType.addView(charRow("_=+-*/&%$€", false, false))
                val r = newRow()
                addKey(r, "?123", 1.5f, KS.SPECIAL) { layer = 1; buildTypePanel() }
                ".,?!'\":".forEach { addChar(r, it, false) }
                addRepeatKey(r, "⌫", 1.5f)
                panelType.addView(r)
            }
        }

        val b = newRow()
        addKey(b, if (layer == 0) "?123" else "ABC", 1.5f, KS.SPECIAL) { layer = if (layer == 0) 1 else 0; buildTypePanel() }
        addKey(b, "🌐", 1.2f, KS.SPECIAL) { showPicker() }
        addKey(b, ",", 1f, KS.NORMAL) { type(",") }
        addKey(b, "dấu cách", 4f, KS.NORMAL) { type(" ") }
        addKey(b, ".", 1f, KS.NORMAL) { type(".") }
        addKey(b, "↵", 1.5f, KS.ACCENT) { sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }
        panelType.addView(b)

        Fonts.apply(panelType)
        refreshShift()
    }

    private fun newRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun charRow(chars: String, letters: Boolean, pad: Boolean): LinearLayout {
        val r = newRow()
        if (pad) r.addView(Space(this), LinearLayout.LayoutParams(0, 1, 0.5f))
        chars.forEach { addChar(r, it, letters) }
        if (pad) r.addView(Space(this), LinearLayout.LayoutParams(0, 1, 0.5f))
        return r
    }

    private fun addKey(row: LinearLayout, label: String, weight: Float, style: KS, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setTextColor(0xFFF3F5FB.toInt())
        tv.textSize = if (label.length > 1) 14f else 19f
        tv.isClickable = true
        tv.setBackgroundResource(
            when (style) { KS.NORMAL -> R.drawable.bg_key; KS.SPECIAL -> R.drawable.bg_key_special; KS.ACCENT -> R.drawable.bg_key_accent }
        )
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
        lp.setMargins(dp(3), 0, dp(3), 0)
        row.addView(tv, lp)
        tv.setOnClickListener { onClick() }
        pressEffect(tv)
        return tv
    }

    private fun addChar(row: LinearLayout, c: Char, letter: Boolean) {
        val label = if (letter && shift > 0) c.uppercaseChar().toString() else c.toString()
        val key = addKey(row, label, 1f, KS.NORMAL) { typeChar(c, letter) }
        if (letter) letterKeys.add(key to c)
    }

    private fun addRepeatKey(row: LinearLayout, label: String, weight: Float) {
        val tv = addKey(row, label, weight, KS.SPECIAL) { }
        bindRepeat(tv) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
    }

    private fun type(s: String) { currentInputConnection?.commitText(s, 1) }

    private fun typeChar(c: Char, letter: Boolean) {
        val ch = if (letter && shift > 0) c.uppercaseChar() else c
        type(ch.toString())
        if (letter && shift == 1) { shift = 0; refreshShift() }
    }

    private fun toggleShift() {
        shift = when (shift) { 0 -> 1; 1 -> 2; else -> 0 }
        refreshShift()
    }

    private fun refreshShift() {
        letterKeys.forEach { (tv, c) -> tv.text = if (shift > 0) c.uppercaseChar().toString() else c.toString() }
        shiftKey?.apply { text = if (shift == 2) "⇪" else "⇧"; isSelected = shift > 0 }
    }
}
