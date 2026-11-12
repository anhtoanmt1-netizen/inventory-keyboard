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
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
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
 * Bàn phím Android chuyên dụng: camera quét mã nằm ngay trong bàn phím.
 * Mỗi mã quét được sẽ: chèn vào ô nhập đang focus -> (tuỳ chọn) gửi Enter -> tiếp tục quét.
 * Vì camera nằm trong bàn phím nên ô nhập của website không bị mất focus.
 */
class ScannerKeyboardService : InputMethodService(), LifecycleOwner {

    // InputMethodService không có lifecycle riêng, CameraX cần LifecycleOwner nên tự tạo.
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    private lateinit var prefs: Prefs
    private lateinit var previewView: PreviewView
    private lateinit var tvStatus: TextView
    private lateinit var btnCam: Button
    private lateinit var btnTorch: Button

    private val handler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analyzer = BarcodeAnalyzer { code -> onCode(code) }

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var starting = false
    private var scanning = false
    private var torchOn = false

    private val lastSeen = HashMap<String, Long>() // chống trùng
    private var busyUntil = 0L                      // tránh 2 mã chen nhau trước khi Enter được gửi
    private var tone: ToneGenerator? = null

    override fun onCreate() {
        super.onCreate()
        registry.currentState = Lifecycle.State.CREATED
        prefs = Prefs(this)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false // không toàn màn hình khi xoay ngang

    override fun onCreateInputView(): View {
        val v = layoutInflater.inflate(R.layout.keyboard_view, null)
        previewView = v.findViewById(R.id.previewView)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        tvStatus = v.findViewById(R.id.tvStatus)
        btnCam = v.findViewById(R.id.btnCam)
        btnTorch = v.findViewById(R.id.btnTorch)

        btnCam.setOnClickListener { if (scanning) stopCamera() else startCamera() }
        btnTorch.setOnClickListener { toggleTorch() }
        v.findViewById<Button>(R.id.btnDel).setOnClickListener { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) }
        v.findViewById<Button>(R.id.btnEnter).setOnClickListener { sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }
        v.findViewById<Button>(R.id.btnSwitch).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        updateUi()
        return v
    }

    /** Bàn phím hiện lên: bật camera nếu cài đặt cho phép. */
    override fun onWindowShown() {
        super.onWindowShown()
        registry.currentState = Lifecycle.State.RESUMED
        lastSeen.clear()
        if (prefs.autoStart && !scanning) startCamera()
    }

    /** Bàn phím ẩn: tắt camera để tiết kiệm pin và nhường camera cho app khác. */
    override fun onWindowHidden() {
        super.onWindowHidden()
        stopCamera()
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

    // ---------------- Camera ----------------

    private fun startCamera() {
        if (starting || scanning) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            setStatus("Chưa có quyền camera. Chạm để mở ứng dụng cấp quyền.")
            tvStatus.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        starting = true
        setStatus("Đang mở camera…")
        if (!registry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            registry.currentState = Lifecycle.State.RESUMED
        }
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
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            ).build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, analyzer)

                camera = p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                scanning = true
                setStatus("Đang quét… đưa mã vào khung")
            } catch (e: Exception) {
                scanning = false
                setStatus("Không mở được camera: ${e.message ?: "lỗi không xác định"}. Chạm CAMERA để thử lại.")
            } finally {
                starting = false
                updateUi()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        try { camera?.cameraControl?.enableTorch(false) } catch (_: Exception) { }
        try { provider?.unbindAll() } catch (_: Exception) { } // giải phóng camera
        camera = null
        scanning = false
        torchOn = false
        if (::tvStatus.isInitialized) setStatus("Camera tắt")
        updateUi()
    }

    private fun toggleTorch() {
        val cam = camera
        if (cam == null) { setStatus("Hãy bật camera trước."); return }
        if (!cam.cameraInfo.hasFlashUnit()) { setStatus("Thiết bị không hỗ trợ đèn flash."); return }
        torchOn = !torchOn
        cam.cameraControl.enableTorch(torchOn)
        updateUi()
    }

    // ---------------- Xử lý mã ----------------

    private fun onCode(code: String) {
        val now = SystemClock.elapsedRealtime()
        if (now < busyUntil) return // đang gửi mã trước, mã này sẽ được quét lại ở khung kế tiếp

        val ic = currentInputConnection
        if (ic == null) { setStatus("Không có ô nhập nào đang được chọn."); return }

        // Chống trùng: mã còn nằm trong camera thì luôn làm mới mốc thời gian.
        // Chỉ khi mã vắng mặt lâu hơn debounce mới được nhận lại (không khóa vĩnh viễn).
        val last = lastSeen[code]
        lastSeen[code] = now
        if (last != null && now - last < prefs.debounceMs) return

        val enterDelay = prefs.enterDelayMs.toLong()
        busyUntil = now + enterDelay + 150

        ic.commitText(code, 1)
        if (prefs.sendEnter) {
            // Đúng một Enter cho mỗi mã
            handler.postDelayed({ sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }, enterDelay)
        }
        feedback()
        setStatus("✔ $code")
    }

    private fun feedback() {
        if (prefs.beep) {
            try {
                if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            } catch (_: Exception) { }
        }
        if (prefs.vibrate) {
            try {
                getSystemService(Vibrator::class.java)
                    ?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (_: Exception) { }
        }
    }

    // ---------------- UI ----------------

    private fun setStatus(text: String) {
        if (::tvStatus.isInitialized) tvStatus.text = text
    }

    private fun updateUi() {
        if (!::btnCam.isInitialized) return
        btnCam.text = if (scanning) "TẮT CAMERA" else "BẬT CAMERA"
        btnTorch.text = if (torchOn) "FLASH ON" else "FLASH"
    }
}
