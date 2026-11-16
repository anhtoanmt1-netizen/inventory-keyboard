package com.inventory.scannerkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Tùy chọn cho nhà phát triển (ẩn, mở bằng cách nhấn liên tiếp 5 lần vào logo SPX ở màn hình cài đặt).
 * Cho phép chỉnh dòng "Created by" ở cuối bàn phím: nội dung, định dạng chữ, hiệu ứng xuất hiện / chuyển động,
 * viền phát sáng và các hiệu ứng ánh sáng. Mọi thay đổi lưu ngay và có xem trước trực tiếp.
 */
object DevOptions {
    fun build(a: Activity, host: LinearLayout, p: Prefs) = DevPanel(a, host, p).build()
}

private class DevPanel(private val a: Activity, private val host: LinearLayout, private val p: Prefs) {
    private val d = a.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()
    private fun col(id: Int) = ContextCompat.getColor(a, id)
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private var st = CreditStyle.fromJson(p.creditJson)
    private lateinit var preview: CreditView
    private lateinit var card: LinearLayout
    private lateinit var tvText: TextView

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    /** Áp một thay đổi, lưu ngay và cập nhật bản xem trước. */
    private fun update(f: (CreditStyle) -> CreditStyle) {
        st = f(st)
        p.creditJson = st.toJson()
        preview.setStyle(st)
    }

    // ------------------------------------------------------------------ thành phần giao diện
    private fun section(text: String) {
        card.addView(TextView(a).apply {
            this.text = text; textSize = 12f; setTextColor(col(R.color.accent)); setTypeface(null, Typeface.BOLD); letterSpacing = 0.06f
        }, lp(WRAP, WRAP).apply { topMargin = dp(20) })
    }

