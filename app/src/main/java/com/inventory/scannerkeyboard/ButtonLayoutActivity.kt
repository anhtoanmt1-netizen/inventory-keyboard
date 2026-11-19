package com.inventory.scannerkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Màn hình "Tùy chỉnh nút Scan": ba khu vực nút (hàng trên, hàng dưới, nhóm Công cụ), di chuyển nút giữa các
 * khu vực, sắp xếp (kéo thả), tối đa 10 nút mỗi hàng, kích thước, thu phóng, khoảng cách, căn lề,
 * ảnh PNG riêng cho từng nút, xem trước trực tiếp, áp dụng / khôi phục mặc định.
 * Mọi thay đổi tự động lưu (sau khoảng 0,35 giây hoặc khi thoát màn hình) và bàn phím nhận ngay, không cần bấm nút.
 */
class ButtonLayoutActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var saved: ScanLayout
    private lateinit var cfg: ScanLayout
    private var selected: String = "tools"
    private var pickKey: String? = null

    private lateinit var previewHost: LinearLayout
    private val areaBoxes = arrayOfNulls<LinearLayout>(3)
    private lateinit var editorBox: LinearLayout
    private lateinit var pickerBox: LinearLayout
    private lateinit var baseStyle: PanelStyle   // kiểu nút Scan của giao diện đang dùng (để xem trước đúng giao diện thật)
    private lateinit var tvDirty: TextView
    private lateinit var pinned: LinearLayout
    private lateinit var scroll: StableScrollView
    private lateinit var areaBox: LinearLayout
    private var areaTab = 0                 // khu vực đang chỉnh bố cục: 0 hàng trên, 1 hàng dưới, 2 nhóm Công cụ
    private var previewScale = 0f           // tỉ lệ thu nhỏ của xem trước, giữ cố định giữa các lần dựng
    private var previewBig = false
    private var previewPending = false
    private var theme: KbTheme? = null
    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()
    private fun col(id: Int) = ContextCompat.getColor(this, id)

    private val areaRows = Array(3) { ArrayList<View>() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        theme = ThemeStore.load(this).current()
        baseStyle = theme?.scan ?: PanelStyle()
        saved = ScanLayout.fromJson(prefs.scanLayoutJson)
        cfg = saved
        setContentView(buildUi())
        refreshAll()
    }

    // ------------------------------------------------------------------ dựng giao diện
    private fun buildUi(): View {
        val sv = StableScrollView(this).apply { setBackgroundColor(col(R.color.bg)); isFillViewport = true }
        scroll = sv
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        sv.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "Tùy chỉnh nút Scan"; textSize = 20f; setTextColor(col(R.color.text)); setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Sắp xếp, đổi kích thước, thay hình và chuyển các nút giữa hàng trên, hàng dưới (bảng Scan) và nhóm Công cụ. Xem trước ngay bên dưới. Mọi thay đổi được tự động lưu và áp dụng cho bàn phím ngay."
            textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(4), 0, dp(8))
        })
        tvDirty = TextView(this).apply {
            text = ""; textSize = 11f; setTextColor(col(R.color.accent)); visibility = View.GONE
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // --- Xem trước: GHIM cố định phía trên (không cuộn theo), dựng bằng chính mã của bàn phím thật ---
        pinned = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.bg))
            setPadding(0, dp(6), 0, dp(6))
        }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), 0, dp(16), dp(4)) }
        bar.addView(TextView(this).apply {
            text = "XEM TRƯỚC · khớp bàn phím thật"; textSize = 11f; setTextColor(col(R.color.accent))
            setTypeface(null, android.graphics.Typeface.BOLD); letterSpacing = 0.05f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(tvDirty, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) })
        bar.addView(TextView(this).apply {
            text = "PHÓNG TO"; textSize = 10f; setTextColor(col(R.color.text)); setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_toggle); setPadding(dp(10), dp(5), dp(10), dp(5)); isClickable = true
            setOnClickListener { previewBig = !previewBig; previewScale = 0f; text = if (previewBig) "THU NHỎ" else "PHÓNG TO"; refreshPreview() }
        })
        pinned.addView(bar)
        previewHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.bg)) }
        pinned.addView(previewHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // --- Bố cục và vị trí: ba nhóm độc lập (mỗi nhóm một bộ thông số riêng) ---
        val g = card(root, "BỐ CỤC VÀ VỊ TRÍ")
        g.addView(TextView(this).apply {
            text = "Hàng trên, Hàng dưới và Nhóm Công cụ có bộ thông số riêng, độc lập nhau. Chọn một nhóm để chỉnh: thay đổi chỉ áp dụng cho nhóm đó và hiện ngay trên khung xem trước."
            textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(4), 0, dp(2))
        })
        chips(g, listOf("HÀNG TRÊN", "HÀNG DƯỚI", "NHÓM CÔNG CỤ"), { areaTab }) { areaTab = it; rebuildAreaBox() }
        areaBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        g.addView(areaBox)
        rebuildAreaBox()

        // --- Danh sách nút: ba khu vực ---
        val l = card(root, "DANH SÁCH NÚT")
        l.addView(TextView(this).apply {
            text = "Giữ và kéo biểu tượng ≡ (hoặc dùng ▲ ▼) để đổi thứ tự trong khu vực. Bấm ⇄ để chuyển nút sang hàng trên, hàng dưới hoặc nhóm Công cụ."
            textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(4), 0, dp(4))
        })
        val titles = listOf("HÀNG TRÊN (hiện ở mọi chế độ)", "HÀNG DƯỚI (bảng Scan, dưới khung camera)", "NHÓM CÔNG CỤ (mở khi bấm nút Công cụ)")
        for (a in 0..2) {
            l.addView(TextView(this).apply {
                text = titles[a]; textSize = 12f; setTextColor(col(R.color.accent)); setTypeface(null, android.graphics.Typeface.BOLD)
                letterSpacing = 0.05f
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            areaBoxes[a] = box
            l.addView(box)
            if (a == ScanLayout.AREA_TOOLS) {
                l.addView(button("+ THÊM NÚT VÀO CÔNG CỤ", false) { showAddToTools() },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(8) })
            }
        }

        // --- Chỉnh sửa nút: chọn trực tiếp từng nút ở cả ba khu vực, mỗi nút có hình ảnh riêng ---
        val e = card(root, "CHỈNH SỬA NÚT")
        e.addView(TextView(this).apply {
            text = "Chạm một nút bên dưới để chỉnh: thêm hình ảnh riêng, kích thước ảnh, vị trí ảnh và thu phóng. Mỗi nút có hình ảnh riêng, không dùng chung."
            textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(4), 0, dp(4))
        })
        pickerBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        e.addView(pickerBox)
        editorBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        e.addView(editorBox)

        // --- Hành động ---
        root.addView(button("LƯU VÀ ÁP DỤNG NGAY", true) { applyLayout() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(16) })
        root.addView(button("KHÔI PHỤC MẶC ĐỊNH", false) { confirmReset() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })
        Fonts.apply(root)
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.bg)) }
        outer.addView(pinned, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        outer.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return outer
    }

    private fun card(parent: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundResource(R.drawable.bg_card); setPadding(dp(16), dp(16), dp(16), dp(16)) }
        c.addView(TextView(this).apply {
            text = title; textSize = 12f; setTextColor(col(R.color.accent)); setTypeface(null, android.graphics.Typeface.BOLD); letterSpacing = 0.08f
        })
        parent.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        return c
    }

    private fun label(parent: LinearLayout, text: String) {
        parent.addView(TextView(this).apply {
            this.text = text; textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
    }

    private fun button(text: String, primary: Boolean, click: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 14f; gravity = Gravity.CENTER; setTextColor(android.graphics.Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        setBackgroundResource(if (primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_secondary)
        isClickable = true; setOnClickListener { click() }
    }

    /** Hàng nút chọn một trong nhiều (chip). */
    private fun chips(parent: LinearLayout, names: List<String>, current: () -> Int, onPick: (Int) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val views = ArrayList<TextView>()
        names.forEachIndexed { i, n ->
            val t = TextView(this).apply {
                text = n; textSize = 12f; gravity = Gravity.CENTER; setTextColor(col(R.color.text))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_toggle); isClickable = true; isSelected = i == current()
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { views.forEachIndexed { k, v -> v.isSelected = k == i }; onPick(i) }
            }
            views.add(t)
            row.addView(t, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
    }

    /** Thanh trượt có nhãn giá trị, nút − / + ở hai đầu và rung nhẹ mỗi khi giá trị thay đổi. */
    private fun seekRow(
        parent: LinearLayout, title: String, min: Int, max: Int, current: () -> Int, unit: String,
        fmt: ((Int) -> String)? = null, onChange: (Int) -> Unit
    ) {
        val tv = TextView(this).apply { textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, android.graphics.Typeface.BOLD) }
        fun show(v: Int) { tv.text = "$title: " + (fmt?.invoke(v) ?: "$v$unit") }
        show(current())
        parent.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        val sr = SeekRow(this, min, max, current()) { v -> show(v); onChange(v) }
        parent.addView(sr.view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
    }

    // ------------------------------------------------------------------ cập nhật
    private val saveHandler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { persist() }

    /** Có thay đổi: cập nhật xem trước và tự động lưu (áp dụng ngay cho bàn phím) sau một khoảng ngắn. */
    private fun changed() {
        refreshPreview()
        if (cfg != saved) {
            tvDirty.text = "● Đang lưu…"
            tvDirty.visibility = View.VISIBLE
            saveHandler.removeCallbacks(saveRunnable)
            saveHandler.postDelayed(saveRunnable, 350L)
        }
    }

    /** Ghi bố cục vào cài đặt; bàn phím nhận thay đổi ngay (xem listener trong ScannerKeyboardService). */
    private fun persist() {
        if (cfg == saved) return
        prefs.scanLayoutJson = cfg.toJson()
        saved = cfg
        cleanupImages(cfg.usedImages())
        if (::tvDirty.isInitialized) {
            tvDirty.text = "✓ Đã tự động lưu và áp dụng cho bàn phím"
            tvDirty.visibility = View.VISIBLE
        }
    }

    override fun onPause() {
        saveHandler.removeCallbacks(saveRunnable)
        persist()
        super.onPause()
    }

    /** Kiểu nút dùng cho xem trước và danh sách: giao diện đang dùng, kèm bo góc người dùng đang chọn. */
    /** Kiểu nút của giao diện đang dùng; bo góc riêng của từng khu vực được áp khi dựng từng nút. */
    private fun tileStyle(): PanelStyle = baseStyle

    private fun refreshAll() {
        refreshList()
        refreshPicker()
        refreshEditor()
        changed()
    }

    private fun rebuildAreaBox() {
        scroll.keepScroll {
            areaBox.removeAllViews()
            buildAreaSettings(areaBox, areaTab)
            Fonts.apply(areaBox)
        }
    }

    private fun editStyle(area: Int, f: (AreaStyle) -> AreaStyle) {
        cfg = cfg.withStyle(area, f(cfg.styleOf(area)))
        changed()
    }

    private fun switchRow(parent: LinearLayout, title: String, desc: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@ButtonLayoutActivity).apply { text = title; textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, android.graphics.Typeface.BOLD) })
            addView(TextView(this@ButtonLayoutActivity).apply { text = desc; textSize = 12f; setTextColor(col(R.color.muted)) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Switch(this).apply { isChecked = get(); setOnCheckedChangeListener { _, on -> set(on) } })
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
    }

    /** Toàn bộ thông số bố cục và vị trí của MỘT khu vực; không ảnh hưởng hai khu vực còn lại. */
    private fun buildAreaSettings(g: LinearLayout, a: Int) {
        val tools = a == ScanLayout.AREA_TOOLS
        fun st() = cfg.styleOf(a)
        g.addView(TextView(this).apply {
            text = when (a) {
                ScanLayout.AREA_TOP -> "Hàng nút phía trên: hiện ở mọi chế độ (Quét, Gõ phím, Clipboard)."
                ScanLayout.AREA_BOTTOM -> "Hàng nút phía dưới: nằm dưới khung camera ở bảng Quét."
                else -> "Nhóm Công cụ: bảng nổi phía trên bàn phím, mở khi bấm nút Công cụ."
            }
            textSize = 12f; setTextColor(col(R.color.accent)); setPadding(0, dp(10), 0, 0)
        })

        label(g, "SỐ NÚT VÀ KÍCH THƯỚC")
        seekRow(g, if (tools) "Số nút tối đa mỗi hàng" else "Số nút mỗi hàng", 1, ScanLayout.MAX_PER_ROW, { st().perRow }, "") { v -> editStyle(a) { it.copy(perRow = v) } }
        seekRow(g, "Chiều rộng nút", 32, 96, { st().widthDp }, "dp") { v -> editStyle(a) { it.copy(widthDp = v) } }
        seekRow(g, "Chiều cao nút", 32, 96, { st().heightDp }, "dp") { v -> editStyle(a) { it.copy(heightDp = v) } }
        seekRow(g, "Thu phóng của nhóm", 60, 160, { st().scale }, "%") { v -> editStyle(a) { it.copy(scale = v) } }
        seekRow(g, "Bo góc nút", -1, 30, { st().cornerDp }, "dp", { v -> if (v < 0) "theo giao diện" else "$v dp" }) { v -> editStyle(a) { it.copy(cornerDp = v) } }
        g.addView(TextView(this).apply {
            text = "Kéo về 0 để nút vuông góc, tăng lên để bo tròn. Mức \"theo giao diện\" giữ bo góc của giao diện đang dùng."
            textSize = 11f; setTextColor(col(R.color.muted)); setPadding(0, dp(2), 0, 0)
        })

        label(g, "KHOẢNG CÁCH GIỮA CÁC NÚT")
        seekRow(g, "Khoảng cách ngang giữa các nút", 0, 24, { st().gapDp }, "dp") { v -> editStyle(a) { it.copy(gapDp = v) } }
        seekRow(g, "Khoảng cách giữa các hàng nút", 0, 24, { st().rowGapDp }, "dp") { v -> editStyle(a) { it.copy(rowGapDp = v) } }

        label(g, "VỊ TRÍ")
        if (tools) {
            g.addView(TextView(this).apply {
                text = "Vị trí ngang của bảng nổi so với màn hình."; textSize = 11f; setTextColor(col(R.color.muted)); setPadding(0, dp(2), 0, 0)
            })
            chips(g, listOf("Sát trái", "Theo nút", "Sát phải", "Giữa"), { st().align }) { i -> editStyle(a) { it.copy(align = i) } }
            seekRow(g, "Cách mép trên bàn phím", 0, 48, { st().padBottomDp }, "dp") { v -> editStyle(a) { it.copy(padBottomDp = v) } }
        } else {
            chips(g, listOf("Trái", "Giữa", "Phải", "Dàn đều", "Cách đều"), { st().align }) { i -> editStyle(a) { it.copy(align = i) } }
            switchRow(g, "Căn theo cột (lưới)", "Bật: các nút thẳng cột với nhau giữa các hàng. Tắt: mỗi hàng tự căn riêng (Dàn đều / Cách đều chỉ có tác dụng khi tắt).",
                { st().grid }) { on -> editStyle(a) { it.copy(grid = on) } }
            if (a == ScanLayout.AREA_TOP) {
                seekRow(g, "Cách phía trên hàng trên", 0, 48, { st().padTopDp }, "dp") { v -> editStyle(a) { it.copy(padTopDp = v) } }
                seekRow(g, "Cách bàn phím / khung camera (phía dưới hàng trên)", 0, 48, { st().padBottomDp }, "dp") { v -> editStyle(a) { it.copy(padBottomDp = v) } }
            } else {
                seekRow(g, "Cách khung camera / bàn phím (phía trên hàng dưới)", 0, 48, { st().padTopDp }, "dp") { v -> editStyle(a) { it.copy(padTopDp = v) } }
                seekRow(g, "Cách mép dưới (phía dưới hàng dưới)", 0, 48, { st().padBottomDp }, "dp") { v -> editStyle(a) { it.copy(padBottomDp = v) } }
            }
        }
        seekRow(g, "Dịch ngang", -80, 80, { st().offsetXDp }, "dp", { v -> if (v == 0) "giữa" else if (v < 0) "trái ${-v} dp" else "phải $v dp" }) { v -> editStyle(a) { it.copy(offsetXDp = v) } }

        g.addView(button("ĐẶT LẠI NHÓM NÀY VỀ MẶC ĐỊNH", false) {
            cfg = cfg.withStyle(a, when (a) {
                ScanLayout.AREA_TOP -> ScanLayout.DEFAULT_TOP_STYLE
                ScanLayout.AREA_BOTTOM -> ScanLayout.DEFAULT_BOTTOM_STYLE
                else -> ScanLayout.DEFAULT_TOOLS_STYLE
            })
            changed()
            rebuildAreaBox()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(16) })
    }

    /** Xem trước cập nhật ngay mỗi khi có thay đổi (gộp nhiều thay đổi trong cùng một khung hình). */
    private fun refreshPreview() {
        if (previewPending) return
        previewPending = true
        previewHost.post { previewPending = false; renderPreview() }
    }

    private fun renderPreview() {
        previewHost.removeAllViews()
        val dm = resources.displayMetrics
        // Khung xem trước có chiều cao CỐ ĐỊNH: không nhảy khi thay đổi thông số, không đẩy vùng điều chỉnh bên dưới
        val frameH = (dm.heightPixels * (if (previewBig) 0.45f else 0.30f)).toInt()
        try {
            val opts = KbPreview.Opts(
                scan = true, type = false, toolsOpen = true,
                onPick = { k -> selected = k; scroll.keepScroll { refreshList(); refreshPicker(); refreshEditor() } },
                maxHeightPx = frameH, frameHeightPx = frameH, fixedScale = previewScale,
                onScale = { previewScale = it }
            )
            val v = KbPreview.build(this, opts, cfg, theme, dm.widthPixels)
            previewHost.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, frameH))
        } catch (e: Throwable) {
            previewHost.addView(TextView(this).apply {
                text = "Không dựng được khung xem trước: ${e.message ?: "lỗi không xác định"}"
                textSize = 12f; setTextColor(col(R.color.muted)); setPadding(dp(16), dp(8), dp(16), dp(8))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, frameH))
        }
    }

    // ------------------------------------------------------------------ danh sách nút + kéo thả + chuyển khu vực
    private fun refreshList() { scroll.keepScroll { refreshListImpl() } }

    private fun refreshListImpl() {
        for (a in 0..2) {
            val box = areaBoxes[a] ?: continue
            box.removeAllViews()
            areaRows[a].clear()
            val list = cfg.listFor(a)
            if (list.isEmpty()) box.addView(TextView(this).apply {
                text = "(chưa có nút nào)"; textSize = 12f; setTextColor(col(R.color.muted)); setPadding(dp(4), dp(8), 0, dp(4))
            })
            list.forEachIndexed { idx, key -> box.addView(buildRow(a, idx, key, list.size), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }) }
            Fonts.apply(box)
        }
    }

    private fun buildRow(area: Int, idx: Int, key: String, n: Int): View {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val handle = TextView(this).apply {
            text = "≡"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted))
        }
        outer.addView(handle, LinearLayout.LayoutParams(dp(34), dp(60)))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        controls.addView(arrow("▲", idx > 0) { move(area, idx, idx - 1) }, LinearLayout.LayoutParams(dp(30), dp(40)))
        controls.addView(arrow("▼", idx < n - 1) { move(area, idx, idx + 1) }, LinearLayout.LayoutParams(dp(30), dp(40)))
        controls.addView(arrow("⇄", true) { showMoveMenu(key) }, LinearLayout.LayoutParams(dp(34), dp(40)))
        val card = ButtonPicker.richRow(this, cfg, key, tileStyle(), null, null)
        card.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (key == selected) card.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat(); setColor(col(R.color.surface2)); setStroke(dp(2), col(R.color.accent))
        }
        card.isClickable = true
        card.setOnClickListener { selected = key; refreshList(); refreshPicker(); refreshEditor() }
        outer.addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        areaRows[area].add(outer)
        attachDrag(handle, area, idx)
        return outer
    }

    private fun arrow(t: String, enabled: Boolean, click: () -> Unit) = TextView(this).apply {
        text = t; textSize = 15f; gravity = Gravity.CENTER; setTextColor(col(R.color.text)); alpha = if (enabled) 1f else 0.25f
        isClickable = enabled; setOnClickListener { if (enabled) click() }
    }

    private fun move(area: Int, from: Int, to: Int) {
        val o = cfg.listFor(area).toMutableList()
        val k = o.removeAt(from)
        o.add(to.coerceIn(0, o.size), k)
        cfg = cfg.withList(area, o)
        refreshList(); changed()
    }

    private val areaNames = listOf("Hàng trên", "Hàng dưới (bảng Scan)", "Nhóm Công cụ")

    /** Chuyển một nút sang khu vực khác. Nút Công cụ không thể nằm trong chính nhóm Công cụ. */
    private fun showMoveMenu(key: String) {
        ButtonPicker.showMoveTo(this, key, cfg, tileStyle()) { area ->
            cfg = cfg.moved(key, area)
            selected = key
            refreshAll()
        }
    }

    /** Danh sách trực quan các nút có thể thêm vào nhóm Công cụ. */
    private fun showAddToTools() {
        ButtonPicker.showAddToTools(this, cfg, tileStyle()) { k ->
            cfg = cfg.moved(k, ScanLayout.AREA_TOOLS)
            selected = k
            refreshAll()
        }
    }

    /** Bảng chọn trực tiếp từng nút để chỉnh: gom theo ba khu vực, mỗi ô là đúng hình nút thật. */
    private fun refreshPicker() { scroll.keepScroll { refreshPickerImpl() } }

    private fun refreshPickerImpl() {
        if (!::pickerBox.isInitialized) return
        pickerBox.removeAllViews()
        val perRow = 4
        for (area in 0..2) {
            pickerBox.addView(TextView(this).apply {
                text = ScanLayout.AREA_NAMES[area].uppercase() + "  (" + cfg.listFor(area).size + ")"
                textSize = 11f; setTextColor(col(R.color.accent)); setTypeface(null, android.graphics.Typeface.BOLD); letterSpacing = 0.05f
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            val keys = cfg.listFor(area)
            if (keys.isEmpty()) pickerBox.addView(TextView(this).apply {
                text = "(chưa có nút nào)"; textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(6), 0, dp(2))
            })
            for (chunk in keys.chunked(perRow)) {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                for (k in chunk) {
                    val cell = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(dp(2), dp(6), dp(2), dp(6))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(12).toFloat()
                            setColor(if (k == selected) 0x22EE4D2D else Color.TRANSPARENT)
                            setStroke(dp(if (k == selected) 2 else 1), if (k == selected) col(R.color.accent) else col(R.color.stroke))
                        }
                        isClickable = true
                        setOnClickListener { selected = k; refreshList(); refreshPicker(); refreshEditor() }
                    }
                    cell.addView(ScanButtons.tile(this, cfg, k, 42, tileStyle()), LinearLayout.LayoutParams(dp(42), dp(42)))
                    cell.addView(TextView(this).apply {
                        text = ScanLayout.LABELS[k] ?: k
                        textSize = 10f; gravity = Gravity.CENTER; maxLines = 2
                        setTextColor(col(if (k == selected) R.color.text else R.color.muted))
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
                    row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
                }
                repeat(perRow - chunk.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }) }
                pickerBox.addView(row)
            }
        }
        Fonts.apply(pickerBox)
    }

    /** Kéo thả trong một khu vực: hàng đang kéo đi theo ngón tay, các hàng khác nhường chỗ; thả tay thì chốt vị trí mới. */
    private fun attachDrag(handle: View, area: Int, origIdx: Int) {
        var startY = 0f
        var desired = origIdx
        handle.setOnTouchListener { v, e ->
            val rows = areaRows[area]
            val n = rows.size
            if (origIdx >= n) return@setOnTouchListener true
            val rowH = (rows[origIdx].height + dp(6)).toFloat()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY; desired = origIdx
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    rows[origIdx].elevation = dp(8).toFloat()
                    rows[origIdx].alpha = 0.85f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - startY
                    rows[origIdx].translationY = dy.coerceIn(-origIdx * rowH, (n - 1 - origIdx) * rowH)
                    desired = (origIdx + (dy / rowH).roundToInt()).coerceIn(0, n - 1)
                    for (i in 0 until n) {
                        if (i == origIdx) continue
                        rows[i].translationY = when {
                            origIdx < desired && i in (origIdx + 1)..desired -> -rowH
                            origIdx > desired && i in desired until origIdx -> rowH
                            else -> 0f
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    for (r in rows) { r.translationY = 0f; r.alpha = 1f; r.elevation = 0f }
                    if (e.actionMasked == MotionEvent.ACTION_UP && desired != origIdx) move(area, origIdx, desired)
                    true
                }
                else -> true
            }
        }
    }

    // ------------------------------------------------------------------ chỉnh riêng từng nút
    private fun refreshEditor() { scroll.keepScroll { refreshEditorImpl() } }

    private fun refreshEditorImpl() {
        editorBox.removeAllViews()
        val key = selected
        val c = cfg.cfg(key)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(col(R.color.surface)); setStroke(dp(1), col(R.color.stroke)) }
        }
        editorBox.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        val moveBtn = button("CHUYỂN", false) { showMoveMenu(key) }
        moveBtn.textSize = 11f
        box.addView(
            ButtonPicker.richRow(this, cfg, key, tileStyle(), "Đang ở: " + ScanLayout.AREA_NAMES[cfg.areaOf(key)], moveBtn),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        label(box, "Hình ảnh riêng cho nút này")
        box.addView(TextView(this).apply {
            text = if (c.image != null) "Nút đang dùng hình ảnh riêng. Bạn có thể đổi hoặc bỏ để quay về biểu tượng mặc định."
            else "Chưa có hình ảnh riêng: nút đang dùng biểu tượng mặc định. Thêm ảnh PNG (nên nền trong suốt) để thay."
            textSize = 11f; setTextColor(col(R.color.muted)); setPadding(0, dp(2), 0, 0)
        })

        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btns.addView(button(if (c.image != null) "ĐỔI HÌNH ẢNH" else "THÊM HÌNH ẢNH", true) { pickImage(key) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, 0, dp(4), 0) })
        if (c.image != null) btns.addView(button("BỎ ẢNH", false) {
            cfg = cfg.withButton(key, c.copy(image = null, imgW = 0, imgH = 0)); refreshAll()
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(4), 0, 0, 0) })
        box.addView(btns, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        if (c.image != null) {
            val (bw, bh) = ScanButtons.sizePx(this, cfg, key)
            label(box, "Kích thước ảnh (px) · nút hiện rộng $bw × cao $bh px")
            val sizes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            fun field(hint: String, value: Int, set: (Int) -> Unit): EditText = EditText(this).apply {
                this.hint = hint; inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine()
                setTextColor(col(R.color.text)); setHintTextColor(col(R.color.muted)); textSize = 15f
                setBackgroundResource(R.drawable.bg_input); setPadding(dp(12), dp(10), dp(12), dp(10))
                if (value > 0) setText(value.toString())
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
                    override fun afterTextChanged(s: Editable?) { set(s.toString().toIntOrNull()?.coerceIn(0, 400) ?: 0) }
                })
            }
            sizes.addView(field("Rộng (để trống = vừa nút)", c.imgW) { v -> cfg = cfg.withButton(key, cfg.cfg(key).copy(imgW = v)); changed() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(4), 0) })
            sizes.addView(field("Cao (để trống = vừa nút)", c.imgH) { v -> cfg = cfg.withButton(key, cfg.cfg(key).copy(imgH = v)); changed() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), 0, 0, 0) })
            box.addView(sizes, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            box.addView(TextView(this).apply {
                text = "Ảnh luôn giữ đúng tỉ lệ, không bị méo; không vượt quá kích thước nút."
                textSize = 11f; setTextColor(col(R.color.muted)); setPadding(0, dp(4), 0, 0)
            })
            label(box, "Vị trí ảnh trong nút: ngang")
            chips(box, listOf("Trái", "Giữa", "Phải"), { cfg.cfg(key).imgH_align }) { cfg = cfg.withButton(key, cfg.cfg(key).copy(imgH_align = it)); changed() }
            label(box, "Vị trí ảnh trong nút: dọc")
            chips(box, listOf("Trên", "Giữa", "Dưới"), { cfg.cfg(key).imgV_align }) { cfg = cfg.withButton(key, cfg.cfg(key).copy(imgV_align = it)); changed() }
        }
        seekRow(box, "Thu phóng riêng của nút", 50, 200, { cfg.cfg(key).scale }, "%") { cfg = cfg.withButton(key, cfg.cfg(key).copy(scale = it)); changed() }
        Fonts.apply(box)
    }

    // ------------------------------------------------------------------ chọn ảnh PNG
    private fun pickImage(key: String) {
        pickKey = key
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png"), 77
            )
        } catch (_: Throwable) { Toast.makeText(this, "Không mở được bộ chọn ảnh.", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val key = pickKey ?: return
        val uri = data?.data
        if (requestCode != 77 || resultCode != RESULT_OK || uri == null) return
        val name = importImage(key, uri)
        if (name == null) { Toast.makeText(this, "Không đọc được ảnh PNG này.", Toast.LENGTH_SHORT).show(); return }
        cfg = cfg.withButton(key, cfg.cfg(key).copy(image = name, imgW = 0, imgH = 0))
        selected = key
        refreshAll()
    }

    /** Đọc ảnh, thu nhỏ nếu quá lớn (tối đa 512px) và lưu lại thành PNG (giữ nền trong suốt). */
    private fun importImage(key: String, uri: Uri): String? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
            var s = 1
            while (opts.outWidth / (s * 2) >= 512 || opts.outHeight / (s * 2) >= 512) s *= 2
            val bmp: Bitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
            } ?: return null
            val name = "btn_${key}_${System.currentTimeMillis()}.png"
            FileOutputStream(File(ScanButtons.imageDir(this), name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            name
        } catch (_: Throwable) { null }
    }

    private fun cleanupImages(keep: Set<String>) {
        try { ScanButtons.imageDir(this).listFiles()?.forEach { if (it.name !in keep) it.delete() } } catch (_: Throwable) { }
    }

    // ------------------------------------------------------------------ áp dụng / khôi phục / thoát
    private fun applyLayout() {
        saveHandler.removeCallbacks(saveRunnable)
        persist()
        Toast.makeText(this, "Đã lưu và áp dụng ngay cho bàn phím.", Toast.LENGTH_SHORT).show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Khôi phục mặc định?")
            .setMessage("Bố cục, kích thước và ảnh riêng của các nút sẽ trở về ban đầu. Các cài đặt khác của ứng dụng không bị ảnh hưởng.")
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Khôi phục") { _, _ ->
                cfg = ScanLayout(); saved = cfg
                prefs.scanLayoutJson = ""
                ScanButtons.forgetBitmaps()
                cleanupImages(emptySet())
                recreate() // dựng lại toàn bộ màn hình để các thanh trượt về đúng giá trị mặc định
            }.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        persist()   // mọi thay đổi đã được tự động lưu; thoát là xong
        super.onBackPressed()
    }
}
