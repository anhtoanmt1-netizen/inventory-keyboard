package com.inventory.scannerkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Space
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/**
 * Màn hình "Giao diện bàn phím": thiết kế, xem trước trực tiếp, lưu / đặt tên / nhân bản / xóa / chuyển đổi
 * nhiều giao diện, và khôi phục mặc định. Có 3 phạm vi tùy chỉnh: bảng Scan, bảng Bàn phím, hoặc cả hai.
 */
class ThemeActivity : Activity() {

    private lateinit var store: ThemeStore
    private lateinit var prefs: Prefs
    private lateinit var work: KbTheme          // bản đang chỉnh (chưa chắc đã lưu)
    private lateinit var saved: KbTheme         // bản đã lưu của giao diện này (để biết có thay đổi hay chưa)

    private var scope = 2                        // 0 = Scan, 1 = Bàn phím, 2 = Cả hai
    private var previewTarget = 1                // 0 = xem bảng Scan, 1 = xem bảng Bàn phím
    private var category = 0

    private lateinit var previewHost: FrameLayout
    private lateinit var previewTabs: LinearLayout
    private lateinit var themeBox: LinearLayout
    private lateinit var catBox: LinearLayout
    private lateinit var controls: LinearLayout
    private lateinit var tvDirty: TextView

    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()
    private fun col(id: Int) = androidx.core.content.ContextCompat.getColor(this, id)

