package com.inventory.scannerkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.File

/**
 * Xuất / nhập giao diện và cài đặt để chia sẻ giữa các thiết bị.
 * Hai nhóm tách riêng (Giao diện, Cài đặt); mỗi nhóm có thể xuất / nhập bằng File (kèm ảnh) hoặc mã QR.
 */
class TransferActivity : Activity() {

    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()
    private fun col(id: Int) = ContextCompat.getColor(this, id)
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val main = Handler(Looper.getMainLooper())

    private var pendingKind = Transfer.KIND_THEME   // nhóm đang xuất / nhập (dùng khi chờ kết quả chọn file)

    private companion object {
        const val REQ_CREATE = 201
        const val REQ_OPEN = 203
        const val REQ_QR_SCAN = 204
        const val REQ_QR_IMAGE = 205
        const val MAX_FILE = 50 * 1024 * 1024
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
    }

    // ------------------------------------------------------------------ giao diện
    private fun buildUi(): View {
        val sv = ScrollView(this).apply { setBackgroundColor(col(R.color.bg)); isFillViewport = true }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        sv.addView(root, ViewGroup.LayoutParams(MATCH, WRAP))
        root.addView(TextView(this).apply {
            text = "Xuất / Nhập"; textSize = 20f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Chia sẻ giao diện và cài đặt giữa các thiết bị bằng File hoặc mã QR. Giao diện và Cài đặt được xuất / nhập riêng."
            textSize = 13f; setTextColor(col(R.color.muted)); setPadding(0, dp(4), 0, dp(4))
        })

        section(root, "GIAO DIỆN", Transfer.KIND_THEME,
            "Gồm các giao diện đã lưu: phím, nền, chữ, hiệu ứng và phím Space. File kèm cả ảnh nền / ảnh phím Space; mã QR gọn hơn nên không kèm ảnh.")
        section(root, "CÀI ĐẶT", Transfer.KIND_SETTINGS,
            "Gồm độ nhạy và tùy chọn quét mã, rung, âm thanh, kích thước, bố cục nút Scan và kiểu dòng \"Created by\". File kèm ảnh nút riêng; mã QR không kèm ảnh.")
        Fonts.apply(root)
        return sv
    }

