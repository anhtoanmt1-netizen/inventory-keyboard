package com.inventory.scannerkeyboard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
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
 *  - Chế độ QUÉT: camera quét liên tục, mã được chèn vào ô đang chọn rồi gửi Enter.
 *  - Chế độ GÕ PHÍM: bàn phím đầy đủ chữ / số / ký hiệu.
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
    private lateinit var tabScan: ImageView
    private lateinit var tabType: ImageView
    private lateinit var panelScan: View
    private lateinit var panelType: LinearLayout
    private lateinit var btnTools: ImageView
    private lateinit var flyout: LinearLayout
    private lateinit var fCam: ImageView
    private lateinit var fFlash: ImageView
    private lateinit var fKeepOn: ImageView
    private lateinit var fDim: ImageView
    private lateinit var fAutoEnter: ImageView

    private val handler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analyzer = BarcodeAnalyzer({ SystemClock.elapsedRealtime() < busyUntil }, { code -> onCode(code) })

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var starting = false
    private var scanning = false
    private var torchOn = false
    private var windowVisible = false
    private var flyoutOpen = false

    private val lastSeen = HashMap<String, Long>() // chống trùng: chỉ chứa các mã đã được nhận
    @Volatile private var busyUntil = 0L            // đang rung / chờ độ nhạy: chưa nhận mã mới
    private var lastActivity = 0L                    // lần quét / bật camera gần nhất (cho tự tắt khi rảnh)
    private var tone: ToneGenerator? = null

    private var mode = 0        // 0 = QUÉT, 1 = GÕ PHÍM
    private var layer = 0       // 0 chữ, 1 ký hiệu, 2 ký hiệu phụ
    private var shift = 0       // 0 thường, 1 hoa một lần, 2 CAPS LOCK
    private val letterKeys = ArrayList<Pair<TextView, Char>>()
    private var shiftKey: ImageView? = null

    private var repeatAction: (() -> Unit)? = null
    private val repeatRunnable = object : Runnable {
        override fun run() { repeatAction?.invoke(); handler.postDelayed(this, 55) }
    }
    private val hideHint = Runnable {
        if (::tvHint.isInitialized) tvHint.animate().alpha(0f).setDuration(180).withEndAction { tvHint.visibility = View.GONE }.start()
    }

    /** Tự tắt camera khi không quét trong thời gian cài đặt (mặc định tắt tính năng này). */
    private val idleCheck = object : Runnable {
        override fun run() {
            val limit = prefs.idleOffSec
            if (scanning && limit > 0 && SystemClock.elapsedRealtime() - lastActivity > limit * 1000L) {
                stopCamera()
                say("Camera đã tự tắt để tiết kiệm pin. Mở lại: biểu tượng công cụ → camera")
            } else if (scanning) {
                handler.postDelayed(this, 5000)
            }
        }
    }

    /** Màn hình tắt → tắt camera ngay; mở khóa → bật lại nếu bàn phím vẫn đang hiện. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> stopCamera()
                Intent.ACTION_USER_PRESENT ->
                    if (windowVisible && mode == 0 && prefs.autoStart && root != null) startCamera()
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        registry.currentState = Lifecycle.State.CREATED
        prefs = Prefs(this)
        try {
            val f = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
            ContextCompat.registerReceiver(this, screenReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Throwable) { }
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
        btnTools = v.findViewById(R.id.btnTools)
        flyout = v.findViewById(R.id.flyout)
        fCam = v.findViewById(R.id.fCam)
        fFlash = v.findViewById(R.id.fFlash)
        fKeepOn = v.findViewById(R.id.fKeepOn)
        fDim = v.findViewById(R.id.fDim)
        fAutoEnter = v.findViewById(R.id.fAutoEnter)

        cameraBox.layoutParams.height = dp(prefs.cameraHeightDp.coerceIn(120, 420))

        // --- Hàng trên ---
        bind(tabScan, "QUÉT: dùng camera quét mã QR / mã vạch, mã tự nhập vào ô đang chọn.") { setMode(0) }
        bind(tabType, "GÕ PHÍM: bàn phím đầy đủ để gõ chữ, số, ký hiệu. Camera tự tắt để tiết kiệm pin.") { setMode(1) }
        bind(v.findViewById(R.id.btnHide), "ẨN BÀN PHÍM: thu gọn bàn phím lại.") { requestHideSelf(0) }

        // --- Hàng dưới ---
        bind(btnTools, "CÔNG CỤ: mở danh sách camera, flash, luôn sáng, giảm sáng, tự Enter. Chạm lại để thu gọn.") { toggleFlyout() }
        val btnDel = v.findViewById<ImageView>(R.id.btnDel)
        bindRepeat(btnDel) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
        bind(v.findViewById(R.id.btnEnter), "ENTER: gửi phím Enter thủ công vào ô đang chọn.") { sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }
        bind(v.findViewById(R.id.btnSwitch), "ĐỔI BÀN PHÍM: chuyển sang bàn phím khác, ví dụ để gõ tiếng Việt có dấu.") { showPicker() }

        // --- Danh sách công cụ (tự thu lại sau khi chọn) ---
        bind(fCam, "CAMERA: bật / tắt camera quét. Nên tắt khi không dùng để tiết kiệm pin.") {
            if (scanning) stopCamera() else startCamera(); closeFlyoutSoon()
        }
        bind(fFlash, "FLASH: bật / tắt đèn pin khi quét nơi thiếu sáng. Chỉ dùng được khi camera đang bật.") {
            toggleTorch(); closeFlyoutSoon()
        }
        bind(fKeepOn, "LUÔN SÁNG: giữ màn hình không tự tắt khi bàn phím đang hiện.") {
            prefs.keepScreenOn = !prefs.keepScreenOn; applyScreenFlags()
            say(if (prefs.keepScreenOn) "Luôn sáng: BẬT" else "Luôn sáng: TẮT"); closeFlyoutSoon()
        }
        bind(fDim, "GIẢM SÁNG: hạ độ sáng màn hình xuống mức đã đặt (mặc định 50%). Chỉnh mức trong ứng dụng.") {
            prefs.dimScreen = !prefs.dimScreen; applyScreenFlags()
            say(if (prefs.dimScreen) "Giảm sáng: ${prefs.dimPercent}%" else "Giảm sáng: TẮT"); closeFlyoutSoon()
        }
        bind(fAutoEnter, "TỰ ENTER: tự gửi Enter sau mỗi mã quét được. Tắt nếu chỉ muốn chèn mã.") {
            prefs.sendEnter = !prefs.sendEnter; updateUi()
            say(if (prefs.sendEnter) "Tự Enter: BẬT" else "Tự Enter: TẮT"); closeFlyoutSoon()
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

    // ----- Danh sách công cụ mở / thu có hoạt ảnh -----
    private fun toggleFlyout() { if (flyoutOpen) closeFlyout() else openFlyout() }

    private fun openFlyout() {
        flyoutOpen = true
        flyout.animate().cancel()
        flyout.visibility = View.VISIBLE
        flyout.pivotX = 0f
        flyout.pivotY = dp(60).toFloat()
        flyout.alpha = 0f
        flyout.translationY = dp(14).toFloat()
        flyout.scaleX = 0.85f
        flyout.scaleY = 0.85f
        flyout.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(190).setInterpolator(DecelerateInterpolator()).start()
        for (i in 0 until flyout.childCount) {
            val item = flyout.getChildAt(i)
            item.alpha = 0f
            item.animate().alpha(1f).setStartDelay(35L * i).setDuration(160).start()
        }
        tvStatus.animate().alpha(0f).setDuration(150).start()
        updateUi()
    }

    private fun closeFlyout() {
        if (!flyoutOpen) return
        flyoutOpen = false
        flyout.animate().cancel()
        flyout.animate().alpha(0f).translationY(dp(10).toFloat()).scaleX(0.9f).scaleY(0.9f)
            .setDuration(150).withEndAction { if (!flyoutOpen) flyout.visibility = View.GONE }.start()
        tvStatus.animate().alpha(1f).setDuration(150).start()
        updateUi()
    }

    private fun closeFlyoutSoon() { handler.postDelayed({ closeFlyout() }, 260) }

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
        if (!::btnTools.isInitialized) return
        fCam.isSelected = scanning
        fCam.setImageResource(if (scanning) R.drawable.ic_camera else R.drawable.ic_camera_off)
        fFlash.isSelected = torchOn
        fKeepOn.isSelected = prefs.keepScreenOn
        fDim.isSelected = prefs.dimScreen
        fAutoEnter.isSelected = prefs.sendEnter
        btnTools.isSelected = flyoutOpen
        btnTools.setImageResource(if (flyoutOpen) R.drawable.ic_close else R.drawable.ic_tune)
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
        if (m == 1) { closeFlyout(); stopCamera() } else if (prefs.autoStart && !scanning) startCamera()
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
        windowVisible = true
        registry.currentState = Lifecycle.State.RESUMED
        lastSeen.clear()
        applyScreenFlags(true)
        if (mode == 0 && prefs.autoStart && !scanning && root != null) startCamera()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        windowVisible = false
        if (::flyout.isInitialized) { flyoutOpen = false; flyout.visibility = View.GONE }
        stopCamera()
        applyScreenFlags(false)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Throwable) { }
        handler.removeCallbacksAndMessages(null)
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
                // Preview và phân tích dùng CÙNG độ phân giải 1280x720: camera chạy gọn hơn (đỡ nóng),
                // còn ảnh đưa vào nhận diện vẫn y như trước nên độ nhạy không đổi.
                val selector = ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                ).build()
                val preview = Preview.Builder().setResolutionSelector(selector).build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, analyzer)
                camera = p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                scanning = true
                lastActivity = SystemClock.elapsedRealtime()
                handler.removeCallbacks(idleCheck)
                if (prefs.idleOffSec > 0) handler.postDelayed(idleCheck, 5000)
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
        handler.removeCallbacks(idleCheck)
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
        say(if (torchOn) "Flash: BẬT" else "Flash: TẮT")
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

        if (lastSeen.size > 200) lastSeen.entries.removeAll { now - it.value > 30_000 }

        val vibMs = if (prefs.vibrate) prefs.vibrateMs.coerceIn(0, 2000) else 0
        val enterDelay = if (prefs.sendEnter) prefs.enterDelayMs.coerceIn(0, 1000) else 0
        // Chỉ quét tiếp sau khi: rung xong + Enter đã gửi + thêm thời gian "độ nhạy".
        busyUntil = now + maxOf(vibMs, enterDelay + 100) + prefs.scanDelayMs.coerceIn(0, 5000)
        // Trong lúc chờ, camera không nhận diện (đỡ tốn pin) nên mốc "đã thấy" tính từ lúc hết chờ.
        lastSeen[code] = busyUntil
        lastActivity = now

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
                shiftKey = addIconKey(r, R.drawable.ic_shift, 1.5f, KS.SPECIAL) { toggleShift() }
                "zxcvbnm".forEach { addChar(r, it, true) }
                addRepeatKey(r, 1.5f)
                panelType.addView(r)
            }
            1 -> {
                panelType.addView(charRow("1234567890", false, false))
                panelType.addView(charRow("@#$%&-+()/", false, false))
                val r = newRow()
                addKey(r, "=\\<", 1.5f, KS.SPECIAL) { layer = 2; buildTypePanel() }
                "*\"':;!?".forEach { addChar(r, it, false) }
                addRepeatKey(r, 1.5f)
                panelType.addView(r)
            }
            else -> {
                panelType.addView(charRow("[]{}<>^~|\\", false, false))
                panelType.addView(charRow("_=+-*/&%$€", false, false))
                val r = newRow()
                addKey(r, "?123", 1.5f, KS.SPECIAL) { layer = 1; buildTypePanel() }
                ".,?!'\":".forEach { addChar(r, it, false) }
                addRepeatKey(r, 1.5f)
                panelType.addView(r)
            }
        }

        val b = newRow()
        addKey(b, if (layer == 0) "?123" else "ABC", 1.5f, KS.SPECIAL) { layer = if (layer == 0) 1 else 0; buildTypePanel() }
        addIconKey(b, R.drawable.ic_globe, 1.2f, KS.SPECIAL) { showPicker() }
        addKey(b, ",", 1f, KS.NORMAL) { type(",") }
        addKey(b, "", 4f, KS.NORMAL) { type(" ") }          // phím cách
        addKey(b, ".", 1f, KS.NORMAL) { type(".") }
        addIconKey(b, R.drawable.ic_enter, 1.5f, KS.ACCENT) { sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }
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

    private fun keyBg(style: KS) = when (style) {
        KS.NORMAL -> R.drawable.bg_key
        KS.SPECIAL -> R.drawable.bg_key_special
        KS.ACCENT -> R.drawable.bg_key_accent
    }

    private fun place(row: LinearLayout, v: View, weight: Float) {
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
        lp.setMargins(dp(3), 0, dp(3), 0)
        row.addView(v, lp)
    }

    private fun addKey(row: LinearLayout, label: String, weight: Float, style: KS, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setTextColor(ContextCompat.getColor(this, R.color.text))
        tv.textSize = if (label.length > 1) 14f else 19f
        tv.isClickable = true
        tv.setBackgroundResource(keyBg(style))
        place(row, tv, weight)
        tv.setOnClickListener { onClick() }
        pressEffect(tv)
        return tv
    }

    /** Phím dùng biểu tượng (kích thước icon luôn 24dp, giống hàng nút ở màn hình quét). */
    private fun addIconKey(row: LinearLayout, iconRes: Int, weight: Float, style: KS, onClick: () -> Unit): ImageView {
        val iv = ImageView(this)
        iv.setImageResource(iconRes)
        iv.imageTintList = ContextCompat.getColorStateList(this, R.color.icon_tint)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setPadding(dp(13), dp(13), dp(13), dp(13))
        iv.isClickable = true
        iv.setBackgroundResource(keyBg(style))
        place(row, iv, weight)
        iv.setOnClickListener { onClick() }
        pressEffect(iv)
        return iv
    }

    private fun addChar(row: LinearLayout, c: Char, letter: Boolean) {
        val label = if (letter && shift > 0) c.uppercaseChar().toString() else c.toString()
        val key = addKey(row, label, 1f, KS.NORMAL) { typeChar(c, letter) }
        if (letter) letterKeys.add(key to c)
    }

    private fun addRepeatKey(row: LinearLayout, weight: Float) {
        val iv = addIconKey(row, R.drawable.ic_backspace, weight, KS.SPECIAL) { }
        bindRepeat(iv) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
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
        shiftKey?.apply {
            setImageResource(if (shift == 2) R.drawable.ic_shift_lock else R.drawable.ic_shift)
            isSelected = shift > 0
        }
    }
}
