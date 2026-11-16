package com.inventory.scannerkeyboard

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Size
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
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
 * Quét mã QR xuất từ SPX Keyboard để nhập giao diện / cài đặt. Dữ liệu dài được chia thành nhiều mã:
 * quét lần lượt từng mã (thứ tự bất kỳ), đủ bộ thì tự ghép lại và trả kết quả về màn hình Xuất / Nhập.
 */
class QrScanActivity : Activity(), LifecycleOwner {

    companion object {
        /** Gói vừa quét xong, chuyển cho TransferActivity (cùng một tiến trình nên không cần qua Intent). */
        @Volatile var result: ShareBundle? = null
    }

    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    private lateinit var previewView: PreviewView
    private lateinit var tvInfo: TextView
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var analyzer: BarcodeAnalyzer? = null
    private val parts = LinkedHashMap<Int, QrPart>()
    private var curId: String? = null
    private var done = false
    private var bound = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        result = null
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
        root.addView(previewView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tvInfo = TextView(this).apply {
            text = "Đưa mã QR vào khung hình. Nếu có nhiều mã, quét lần lượt từng mã."
            textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setBackgroundColor(0xB3000000.toInt()); setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        root.addView(tvInfo, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        val cancel = TextView(this).apply {
            text = "HỦY"; textSize = 15f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD); setBackgroundResource(R.drawable.bg_btn_secondary)
            isClickable = true; setOnClickListener { finish() }
        }
        root.addView(cancel, FrameLayout.LayoutParams(dp(160), dp(48), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(32) })
        setContentView(root)
        registry.currentState = Lifecycle.State.CREATED

        analyzer = BarcodeAnalyzer({ done }, { code -> onCode(code) })
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 41)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 41) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startCamera()
        else { Toast.makeText(this, "Cần quyền camera để quét mã QR.", Toast.LENGTH_LONG).show(); finish() }
    }

    override fun onStart() {
        super.onStart()
        registry.currentState = Lifecycle.State.STARTED
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
    }

    override fun onStop() {
        registry.currentState = Lifecycle.State.CREATED
        bound = false
        super.onStop()
    }

    override fun onDestroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        analyzer?.close()
        executor.shutdown()
        super.onDestroy()
    }

    private fun startCamera() {
        if (bound) return
        bound = true
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val p = future.get()
                p.unbindAll()
                val selector = ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                ).build()
                val preview = Preview.Builder().setResolutionSelector(selector).build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, analyzer!!)
                p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                bound = false
                tvInfo.text = "Không mở được camera: ${e.message ?: "lỗi không xác định"}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onCode(code: String) {
        if (done) return
        val p = Transfer.parseQr(code)
        if (p == null) { tvInfo.text = "Đây không phải mã của SPX Keyboard."; return }
        if (curId != p.id) { parts.clear(); curId = p.id }
        if (parts.put(p.index, p) == null) Haptics.tick(this, 60)
        tvInfo.text = "Đã quét ${parts.size}/${p.total} mã" + if (parts.size < p.total) ". Quét tiếp các mã còn lại." else ""
        if (parts.size == p.total) {
            val b = Transfer.assemble(parts.values)
            if (b == null) {
                tvInfo.text = "Dữ liệu bị hỏng hoặc thiếu mã. Hãy quét lại từ đầu."
                parts.clear(); curId = null
            } else {
                done = true
                result = b
                setResult(RESULT_OK)
                finish()
            }
        }
    }
}
