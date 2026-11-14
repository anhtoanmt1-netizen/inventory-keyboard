package com.inventory.scannerkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Màn hình "Tùy chỉnh giao diện": chọn phạm vi (Scan / Bàn phím / Cả hai), chỉnh phím, nền, chữ với
 * xem trước trực tiếp, lưu nhiều giao diện (đặt tên, nhân bản, chuyển đổi, xóa), khôi phục mặc định.
 * Mọi thay đổi được lưu ngay; bàn phím dùng giao diện mới ở lần hiện kế tiếp.
 */
class ThemeActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var state: ThemeState

    private var scope = -1   // -1 chưa chọn, 0 bảng Scan, 1 bảng Bàn phím, 2 cả hai
    private var group = 0    // 0 phím, 1 nền, 2 chữ

    private lateinit var previewHost: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var tabsBox: LinearLayout
    private lateinit var editorBox: LinearLayout
    private lateinit var tvActive: TextView

    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()
    private fun col(id: Int) = ContextCompat.getColor(this, id)
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        var st = ThemeStore.load(this)
        if (st.themes.isEmpty()) {
            val t = KbTheme(ThemeStore.newId(), "Giao diện 1")
            st = ThemeState(t.id, listOf(t))
            ThemeStore.save(this, st)
        } else if (st.current()?.id != st.active) {
            st = st.copy(active = st.current()!!.id)
            ThemeStore.save(this, st)
        }
        state = st
        setContentView(buildUi())
        refreshAll()
    }

    private fun cur(): KbTheme = state.current()!!

    /** Giá trị hiển thị trên thanh trượt: bảng Scan thì lấy của Scan, còn lại lấy của Bàn phím. */
    private fun viewStyle(): PanelStyle = if (scope == 0) cur().scan else cur().kb

    /** Áp một thay đổi lên (các) bảng đang được chọn phạm vi, lưu ngay và cập nhật xem trước. */
    private fun edit(f: (PanelStyle) -> PanelStyle) {
        val t = cur()
        val nt = t.copy(
            scan = if (scope == 0 || scope == 2) f(t.scan) else t.scan,
            kb = if (scope == 1 || scope == 2) f(t.kb) else t.kb
        )
        state = state.copy(themes = state.themes.map { if (it.id == nt.id) nt else it })
        ThemeStore.save(this, state)
        refreshPreview()
    }

    // ------------------------------------------------------------------ dựng giao diện
    private fun buildUi(): View {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.bg)) }

        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(6)) }
        head.addView(TextView(this).apply {
            text = "Tùy chỉnh giao diện"; textSize = 20f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD)
        })
        head.addView(TextView(this).apply {
            text = "Thay đổi được lưu và xem trước ngay. Bàn phím dùng giao diện mới khi bạn mở lại bàn phím."
            textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(2), 0, 0)
        })
        root.addView(head)
        Fonts.apply(head)

        // Xem trước luôn nằm trên cùng, không cuộn theo
        previewHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.bg)) }
        root.addView(previewHost, lp(MATCH, WRAP))

        val sv = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(24)) }
        sv.addView(content, ViewGroup.LayoutParams(MATCH, WRAP))
        root.addView(sv, lp(MATCH, 0).apply { weight = 1f })

        // --- Giao diện của tôi ---
        val themes = card(content, "GIAO DIỆN CỦA TÔI")
        tvActive = TextView(this).apply { textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(4), 0, 0) }
        themes.addView(tvActive)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        themes.addView(listBox)
        themes.addView(button("+ TẠO GIAO DIỆN MỚI", false) {
            askName("Giao diện mới", "Giao diện ${state.themes.size + 1}") { addTheme(it, null) }
        }, lp(MATCH, dp(44)).apply { topMargin = dp(10) })

        // --- Phạm vi ---
        val sc = card(content, "PHẠM VI ÁP DỤNG")
        sc.addView(TextView(this).apply {
            text = "Chọn phạm vi trước khi chỉnh sửa. Chọn \"Cả hai bảng\" để mỗi thay đổi áp dụng đồng thời cho Scan và Bàn phím."
            textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(4), 0, 0)
        })
        chips(sc, listOf("Bảng Scan", "Bảng Bàn phím", "Cả hai bảng"), 3, { scope }) {
            scope = it
            rebuildEditor()
            refreshPreview()
        }

        // --- Chỉnh sửa ---
        val ed = card(content, "CHỈNH SỬA")
        tabsBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ed.addView(tabsBox, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        editorBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ed.addView(editorBox)

        // --- Khôi phục ---
        content.addView(button("KHÔI PHỤC MẶC ĐỊNH", false) { confirmReset() }, lp(MATCH, dp(48)).apply { topMargin = dp(16) })

        Fonts.apply(sv)
        return root
    }

    private fun card(parent: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_card); setPadding(dp(16), dp(16), dp(16), dp(16)) }
        c.addView(TextView(this).apply {
            text = title; textSize = 12f; setTextColor(col(R.color.accent)); setTypeface(null, Typeface.BOLD); letterSpacing = 0.08f
        })
        parent.addView(c, lp(MATCH, WRAP).apply { topMargin = dp(12) })
        return c
    }

    private fun label(parent: LinearLayout, text: String, top: Int = 14) {
        parent.addView(TextView(this).apply {
            this.text = text; textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD)
        }, lp(WRAP, WRAP).apply { topMargin = dp(top) })
    }

    private fun note(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text; textSize = 12f; setTextColor(col(R.color.muted))
        }, lp(MATCH, WRAP).apply { topMargin = dp(6) })
    }

    private fun button(text: String, primary: Boolean, click: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 14f; gravity = Gravity.CENTER; setTextColor(android.graphics.Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        setBackgroundResource(if (primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_secondary)
        isClickable = true; setOnClickListener { click() }
    }

    private fun smallButton(text: String, click: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 11f; gravity = Gravity.CENTER; setTextColor(col(R.color.text))
        setTypeface(null, Typeface.BOLD)
        setBackgroundResource(R.drawable.bg_btn_secondary)
        setPadding(dp(8), 0, dp(8), 0)
        isClickable = true; setOnClickListener { click() }
    }

    /** Hàng nút chọn một trong nhiều, tự xuống hàng sau perRow nút. */
    private fun chips(parent: LinearLayout, names: List<String>, perRow: Int, current: () -> Int, onPick: (Int) -> Unit) {
        val views = ArrayList<TextView>()
        for (chunk in names.indices.chunked(perRow)) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in chunk) {
                val t = TextView(this).apply {
                    text = names[i]; textSize = 12f; gravity = Gravity.CENTER; setTextColor(col(R.color.text))
                    setTypeface(null, Typeface.BOLD)
                    setBackgroundResource(R.drawable.bg_toggle); isClickable = true; isSelected = i == current()
                    setPadding(dp(4), 0, dp(4), 0)
                    setOnClickListener { views.forEachIndexed { k, v -> v.isSelected = k == i }; onPick(i) }
                }
                views.add(t)
                row.addView(t, lp(0, dp(40)).apply { weight = 1f; setMargins(dp(2), 0, dp(2), 0) })
            }
            repeat(perRow - chunk.size) { row.addView(View(this), lp(0, dp(40)).apply { weight = 1f }) }
            parent.addView(row, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        }
    }

    /** Thanh trượt có nhãn giá trị. Chạm vào nhãn để nhập số chính xác. */
    private fun seekGeneric(parent: LinearLayout, title: String, min: Int, max: Int, unit: String, getCur: () -> Int, onChange: (Int) -> Unit) {
        val tv = TextView(this).apply { textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD) }
        fun show(v: Int) { tv.text = "$title: $v$unit  ✎" }
        val start = getCur().coerceIn(min, max)
        show(start)
        parent.addView(tv, lp(WRAP, WRAP).apply { topMargin = dp(14) })
        val sb = SeekBar(this).apply { this.max = max - min; progress = start - min }
        tv.setOnClickListener {
            ColorPickerDialog.askNumber(this, title, unit, min, max, sb.progress + min) { v ->
                sb.progress = v - min; show(v); onChange(v)
            }
        }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) { show(p + min); onChange(p + min) } }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        parent.addView(sb, lp(MATCH, WRAP).apply { topMargin = dp(4) })
    }

    private fun seek(parent: LinearLayout, title: String, min: Int, max: Int, unit: String, get: (PanelStyle) -> Int, set: (PanelStyle, Int) -> PanelStyle) =
        seekGeneric(parent, title, min, max, unit, { get(viewStyle()) }) { v -> edit { s -> set(s, v) } }

    /** Dòng chọn màu: bấm ô màu để mở bảng chọn màu, kéo là xem trước ngay. */
    private fun colorRow(parent: LinearLayout, title: String, withAlpha: Boolean, get: (PanelStyle) -> Int, set: (PanelStyle, Int) -> PanelStyle) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply { text = title; textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD) }, lp(0, WRAP).apply { weight = 1f })
        val sw = View(this)
        fun paint() {
            sw.background = GradientDrawable().apply { setColor(get(viewStyle())); cornerRadius = dp(8).toFloat(); setStroke(dp(1), 0x66FFFFFF) }
        }
        paint()
        sw.isClickable = true
        sw.setOnClickListener {
            ColorPickerDialog.show(this, title, get(viewStyle()), withAlpha) { c -> edit { s -> set(s, c) }; paint() }
        }
        row.addView(sw, lp(dp(56), dp(34)))
        parent.addView(row, lp(MATCH, WRAP).apply { topMargin = dp(14) })
    }

    // ------------------------------------------------------------------ cập nhật
    private fun refreshAll() {
        refreshList()
        rebuildEditor()
        refreshPreview()
    }

    private fun refreshPreview() {
        previewHost.removeAllViews()
        val t = cur()
        // Chưa chọn phạm vi thì hiện cả hai bảng
        if (scope != 1) previewHost.addView(ThemeRender.previewScan(this, t.scan), lp(MATCH, WRAP))
        if (scope != 0) previewHost.addView(
            ThemeRender.previewKeyboard(this, t.kb, min(prefs.keyRowDp, 44)),
            lp(MATCH, WRAP).apply { topMargin = if (scope == -1 || scope == 2) dp(6) else 0 }
        )
    }

    private fun refreshList() {
        listBox.removeAllViews()
        tvActive.text = "Đang dùng: ${cur().name}"
        for (t in state.themes) {
            val active = t.id == state.active
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val name = TextView(this).apply {
                text = (if (active) "● " else "○ ") + t.name
                textSize = 14f; gravity = Gravity.CENTER_VERTICAL; setTextColor(col(R.color.text))
                setTypeface(null, Typeface.BOLD)
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setBackgroundResource(R.drawable.bg_toggle); isSelected = active
                setPadding(dp(12), 0, dp(8), 0); isClickable = true
                setOnClickListener { switchTo(t) }
            }
            row.addView(name, lp(0, dp(42)).apply { weight = 1f })
            row.addView(smallButton("Nhân bản") { askName("Nhân bản giao diện", "${t.name} (bản sao)") { addTheme(it, t) } }, lp(WRAP, dp(42)).apply { marginStart = dp(4) })
            row.addView(smallButton("Tên") { askName("Đổi tên giao diện", t.name) { renameTheme(t, it) } }, lp(WRAP, dp(42)).apply { marginStart = dp(4) })
            row.addView(smallButton("Xóa") { confirmDelete(t) }, lp(WRAP, dp(42)).apply { marginStart = dp(4) })
            listBox.addView(row, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        }
        Fonts.apply(listBox)
    }

    private fun rebuildEditor() {
        tabsBox.removeAllViews()
        editorBox.removeAllViews()
        if (scope < 0) {
            note(editorBox, "Hãy chọn phạm vi áp dụng ở trên để bắt đầu chỉnh sửa.")
            Fonts.apply(editorBox)
            return
        }
        val names = listOf("Phím", "Nền", "Chữ")
        val icons = listOf(R.drawable.ic_keyboard, R.drawable.ic_palette, R.drawable.ic_tune)
        names.forEachIndexed { i, n ->
            val t = TextView(this).apply {
                text = n; textSize = 12f; gravity = Gravity.CENTER; setTextColor(col(R.color.text))
                setTypeface(null, Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_toggle); isClickable = true; isSelected = i == group
                setPadding(0, dp(6), 0, dp(6))
                val ic = ContextCompat.getDrawable(this@ThemeActivity, icons[i])?.mutate()
                ic?.setTint(col(R.color.icon))
                setCompoundDrawablesWithIntrinsicBounds(null, ic, null, null)
                compoundDrawablePadding = dp(2)
                setOnClickListener { group = i; rebuildEditor() }
            }
            tabsBox.addView(t, lp(0, WRAP).apply { weight = 1f; setMargins(dp(2), 0, dp(2), 0) })
        }
        when (group) {
            0 -> buildKeys(editorBox)
            1 -> buildBackground(editorBox)
            else -> buildFont(editorBox)
        }
        Fonts.apply(tabsBox)
        Fonts.apply(editorBox)
    }

    // ------------------------------------------------------------------ Nhóm 1: phím
    private fun buildKeys(g: LinearLayout) {
        note(g, "Chạm vào giá trị bên cạnh tên để nhập số chính xác. Phạm vi hiện tại: " + scopeName() + ".")
        if (scope != 0) {
            seekGeneric(g, "Chiều cao hàng phím", 36, 72, "dp", { prefs.keyRowDp }) { prefs.keyRowDp = it; refreshPreview() }
            seek(g, "Khoảng cách giữa các phím", 0, 10, "dp", { it.gapDp }) { s, v -> s.copy(gapDp = v) }
        } else note(g, "Khoảng cách phím và chiều cao hàng chỉ áp dụng cho bảng Bàn phím. Hàng nút Scan chỉnh ở \"Tùy chỉnh nút Scan\".")
        seek(g, "Bo góc", 0, 30, "dp", { it.cornerDp }) { s, v -> s.copy(cornerDp = v) }
        seek(g, "Độ dày viền", 0, 4, "dp", { it.borderDp }) { s, v -> s.copy(borderDp = v) }
        colorRow(g, "Màu viền", true, { it.borderColor }) { s, c -> s.copy(borderColor = c) }
        colorRow(g, "Màu phím thường", false, { it.keyColor }) { s, c -> s.copy(keyColor = c) }
        colorRow(g, "Màu phím chức năng", false, { it.specialColor }) { s, c -> s.copy(specialColor = c) }
        colorRow(g, "Màu phím nhấn / đang chọn", false, { it.accentColor }) { s, c -> s.copy(accentColor = c) }
        seek(g, "Độ đặc của phím", 20, 100, "%", { it.keyAlpha }) { s, v -> s.copy(keyAlpha = v) }
        seek(g, "Độ sâu 3D (cạnh nổi)", 0, 8, "dp", { it.depthDp }) { s, v -> s.copy(depthDp = v) }
        seek(g, "Bóng đổ (độ nổi)", 0, 10, "dp", { it.shadowDp }) { s, v -> s.copy(shadowDp = v) }
        colorRow(g, "Màu bóng đổ", false, { it.shadowColor }) { s, c -> s.copy(shadowColor = c) }
        seek(g, "Độ bóng phản chiếu", 0, 60, "%", { it.glossPct }) { s, v -> s.copy(glossPct = v) }
    }

    // ------------------------------------------------------------------ Nhóm 2: hình nền
    private fun buildBackground(g: LinearLayout) {
        note(g, "Hình nền chỉ nằm phía sau các phím, không che chữ và ký hiệu trên phím.")
        label(g, "Kiểu nền", 10)
        chips(g, listOf("Mặc định", "Đơn sắc", "Gradient", "Ảnh"), 4, { viewStyle().bgType }) { i ->
            edit { it.copy(bgType = i) }
            rebuildEditor()
        }
        val s = viewStyle()
        if (s.bgType == 0) {
            note(g, "Đang dùng nền gốc của ứng dụng. Chọn Đơn sắc, Gradient hoặc Ảnh để tùy chỉnh.")
            return
        }
        colorRow(g, if (s.bgType == 2) "Màu đầu" else "Màu nền", false, { it.bgColor1 }) { st, c -> st.copy(bgColor1 = c) }
        if (s.bgType == 2) {
            colorRow(g, "Màu cuối", false, { it.bgColor2 }) { st, c -> st.copy(bgColor2 = c) }
            seek(g, "Hướng gradient", 0, 360, "°", { it.bgAngle }) { st, v -> st.copy(bgAngle = v) }
        }
        if (s.bgType == 3) {
            label(g, "Ảnh nền")
            val has = s.bgImage != null
            note(g, if (has) "Đã chọn ảnh nền." else "Chưa chọn ảnh. Ảnh được lấy từ thư viện của thiết bị.")
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r.addView(button(if (has) "ĐỔI ẢNH" else "CHỌN ẢNH", true) { pickBackground() }, lp(0, dp(44)).apply { weight = 1f; setMargins(0, 0, dp(4), 0) })
            if (has) r.addView(button("BỎ ẢNH", false) {
                edit { it.copy(bgImage = null) }
                ThemeStore.cleanupImages(this, state)
                rebuildEditor()
            }, lp(0, dp(44)).apply { weight = 1f; setMargins(dp(4), 0, 0, 0) })
            g.addView(r, lp(MATCH, WRAP).apply { topMargin = dp(8) })
            label(g, "Chế độ hiển thị")
            chips(g, listOf("Phủ kín", "Vừa khít", "Gốc", "Kéo giãn"), 4, { viewStyle().bgMode }) { i ->
                edit { it.copy(bgMode = i) }
                rebuildEditor()
            }
            if (s.bgMode == 0) {
                seek(g, "Vị trí ngang", 0, 100, "%", { it.bgPosX }) { st, v -> st.copy(bgPosX = v) }
                seek(g, "Vị trí dọc", 0, 100, "%", { it.bgPosY }) { st, v -> st.copy(bgPosY = v) }
            }
            seek(g, "Độ mờ ảnh", 0, 30, "", { it.bgBlur }) { st, v -> st.copy(bgBlur = v) }
        }
        seek(g, "Độ sáng", 30, 170, "%", { it.bgBrightness }) { st, v -> st.copy(bgBrightness = v) }
        seek(g, "Độ tương phản", 50, 170, "%", { it.bgContrast }) { st, v -> st.copy(bgContrast = v) }
    }

    // ------------------------------------------------------------------ Nhóm 3: font chữ
    private fun buildFont(g: LinearLayout) {
        note(g, "Phạm vi hiện tại: " + scopeName() + ". Bảng Scan chỉ có dòng trạng thái và biểu tượng nên chữ tác động chủ yếu lên bảng Bàn phím.")
        label(g, "Kiểu font", 10)
        chips(g, ThemeRender.FAMILY_NAMES, 4, { viewStyle().fontFamily }) { i -> edit { it.copy(fontFamily = i) } }
        seek(g, "Độ đậm / mảnh", 300, 800, "", { it.fontWeight }) { s, v -> s.copy(fontWeight = v) }
        note(g, "Montserrat chỉnh được mọi mức. Với font khác, độ đậm tùy theo máy.")
        seek(g, "Kích thước chữ", 70, 140, "%", { it.fontScale }) { s, v -> s.copy(fontScale = v) }
        colorRow(g, "Màu chữ", false, { it.textColor }) { s, c -> s.copy(textColor = c) }
        colorRow(g, "Màu biểu tượng", false, { it.iconColor }) { s, c -> s.copy(iconColor = c) }
        seek(g, "Bóng chữ", 0, 8, "", { it.textShadow }) { s, v -> s.copy(textShadow = v) }
        seek(g, "Độ nổi của chữ", 0, 4, "", { it.textRaise }) { s, v -> s.copy(textRaise = v) }
        colorRow(g, "Màu bóng chữ", true, { it.textShadowColor }) { s, c -> s.copy(textShadowColor = c) }
    }

    private fun scopeName() = when (scope) { 0 -> "bảng Scan"; 1 -> "bảng Bàn phím"; else -> "cả hai bảng" }

    // ------------------------------------------------------------------ quản lý giao diện
    private fun askName(title: String, initial: String, onOk: (String) -> Unit) {
        val et = EditText(this).apply {
            setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT; setText(initial); setSelection(text.length)
        }
        val wrap = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(et, lp(MATCH, WRAP)) }
        AlertDialog.Builder(this).setTitle(title).setView(wrap)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu") { _, _ ->
                val n = et.text.toString().trim()
                if (n.isNotEmpty()) onOk(n.take(40))
            }.show()
    }

    private fun addTheme(name: String, from: KbTheme?) {
        val t = KbTheme(ThemeStore.newId(), name, from?.scan ?: PanelStyle(), from?.kb ?: PanelStyle())
        state = ThemeState(t.id, state.themes + t)
        ThemeStore.save(this, state)
        refreshAll()
    }

    private fun renameTheme(t: KbTheme, name: String) {
        state = state.copy(themes = state.themes.map { if (it.id == t.id) it.copy(name = name) else it })
        ThemeStore.save(this, state)
        refreshList()
    }

    private fun switchTo(t: KbTheme) {
        if (state.active == t.id) return
        state = state.copy(active = t.id)
        ThemeStore.save(this, state)
        refreshAll()
    }

    private fun confirmDelete(t: KbTheme) {
        if (state.themes.size <= 1) {
            Toast.makeText(this, "Cần giữ lại ít nhất một giao diện.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Xóa giao diện?")
            .setMessage("Giao diện \"${t.name}\" sẽ bị xóa vĩnh viễn.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Xóa") { _, _ ->
                val left = state.themes.filter { it.id != t.id }
                state = ThemeState(if (state.active == t.id) left.first().id else state.active, left)
                ThemeStore.save(this, state)
                ThemeStore.cleanupImages(this, state)
                ThemeImages.forget()
                refreshAll()
            }.show()
    }

    private fun confirmReset() {
        if (scope < 0) {
            Toast.makeText(this, "Hãy chọn phạm vi áp dụng trước.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Khôi phục mặc định?")
            .setMessage("Các tùy chỉnh phím, nền và chữ của " + scopeName() + " trong giao diện \"" + cur().name +
                "\" sẽ trở về ban đầu. Các giao diện khác, dữ liệu và chức năng khác của ứng dụng không bị ảnh hưởng.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Khôi phục") { _, _ ->
                edit { PanelStyle() }
                if (scope != 0) prefs.keyRowDp = 50
                ThemeStore.cleanupImages(this, state)
                ThemeImages.forget()
                rebuildEditor()
                refreshPreview()
            }.show()
    }

    // ------------------------------------------------------------------ chọn ảnh nền
    private fun pickBackground() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 78)
        } catch (_: Throwable) { Toast.makeText(this, "Không mở được thư viện ảnh.", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != 78 || resultCode != RESULT_OK || uri == null) return
        val name = importBackground(uri)
        if (name == null) { Toast.makeText(this, "Không đọc được ảnh này.", Toast.LENGTH_SHORT).show(); return }
        edit { it.copy(bgType = 3, bgImage = name) }
        ThemeStore.cleanupImages(this, state)
        ThemeImages.forget()
        rebuildEditor()
    }

    /** Đọc ảnh, xoay theo EXIF, thu nhỏ (tối đa ~1280px) để nhẹ máy, rồi lưu vào bộ nhớ riêng của ứng dụng. */
    private fun importBackground(uri: Uri): String? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            var s = 1
            while (opts.outWidth / (s * 2) >= 1280 || opts.outHeight / (s * 2) >= 1280) s *= 2
            var bmp: Bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
            } ?: return null
            val orientation = try {
                contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Throwable) { ExifInterface.ORIENTATION_NORMAL }
            val rot = when (orientation) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
            if (rot != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
            val png = bmp.hasAlpha()
            val name = "bg_${System.currentTimeMillis()}." + (if (png) "png" else "jpg")
            FileOutputStream(File(ThemeStore.imageDir(this), name)).use {
                bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it)
            }
            name
        } catch (_: Throwable) { null }
    }
}