    private val CATEGORIES = Cats.NAMES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ThemeStore(this)
        prefs = Prefs(this)
        work = store.active()
        saved = work
        setContentView(buildUi())
        rebuildThemeBox()
        rebuildCategories()
        rebuildControls()
        rebuildPreview()
        updateDirty()
    }

    // ------------------------------------------------------------------ khung giao diện
    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.bg)) }

        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(6)) }
        head.addView(TextView(this).apply {
            text = "Giao diện bàn phím"; textSize = 18f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD)
        })
        tvDirty = TextView(this).apply {
            text = "● Có thay đổi chưa áp dụng"; textSize = 12f; setTextColor(col(R.color.accent)); setTypeface(null, Typeface.BOLD); visibility = View.GONE
        }
        head.addView(tvDirty)
        root.addView(head)

        // --- Live Preview: luôn ghim phía trên để vừa chỉnh vừa thấy ---
        previewTabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), 0, dp(12), dp(4)) }
        root.addView(previewTabs)
        previewHost = FrameLayout(this)
        root.addView(previewHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val sv = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24)) }
        sv.addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // --- Giao diện đã lưu ---
        val tc = card(body, "GIAO DIỆN CỦA BẠN")
        themeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tc.addView(themeBox)

        // --- Phạm vi ---
        val sc = card(body, "PHẠM VI TÙY CHỈNH")
        sc.addView(small("Chọn bảng sẽ được áp dụng các chỉnh sửa bên dưới."))
        chips(sc, listOf("Bảng Scan", "Bảng Bàn phím", "Cả hai"), { scope }) {
            scope = it
            if (it == 0) previewTarget = 0 else if (it == 1) previewTarget = 1
            rebuildControls(); rebuildPreview()
        }

        // --- Danh mục + điều khiển ---
        val cc = card(body, "TÙY CHỈNH")
        catBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(catBox) }
        cc.addView(hs)
        controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cc.addView(controls)

        body.addView(button("ÁP DỤNG GIAO DIỆN NÀY", true) { applyTheme() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(16) })
        body.addView(button("KHÔI PHỤC MẶC ĐỊNH (PHẠM VI ĐANG CHỌN)", false) { confirmReset() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })
        Fonts.apply(root)
        return root
    }

    // ------------------------------------------------------------------ tiện ích dựng giao diện
    private fun card(parent: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_card); setPadding(dp(16), dp(14), dp(16), dp(14)) }
        c.addView(TextView(this).apply {
            text = title; textSize = 12f; setTextColor(col(R.color.accent)); setTypeface(null, Typeface.BOLD); letterSpacing = 0.08f
        })
        parent.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        return c
    }

    private fun small(t: String) = TextView(this).apply { text = t; textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(4), 0, dp(2)) }

    private fun title(parent: LinearLayout, t: String) {
        parent.addView(TextView(this).apply { text = t; textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
    }

    private fun button(text: String, primary: Boolean, click: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        setBackgroundResource(if (primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_secondary)
        isClickable = true; setOnClickListener { click() }
    }

    private fun chip(text: String, selected: Boolean, click: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 12f; gravity = Gravity.CENTER; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD)
        setBackgroundResource(R.drawable.bg_toggle); isClickable = true; isSelected = selected
        setPadding(dp(12), 0, dp(12), 0); setOnClickListener { click() }
    }

    /** Hàng chip chọn một: các chip chia đều chiều ngang. */
    private fun chips(parent: LinearLayout, names: List<String>, current: () -> Int, onPick: (Int) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val views = ArrayList<TextView>()
        names.forEachIndexed { i, n ->
            val t = chip(n, i == current()) { views.forEachIndexed { k, v -> v.isSelected = k == i }; onPick(i) }
            views.add(t)
            row.addView(t, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
    }

    // ------------------------------------------------------------------ đọc / ghi thông số
    private fun cur(): StyleSet = if (scope == 0) work.scan else work.kb

    /** Ghi một thông số vào (các) bảng thuộc phạm vi đang chọn rồi cập nhật Live Preview ngay. */
    private fun edit(key: String, v: Any?) {
        work = work.copy(
            scan = if (scope != 1) work.scan.with(key, v) else work.scan,
            kb = if (scope != 0) work.kb.with(key, v) else work.kb
        )
        rebuildPreview(); updateDirty()
    }

    private fun updateDirty() { tvDirty.visibility = if (work != saved) View.VISIBLE else View.GONE }

    // ------------------------------------------------------------------ các ô điều khiển
    /** Thanh trượt + ô nhập số trực tiếp. */
    private fun valueRow(parent: LinearLayout, label: String, key: String, min: Int, max: Int, unit: String) {
        title(parent, label)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val sb = SeekBar(this).apply { this.max = max - min; progress = cur().int(key).coerceIn(min, max) - min }
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED; setSingleLine()
            setTextColor(col(R.color.text)); textSize = 14f; gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_input); setPadding(dp(6), dp(8), dp(6), dp(8))
            setText(cur().int(key).coerceIn(min, max).toString())
        }
        var guard = false
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser || guard) return
                guard = true; et.setText((p + min).toString()); guard = false
                edit(key, p + min)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (guard) return
                val v = s.toString().toIntOrNull() ?: return
                val c = v.coerceIn(min, max)
                guard = true; sb.progress = c - min; guard = false
                edit(key, c)
            }
        })
        row.addView(sb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(et, LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), 0, dp(4), 0) })
        row.addView(TextView(this).apply { text = unit; textSize = 12f; setTextColor(col(R.color.muted)) })
        parent.addView(row)
    }

    private fun toggleRow(parent: LinearLayout, label: String, desc: String?, key: String, rebuild: Boolean) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val tx = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tx.addView(TextView(this).apply { text = label; textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD) })
        if (desc != null) tx.addView(small(desc))
        row.addView(tx, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this).apply { isChecked = cur().bool(key) }
        sw.setOnCheckedChangeListener { _, on -> edit(key, on); if (rebuild) rebuildControls() }
        row.addView(sw)
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
    }

    private fun chipRow(parent: LinearLayout, label: String, key: String, names: List<String>, rebuild: Boolean = false) {
        title(parent, label)
        chips(parent, names, { cur().int(key) }) { edit(key, it); if (rebuild) rebuildControls() }
    }

    private fun colorRow(parent: LinearLayout, label: String, key: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply { text = label; textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = View(this)
        fun paint() { sw.background = GradientDrawable().apply { setColor(cur().int(key)); cornerRadius = dp(8).toFloat(); setStroke(dp(1), 0x66888888) } }
        paint()
        row.addView(sw, LinearLayout.LayoutParams(dp(56), dp(34)))
        row.isClickable = true
        row.setOnClickListener {
            ColorPickerDialog.show(this, label, cur().int(key)) { c -> edit(key, c); paint() }
        }
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
    }

    // ------------------------------------------------------------------ danh mục
    private fun rebuildCategories() {
        catBox.removeAllViews()
        CATEGORIES.forEachIndexed { i, n ->
            catBox.addView(chip(n, i == category) { category = i; rebuildCategories(); rebuildControls() },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { setMargins(dp(2), dp(8), dp(2), 0) })
        }
        Fonts.apply(catBox)
    }

    private fun rebuildControls() {
        controls.removeAllViews()
        controls.addView(small(when (scope) { 0 -> "Đang chỉnh: bảng Scan"; 1 -> "Đang chỉnh: bảng Bàn phím"; else -> "Đang chỉnh: cả hai bảng (giá trị hiển thị lấy từ bảng Bàn phím)" }))
        when (category) {
            0 -> keyControls(controls)
            1 -> backgroundControls(controls)
            2 -> fontControls(controls)
            3 -> layoutControls(controls)
            else -> Cats.build(this, category, controls, ControlKit(
                value = { l, k, a, b, u -> valueRow(controls, l, k, a, b, u) },
                toggle = { l, dsc, k, r -> toggleRow(controls, l, dsc, k, r) },
                chips = { l, k, n, r -> chipRow(controls, l, k, n, r) },
                color = { l, k -> colorRow(controls, l, k) },
                cur = { cur() }
            ))
        }
        Fonts.apply(controls)
    }

    private fun keyControls(p: LinearLayout) {
        colorRow(p, "Màu phím thường", "keyColor")
        colorRow(p, "Màu phím chức năng", "specialColor")
        colorRow(p, "Màu phím nhấn / được chọn", "accentColor")
        valueRow(p, "Độ bo góc", "corner", 0, 28, "dp")
        valueRow(p, "Chiều rộng phím", "keyW", 40, 100, "%")
        valueRow(p, "Chiều cao phím", "keyH", 40, 100, "%")
        valueRow(p, "Khoảng cách giữa các phím", "gap", 0, 16, "dp")
        valueRow(p, "Độ nổi của phím", "depth", 0, 8, "dp")
        toggleRow(p, "Bóng đổ", "Thêm lớp đế tối phía dưới phím.", "shadowOn", true)
        if (cur().bool("shadowOn")) valueRow(p, "Cường độ bóng", "shadow", 10, 100, "%")
        toggleRow(p, "Hiệu ứng nổi 3D", "Thân phím chuyển sáng ở trên, tối ở dưới.", "d3", false)
        toggleRow(p, "Đường viền", null, "borderOn", true)
        if (cur().bool("borderOn")) {
            valueRow(p, "Độ dày viền", "borderW", 1, 6, "dp")
            colorRow(p, "Màu viền", "borderColor")
        }
    }

    private fun backgroundControls(p: LinearLayout) {
        chipRow(p, "Kiểu hình nền", "bgType", listOf("Màu đơn", "Chuyển màu", "Hình ảnh"), true)
        val t = cur().int("bgType")
        p.addView(small("Hình nền chỉ hiện phía sau các phím, không che chữ và ký hiệu trên phím."))
        colorRow(p, if (t == 1) "Màu bắt đầu" else if (t == 2) "Màu nền dự phòng" else "Màu nền", "bgColor")
        if (t == 1) {
            colorRow(p, "Màu kết thúc", "bgColor2")
            valueRow(p, "Góc chuyển màu", "bgAngle", 0, 360, "°")
        }
        if (t == 2) {
            val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            btns.addView(button("CHỌN ẢNH TỪ THƯ VIỆN", true) { pickImage() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, 0, dp(4), 0) })
            if (cur().str("bgImage") != null) btns.addView(button("BỎ ẢNH", false) { edit("bgImage", null); rebuildControls() },
                LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(4), 0, 0, 0) })
            p.addView(btns, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            if (cur().str("bgImage") == null) p.addView(small("Chưa chọn ảnh. Đang dùng màu nền dự phòng."))
            else {
                chipRow(p, "Cách hiển thị", "bgFit", listOf("Phủ kín", "Vừa khung", "Kéo giãn"), true)
                if (cur().int("bgFit") != 2) {
                    valueRow(p, "Vị trí ngang (lấy nét)", "bgFocusX", 0, 100, "%")
                    valueRow(p, "Vị trí dọc (lấy nét)", "bgFocusY", 0, 100, "%")
                }
                valueRow(p, "Độ sáng", "bgBright", -100, 100, "")
                valueRow(p, "Độ tương phản", "bgContrast", 0, 200, "%")
                valueRow(p, "Độ mờ", "bgBlur", 0, 100, "")
            }
        }
    }

    private fun fontControls(p: LinearLayout) {
        title(p, "Kiểu chữ")
        val names = KbStyler.FONT_NAMES
        for (chunk in names.indices.chunked(4)) {
            chips(p, chunk.map { names[it] }, { cur().int("font") - chunk[0] }) { edit("font", chunk[0] + it); rebuildControls() }
        }
        valueRow(p, "Kích thước chữ", "textSp", 12, 28, "sp")
        valueRow(p, "Độ đậm / mảnh", "weight", 100, 900, "")
        p.addView(small("Độ đậm áp dụng đầy đủ với font Montserrat; các font khác phụ thuộc vào điện thoại."))
        colorRow(p, "Màu chữ", "textColor")
        chipRow(p, "Hiệu ứng chữ", "textFx", listOf("Không", "Đổ bóng", "Phát sáng", "Nổi"), true)
        if (cur().int("textFx") != 0) valueRow(p, "Độ nổi và bóng chữ", "fxStrength", 0, 100, "%")
        chipRow(p, "Cách hiển thị chữ chính", "caseMode", listOf("Theo Shift", "Luôn HOA", "Luôn thường"))
        valueRow(p, "Kích thước nhãn phụ (?123, VN, ABC)", "subPct", 60, 130, "%")
    }

    private fun layoutControls(p: LinearLayout) {
        p.addView(small("Chiều cao dưới đây áp dụng ngay cho mọi giao diện (cũng có thể kéo thanh trên cùng của bàn phím)."))
        globalSeek(p, "Chiều cao hàng phím", 36, 72, { prefs.keyRowDp }, "dp") { prefs.keyRowDp = it }
        globalSeek(p, "Chiều cao khung camera", 120, 420, { prefs.cameraHeightDp }, "dp") { prefs.cameraHeightDp = it }
        valueRow(p, "Chiều rộng bàn phím", "widthPct", 60, 100, "%")
        chipRow(p, "Vị trí / căn chỉnh", "align", listOf("Trái", "Giữa", "Phải"))
        chipRow(p, "Chế độ một tay", "oneHand", listOf("Tắt", "Tay trái", "Tay phải"))
        valueRow(p, "Nâng bàn phím lên khỏi mép dưới", "bottomDp", 0, 64, "dp")
    }

    /** Thanh trượt cho thông số dùng chung (không thuộc riêng giao diện nào). */
    private fun globalSeek(p: LinearLayout, label: String, min: Int, max: Int, get: () -> Int, unit: String, set: (Int) -> Unit) {
        val tv = TextView(this).apply { textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD) }
        fun show(v: Int) { tv.text = "$label: $v$unit" }
        show(get().coerceIn(min, max))
        p.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        p.addView(SeekBar(this).apply {
            this.max = max - min; progress = get().coerceIn(min, max) - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, pr: Int, fromUser: Boolean) { if (fromUser) { show(pr + min); set(pr + min); rebuildPreview() } }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
    }

    // ------------------------------------------------------------------ ảnh nền
    private fun pickImage() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 88)
        } catch (_: Throwable) { Toast.makeText(this, "Không mở được bộ chọn ảnh.", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != 88 || resultCode != RESULT_OK || uri == null) return
        val name = importBackground(uri)
        if (name == null) { Toast.makeText(this, "Không đọc được ảnh này.", Toast.LENGTH_SHORT).show(); return }
        edit("bgImage", name)
        edit("bgType", 2)
        rebuildControls()
    }

    /** Đọc ảnh, thu nhỏ còn tối đa 1280px để đỡ tốn bộ nhớ, lưu lại. */
    private fun importBackground(uri: Uri): String? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) null else {
            var s = 1
            while (o.outWidth / (s * 2) >= 1280 || o.outHeight / (s * 2) >= 1280) s *= 2
            val bmp: Bitmap? = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
            }
            if (bmp == null) null else {
                val name = "bg_${System.currentTimeMillis()}.jpg"
                FileOutputStream(File(ThemeImages.dir(this), name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                name
            }
        }
    } catch (_: Throwable) { null }

    // ------------------------------------------------------------------ Live Preview
    private fun rebuildPreview() {
        previewTabs.removeAllViews()
        if (scope == 2) {
            previewTabs.addView(small("Xem trước:"), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply { rightMargin = dp(8) })
            previewTabs.addView(chip("Bảng Scan", previewTarget == 0) { previewTarget = 0; rebuildPreview() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply { setMargins(dp(2), 0, dp(2), 0) })
            previewTabs.addView(chip("Bảng Bàn phím", previewTarget == 1) { previewTarget = 1; rebuildPreview() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply { setMargins(dp(2), 0, dp(2), 0) })
            Fonts.apply(previewTabs)
        }
        previewHost.removeAllViews()
        val s = if (previewTarget == 0) work.scan else work.kb
        val outer = FrameLayout(this)
        val board = FrameLayout(this)
        val backdrop = BackdropView(this).apply { setStyle(s) }
        board.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        board.addView(content, FrameLayout.LayoutParams(-1, -2))
        outer.addView(board, FrameLayout.LayoutParams(-1, -2))
        previewHost.addView(outer, FrameLayout.LayoutParams(-1, -2))
        KbStyler.applyBoardLayout(board, s)
        content.addView(previewTopRow(s))
        if (previewTarget == 0) buildScanPreview(content, s) else buildKeysPreview(content, s)
        Fonts.apply(content)
    }

    private fun previewTopRow(s: StyleSet): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(5), 0, dp(5), dp(3)) }
        listOf(R.drawable.ic_scan to (previewTarget == 0), R.drawable.ic_keyboard to (previewTarget == 1), R.drawable.ic_clipboard to false, R.drawable.ic_chevron_down to false)
            .forEachIndexed { i, (icon, sel) ->
                val iv = ImageView(this).apply { setImageResource(icon); setPadding(dp(8), dp(8), dp(8), dp(8)); isSelected = sel }
                KbStyler.styleIconButton(iv, s, if (i == 3) KbStyler.Kind.NORMAL else KbStyler.Kind.TOGGLE)
                row.addView(iv, LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
            }
        return row
    }

    private fun buildScanPreview(content: LinearLayout, s: StyleSet) {
        val cam = FrameLayout(this).apply {
            background = GradientDrawable().apply { setColor(0xFF10131C.toInt()); cornerRadius = dp(14).toFloat() }
        }
        cam.addView(TextView(this).apply { text = "Khung camera quét"; textSize = 12f; setTextColor(0x88FFFFFF.toInt()); gravity = Gravity.CENTER },
            FrameLayout.LayoutParams(-1, -1))
        content.addView(cam, LinearLayout.LayoutParams(-1, dp(84)).apply { setMargins(dp(6), 0, dp(6), dp(6)) })
        val lay = ScanLayout.fromJson(prefs.scanLayoutJson)
        content.addView(ScanButtons.buildRows(this, lay, ScanButtons.makeAll(this, lay, s)))
    }

    private fun buildKeysPreview(content: LinearLayout, s: StyleSet) {
        val rowH = dp(34)
        val scale = 0.68f
        val half = dp(s.int("gap")) / 2
        fun row(chars: String, pad: Boolean = false): LinearLayout {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(2), dp(1), dp(2), dp(1)) }
            if (pad) r.addView(Space(this), LinearLayout.LayoutParams(0, 1, 0.5f))
            chars.forEach { c -> r.addView(key(KbStyler.displayLabel(s, c, c.isLetter(), false), s, KbStyler.Kind.NORMAL, scale), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(half, 0, half, 0) }) }
            if (pad) r.addView(Space(this), LinearLayout.LayoutParams(0, 1, 0.5f))
            content.addView(r, LinearLayout.LayoutParams(-1, rowH))
            return r
        }
        row("1234567890"); row("qwertyuiop"); row("asdfghjkl", true)
        val r4 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(2), dp(1), dp(2), dp(1)) }
        r4.addView(iconKey(R.drawable.ic_shift, s, KbStyler.Kind.SPECIAL), LinearLayout.LayoutParams(0, -1, 1.5f).apply { setMargins(half, 0, half, 0) })
        "zxcvbnm".forEach { c -> r4.addView(key(KbStyler.displayLabel(s, c, true, false), s, KbStyler.Kind.NORMAL, scale), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(half, 0, half, 0) }) }
        r4.addView(iconKey(R.drawable.ic_backspace, s, KbStyler.Kind.SPECIAL), LinearLayout.LayoutParams(0, -1, 1.5f).apply { setMargins(half, 0, half, 0) })
        content.addView(r4, LinearLayout.LayoutParams(-1, rowH))
        val r5 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(2), dp(1), dp(2), dp(1)) }
        fun lp(w: Float) = LinearLayout.LayoutParams(0, -1, w).apply { setMargins(half, 0, half, 0) }
        r5.addView(key("?123", s, KbStyler.Kind.SPECIAL, scale), lp(1.5f))
        r5.addView(key("VN", s, KbStyler.Kind.SPECIAL, scale), lp(1.2f))
        r5.addView(iconKey(R.drawable.ic_globe, s, KbStyler.Kind.SPECIAL), lp(1.2f))
        r5.addView(key(",", s, KbStyler.Kind.NORMAL, scale), lp(1f))
        r5.addView(key("", s, KbStyler.Kind.NORMAL, scale), lp(3f))
        r5.addView(key(".", s, KbStyler.Kind.NORMAL, scale), lp(1f))
        r5.addView(iconKey(R.drawable.ic_enter, s, KbStyler.Kind.ACCENT), lp(1.5f))
        content.addView(r5, LinearLayout.LayoutParams(-1, rowH))
    }

    private fun key(label: String, s: StyleSet, kind: KbStyler.Kind, scale: Float) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER
        KbStyler.styleText(this, s, KbStyler.keyTextSize(s, label), scale)
        background = KbStyler.background(this@ThemeActivity, s, kind)
    }

    private fun iconKey(icon: Int, s: StyleSet, kind: KbStyler.Kind) = ImageView(this).apply {
        setImageResource(icon); setPadding(dp(9), dp(7), dp(9), dp(7)); scaleType = ImageView.ScaleType.FIT_CENTER
        KbStyler.styleIconButton(this, s, kind)
    }

    // ------------------------------------------------------------------ quản lý giao diện
    private fun rebuildThemeBox() {
        themeBox.removeAllViews()
        val all = store.all()
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        all.forEach { t ->
            val mark = if (t.id == store.activeId()) "★ " else ""
            row.addView(chip(mark + t.name, t.id == work.id) { switchTo(t.id) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { setMargins(dp(2), dp(8), dp(2), 0) })
        }
        hs.addView(row)
        themeBox.addView(hs)
        themeBox.addView(small("★ = giao diện đang dùng trên bàn phím. Chạm một giao diện để chỉnh sửa."))
        val acts = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun act(t: String, click: () -> Unit) = acts.addView(button(t, false, click), LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), dp(6), dp(2), 0) })
        act("+ Mới") { promptName("Giao diện mới", "Giao diện ${all.size}") { n -> createTheme(n, StyleSet(), StyleSet()) } }
        act("Nhân bản") { promptName("Nhân bản giao diện", work.name + " (bản sao)") { n -> createTheme(n, work.scan, work.kb) } }
        act("Đổi tên") { promptName("Đổi tên giao diện", work.name) { n -> work = work.copy(name = n); saveKeepingState(); rebuildThemeBox() } }
        act("Xóa") { confirmDelete() }
        themeBox.addView(acts)
        Fonts.apply(themeBox)
    }

    private fun promptName(title: String, initial: String, onOk: (String) -> Unit) {
        val et = EditText(this).apply { setText(initial); setSingleLine(); setSelection(initial.length); setPadding(dp(20), dp(16), dp(20), dp(8)) }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("OK") { _, _ ->
                val n = et.text.toString().trim().take(40)
                if (n.isNotEmpty()) onOk(n)
            }.show()
    }

    /** Lưu thay đổi tên (và các chỉnh sửa hiện có) mà không đổi giao diện đang dùng. */
    private fun saveKeepingState() {
        store.save(work)
        saved = work
        updateDirty()
    }

    private fun createTheme(name: String, scan: StyleSet, kb: StyleSet) {
        val t = KbTheme(store.newId(), name, scan, kb)
        store.save(t)
        work = t; saved = t
        rebuildThemeBox(); rebuildControls(); rebuildPreview(); updateDirty()
    }

    private fun switchTo(id: String) {
        if (id == work.id) return
        val go = {
            val t = store.get(id)
            if (t != null) {
                work = t; saved = t
                rebuildThemeBox(); rebuildControls(); rebuildPreview(); updateDirty()
            }
        }
        if (work == saved) { go(); return }
        AlertDialog.Builder(this)
            .setTitle("Có thay đổi chưa áp dụng")
            .setMessage("Chuyển sang giao diện khác sẽ bỏ các thay đổi chưa áp dụng của \"${work.name}\".")
            .setNegativeButton("Ở lại", null)
            .setNeutralButton("Bỏ thay đổi") { _, _ -> go() }
            .setPositiveButton("Áp dụng rồi chuyển") { _, _ -> applyTheme(); go() }
            .show()
    }

    private fun confirmDelete() {
        if (work.id == KbTheme.DEFAULT_ID) { Toast.makeText(this, "Không thể xóa giao diện Mặc định.", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Xóa giao diện?")
            .setMessage("Giao diện \"${work.name}\" sẽ bị xóa vĩnh viễn.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Xóa") { _, _ ->
                store.delete(work.id)
                work = store.active(); saved = work
                rebuildThemeBox(); rebuildControls(); rebuildPreview(); updateDirty()
            }.show()
    }

    private fun applyTheme() {
        store.save(work)
        store.setActive(work.id)
        saved = work
        store.cleanupFiles(work.usedFiles())
        rebuildThemeBox(); updateDirty()
        Toast.makeText(this, "Đã áp dụng giao diện \"${work.name}\". Mở lại bàn phím để thấy.", Toast.LENGTH_SHORT).show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this).setTitle("Khôi phục mặc định?")
            .setMessage("Các thông số giao diện của phạm vi đang chọn sẽ trở về ban đầu. Các giao diện khác và cài đặt khác của ứng dụng không bị ảnh hưởng. Bấm ÁP DỤNG để lưu.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Khôi phục") { _, _ ->
                work = work.copy(
                    scan = if (scope != 1) StyleSet() else work.scan,
                    kb = if (scope != 0) StyleSet() else work.kb
                )
                rebuildControls(); rebuildPreview(); updateDirty()
            }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (work == saved) { store.cleanupFiles(); super.onBackPressed(); return }
        AlertDialog.Builder(this)
            .setTitle("Có thay đổi chưa áp dụng")
            .setNegativeButton("Ở lại", null)
            .setNeutralButton("Bỏ thay đổi") { _, _ -> store.cleanupFiles(); finish() }
            .setPositiveButton("Áp dụng") { _, _ -> applyTheme(); finish() }
            .show()
    }
}

/** Bộ công cụ dựng ô điều khiển, để các danh mục mở rộng (hiệu ứng, ánh sáng) dùng chung. */
class ControlKit(
    val value: (label: String, key: String, min: Int, max: Int, unit: String) -> Unit,
    val toggle: (label: String, desc: String?, key: String, rebuild: Boolean) -> Unit,
    val chips: (label: String, key: String, names: List<String>, rebuild: Boolean) -> Unit,
    val color: (label: String, key: String) -> Unit,
    val cur: () -> StyleSet
)

/** Danh mục điều khiển mở rộng (đợt 4B). */
object Cats {
    val NAMES = listOf("Phím", "Nền", "Chữ", "Bố cục")
    fun build(a: Activity, category: Int, parent: LinearLayout, kit: ControlKit) { }
}
