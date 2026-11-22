package com.inventory.scannerkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Màn hình "Tùy chỉnh giao diện": chọn bảng cần chỉnh (Scan hoặc Bàn phím, hai bảng độc lập), chỉnh phím, nền, chữ với
 * xem trước trực tiếp, lưu nhiều giao diện (đặt tên, nhân bản, chuyển đổi, xóa), khôi phục mặc định.
 * Mọi thay đổi được lưu ngay; bàn phím dùng giao diện mới ở lần hiện kế tiếp.
 */
class ThemeActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var state: ThemeState

    private var panel = 0   // bảng đang chỉnh: 0 bảng Scan, 1 bảng Bàn phím. Hai bảng độc lập hoàn toàn
    private lateinit var scroll: StableScrollView
    private var group = 0    // 0 phím, 1 nền, 2 chữ, 3 chạm, 4 ánh sáng, 5 chuyển động nền, 6 bố cục, 7 phím Space

    private lateinit var sim: KbSimView   // khung xem trước mô phỏng (nhẹ, kích thước cố định)
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
    private fun viewStyle(): PanelStyle = if (panel == 0) cur().scan else cur().kb

    /** Áp một thay đổi CHỈ lên bảng đang chỉnh (Scan hoặc Bàn phím), lưu ngay và cập nhật xem trước. */
    private fun edit(f: (PanelStyle) -> PanelStyle) {
        val t = cur()
        val nt = t.copy(
            scan = if (panel == 0) f(t.scan) else t.scan,
            kb = if (panel == 1) f(t.kb) else t.kb
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
            text = "Thay đổi được lưu, xem trước và áp dụng cho bàn phím ngay."
            textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(2), 0, 0)
        })
        root.addView(head)
        Fonts.apply(head)

        // Xem trước MÔ PHỎNG luôn nằm trên cùng, không cuộn theo, chiều cao cố định
        root.addView(TextView(this).apply {
            text = "XEM TRƯỚC (mô phỏng)"; textSize = 11f; setTextColor(col(R.color.accent))
            setTypeface(null, Typeface.BOLD); letterSpacing = 0.05f; setPadding(dp(16), dp(6), dp(16), dp(4))
        }, lp(MATCH, WRAP))
        sim = KbSimView(this)
        root.addView(sim, lp(MATCH, minOf(dp(240), (resources.displayMetrics.heightPixels * 0.36f).toInt())))

        val sv = StableScrollView(this).apply { isFillViewport = true }
        scroll = sv
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

        // --- Chỉnh sửa ---
        val ed = card(content, "CHỈNH SỬA")
        note(ed, "Bảng Scan và Bảng Bàn phím có hệ thống tùy chỉnh riêng, độc lập nhau: chọn bảng cần chỉnh, thay đổi chỉ áp dụng cho bảng đó.")
        chips(ed, listOf("BẢNG SCAN", "BẢNG BÀN PHÍM"), 2, { panel }) {
            panel = it
            if (panel == 0 && group == 7) group = 0      // phím Space chỉ thuộc bảng Bàn phím
            rebuildEditor()
            refreshPreview()
        }
        tabsBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        tabScroll.addView(tabsBox, ViewGroup.LayoutParams(WRAP, WRAP))
        ed.addView(tabScroll, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        note(ed, "Vuốt ngang thanh nhóm để xem đủ: Phím, Nền, Chữ, Chạm, Sáng, Động, Bố cục, Space.")
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

    /** Thanh trượt có nhãn giá trị, nút − / + ở hai đầu, rung nhẹ khi đổi giá trị. Chạm vào nhãn để nhập số chính xác. */
    private fun seekGeneric(parent: LinearLayout, title: String, min: Int, max: Int, unit: String, getCur: () -> Int, onChange: (Int) -> Unit) {
        val tv = TextView(this).apply { textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD) }
        fun show(v: Int) { tv.text = "$title: $v$unit  ✎" }
        val start = getCur().coerceIn(min, max)
        show(start)
        parent.addView(tv, lp(WRAP, WRAP).apply { topMargin = dp(14) })
        val sr = SeekRow(this, min, max, start) { v -> show(v); onChange(v) }
        tv.setOnClickListener {
            ColorPickerDialog.askNumber(this, title, unit, min, max, sr.value) { v ->
                sr.set(v); show(v); onChange(v)
            }
        }
        parent.addView(sr.view, lp(MATCH, WRAP).apply { topMargin = dp(4) })
    }

    private fun seek(parent: LinearLayout, title: String, min: Int, max: Int, unit: String, get: (PanelStyle) -> Int, set: (PanelStyle, Int) -> PanelStyle) =
        seekGeneric(parent, title, min, max, unit, { get(viewStyle()) }) { v -> edit { s -> set(s, v) } }

    /** Dòng chọn màu: bấm ô màu để mở bảng chọn màu, kéo là xem trước ngay. */
    private fun colorRow(parent: LinearLayout, title: String, withAlpha: Boolean, get: (PanelStyle) -> Int, set: (PanelStyle, Int) -> PanelStyle) =
        colorPick(parent, title, withAlpha, { get(viewStyle()) }) { c -> edit { s -> set(s, c) } }

    // ------------------------------------------------------------------ cập nhật
    private fun refreshAll() {
        refreshList()
        rebuildEditor()
        refreshPreview()
    }

    /** Vẽ lại khung xem trước mô phỏng theo giá trị đang chỉnh (chỉ vẽ lại, không dựng lại giao diện nên không bao giờ nhảy khung). */
    private fun refreshPreview() {
        val t = cur()
        if (panel == 0) {
            sim.showScan(ScanLayout.fromJson(prefs.scanLayoutJson), t.scan, null, false)
        } else {
            sim.showType(t.kb, t.space, KbMetrics.rowHeightPx(this, prefs, t.kb))
        }
    }

    private fun refreshList() { scroll.keepScroll { refreshListImpl() } }

    private fun refreshListImpl() {
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
        scroll.keepScroll { rebuildEditorImpl() }
    }

    private fun rebuildEditorImpl() {
        tabsBox.removeAllViews()
        editorBox.removeAllViews()
        val allNames = listOf("Phím", "Nền", "Chữ", "Chạm", "Sáng", "Động", "Bố cục", "Space")
        val allIcons = listOf(
            R.drawable.ic_keyboard, R.drawable.ic_palette, R.drawable.ic_tune, R.drawable.ic_sparkle,
            R.drawable.ic_sun, R.drawable.ic_motion, R.drawable.ic_layout, R.drawable.ic_space
        )
        // Phím Space chỉ có ở bảng Bàn phím
        val count = if (panel == 1) allNames.size else allNames.size - 1
        val names = allNames.take(count)
        val icons = allIcons.take(count)
        if (group >= count) group = 0
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
            tabsBox.addView(t, lp(dp(68), WRAP).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        when (group) {
            0 -> buildKeys(editorBox)
            1 -> buildBackground(editorBox)
            2 -> buildFont(editorBox)
            3 -> buildTouch(editorBox)
            4 -> buildLight(editorBox)
            5 -> buildMotion(editorBox)
            6 -> buildLayout(editorBox)
            else -> buildSpace(editorBox)
        }
        Fonts.apply(tabsBox)
        Fonts.apply(editorBox)
    }

    // ------------------------------------------------------------------ Nhóm 1: phím
    private fun buildKeys(g: LinearLayout) {
        note(g, "Chạm vào giá trị bên cạnh tên để nhập số chính xác. Đang chỉnh: " + scopeName() + ".")
        if (panel == 1) {
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
        note(g, "Đang chỉnh: " + scopeName() + ". Bảng Scan chỉ có dòng trạng thái và biểu tượng nên chữ tác động chủ yếu lên bảng Bàn phím.")
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

    /** Hàng công tắc BẬT / TẮT có mô tả. rebuild = true thì dựng lại bảng chỉnh để hiện / ẩn các thông số đi kèm. */
    private fun switchRow(parent: LinearLayout, title: String, desc: String, rebuild: Boolean, get: (PanelStyle) -> Boolean, set: (PanelStyle, Boolean) -> PanelStyle) =
        switchPick(parent, title, desc, rebuild, { get(viewStyle()) }) { on -> edit { s -> set(s, on) } }

    /** Công tắc cho một bit trong lightFx. */
    private fun lightSwitch(parent: LinearLayout, title: String, desc: String, bit: Int) =
        switchRow(parent, title, desc, true, { (it.lightFx and bit) != 0 }) { s, on ->
            s.copy(lightFx = if (on) s.lightFx or bit else s.lightFx and bit.inv())
        }

    // ------------------------------------------------------------------ Nhóm 4: hiệu ứng khi chạm và gõ
    private fun buildTouch(g: LinearLayout) {
        note(g, "Mỗi hiệu ứng có công tắc riêng, bật bao nhiêu cũng được. Hiệu ứng ánh sáng, chuyển động và chạm chỉ chạy trên bàn phím thật (khung xem trước phía trên là bản mô phỏng tĩnh). Đang chỉnh: " + scopeName() + ".")
        switchRow(g, "Nhún phím", "Phím co lại một chút khi chạm.", true, { it.fxBounce }) { s, on -> s.copy(fxBounce = on) }
        if (viewStyle().fxBounce) {
            seek(g, "Mức nhún", 80, 100, "%", { it.fxBounceScale }) { s, v -> s.copy(fxBounceScale = v) }
            switchRow(g, "Đàn hồi", "Phím bật nảy lại khi nhả tay.", false, { it.fxElastic }) { s, on -> s.copy(fxElastic = on) }
        }
        switchRow(g, "Gợn sóng", "Vòng sáng lan ra từ điểm chạm.", false, { it.fxRipple }) { s, on -> s.copy(fxRipple = on) }
        switchRow(g, "Phát sáng khi chạm", "Phím lóe sáng rồi mờ dần.", false, { it.fxFlash }) { s, on -> s.copy(fxFlash = on) }
        switchRow(g, "Hạt sáng", "Các hạt sáng bắn ra từ điểm chạm.", true, { it.fxSparks }) { s, on -> s.copy(fxSparks = on) }
        if (viewStyle().fxSparks) seek(g, "Số hạt sáng", 4, 24, "", { it.fxSparkCount }) { s, v -> s.copy(fxSparkCount = v) }
        colorRow(g, "Màu hiệu ứng", false, { it.fxColor }) { s, c -> s.copy(fxColor = c) }
        seek(g, "Cường độ", 10, 100, "%", { it.fxIntensity }) { s, v -> s.copy(fxIntensity = v) }
        seek(g, "Thời gian hiệu ứng", 150, 1000, " ms", { it.fxDuration }) { s, v -> s.copy(fxDuration = v) }
    }

    // ------------------------------------------------------------------ Nhóm 6: ánh sáng và màu sắc
    private fun buildLight(g: LinearLayout) {
        note(g, "Có thể bật nhiều kiểu cùng lúc. Màu viền lấy theo thứ tự ưu tiên Rainbow, RGB, Gradient, rồi tới màu bạn chọn. Neon và Glow quyết định kiểu quầng sáng; Lan tỏa là lớp ánh sáng nền. Hiệu ứng chỉ chạy khi bàn phím đang hiện. Đang chỉnh: " + scopeName() + ".")
        lightSwitch(g, "RGB", "Viền đổi màu liên tục, tất cả phím cùng một màu.", FxDrawable.RGB)
        lightSwitch(g, "Rainbow", "Dải màu cầu vồng chạy ngang các phím.", FxDrawable.RAINBOW)
        lightSwitch(g, "Gradient", "Chuyển màu mượt giữa hai màu bạn chọn.", FxDrawable.GRADIENT)
        lightSwitch(g, "Neon", "Viền mảnh có lõi sáng trắng và quầng sáng.", FxDrawable.NEON)
        lightSwitch(g, "Glow", "Quầng sáng mềm quanh mỗi phím.", FxDrawable.GLOW)
        lightSwitch(g, "Ánh sáng lan tỏa", "Vệt sáng mềm trôi trên toàn bảng.", FxDrawable.DIFFUSE)
        val bits = viewStyle().lightFx
        if (bits == 0) return
        colorRow(g, "Màu ánh sáng", false, { it.lightColor }) { s, c -> s.copy(lightColor = c) }
        if ((bits and FxDrawable.GRADIENT) != 0) colorRow(g, "Màu thứ hai (Gradient)", false, { it.lightColor2 }) { s, c -> s.copy(lightColor2 = c) }
        seek(g, "Cường độ ánh sáng", 10, 100, "%", { it.lightIntensity }) { s, v -> s.copy(lightIntensity = v) }
        val moving = FxDrawable.RGB or FxDrawable.RAINBOW or FxDrawable.GRADIENT or FxDrawable.DIFFUSE
        if ((bits and moving) != 0) seek(g, "Tốc độ chuyển màu", 1, 100, "", { it.lightSpeed }) { s, v -> s.copy(lightSpeed = v) }
        if ((bits and (FxDrawable.GLOW or FxDrawable.NEON)) != 0) seek(g, "Độ lan tỏa của quầng sáng", 0, 12, "dp", { it.lightSpread }) { s, v -> s.copy(lightSpread = v) }
    }

    /** Công tắc cho một bit trong motionFx. */
    private fun motionSwitch(parent: LinearLayout, title: String, desc: String, bit: Int) =
        switchRow(parent, title, desc, true, { (it.motionFx and bit) != 0 }) { s, on ->
            s.copy(motionFx = if (on) s.motionFx or bit else s.motionFx and bit.inv())
        }

    // ------------------------------------------------------------------ Nhóm 5: hiệu ứng chuyển động nền
    private fun buildMotion(g: LinearLayout) {
        note(g, "Hiệu ứng nằm phía sau phím, không che chữ. Bật riêng từng cái và kết hợp tự do. Nhiều hiệu ứng cùng lúc sẽ tốn pin hơn; ứng dụng tự giảm số hạt nếu máy vẽ chậm. Đang chỉnh: " + scopeName() + ".")
        motionSwitch(g, "Sao băng", "Vệt sao băng vụt qua.", MotionBgDrawable.SHOOTING)
        motionSwitch(g, "Đom đóm", "Đốm sáng xanh vàng chập chờn bay lượn.", MotionBgDrawable.FIREFLY)
        motionSwitch(g, "Bong bóng nước", "Bong bóng trong suốt nổi lên.", MotionBgDrawable.BUBBLE)
        motionSwitch(g, "Tuyết rơi", "Bông tuyết nhẹ rơi xuống.", MotionBgDrawable.SNOW)
        motionSwitch(g, "Cánh hoa", "Cánh hoa hồng nhạt xoay rơi.", MotionBgDrawable.PETAL)
        motionSwitch(g, "Bụi phép thuật", "Các đốm lấp lánh nhấp nháy.", MotionBgDrawable.MAGIC)
        motionSwitch(g, "Dải cực quang", "Dải sáng xanh tím uốn lượn.", MotionBgDrawable.AURORA)
        motionSwitch(g, "Ngân hà", "Vòng xoáy sao chậm rãi.", MotionBgDrawable.GALAXY)
        motionSwitch(g, "Tia sáng", "Chùm tia sáng quét nhẹ từ phía trên.", MotionBgDrawable.RAYS)
        motionSwitch(g, "Các hạt chuyển động", "Hạt nhỏ trôi lơ lửng khắp nền.", MotionBgDrawable.PARTICLES)
        if (viewStyle().motionFx == 0) return
        seek(g, "Tốc độ", 10, 300, "%", { it.motionSpeed }) { s, v -> s.copy(motionSpeed = v) }
        seek(g, "Kích thước", 50, 300, "%", { it.motionSize }) { s, v -> s.copy(motionSize = v) }
        seek(g, "Mật độ", 10, 300, "%", { it.motionDensity }) { s, v -> s.copy(motionDensity = v) }
        seek(g, "Độ sáng", 10, 100, "%", { it.motionBrightness }) { s, v -> s.copy(motionBrightness = v) }
        seek(g, "Độ mờ (quầng mềm quanh hạt)", 0, 10, "", { it.motionBlur }) { s, v -> s.copy(motionBlur = v) }
    }

    // ------------------------------------------------------------------ Nhóm 7: bố cục và vị trí
    private fun buildLayout(g: LinearLayout) {
        note(g, "Đang chỉnh: " + scopeName() + ". Khung xem trước phía trên thể hiện đúng chiều rộng, căn lề và vị trí bạn chọn.")
        if (panel == 1) {
            seekGeneric(g, "Chiều cao hàng phím", 36, 72, "dp", { prefs.keyRowDp }) { prefs.keyRowDp = it; refreshPreview() }
            seek(g, "Khoảng cách giữa các phím", 0, 10, "dp", { it.gapDp }) { s, v -> s.copy(gapDp = v) }
        }
        seek(g, "Chiều rộng tổng thể", 50, 100, "%", { it.layoutWidth }) { s, v -> s.copy(layoutWidth = v) }
        label(g, "Căn lề")
        chips(g, listOf("Trái", "Giữa", "Phải"), 3, { viewStyle().layoutAlign }) { i -> edit { it.copy(layoutAlign = i) } }
        label(g, "Chế độ một tay")
        chips(g, listOf("Tắt", "Tay trái", "Tay phải"), 3, { viewStyle().layoutOneHand }) { i -> edit { it.copy(layoutOneHand = i) } }
        note(g, "Thu bàn phím về một bên (tối đa 75% chiều rộng) để bấm bằng một tay. Khi bật, chế độ này thay cho căn lề.")
        seek(g, "Nâng khỏi mép dưới", 0, 48, "dp", { it.layoutBottom }) { s, v -> s.copy(layoutBottom = v) }
        switchRow(g, "Tự tối ưu theo màn hình", "Màn hình rộng (máy tính bảng, xoay ngang) giới hạn bề rộng cho dễ bấm; màn hình nhỏ hoặc lớn tự điều chỉnh nhẹ chiều cao hàng phím.", false, { it.layoutAuto }) { s, on -> s.copy(layoutAuto = on) }
    }

    // ------------------------------------------------------------------ Nhóm 8: phím Space
    /** Áp một thay đổi lên kiểu phím Space của giao diện đang dùng, lưu ngay và cập nhật xem trước. */
    private fun editSpace(f: (SpaceStyle) -> SpaceStyle) {
        val t = cur()
        val nt = t.copy(space = f(t.space))
        state = state.copy(themes = state.themes.map { if (it.id == nt.id) nt else it })
        ThemeStore.save(this, state)
        refreshPreview()
    }

    /** Dòng chọn màu cho một giá trị bất kỳ (không gắn với PanelStyle). */
    private fun colorPick(parent: LinearLayout, title: String, withAlpha: Boolean, get: () -> Int, set: (Int) -> Unit) {
        parent.addView(UiKit.colorRow(this, title, withAlpha, get, set), lp(MATCH, WRAP).apply { topMargin = dp(14) })
    }

    private fun switchPick(parent: LinearLayout, title: String, desc: String, rebuild: Boolean, get: () -> Boolean, set: (Boolean) -> Unit) {
        parent.addView(UiKit.switchRow(this, title, desc, get) { on -> set(on); if (rebuild) rebuildEditor() }, lp(MATCH, WRAP).apply { topMargin = dp(14) })
    }

    private fun spaceSeek(g: LinearLayout, title: String, min: Int, max: Int, unit: String, get: (SpaceStyle) -> Int, set: (SpaceStyle, Int) -> SpaceStyle) =
        seekGeneric(g, title, min, max, unit, { get(cur().space) }) { v -> editSpace { set(it, v) } }

    private fun buildSpace(g: LinearLayout) {
        val sp = cur().space
        note(g, "Phím Space nằm ở hàng dưới của bảng Bàn phím. Phần này chỉ thuộc bảng Bàn phím và áp dụng cho giao diện \"${cur().name}\". Khung xem trước phía trên thể hiện đúng phím Space.")

        label(g, "CHỨC NĂNG", 12)
        switchPick(g, "Vuốt để di chuyển con trỏ", "Đặt ngón tay lên phím Space rồi vuốt trái / phải để dịch con trỏ trong ô nhập.", true,
            { cur().space.swipe }) { on -> editSpace { it.copy(swipe = on) } }
        if (sp.swipe) {
            spaceSeek(g, "Quãng vuốt cho mỗi ký tự (nhỏ = nhạy hơn)", 6, 40, "dp", { it.swipeStep }) { s, v -> s.copy(swipeStep = v) }
            switchPick(g, "Rung nhẹ mỗi bước", "Rung theo từng ký tự con trỏ di chuyển (theo cường độ rung phím trong cài đặt).", false,
                { cur().space.swipeHaptic }) { on -> editSpace { it.copy(swipeHaptic = on) } }
        }
        spaceSeek(g, "Độ rộng phím Space (÷10 = số lần phím chữ)", 20, 70, "", { it.weight }) { s, v -> s.copy(weight = v) }

        label(g, "GIAO DIỆN RIÊNG", 22)
        switchPick(g, "Dùng giao diện riêng cho phím Space", "Tắt: phím Space giống các phím chữ thường.", true,
            { cur().space.custom }) { on -> editSpace { it.copy(custom = on) } }
        if (!sp.custom) return

        spaceSeek(g, "Bo góc", 0, 30, "dp", { it.cornerDp }) { s, v -> s.copy(cornerDp = v) }
        spaceSeek(g, "Độ dày viền", 0, 4, "dp", { it.borderDp }) { s, v -> s.copy(borderDp = v) }
        colorPick(g, "Màu viền", true, { cur().space.borderColor }) { c -> editSpace { it.copy(borderColor = c) } }
        colorPick(g, "Màu phím", false, { cur().space.color }) { c -> editSpace { it.copy(color = c) } }
        spaceSeek(g, "Độ đặc của phím", 20, 100, "%", { it.alpha }) { s, v -> s.copy(alpha = v) }
        spaceSeek(g, "Độ sâu 3D (cạnh nổi)", 0, 8, "dp", { it.depthDp }) { s, v -> s.copy(depthDp = v) }
        spaceSeek(g, "Bóng đổ (độ nổi)", 0, 10, "dp", { it.shadowDp }) { s, v -> s.copy(shadowDp = v) }
        colorPick(g, "Màu bóng đổ", false, { cur().space.shadowColor }) { c -> editSpace { it.copy(shadowColor = c) } }
        spaceSeek(g, "Độ bóng phản chiếu", 0, 60, "%", { it.glossPct }) { s, v -> s.copy(glossPct = v) }

        label(g, "ẢNH TRÊN PHÍM", 22)
        val has = sp.image != null
        note(g, if (has) "Đã chọn ảnh cho phím Space." else "Chưa chọn ảnh. Ảnh được lấy từ thư viện của thiết bị và hiện bên dưới chữ, bo góc theo phím.")
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r.addView(button(if (has) "ĐỔI ẢNH" else "TẢI ẢNH LÊN", true) { pickSpaceImage() }, lp(0, dp(44)).apply { weight = 1f; setMargins(0, 0, dp(4), 0) })
        if (has) r.addView(button("BỎ ẢNH", false) {
            editSpace { it.copy(image = null) }
            ThemeStore.cleanupImages(this, state)
            SpaceRender.forget()
            rebuildEditor()
        }, lp(0, dp(44)).apply { weight = 1f; setMargins(dp(4), 0, 0, 0) })
        g.addView(r, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        if (has) {
            label(g, "Chế độ hiển thị")
            chips(g, listOf("Phủ kín", "Vừa khít", "Kéo giãn"), 3, { cur().space.imgMode }) { i -> editSpace { it.copy(imgMode = i) }; rebuildEditor() }
            spaceSeek(g, "Độ đặc của ảnh", 10, 100, "%", { it.imgAlpha }) { s, v -> s.copy(imgAlpha = v) }
            if (sp.imgMode == 0) {
                spaceSeek(g, "Vị trí ngang", 0, 100, "%", { it.imgPosX }) { s, v -> s.copy(imgPosX = v) }
                spaceSeek(g, "Vị trí dọc", 0, 100, "%", { it.imgPosY }) { s, v -> s.copy(imgPosY = v) }
            }
        }

        label(g, "CHỮ TRÊN PHÍM", 22)
        chips(g, listOf("Không chữ", "Chữ tự đặt", "Ngôn ngữ"), 3, { cur().space.labelMode }) { i -> editSpace { it.copy(labelMode = i) }; rebuildEditor() }
        if (sp.labelMode != 0) {
            if (sp.labelMode == 1) {
                val tv = TextView(this).apply {
                    text = "Nội dung: ${sp.labelText}  ✎"; textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD)
                    isClickable = true
                    setOnClickListener { askName("Chữ trên phím Space", cur().space.labelText) { n -> editSpace { it.copy(labelText = n.take(24)) }; rebuildEditor() } }
                }
                g.addView(tv, lp(WRAP, WRAP).apply { topMargin = dp(14) })
            }
            spaceSeek(g, "Cỡ chữ", 8, 28, "sp", { it.labelSize }) { s, v -> s.copy(labelSize = v) }
            colorPick(g, "Màu chữ", false, { cur().space.labelColor }) { c -> editSpace { it.copy(labelColor = c) } }
            label(g, "Kiểu font")
            chips(g, ThemeRender.FAMILY_NAMES, 4, { cur().space.fontFamily }) { i -> editSpace { it.copy(fontFamily = i) } }
            spaceSeek(g, "Độ đậm / mảnh", 300, 800, "", { it.fontWeight }) { s, v -> s.copy(fontWeight = v) }
            spaceSeek(g, "Khoảng cách chữ (x0,01 em)", 0, 40, "", { it.letterSpacing }) { s, v -> s.copy(letterSpacing = v) }
            spaceSeek(g, "Bóng chữ", 0, 8, "", { it.labelShadow }) { s, v -> s.copy(labelShadow = v) }
        }

        g.addView(button("KHÔI PHỤC PHÍM SPACE", false) {
            editSpace { SpaceStyle() }
            ThemeStore.cleanupImages(this, state)
            SpaceRender.forget()
            rebuildEditor()
        }, lp(MATCH, dp(44)).apply { topMargin = dp(18) })
    }

    private fun scopeName() = if (panel == 0) "bảng Scan" else "bảng Bàn phím"

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
        val t = KbTheme(ThemeStore.newId(), name, from?.scan ?: PanelStyle(), from?.kb ?: PanelStyle(), from?.space ?: SpaceStyle())
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
        AlertDialog.Builder(this)
            .setTitle("Khôi phục mặc định?")
            .setMessage("Các tùy chỉnh phím, nền và chữ của " + scopeName() + " trong giao diện \"" + cur().name +
                "\" sẽ trở về ban đầu. Các giao diện khác, dữ liệu và chức năng khác của ứng dụng không bị ảnh hưởng.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Khôi phục") { _, _ ->
                edit { PanelStyle() }
                if (panel == 1) prefs.keyRowDp = 50
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

    private fun pickSpaceImage() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 79)
        } catch (_: Throwable) { Toast.makeText(this, "Không mở được thư viện ảnh.", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        if (requestCode == 79) {
            val n = importBackground(uri, "sp")
            if (n == null) { Toast.makeText(this, "Không đọc được ảnh này.", Toast.LENGTH_SHORT).show(); return }
            editSpace { it.copy(image = n, custom = true) }
            ThemeStore.cleanupImages(this, state)
            SpaceRender.forget()
            rebuildEditor()
            return
        }
        if (requestCode != 78) return
        val name = importBackground(uri)
        if (name == null) { Toast.makeText(this, "Không đọc được ảnh này.", Toast.LENGTH_SHORT).show(); return }
        edit { it.copy(bgType = 3, bgImage = name) }
        ThemeStore.cleanupImages(this, state)
        ThemeImages.forget()
        rebuildEditor()
    }

    /** Đọc ảnh, xoay theo EXIF, thu nhỏ (tối đa ~1280px) để nhẹ máy, rồi lưu vào bộ nhớ riêng của ứng dụng. */
    private fun importBackground(uri: Uri, prefix: String = "bg"): String? {
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
            val name = "${prefix}_${System.currentTimeMillis()}." + (if (png) "png" else "jpg")
            FileOutputStream(File(ThemeStore.imageDir(this), name)).use {
                bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it)
            }
            name
        } catch (_: Throwable) { null }
    }
}