    private fun label(text: String) {
        card.addView(TextView(a).apply {
            this.text = text; textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD)
        }, lp(WRAP, WRAP).apply { topMargin = dp(14) })
    }

    private fun note(text: String) {
        card.addView(TextView(a).apply { this.text = text; textSize = 12f; setTextColor(col(R.color.muted)) }, lp(MATCH, WRAP).apply { topMargin = dp(4) })
    }

    private fun button(text: String, primary: Boolean, click: () -> Unit) = TextView(a).apply {
        this.text = text; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        setBackgroundResource(if (primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_secondary)
        isClickable = true; setOnClickListener { click() }
    }

    /** Chọn một trong nhiều; tự xuống hàng sau [perRow] lựa chọn. */
    private fun chips(names: List<String>, perRow: Int, current: () -> Int, onPick: (Int) -> Unit) {
        val all = ArrayList<TextView>()
        fun mark() { all.forEachIndexed { i, t -> t.isSelected = i == current() } }
        for (chunk in names.indices.chunked(perRow)) {
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in chunk) {
                val t = TextView(a).apply {
                    text = names[i]; textSize = 12f; gravity = Gravity.CENTER; setTextColor(col(R.color.text))
                    setBackgroundResource(R.drawable.bg_toggle); isClickable = true; setPadding(dp(4), 0, dp(4), 0)
                    setOnClickListener { onPick(i); mark() }
                }
                all.add(t)
                row.addView(t, lp(0, dp(40)).apply { weight = 1f; setMargins(dp(2), dp(3), dp(2), dp(3)) })
            }
            repeat(perRow - chunk.size) { row.addView(View(a), lp(0, dp(40)).apply { weight = 1f }) }
            card.addView(row, lp(MATCH, WRAP).apply { topMargin = dp(4) })
        }
        mark()
    }

    /** Thanh trượt có nút − / + và rung nhẹ. Chạm vào nhãn để nhập số chính xác. */
    private fun seek(title: String, min: Int, max: Int, unit: String, get: () -> Int, set: (Int) -> Unit) {
        val tv = TextView(a).apply { textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD) }
        fun show(v: Int) { tv.text = "$title: $v$unit  ✎" }
        val start = get().coerceIn(min, max)
        show(start)
        card.addView(tv, lp(WRAP, WRAP).apply { topMargin = dp(14) })
        val sr = SeekRow(a, min, max, start) { v -> show(v); set(v) }
        tv.setOnClickListener {
            ColorPickerDialog.askNumber(a, title, unit, min, max, sr.value) { v -> sr.set(v); show(v); set(v) }
        }
        card.addView(sr.view, lp(MATCH, WRAP).apply { topMargin = dp(4) })
    }

    private fun colorPick(title: String, withAlpha: Boolean, get: () -> Int, set: (Int) -> Unit) {
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(a).apply { text = title; textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD) }, lp(0, WRAP).apply { weight = 1f })
        val sw = View(a)
        fun paint() { sw.background = GradientDrawable().apply { setColor(get()); cornerRadius = dp(8).toFloat(); setStroke(dp(1), 0x66FFFFFF) } }
        paint()
        sw.isClickable = true
        sw.setOnClickListener { ColorPickerDialog.show(a, title, get(), withAlpha) { c -> set(c); paint() } }
        row.addView(sw, lp(dp(56), dp(34)))
        card.addView(row, lp(MATCH, WRAP).apply { topMargin = dp(14) })
    }

    private fun switchRow(title: String, desc: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val texts = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(a).apply { text = title; textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD) })
        if (desc.isNotEmpty()) texts.addView(TextView(a).apply { text = desc; textSize = 12f; setTextColor(col(R.color.muted)) })
        row.addView(texts, lp(0, WRAP).apply { weight = 1f })
        row.addView(Switch(a).apply { isChecked = get(); setOnCheckedChangeListener { _, on -> set(on) } })
        card.addView(row, lp(MATCH, WRAP).apply { topMargin = dp(14) })
    }

    // ------------------------------------------------------------------ dựng bảng
    fun build() {
        card = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_card); setPadding(dp(16), dp(16), dp(16), dp(16)) }
        card.addView(TextView(a).apply {
            text = "TÙY CHỌN CHO NHÀ PHÁT TRIỂN"; textSize = 12f; setTextColor(col(R.color.accent)); setTypeface(null, Typeface.BOLD); letterSpacing = 0.08f
        })
        note("Tùy chỉnh dòng chữ \"Created by\" ở cuối bàn phím. Mọi thay đổi lưu ngay. Nhấn liên tiếp 5 lần vào logo SPX để ẩn mục này.")

        // Xem trước (nền tối giống bàn phím)
        val pv = FrameLayout(a).apply {
            background = GradientDrawable().apply { setColor(0xFF0F1420.toInt()); cornerRadius = dp(10).toFloat() }
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        preview = CreditView(a)
        preview.setStyle(st)
        pv.addView(preview, FrameLayout.LayoutParams(MATCH, WRAP))
        card.addView(pv, lp(MATCH, WRAP).apply { topMargin = dp(12) })

        // ---- Nội dung ----
        section("NỘI DUNG")
        tvText = TextView(a).apply {
            textSize = 14f; setTextColor(col(R.color.text)); isClickable = true
            setBackgroundResource(R.drawable.bg_toggle); setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        fun showText() { tvText.text = st.text + "  ✎" }
        showText()
        tvText.setOnClickListener {
            val et = EditText(a).apply {
                setText(st.text); setSelection(text.length); inputType = InputType.TYPE_CLASS_TEXT; setSingleLine()
            }
            val wrap = LinearLayout(a).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(et, lp(MATCH, WRAP)) }
            AlertDialog.Builder(a).setTitle("Nội dung dòng chữ").setView(wrap)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu") { _, _ ->
                    val t = et.text.toString().trim()
                    update { it.copy(text = if (t.isEmpty()) CreditStyle.DEFAULT_TEXT else t.take(120)) }
                    showText()
                }.show()
        }
        card.addView(tvText, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        card.addView(button("ĐẶT LẠI NỘI DUNG MẶC ĐỊNH", false) { update { it.copy(text = CreditStyle.DEFAULT_TEXT) }; showText() },
            lp(MATCH, dp(42)).apply { topMargin = dp(8) })

        // ---- Định dạng chữ ----
        section("ĐỊNH DẠNG CHỮ")
        seek("Kích thước chữ", 6, 30, "sp", { st.sizeSp }) { v -> update { it.copy(sizeSp = v) } }
        colorPick("Màu chữ", true, { st.color }) { c -> update { it.copy(color = c) } }
        label("Font chữ")
        chips(ThemeRender.FAMILY_NAMES, 4, { st.fontFamily }) { i -> update { it.copy(fontFamily = i) } }
        seek("Độ đậm / mảnh", 300, 800, "", { st.weight }) { v -> update { it.copy(weight = v) } }
        label("Kiểu chữ")
        chips(listOf("Thường", "Đậm", "Nghiêng", "Đậm nghiêng"), 4, { st.textStyle }) { i -> update { it.copy(textStyle = i) } }
        switchRow("Gạch chân", "", { st.underline }) { on -> update { it.copy(underline = on) } }
        label("Kiểu chữ hoa / thường")
        chips(listOf("Giữ nguyên", "IN HOA", "in thường"), 3, { st.caps }) { i -> update { it.copy(caps = i) } }
        seek("Khoảng cách giữa các chữ (x0,01 em)", -5, 50, "", { st.letterSpacing }) { v -> update { it.copy(letterSpacing = v) } }
        label("Căn lề")
        chips(listOf("Trái", "Giữa", "Phải"), 3, { st.align }) { i -> update { it.copy(align = i) } }
        seek("Độ đặc của chữ", 10, 100, "%", { st.alpha }) { v -> update { it.copy(alpha = v) } }
        seek("Bóng đổ", 0, 8, "", { st.shadow }) { v -> update { it.copy(shadow = v) } }
        colorPick("Màu bóng đổ", true, { st.shadowColor }) { c -> update { it.copy(shadowColor = c) } }

        // ---- Hiệu ứng chuyển động ----
        section("HIỆU ỨNG XUẤT HIỆN VÀ CHUYỂN ĐỘNG")
        chips(CreditStyle.ANIM_NAMES, 2, { st.anim }) { i -> update { it.copy(anim = i) } }
        seek("Tốc độ chạy / xuất hiện", 1, 100, "", { st.speed }) { v -> update { it.copy(speed = v) } }
        switchRow("Lặp lại hiệu ứng xuất hiện", "Rơi chữ, gõ chữ, hiện dần, trượt vào, phóng to: nghỉ một lúc rồi chạy lại.", { st.repeat }) { on -> update { it.copy(repeat = on) } }

        // ---- Viền & phát sáng ----
        section("VIỀN VÀ QUẦNG SÁNG")
        seek("Độ dày viền chữ", 0, 6, "", { st.outline }) { v -> update { it.copy(outline = v) } }
        colorPick("Màu viền chữ", false, { st.outlineColor }) { c -> update { it.copy(outlineColor = c) } }
        seek("Độ lan của quầng sáng", 0, 20, "dp", { st.glow }) { v -> update { it.copy(glow = v) } }
        colorPick("Màu quầng sáng", false, { st.glowColor }) { c -> update { it.copy(glowColor = c) } }
        switchRow("Quầng sáng nhấp nhô", "Quầng sáng thở sáng dần rồi dịu lại.", { st.glowPulse }) { on -> update { it.copy(glowPulse = on) } }

        // ---- Hiệu ứng ánh sáng ----
        section("HIỆU ỨNG ÁNH SÁNG")
        fun lightRow(title: String, desc: String, bit: Int) =
            switchRow(title, desc, { (st.light and bit) != 0 }) { on -> update { it.copy(light = if (on) it.light or bit else it.light and bit.inv()) } }
        lightRow("Vệt sáng quét", "Một vệt sáng chạy ngang qua dòng chữ.", CreditStyle.LIGHT_SWEEP)
        lightRow("Chữ cầu vồng", "Màu chữ chuyển động theo dải cầu vồng.", CreditStyle.LIGHT_RAINBOW)
        lightRow("Chuyển màu", "Chuyển động giữa màu chữ và màu thứ hai bên dưới.", CreditStyle.LIGHT_GRADIENT)
        colorPick("Màu thứ hai (chuyển màu)", false, { st.color2 }) { c -> update { it.copy(color2 = c) } }
        lightRow("Nhấp nháy", "Chữ sáng tối luân phiên nhẹ nhàng.", CreditStyle.LIGHT_BLINK)
        seek("Tốc độ hiệu ứng ánh sáng", 1, 100, "", { st.lightSpeed }) { v -> update { it.copy(lightSpeed = v) } }

        card.addView(button("KHÔI PHỤC MẶC ĐỊNH", false) {
            AlertDialog.Builder(a).setTitle("Khôi phục mặc định")
                .setMessage("Đưa dòng \"Created by\" về nội dung, kiểu chữ và hiệu ứng ban đầu?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Khôi phục") { _, _ ->
                    st = CreditStyle()
                    p.creditJson = ""
                    host.removeAllViews()
                    DevOptions.build(a, host, p)
                }.show()
        }, lp(MATCH, dp(46)).apply { topMargin = dp(22) })

        host.addView(card, lp(MATCH, WRAP).apply { topMargin = dp(12) })
        Fonts.apply(card)
    }
}