    private fun section(root: LinearLayout, title: String, kind: String, desc: String) {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_card); setPadding(dp(16), dp(16), dp(16), dp(16)) }
        c.addView(TextView(this).apply {
            text = title; textSize = 12f; setTextColor(col(R.color.accent)); setTypeface(null, Typeface.BOLD); letterSpacing = 0.08f
        })
        c.addView(TextView(this).apply { text = desc; textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(4), 0, dp(4)) })
        c.addView(sub("XUẤT"))
        c.addView(row(btn("LƯU RA FILE", true) { exportFile(kind) }, btn("CHIA SẺ FILE", false) { shareFile(kind) }))
        c.addView(row(btn("HIỆN MÃ QR", false) { showQr(kind) }, null))
        c.addView(sub("NHẬP"))
        c.addView(row(btn("TỪ FILE", true) { importFile(kind) }, btn("QUÉT MÃ QR", false) { scanQr(kind) }))
        c.addView(row(btn("MÃ QR TỪ ẢNH", false) { importQrImages(kind) }, null))
        root.addView(c, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })
    }

    private fun sub(text: String) = TextView(this).apply {
        this.text = text; textSize = 11f; setTextColor(col(R.color.muted)); letterSpacing = 0.1f; setPadding(0, dp(12), 0, dp(2))
    }

    private fun btn(text: String, primary: Boolean, click: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD); setSingleLine()
        setBackgroundResource(if (primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_secondary)
        isClickable = true; setOnClickListener { click() }
    }

    private fun row(a: View, b: View?): LinearLayout {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r.addView(a, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(0, dp(4), dp(4), 0) })
        if (b != null) r.addView(b, LinearLayout.LayoutParams(0, dp(46), 1f).apply { setMargins(dp(4), dp(4), 0, 0) })
        else r.addView(View(this), LinearLayout.LayoutParams(0, dp(46), 1f))
        return r
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    // ------------------------------------------------------------------ xuất
    private fun bundleForFile(kind: String): ShareBundle? =
        if (kind == Transfer.KIND_THEME) {
            if (ThemeStore.load(this).themes.isEmpty()) { toast("Chưa có giao diện nào để xuất."); null }
            else Transfer.themeBundle(this, false, true)
        } else Transfer.settingsBundle(this, true)

    private fun exportFile(kind: String) {
        pendingKind = kind
        try {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, Transfer.fileName(kind)), REQ_CREATE
            )
        } catch (_: Throwable) { toast("Không mở được trình chọn nơi lưu file.") }
    }

    private fun shareFile(kind: String) {
        val b = bundleForFile(kind) ?: return
        try {
            val dir = File(cacheDir, "export").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, Transfer.fileName(kind))
            f.writeBytes(Transfer.toZip(b))
            val uri = FileProvider.getUriForFile(this, "$packageName.clipfiles", f)
            val send = Intent(Intent.ACTION_SEND).setType("application/octet-stream")
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(send, "Chia sẻ file"))
        } catch (_: Throwable) { toast("Không chia sẻ được file.") }
    }

    private fun showQr(kind: String) {
        if (kind == Transfer.KIND_THEME) {
            val st = ThemeStore.load(this)
            if (st.themes.isEmpty()) { toast("Chưa có giao diện nào để xuất."); return }
            AlertDialog.Builder(this).setTitle("Xuất bằng mã QR")
                .setItems(arrayOf("Chỉ giao diện đang dùng (ít mã nhất)", "Tất cả giao diện")) { _, which ->
                    presentQr(Transfer.themeBundle(this, which == 0, false))
                }.setNegativeButton("Hủy", null).show()
        } else presentQr(Transfer.settingsBundle(this, false))
    }

    /** Hiện lần lượt các mã QR (tự chuyển mã sau vài giây, có nút trước / sau). */
    private fun presentQr(b: ShareBundle) {
        val chunks = Transfer.toQrChunks(b)
        val size = (resources.displayMetrics.widthPixels * 0.78f).toInt().coerceAtMost(dp(360))
        val bitmaps = chunks.map { Transfer.qrBitmap(it, 720) }
        if (bitmaps.any { it == null }) { toast("Dữ liệu quá lớn để tạo mã QR. Hãy xuất bằng file."); return }

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(16), dp(12), dp(16), dp(4)) }
        val tvCount = TextView(this).apply { textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER }
        val iv = ImageView(this).apply { setBackgroundColor(Color.WHITE); setPadding(dp(8), dp(8), dp(8), dp(8)) }
        val hint = TextView(this).apply {
            textSize = 12f; setTextColor(col(R.color.muted)); gravity = Gravity.CENTER
            text = if (chunks.size > 1) "Dữ liệu được chia thành ${chunks.size} mã. Trên máy kia chọn QUÉT MÃ QR rồi quét lần lượt từng mã (thứ tự bất kỳ)."
            else "Trên máy kia chọn QUÉT MÃ QR rồi hướng camera vào mã này."
        }
        box.addView(tvCount)
        box.addView(iv, LinearLayout.LayoutParams(size, size).apply { topMargin = dp(8) })
        box.addView(hint, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })

        var idx = 0
        var running = true
        fun show(i: Int) {
            idx = (i + chunks.size) % chunks.size
            iv.setImageBitmap(bitmaps[idx])
            tvCount.text = "Mã ${idx + 1} / ${chunks.size}"
        }
        show(0)
        val auto = object : Runnable {
            override fun run() {
                if (!running) return
                if (chunks.size > 1) show(idx + 1)
                main.postDelayed(this, 3500L)
            }
        }
        main.postDelayed(auto, 3500L)

        val builder = AlertDialog.Builder(this).setView(box).setPositiveButton("Đóng", null)
        if (chunks.size > 1) builder.setNeutralButton("Mã tiếp ›", null).setNegativeButton("‹ Mã trước", null)
        val dlg = builder.create()
        dlg.setOnDismissListener { running = false; main.removeCallbacks(auto) }
        dlg.show()
        if (chunks.size > 1) {
            // Giữ hộp thoại mở khi bấm chuyển mã
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { show(idx + 1); main.removeCallbacks(auto); main.postDelayed(auto, 6000L) }
            dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { show(idx - 1); main.removeCallbacks(auto); main.postDelayed(auto, 6000L) }
        }
    }

    // ------------------------------------------------------------------ nhập
    private fun importFile(kind: String) {
        pendingKind = kind
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_OPEN)
        } catch (_: Throwable) { toast("Không mở được trình chọn file.") }
    }

    private fun scanQr(kind: String) {
        pendingKind = kind
        QrScanActivity.result = null
        startActivityForResult(Intent(this, QrScanActivity::class.java), REQ_QR_SCAN)
    }

    private fun importQrImages(kind: String) {
        pendingKind = kind
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), REQ_QR_IMAGE
            )
        } catch (_: Throwable) { toast("Không mở được thư viện ảnh.") }
    }

    private fun readAll(uri: Uri): ByteArray? = try {
        contentResolver.openInputStream(uri)?.use { ins ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_FILE) return null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    } catch (_: Throwable) { null }

    /** Đọc các ảnh chứa mã QR bằng ML Kit, ghép đủ bộ mã rồi nhập. */
    private fun readQrImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val found = ArrayList<QrPart>()
        var pending = uris.size
        toast("Đang đọc mã QR từ ảnh…")
        fun allRead() {
            scanner.close()
            val full = found.groupBy { it.id }.values.firstOrNull { g -> g.map { it.index }.toSet().size == g.first().total }
            val b = full?.let { Transfer.assemble(it.distinctBy { p -> p.index }) }
            if (b == null) toast("Không đọc đủ mã QR để nhập. Cần chọn đủ tất cả các ảnh mã.") else handleBundle(b)
        }
        for (u in uris) {
            try {
                scanner.process(InputImage.fromFilePath(this, u))
                    .addOnSuccessListener { list -> for (bc in list) bc.rawValue?.let { Transfer.parseQr(it) }?.let { found.add(it) } }
                    .addOnCompleteListener { pending--; if (pending == 0) allRead() }
            } catch (_: Throwable) {
                pending--
                if (pending == 0) allRead()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_CREATE -> {
                val uri = data?.data ?: return
                val b = bundleForFile(pendingKind) ?: return
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(Transfer.toZip(b)) }
                    toast("Đã lưu file.")
                } catch (_: Throwable) { toast("Không lưu được file.") }
            }
            REQ_OPEN -> {
                val uri = data?.data ?: return
                val bytes = readAll(uri)
                val b = bytes?.let { Transfer.readBundle(it) }
                if (b == null) toast("File không đúng định dạng hoặc quá lớn.") else handleBundle(b)
            }
            REQ_QR_SCAN -> QrScanActivity.result?.let { handleBundle(it); QrScanActivity.result = null }
            REQ_QR_IMAGE -> {
                val uris = ArrayList<Uri>()
                data?.clipData?.let { cd -> for (i in 0 until cd.itemCount) cd.getItemAt(i).uri?.let { uris.add(it) } }
                data?.data?.let { if (it !in uris) uris.add(it) }
                readQrImages(uris)
            }
        }
    }

    /** Hỏi xác nhận rồi áp dụng gói dữ liệu đã đọc được. */
    private fun handleBundle(b: ShareBundle) {
        if (b.kind != pendingKind) {
            toast(if (b.kind == Transfer.KIND_THEME) "Đây là dữ liệu GIAO DIỆN. Hãy nhập ở mục Giao diện."
            else "Đây là dữ liệu CÀI ĐẶT. Hãy nhập ở mục Cài đặt.")
            return
        }
        if (b.kind == Transfer.KIND_THEME) {
            val inc = ThemeStore.parse(b.json.optJSONObject("themes")?.toString())
            if (inc.themes.isEmpty()) { toast("Không có giao diện nào trong dữ liệu này."); return }
            val names = inc.themes.joinToString(", ") { it.name }
            val extra = if (b.images.isNotEmpty()) "\nKèm ${b.images.size} ảnh." else ""
            AlertDialog.Builder(this).setTitle("Nhập ${inc.themes.size} giao diện")
                .setMessage("$names$extra\n\nCác giao diện này được thêm vào danh sách, không ghi đè giao diện hiện có.")
                .setPositiveButton("Thêm và dùng ngay") { _, _ -> doApplyThemes(b, true) }
                .setNeutralButton("Chỉ thêm") { _, _ -> doApplyThemes(b, false) }
                .setNegativeButton("Hủy", null).show()
        } else {
            if (b.json.optJSONObject("prefs") == null) { toast("Không có cài đặt nào trong dữ liệu này."); return }
            AlertDialog.Builder(this).setTitle("Nhập cài đặt")
                .setMessage("Các cài đặt hiện tại sẽ được thay bằng cài đặt nhận được, gồm độ nhạy quét mã, rung, âm thanh, kích thước, bố cục nút Scan và kiểu dòng \"Created by\". Tiếp tục?")
                .setPositiveButton("Nhập") { _, _ ->
                    val n = Transfer.applySettings(this, b)
                    toast(if (n > 0) "Đã nhập $n mục cài đặt. Ẩn rồi mở lại bàn phím để áp dụng." else "Không có mục nào để nhập.")
                }
                .setNegativeButton("Hủy", null).show()
        }
    }

    private fun doApplyThemes(b: ShareBundle, activate: Boolean) {
        val n = Transfer.applyThemes(this, b, activate)
        toast(if (n > 0) "Đã thêm $n giao diện." + if (activate) " Đang dùng giao diện mới." else " Chọn trong Tùy chỉnh giao diện." else "Không thêm được giao diện nào.")
    }
}
