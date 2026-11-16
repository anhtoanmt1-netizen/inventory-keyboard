package com.inventory.scannerkeyboard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Size
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
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
import kotlin.math.abs
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
    private lateinit var panelClipHost: FrameLayout
    private lateinit var searchBar: View
    private lateinit var tvSearch: TextView
    private lateinit var panelScan: View
    private lateinit var panelType: LinearLayout
    private lateinit var btnArea: LinearLayout       // hàng nút dưới (bảng Scan)
    private lateinit var topArea: LinearLayout       // hàng nút trên (hiện ở mọi chế độ)
    private lateinit var toolsBox: FrameLayout       // nhóm Công cụ (mở ra bên dưới hàng trên)
    private lateinit var toolsRow: LinearLayout
    private lateinit var creditView: CreditView
    private var creditRaw = "\u0000"
    private var btnMap: Map<String, ImageView> = emptyMap()
    private var customKeys: Set<String> = emptySet()   // nút đang dùng ảnh PNG riêng (không đổi biểu tượng theo trạng thái)
    private var toolsMode = 0                          // 0 danh sách công cụ, 1 chọn nhanh âm thanh
    private var clipViewer: ClipViewer? = null
    private var spaceStyle = SpaceStyle()
    private var spaceView: TextView? = null
    private var layoutSig = ""

    // ---- Giao diện tùy chỉnh (xem KbTheme.kt / ThemeRender.kt) ----
    private var themeRaw = "\u0000"            // chuỗi giao diện đã nạp; đổi là biết cần dựng lại
    private var styleScan = PanelStyle()
    private var styleKb = PanelStyle()
    private val keyViews = ArrayList<View>()    // các phím đang có trên bảng gõ, để áp font / màu chữ
    private var statusColor0 = 0
    private var hintColor0 = 0
    private var scanTextCaptured = false
    private var fxKb: FxDrawable? = null        // hiệu ứng chạm + ánh sáng trên bảng gõ
    private var fxScan: FxDrawable? = null      // hiệu ứng chạm + ánh sáng trên hàng nút Scan
    private var fxTop: FxDrawable? = null       // hiệu ứng chạm + ánh sáng trên hàng nút trên
    private var fxTools: FxDrawable? = null     // hiệu ứng chạm + ánh sáng trên nhóm Công cụ
    private var scanButtonViews: List<View> = emptyList()   // nút ở hàng dưới
    private var topViews: List<View> = emptyList()          // nút ở hàng trên
    private var toolViews: List<View> = emptyList()         // nút trong nhóm Công cụ

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

    private var mode = 0        // 0 = QUÉT, 1 = GÕ PHÍM, 2 = CLIPBOARD
    private var searchActive = false   // đang gõ từ khóa tìm kiếm Clipboard bằng bàn phím
    private var searchText = ""
    private var layer = 0       // 0 chữ, 1 ký hiệu, 2 ký hiệu phụ
    private var shift = 0       // 0 thường, 1 hoa một lần, 2 CAPS LOCK
    private val letterKeys = ArrayList<Pair<TextView, Char>>()
    private var shiftKey: ImageView? = null

    // --- Gõ tiếng Việt (Telex) và âm thanh phím ---
    private val telex = TelexEngine()
    private var vn = true                 // mặc định tiếng Việt mỗi khi bàn phím được tạo
    private var lastComposeAt = 0L
    private var soundHolder: KeySound? = null
    private fun sounds(): KeySound? {
        if (soundHolder == null) soundHolder = try { KeySound(this) } catch (_: Throwable) { null }
        return soundHolder
    }
    private fun keyClick() {
        if (prefs.keySound) sounds()?.play(prefs.keySoundType, prefs.keySoundVolume)
    }
    /** Rung nhẹ khi chạm / gõ phím, có công tắc và cường độ riêng trong cài đặt. */
    private fun keyHaptic() {
        if (prefs.keyHaptic) Haptics.tick(this, prefs.keyHapticStrength)
    }

    private var repeatAction: (() -> Unit)? = null
    private var repeatStart = 0L
    private var repeatCount = 0
    private val repeatRunnable = object : Runnable {
        override fun run() {
            repeatAction?.invoke()
            repeatCount++
            // Tốc độ tăng dần theo thời gian giữ: khoảng 120 ms mỗi lần lúc đầu, nhanh dần tới khoảng 28 ms sau ~2 giây.
            val t = ((SystemClock.uptimeMillis() - repeatStart) / 2200f).coerceIn(0f, 1f)
            val interval = (120f - 92f * t).toLong()
            // Rung nhẹ cho từng ký tự bị xóa; khi xóa rất nhanh thì cách một nhịp mới rung để không bị ù.
            if (interval >= 55L || repeatCount % 2 == 0) keyHaptic()
            handler.postDelayed(this, interval)
        }
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

    private val KEY_ROW_MIN = 36
    private val KEY_ROW_MAX = 72
    private val CLIP_MIN = 120
    private val CLIP_MAX = 420

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        registry.currentState = Lifecycle.State.CREATED
        prefs = Prefs(this)
        vn = true
        clipStore = ClipStore(this)
        try {
            clipMgr = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipMgr?.addPrimaryClipChangedListener(clipListener)
        } catch (_: Throwable) { }
        try {
            val f = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT) }
            ContextCompat.registerReceiver(this, screenReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Throwable) { }
    }

    // =====================================================================
    //  CLIPBOARD
    // =====================================================================
    private lateinit var clipStore: ClipStore
    private var clipPanel: ClipPanel? = null
    private var clipMgr: ClipboardManager? = null
    private var ignoreClipUntil = 0L
    private var lastImageUri = ""
    private var lastTextSig = ""
    private val ioExec: ExecutorService = Executors.newSingleThreadExecutor()
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { ingestClipboard() }

    /** Đọc nội dung vừa sao chép (văn bản hoặc hình ảnh) và lưu vào kho Clipboard. */
    private fun ingestClipboard(fromShow: Boolean = false) {
        if (SystemClock.elapsedRealtime() < ignoreClipUntil) return
        val mgr = clipMgr ?: return
        try {
            val clip = mgr.primaryClip ?: return
            if (clip.itemCount == 0) return
            val desc = clip.description
            if (Build.VERSION.SDK_INT >= 33 && desc?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return
            val item = clip.getItemAt(0)
            val uri = item.uri
            if (uri != null && desc != null && desc.hasMimeType("image/*")) {
                val key = uri.toString()
                if (key == lastImageUri) return
                lastImageUri = key
                val mime = desc.getMimeType(0)
                val ext = when {
                    mime.contains("png") -> "png"
                    mime.contains("webp") -> "webp"
                    mime.contains("gif") -> "gif"
                    else -> "jpg"
                }
                val out = clipStore.newImageFile(ext)
                val resolver = contentResolver
                ioExec.execute {
                    var ok = false
                    try {
                        resolver.openInputStream(uri)?.use { input ->
                            out.outputStream().use { o ->
                                val buf = ByteArray(16 * 1024)
                                var total = 0L
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    total += n
                                    if (total > 12L * 1024 * 1024) throw java.io.IOException("ảnh quá lớn")
                                    o.write(buf, 0, n)
                                }
                            }
                            ok = out.length() > 0
                        }
                    } catch (_: Throwable) { }
                    if (ok) handler.post { clipStore.addImage(out.name); clipPanel?.refresh() } else out.delete()
                }
            } else {
                val text = item.coerceToText(this)?.toString() ?: return
                val sig = "${text.length}:${text.hashCode()}"
                // Khi chỉ là mở bàn phím: bỏ qua nếu nội dung này đã được lưu rồi (tránh mục đã xóa tự quay lại)
                if (fromShow && sig == lastTextSig) return
                lastTextSig = sig
                if (clipStore.addText(text)) clipPanel?.refresh()
            }
        } catch (_: Throwable) { }
    }

    /** Dán một mục Clipboard vào ô đang nhập. */
    private fun pasteClip(item: ClipItem) {
        val ic = currentInputConnection
        if (ic == null) { Toast.makeText(this, "Không có ô nhập nào đang được chọn.", Toast.LENGTH_SHORT).show(); return }
        if (item.type == ClipStore.TYPE_TEXT) {
            finishComposing()
            ic.commitText(item.text, 1)
            return
        }
        val file = clipStore.imageFile(item)
        if (file == null || !file.exists()) { Toast.makeText(this, "Không tìm thấy file ảnh.", Toast.LENGTH_SHORT).show(); return }
        val mime = when (file.extension.lowercase()) { "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"; else -> "image/jpeg" }
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.clipfiles", file)
            val ei = currentInputEditorInfo
            val accepted = ei != null && EditorInfoCompat.getContentMimeTypes(ei).any { ClipDescription.compareMimeTypes(mime, it) }
            if (accepted) {
                val info = InputContentInfoCompat(uri, ClipDescription("clip", arrayOf(mime)), null)
                val flags = if (Build.VERSION.SDK_INT >= 25) InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION else 0
                if (InputConnectionCompat.commitContent(ic, ei!!, info, flags, null)) return
            }
            // Ô nhập không nhận ảnh trực tiếp: đặt ảnh lên khay nhớ tạm để người dùng dán bằng thao tác của ứng dụng đó.
            ignoreClipUntil = SystemClock.elapsedRealtime() + 1500
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newUri(contentResolver, "clip", uri))
            Toast.makeText(this, "Ô này không nhận ảnh trực tiếp. Ảnh đã được sao chép, hãy chạm giữ ô nhập và chọn Dán.", Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {
            Toast.makeText(this, "Không dán được ảnh.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Mở bảng xem nội dung đầy đủ của một mục Clipboard (nút con mắt). */
    private fun showClipViewer(item: ClipItem) {
        val viewer = clipViewer ?: ClipViewer(this, clipStore, object : ClipViewer.Host {
            override fun paste(item: ClipItem) { pasteClip(item); clipStore.touch(item.id); clipPanel?.refresh() }
            override fun copy(item: ClipItem) = copyClipToSystem(item)
            override fun changed() { clipPanel?.refresh(true) }
        }).also { clipViewer = it }
        viewer.show(item)
    }

    /** Sao chép một mục Clipboard lên khay nhớ tạm của máy (không tự lưu lại thành mục mới). */
    private fun copyClipToSystem(item: ClipItem) {
        try {
            ignoreClipUntil = SystemClock.elapsedRealtime() + 1500
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            if (item.type == ClipStore.TYPE_TEXT) {
                cm.setPrimaryClip(ClipData.newPlainText("clip", item.text))
            } else {
                val file = clipStore.imageFile(item)
                if (file == null || !file.exists()) { Toast.makeText(this, "Không tìm thấy file ảnh.", Toast.LENGTH_SHORT).show(); return }
                val uri = FileProvider.getUriForFile(this, "$packageName.clipfiles", file)
                cm.setPrimaryClip(ClipData.newUri(contentResolver, "clip", uri))
            }
            Toast.makeText(this, "Đã sao chép.", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
            Toast.makeText(this, "Không sao chép được.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Nạp kiểu chữ và hiệu ứng dòng "Created by" nếu đã thay đổi trong Tùy chọn cho nhà phát triển. */
    private fun refreshCredit() {
        if (!::creditView.isInitialized) return
        val raw = prefs.creditJson
        if (raw != creditRaw) {
            creditRaw = raw
            creditView.setStyle(CreditStyle.fromJson(raw))
        }
    }

    /** Gõ trực tiếp ảnh vào ô đang nhập (nếu ô đó nhận ảnh). Trả về true nếu gửi được. */
    private fun commitImageDirect(file: java.io.File): Boolean {
        val ic = currentInputConnection ?: return false
        val ei = currentInputEditorInfo ?: return false
        val mime = when (file.extension.lowercase()) { "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"; else -> "image/jpeg" }
        return try {
            if (EditorInfoCompat.getContentMimeTypes(ei).none { ClipDescription.compareMimeTypes(mime, it) }) return false
            val uri = FileProvider.getUriForFile(this, "$packageName.clipfiles", file)
            val info = InputContentInfoCompat(uri, ClipDescription("clip", arrayOf(mime)), null)
            val flags = if (Build.VERSION.SDK_INT >= 25) InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION else 0
            InputConnectionCompat.commitContent(ic, ei, info, flags, null)
        } catch (_: Throwable) { false }
    }

    /**
     * Gửi tất cả các mục đã chọn trong một lần thao tác, đúng thứ tự danh sách truyền vào
     * (là thứ tự người dùng đã chọn). Văn bản chèn bằng commitText, hình ảnh gửi bằng commitContent.
     * Hai mục văn bản liền nhau được ngăn cách bằng một dấu xuống dòng để không dính vào nhau.
     */
    private fun pasteClips(items: List<ClipItem>) {
        if (items.isEmpty()) return
        if (currentInputConnection == null) {
            Toast.makeText(this, "Không có ô nhập nào đang được chọn.", Toast.LENGTH_SHORT).show(); return
        }
        finishComposing()
        val queue = ArrayList(items)
        var failedImages = 0
        var prevWasText = false
        val stepDelay = 220L // để ứng dụng đích kịp nhận từng mục, nhất là ảnh

        fun finish() {
            if (failedImages > 0) Toast.makeText(
                this,
                if (failedImages == queue.count { it.type == ClipStore.TYPE_IMAGE })
                    "Ô này không nhận ảnh trực tiếp nên không gửi được ảnh. Hãy dán từng ảnh bằng cách chạm vào ảnh."
                else "Có $failedImages ảnh không gửi được vì ô này không nhận ảnh.",
                Toast.LENGTH_LONG
            ).show()
        }

        fun step(i: Int) {
            if (i >= queue.size) { finish(); return }
            val ic = currentInputConnection
            if (ic == null) { finish(); return }
            val item = queue[i]
            var sent = false
            if (item.type == ClipStore.TYPE_TEXT) {
                ic.commitText(if (prevWasText) "\n" + item.text else item.text, 1)
                prevWasText = true
                sent = true
            } else {
                val f = clipStore.imageFile(item)
                if (f != null && f.exists() && commitImageDirect(f)) sent = true else failedImages++
                prevWasText = false
            }
            if (i + 1 < queue.size) handler.postDelayed({ step(i + 1) }, if (sent) stepDelay else 0L) else finish()
        }
        step(0)
    }

    // ----- Tìm kiếm Clipboard: dùng chính bàn phím gõ để nhập từ khóa -----
    private fun startSearch() {
        searchActive = true
        searchText = clipPanel?.query ?: ""
        finishComposing()
        panelClipHost.visibility = View.GONE
        panelType.visibility = View.VISIBLE
        searchBar.visibility = View.VISIBLE
        updateSearchBar()
    }

    private fun endSearch() {
        if (!searchActive) return
        searchActive = false
        searchBar.visibility = View.GONE
        panelType.visibility = View.GONE
        panelClipHost.visibility = View.VISIBLE
        clipPanel?.setQuery(searchText)
    }

    private fun updateSearchBar() {
        tvSearch.hint = "Nhập từ khóa tìm kiếm…"
        tvSearch.text = searchText
    }

    /** Bàn phím gõ đang được dùng (chế độ GÕ PHÍM hoặc đang nhập từ khóa tìm kiếm). */
    private fun typing() = mode == 1 || searchActive

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        telex.reset() // sang ô nhập khác: bỏ từ đang gõ dở
    }

    /** Người dùng chạm sang chỗ khác trong ô nhập → kết thúc từ đang gõ dở. */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (!telex.isEmpty && SystemClock.elapsedRealtime() - lastComposeAt > 150 &&
            (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
        ) {
            telex.reset()
            currentInputConnection?.finishComposingText()
        }
    }

    // =====================================================================
    //  GIAO DIỆN
    // =====================================================================
    override fun onCreateInputView(): View {
        val v = layoutInflater.inflate(R.layout.keyboard_view, null)
        root = v
        loadTheme()
        previewView = v.findViewById(R.id.previewView)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        overlay = v.findViewById(R.id.overlay)
        cameraBox = v.findViewById(R.id.cameraBox)
        tvStatus = v.findViewById(R.id.tvStatus)
        tvHint = v.findViewById(R.id.tvHint)
        panelClipHost = v.findViewById(R.id.panelClip)
        searchBar = v.findViewById(R.id.searchBar)
        tvSearch = v.findViewById(R.id.tvSearch)
        panelScan = v.findViewById(R.id.panelScan)
        panelType = v.findViewById(R.id.panelType)
        btnArea = v.findViewById(R.id.btnArea)
        topArea = v.findViewById(R.id.topArea)
        toolsBox = v.findViewById(R.id.toolsBox)
        toolsRow = v.findViewById(R.id.toolsRow)
        creditView = v.findViewById(R.id.creditView)

        cameraBox.layoutParams.height = dp(prefs.cameraHeightDp.coerceIn(120, 420))

        // --- Hàng trên, hàng dưới và nhóm Công cụ: dựng theo bố cục người dùng chọn ---
        rebuildScanButtons()

        setupDragHandle(v.findViewById(R.id.dragHandle))
        buildTypePanel()
        v.findViewById<View>(R.id.btnSearchDone).setOnClickListener { endSearch() }
        clipPanel?.release()
        clipPanel = ClipPanel(this, clipStore, object : ClipPanel.Callbacks {
            override fun onPaste(item: ClipItem) = pasteClip(item)
            override fun onPasteAll(items: List<ClipItem>) = pasteClips(items)
            override fun onSearch() = startSearch()
            override fun onSearchCleared() { searchText = "" }
            override fun onView(item: ClipItem) = showClipViewer(item)
        }).also {
            it.setListHeight(dp(prefs.clipHeightDp.coerceIn(CLIP_MIN, CLIP_MAX)))
            panelClipHost.removeAllViews()
            panelClipHost.addView(it.view)
        }
        Fonts.apply(v)
        fxKb?.detach(); fxScan?.detach(); fxTop?.detach(); fxTools?.detach()
        fxKb = FxDrawable(panelType) { keyViews }.also { it.attach() }
        fxScan = FxDrawable(btnArea) { scanButtonViews }.also { it.attach() }
        fxTop = FxDrawable(topArea) { topViews }.also { it.attach() }
        fxTools = FxDrawable(toolsBox) { toolViews }.also { it.attach() }
        applyKbTheme()   // sau Fonts.apply để font theo giao diện không bị ghi đè
        applyScanTheme()
        refreshCredit()
        applyScreenFlags()
        setMode(mode)
        updateUi()
        return v
    }

    /**
     * Dựng lại toàn bộ các nút (hàng trên, hàng dưới, nhóm Công cụ) theo bố cục người dùng đã lưu
     * (khu vực, thứ tự, kích thước, căn lề, ảnh PNG riêng) rồi gắn lại thao tác cho từng nút.
     */
    private fun rebuildScanButtons() {
        val json = prefs.scanLayoutJson
        val lay = ScanLayout.fromJson(json)
        ScanButtons.forgetBitmaps()
        val m = ScanButtons.makeAll(this, lay)
        val custom = HashSet<String>()
        for ((k, b) in m) {
            val hasImg = ScanButtons.hasCustomImage(this, lay, k)
            if (hasImg) custom.add(k)
            ThemeRender.styleButton(
                this, b, styleScan,
                if (ScanLayout.isToggle(k)) ThemeRender.Kind.TOGGLE else ThemeRender.Kind.NORMAL,
                hasImg
            )
        }
        btnMap = m
        customKeys = custom
        bindButtons(m)
        topArea.removeAllViews()
        topArea.addView(ScanButtons.buildRows(this, lay, m, lay.top, lay.perRowTop))
        btnArea.removeAllViews()
        btnArea.addView(ScanButtons.buildRows(this, lay, m, lay.bottom, lay.perRowBottom))
        topViews = lay.top.mapNotNull { m[it] }
        scanButtonViews = lay.bottom.mapNotNull { m[it] }
        toolViews = lay.tools.mapNotNull { m[it] }
        renderTools()
        layoutSig = json
        updateUi()
    }

    /** Nút đang nằm trong nhóm Công cụ: dùng xong thì nhóm tự thu lại. */
    private fun afterToolUse(key: String) {
        if (btnMap[key]?.parent === toolsRow) closeFlyoutSoon()
    }

    private fun bindButtons(m: Map<String, ImageView>) {
        fun hint(k: String) = ScanLayout.HINTS[k] ?: ""
        bind(m.getValue("tools"), hint("tools")) { toggleFlyout() }
        bind(m.getValue("scan"), hint("scan")) { setMode(0) }
        bind(m.getValue("type"), hint("type")) { setMode(1) }
        bind(m.getValue("clip"), hint("clip")) { setMode(2) }
        bind(m.getValue("settings"), hint("settings")) { openSettings() }
        bind(m.getValue("switch"), hint("switch")) { showPicker() }
        bind(m.getValue("hide"), hint("hide")) { requestHideSelf(0) }
        bind(m.getValue("cam"), hint("cam")) {
            if (scanning) stopCamera() else startCamera(); afterToolUse("cam")
        }
        bind(m.getValue("flash"), hint("flash")) { toggleTorch(); afterToolUse("flash") }
        bind(m.getValue("keepOn"), hint("keepOn")) {
            prefs.keepScreenOn = !prefs.keepScreenOn; applyScreenFlags()
            say(if (prefs.keepScreenOn) "Luôn sáng: BẬT" else "Luôn sáng: TẮT"); afterToolUse("keepOn")
        }
        bind(m.getValue("dim"), hint("dim")) {
            prefs.dimScreen = !prefs.dimScreen; applyScreenFlags()
            say(if (prefs.dimScreen) "Giảm sáng: ${prefs.dimPercent}%" else "Giảm sáng: TẮT"); afterToolUse("dim")
        }
        bindRepeat(m.getValue("del"), false) { handleDelete() }
        bind(m.getValue("enter"), hint("enter")) { finishComposing(); sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }
        bind(m.getValue("autoEnter"), hint("autoEnter")) {
            prefs.sendEnter = !prefs.sendEnter; updateUi()
            say(if (prefs.sendEnter) "Tự Enter: BẬT" else "Tự Enter: TẮT"); afterToolUse("autoEnter")
        }
        bind(m.getValue("sound"), hint("sound")) {
            prefs.keySound = !prefs.keySound
            if (prefs.keySound) keyClick()
            updateUi()
            say(if (prefs.keySound) "Âm thanh phím: BẬT" else "Âm thanh phím: TẮT"); afterToolUse("sound")
        }
        bind(m.getValue("soundPick"), hint("soundPick")) { openSoundPicker() }
    }

    private fun fxOf(v: View): FxDrawable? = when {
        fxKb?.owns(v) == true -> fxKb
        fxScan?.owns(v) == true -> fxScan
        fxTop?.owns(v) == true -> fxTop
        fxTools?.owns(v) == true -> fxTools
        else -> null
    }

    // ----- Nhóm Công cụ: danh sách nút, hoặc dải chọn nhanh âm thanh -----
    private fun renderTools() {
        if (!::toolsRow.isInitialized) return
        toolsRow.removeAllViews()
        if (toolsMode == 1) { renderSoundPicker(); return }
        for (b in toolViews) {
            (b.parent as? ViewGroup)?.removeView(b)
            toolsRow.addView(b, LinearLayout.LayoutParams(b.layoutParams.width, b.layoutParams.height).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        if (toolViews.isEmpty()) {
            toolsRow.addView(TextView(this).apply {
                text = "Nhóm Công cụ đang trống. Thêm nút trong Tùy chỉnh nút Scan."
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@ScannerKeyboardService, R.color.muted))
                setPadding(dp(8), dp(10), dp(8), dp(10))
            })
        }
    }

    private fun openSoundPicker() {
        if (flyoutOpen) { toolsMode = 1; renderTools() } else openFlyout(1)
    }

    /** Dải chọn nhanh âm thanh: nút quay lại và 8 kiểu âm thanh đánh số, chạm để chọn và nghe thử ngay. */
    private fun renderSoundPicker() {
        fun chip(label: String, on: Boolean, click: () -> Unit): TextView = TextView(this).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@ScannerKeyboardService, R.color.text))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_toggle)
            isSelected = on
            isClickable = true
            setOnClickListener { click() }
        }
        val lp = { LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(3), 0, dp(3), 0) } }
        toolsRow.addView(chip("‹", false) { toolsMode = 0; renderTools() }, lp())
        for (i in KeySound.NAMES.indices) {
            toolsRow.addView(chip("${i + 1}", prefs.keySoundType == i && prefs.keySound) {
                prefs.keySound = true
                prefs.keySoundType = i
                keyClick()
                say("Âm thanh phím: ${KeySound.NAMES[i]}")
                updateUi()
                renderTools()
            }, lp())
        }
    }

    /** Nút thường: chạm thực hiện, giữ lâu hiện hướng dẫn, có hiệu ứng nhún nhẹ. */
    private fun bind(view: View, hint: String, action: () -> Unit) {
        view.setOnClickListener { action() }
        view.setOnLongClickListener { showHint(hint); true }
        pressEffect(view)
    }

    /** Chạm xuống: hiệu ứng theo giao diện (nhún, gợn sóng, hạt sáng...) hoặc nhún mặc định nếu không thuộc bảng nào. */
    private fun touchDown(v: View, e: MotionEvent) {
        val fx = fxOf(v)
        if (fx != null) fx.down(v, e.x, e.y)
        else v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(70).start()
    }

    private fun touchUp(v: View) {
        val fx = fxOf(v)
        if (fx != null) fx.up(v)
        else v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
    }

    private fun pressEffect(view: View, sound: Boolean = false) {
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (sound) keyClick()
                    keyHaptic()
                    touchDown(v, e)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchUp(v)
            }
            false
        }
    }

    /** Nút giữ để lặp lại (phím xóa). */
    private fun bindRepeat(view: View, sound: Boolean, action: () -> Unit) {
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (sound) keyClick()
                    keyHaptic()
                    v.isPressed = true
                    touchDown(v, e)
                    action()
                    repeatAction = action
                    repeatStart = SystemClock.uptimeMillis()
                    repeatCount = 0
                    handler.removeCallbacks(repeatRunnable)
                    handler.postDelayed(repeatRunnable, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    touchUp(v)
                    handler.removeCallbacks(repeatRunnable)
                    repeatAction = null
                }
            }
            true
        }
    }

    private fun showHint(text: String) {
        if (!::tvHint.isInitialized) return
        if (mode != 0) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); return }   // khung camera chỉ hiện ở chế độ Quét
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

    private fun openFlyout(startMode: Int = 0) {
        toolsMode = startMode
        flyoutOpen = true
        renderTools()
        toolsBox.animate().cancel()
        toolsBox.visibility = View.VISIBLE
        toolsBox.pivotX = 0f
        toolsBox.pivotY = 0f
        toolsBox.alpha = 0f
        toolsBox.translationY = -dp(10).toFloat()
        toolsBox.scaleX = 0.9f
        toolsBox.scaleY = 0.9f
        toolsBox.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(190).setInterpolator(DecelerateInterpolator()).start()
        for (i in 0 until toolsRow.childCount) {
            val item = toolsRow.getChildAt(i)
            item.alpha = 0f
            item.animate().alpha(1f).setStartDelay(35L * i).setDuration(160).start()
        }
        updateUi()
    }

    private fun closeFlyout() {
        if (!flyoutOpen) return
        flyoutOpen = false
        toolsBox.animate().cancel()
        toolsBox.animate().alpha(0f).translationY(-dp(8).toFloat()).scaleX(0.92f).scaleY(0.92f)
            .setDuration(150).withEndAction { if (!flyoutOpen) { toolsBox.visibility = View.GONE; toolsMode = 0 } }.start()
        updateUi()
    }

    private fun closeFlyoutSoon() { handler.postDelayed({ closeFlyout() }, 260) }

    /**
     * Thanh kéo trên cùng: kéo lên = cao hơn, kéo xuống = thấp hơn.
     * Chế độ QUÉT chỉnh chiều cao khung camera; chế độ GÕ PHÍM chỉnh chiều cao hàng phím.
     */
    private fun setupDragHandle(handle: View) {
        var startY = 0f
        var startCam = 0
        var startRow = 0
        var startClip = 0
        handle.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    startY = e.rawY
                    startCam = cameraBox.height
                    startRow = rowHeightPx()
                    startClip = clipPanel?.listHeight ?: dp(230)
                    if (mode == 0) showHint("Kéo lên để tăng, kéo xuống để giảm chiều cao khung quét.")
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = startY - e.rawY
                    when {
                        mode == 0 -> {
                            cameraBox.layoutParams.height = (startCam + dy).toInt().coerceIn(dp(120), dp(420))
                            cameraBox.requestLayout()
                        }
                        typing() -> {
                            // 5 hàng phím: chia đều độ chênh cho từng hàng
                            val h = (startRow + dy / 5f).toInt().coerceIn(dp(KEY_ROW_MIN), dp(KEY_ROW_MAX))
                            for (i in 0 until panelType.childCount) panelType.getChildAt(i).layoutParams.height = h
                            panelType.requestLayout()
                        }
                        else -> clipPanel?.setListHeight((startClip + dy).toInt().coerceIn(dp(CLIP_MIN), dp(CLIP_MAX)))
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val density = resources.displayMetrics.density
                    when {
                        mode == 0 -> prefs.cameraHeightDp = (cameraBox.height / density).toInt().coerceIn(120, 420)
                        typing() -> if (panelType.childCount > 0) {
                            val px = panelType.getChildAt(0).layoutParams.height
                            prefs.keyRowDp = (px / density / autoRowScale()).toInt().coerceIn(KEY_ROW_MIN, KEY_ROW_MAX)
                        }
                        else -> clipPanel?.let { prefs.clipHeightDp = (it.listHeight / density).toInt().coerceIn(CLIP_MIN, CLIP_MAX) }
                    }
                }
            }
            true
        }
    }

    private fun openSettings() {
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            requestHideSelf(0)
        } catch (_: Throwable) { say("Không mở được màn hình cài đặt.") }
    }

    private fun updateUi() {
        if (btnMap.isEmpty()) return
        fun sel(k: String, on: Boolean) { btnMap[k]?.isSelected = on }
        sel("cam", scanning)
        sel("flash", torchOn)
        sel("keepOn", prefs.keepScreenOn)
        sel("dim", prefs.dimScreen)
        sel("autoEnter", prefs.sendEnter)
        sel("sound", prefs.keySound)
        sel("tools", flyoutOpen)
        sel("scan", mode == 0)
        sel("type", mode == 1)
        sel("clip", mode == 2)
        if ("cam" !in customKeys) btnMap["cam"]?.setImageResource(if (scanning) R.drawable.ic_camera else R.drawable.ic_camera_off)
        if ("sound" !in customKeys) btnMap["sound"]?.setImageResource(if (prefs.keySound) R.drawable.ic_volume else R.drawable.ic_volume_off)
        if ("tools" !in customKeys) btnMap["tools"]?.setImageResource(if (flyoutOpen) R.drawable.ic_close else R.drawable.ic_tune)
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
        if (searchActive) { searchActive = false; searchBar.visibility = View.GONE }
        if (mode == 2 && m != 2) clipPanel?.endSelect()
        finishComposing()
        mode = m
        panelScan.visibility = if (m == 0) View.VISIBLE else View.GONE
        panelType.visibility = if (m == 1) View.VISIBLE else View.GONE
        panelClipHost.visibility = if (m == 2) View.VISIBLE else View.GONE
        closeFlyout()
        if (m != 0) stopCamera() else if (prefs.autoStart && !scanning) startCamera()
        if (m == 2) clipPanel?.onShown()
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
        val themeChanged = loadTheme()
        if (root != null && (themeChanged || prefs.scanLayoutJson != layoutSig)) rebuildScanButtons()
        if (root != null && themeChanged) { buildTypePanel(); applyKbTheme(); applyScanTheme() }
        refreshCredit()
        if (prefs.keySound) sounds()
        ingestClipboard(true) // lấy nội dung đã sao chép trong lúc bàn phím chưa chạy
        applyScreenFlags(true)
        if (mode == 0 && prefs.autoStart && !scanning && root != null) startCamera()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        windowVisible = false
        telex.reset()
        if (::toolsBox.isInitialized) { flyoutOpen = false; toolsMode = 0; toolsBox.animate().cancel(); toolsBox.visibility = View.GONE }
        clipViewer?.dismiss()
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
        soundHolder?.release()
        try { clipMgr?.removePrimaryClipChangedListener(clipListener) } catch (_: Throwable) { }
        clipPanel?.release()
        clipViewer?.dismiss()
        ioExec.shutdown()
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

        finishComposing()
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
            Haptics.pulse(this, vibMs.toLong())
        }
    }

    // =====================================================================
    //  BÀN PHÍM GÕ (chữ / số / ký hiệu)
    // =====================================================================
    private enum class KS { NORMAL, SPECIAL, ACCENT }

    /** Nạp giao diện đang dùng. Trả về true nếu khác lần trước (cần dựng lại các bảng). */
    private fun loadTheme(): Boolean {
        val raw = ThemeStore.raw(this)
        if (raw == themeRaw) return false
        themeRaw = raw
        val t = ThemeStore.parse(raw).current()
        styleScan = t?.scan ?: PanelStyle()
        styleKb = t?.kb ?: PanelStyle()
        spaceStyle = t?.space ?: SpaceStyle()
        SpaceRender.forget()
        return true
    }

    /** Nền phía sau các phím + font / màu chữ của bảng Bàn phím. */
    /** Áp bố cục (chiều rộng, căn lề, một tay, nâng đáy) của giao diện lên một bảng. */
    private fun applyLayoutTo(panel: View, st: PanelStyle) {
        val lp = panel.layoutParams as? LinearLayout.LayoutParams ?: return
        val full = (root?.width ?: 0).let { if (it > 0) it else resources.displayMetrics.widthPixels }
        ThemeRender.applyPanelLayout(lp, st, full, resources.displayMetrics.density)
        panel.layoutParams = lp
    }

    /** Chiều cao một hàng phím: giá trị người dùng kéo chỉnh, có hệ số tự tối ưu theo cỡ màn hình nếu bật. */
    private fun autoRowScale(): Float {
        if (!styleKb.layoutAuto) return 1f
        val m = resources.displayMetrics
        return (minOf(m.widthPixels, m.heightPixels) / m.density / 360f).coerceIn(0.9f, 1.15f)
    }

    private fun rowHeightPx(): Int =
        dp((prefs.keyRowDp.coerceIn(KEY_ROW_MIN, KEY_ROW_MAX) * autoRowScale()).toInt())

    private fun applyKbTheme() {
        fxKb?.style = styleKb
        applyLayoutTo(panelType, styleKb)
        panelType.clipChildren = false
        panelType.background = ThemeRender.panelBackground(this, styleKb)
        applyKbText()
    }

    private fun applyKbText() {
        for (k in keyViews) if (k is TextView) {
            if (k === spaceView) SpaceRender.styleLabel(k, styleKb, spaceStyle, vn) else ThemeRender.styleText(k, styleKb)
        }
    }

    /** Nền của bảng Scan + font của dòng trạng thái / gợi ý (bảng Scan không có chữ nào khác). */
    private fun applyScanTheme() {
        fxScan?.style = styleScan
        fxTop?.style = styleScan
        fxTools?.style = styleScan
        applyLayoutTo(panelScan, styleScan)
        panelScan.background = ThemeRender.panelBackground(this, styleScan)
        if (!scanTextCaptured) {
            statusColor0 = tvStatus.currentTextColor
            hintColor0 = tvHint.currentTextColor
            scanTextCaptured = true
        }
        val custom = styleScan.textColor != PanelStyle().textColor
        for ((t, c0) in listOf(tvStatus to statusColor0, tvHint to hintColor0)) {
            Fonts.apply(t)                       // trả về font gốc trước khi áp font theo giao diện
            t.setTextColor(c0)
            t.setShadowLayer(0f, 0f, 0f, 0)
            ThemeRender.styleText(t, styleScan, custom)
        }
    }

    private fun buildTypePanel() {
        panelType.removeAllViews()
        keyViews.clear()
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
        addKey(b, if (vn) "VN" else "EN", 1.2f, KS.SPECIAL) { finishComposing(); vn = !vn; buildTypePanel() }
        addKey(b, ",", 1f, KS.NORMAL) { type(",") }
        addSpaceKey(b)                                      // phím cách: vuốt ngang để di chuyển con trỏ
        addKey(b, ".", 1f, KS.NORMAL) { type(".") }
        addIconKey(b, R.drawable.ic_enter, 1.5f, KS.ACCENT) {
            if (searchActive) endSearch() else { finishComposing(); sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) }
        }
        panelType.addView(b)

        Fonts.apply(panelType)
        applyKbText()
        refreshShift()
    }

    private fun newRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeightPx())
        setPadding(0, dp(styleKb.gapDp * 2 / 3), 0, dp(styleKb.gapDp * 2 / 3))
        clipChildren = false
        clipToPadding = false
    }

    private fun charRow(chars: String, letters: Boolean, pad: Boolean): LinearLayout {
        val r = newRow()
        if (pad) r.addView(Space(this), LinearLayout.LayoutParams(0, 1, 0.5f))
        chars.forEach { addChar(r, it, letters) }
        if (pad) r.addView(Space(this), LinearLayout.LayoutParams(0, 1, 0.5f))
        return r
    }

    private fun kindOf(style: KS) = when (style) {
        KS.NORMAL -> ThemeRender.Kind.NORMAL
        KS.SPECIAL -> ThemeRender.Kind.SPECIAL
        KS.ACCENT -> ThemeRender.Kind.ACCENT
    }

    private fun place(row: LinearLayout, v: View, weight: Float) {
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
        lp.setMargins(dp(styleKb.gapDp), 0, dp(styleKb.gapDp), 0)
        row.addView(v, lp)
    }

    private fun addKey(row: LinearLayout, label: String, weight: Float, style: KS, onClick: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setTextColor(ContextCompat.getColor(this, R.color.text))
        tv.textSize = (if (label.length > 1) 14f else 19f) * styleKb.fontScale / 100f
        tv.isClickable = true
        tv.background = ThemeRender.keyBg(this, styleKb, kindOf(style))
        ThemeRender.elevate(tv, styleKb)
        keyViews.add(tv)
        place(row, tv, weight)
        tv.setOnClickListener { onClick() }
        pressEffect(tv, true)
        return tv
    }

    /** Phím dùng biểu tượng (kích thước icon luôn 24dp, giống hàng nút ở màn hình quét). */
    private fun addIconKey(row: LinearLayout, iconRes: Int, weight: Float, style: KS, onClick: () -> Unit): ImageView {
        val iv = ImageView(this)
        iv.setImageResource(iconRes)
        ThemeRender.styleIcon(iv, styleKb)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setPadding(dp(13), dp(13), dp(13), dp(13))
        iv.isClickable = true
        iv.background = ThemeRender.keyBg(this, styleKb, kindOf(style))
        ThemeRender.elevate(iv, styleKb)
        keyViews.add(iv)
        place(row, iv, weight)
        iv.setOnClickListener { onClick() }
        pressEffect(iv, true)
        return iv
    }

    private fun addChar(row: LinearLayout, c: Char, letter: Boolean) {
        val label = if (letter && shift > 0) c.uppercaseChar().toString() else c.toString()
        val key = addKey(row, label, 1f, KS.NORMAL) { typeChar(c, letter) }
        if (letter) letterKeys.add(key to c)
    }

    /** Phím cách: kiểu riêng theo giao diện; chạm để gõ dấu cách, vuốt ngang để di chuyển con trỏ. */
    private fun addSpaceKey(row: LinearLayout) {
        val sp = spaceStyle
        val tv = TextView(this)
        tv.gravity = Gravity.CENTER
        tv.setSingleLine()
        tv.setTextColor(ContextCompat.getColor(this, R.color.text))
        tv.textSize = 14f * styleKb.fontScale / 100f
        tv.isClickable = true
        tv.background = SpaceRender.background(this, styleKb, sp)
        ThemeRender.elevate(tv, SpaceRender.shapeStyle(styleKb, sp))
        spaceView = tv
        keyViews.add(tv)
        place(row, tv, sp.weight / 10f)
        bindSpace(tv)
    }

    /** Dịch con trỏ sang trái / phải một ký tự (kết thúc chữ Telex đang gõ dở trước). */
    private fun moveCursor(dir: Int) {
        finishComposing()
        sendDownUpKeyEvents(if (dir > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
        if (spaceStyle.swipeHaptic) keyHaptic()
    }

    private fun bindSpace(v: View) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var lastX = 0f
        var swiping = false
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    lastX = e.x
                    swiping = false
                    keyClick()
                    keyHaptic()
                    view.isPressed = true
                    view.drawableHotspotChanged(e.x, e.y)
                    touchDown(view, e)
                }
                MotionEvent.ACTION_MOVE -> {
                    val sp = spaceStyle
                    if (sp.swipe && !searchActive) {
                        if (!swiping && abs(e.x - downX) > slop * 1.5f) { swiping = true; lastX = downX }
                        if (swiping) {
                            val step = dp(sp.swipeStep.coerceIn(6, 40)).toFloat()
                            while (e.x - lastX >= step) { moveCursor(1); lastX += step }
                            while (lastX - e.x >= step) { moveCursor(-1); lastX -= step }
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    touchUp(view)
                    val inside = e.x >= 0f && e.x <= view.width && e.y >= 0f && e.y <= view.height
                    if (!swiping && inside) type(" ")
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    touchUp(view)
                }
            }
            true
        }
    }

    private fun addRepeatKey(row: LinearLayout, weight: Float) {
        val iv = addIconKey(row, R.drawable.ic_backspace, weight, KS.SPECIAL) { }
        bindRepeat(iv, true) { handleDelete() }
    }

    /** Chèn văn bản thường (kết thúc từ Telex đang gõ dở trước). */
    private fun type(s: String) {
        if (searchActive) { searchText += s; updateSearchBar(); return }
        finishComposing()
        currentInputConnection?.commitText(s, 1)
    }

    private fun typeChar(c: Char, letter: Boolean) {
        val ch = if (letter && shift > 0) c.uppercaseChar() else c
        if (vn && letter && !searchActive) telexKey(ch) else type(ch.toString())
        if (letter && shift == 1) { shift = 0; refreshShift() }
    }

    private fun telexKey(ch: Char) {
        val ic = currentInputConnection ?: return
        telex.push(ch)
        ic.setComposingText(telex.text(), 1)
        lastComposeAt = SystemClock.elapsedRealtime()
    }

    /** Chốt từ đang gõ dở (giữ nguyên chữ đã hiện). */
    private fun finishComposing() {
        if (!telex.isEmpty) {
            telex.reset()
            currentInputConnection?.finishComposingText()
        }
    }

    /** Xóa: nếu đang gõ dở một từ Telex thì bỏ phím cuối của từ đó, ngược lại xóa ký tự như bình thường. */
    private fun handleDelete() {
        if (searchActive) { searchText = searchText.dropLast(1); updateSearchBar(); return }
        if (!telex.isEmpty) {
            telex.backspace()
            currentInputConnection?.setComposingText(if (telex.isEmpty) "" else telex.text(), 1)
            lastComposeAt = SystemClock.elapsedRealtime()
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
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
