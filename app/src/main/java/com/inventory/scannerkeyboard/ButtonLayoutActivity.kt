package com.inventory.scannerkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
 * Màn hình "Tùy chỉnh nút Scan": sắp xếp (kéo thả), số nút mỗi hàng, kích thước, thu phóng, khoảng cách,
 * căn lề, ảnh PNG riêng cho từng nút, xem trước trực tiếp, áp dụng / khôi phục mặc định.
 * Mọi thay đổi chỉ có hiệu lực trên bàn phím sau khi bấm ÁP DỤNG.
 */
class ButtonLayoutActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var saved: ScanLayout
    private lateinit var cfg: ScanLayout
    private var selected: String = "tools"
    private var pickKey: String? = null

    private lateinit var previewHost: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var editorBox: LinearLayout
    private lateinit var tvDirty: TextView
    private val d by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * d).toInt()
    private fun col(id: Int) = ContextCompat.getColor(this, id)

    private val rowViews = ArrayList<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        saved = ScanLayout.fromJson(prefs.scanLayoutJson)
        cfg = saved
        setContentView(buildUi())
        refreshAll()
    }

    // ------------------------------------------------------------------ dựng giao diện
    private fun buildUi(): View {
        val sv = ScrollView(this).apply { setBackgroundColor(col(R.color.bg)); isFillViewport = true }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        sv.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "Tùy chỉnh nút Scan"; textSize = 20f; setTextColor(col(R.color.text)); setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Sắp xếp, đổi kích thước và thay hình các nút ở hàng chức năng dưới bảng Scan. Xem trước ngay bên dưới, bấm ÁP DỤNG để dùng trên bàn phím."
            textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(4), 0, dp(8))
        })
        tvDirty = TextView(this).apply {
            text = "● Có thay đổi chưa áp dụng"; textSize = 12f; setTextColor(col(R.color.accent)); visibility = View.GONE
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(tvDirty)

        // --- Xem trước: kéo rộng hết chiều ngang như bàn phím thật ---
        val pv = card(root, "XEM TRƯỚC")
        (pv.layoutParams as LinearLayout.LayoutParams).setMargins(-dp(16), dp(8), -dp(16), 0)
        pv.setPadding(0, dp(16), 0, dp(8))
        (pv.getChildAt(0) as TextView).setPadding(dp(16), 0, 0, dp(8))
        previewHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(col(R.color.bg)) }
        pv.addView(previewHost, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // --- Bố cục chung ---
        val g = card(root, "BỐ CỤC CHUNG")
        label(g, "Số nút trên mỗi hàng")
        chips(g, listOf("1", "2", "3", "4", "5"), { cfg.perRow - 1 }) { cfg = cfg.copy(perRow = it + 1); changed() }
        seekRow(g, "Chiều rộng nút", 32, 96, { cfg.widthDp }, "dp") { cfg = cfg.copy(widthDp = it); changed() }
        seekRow(g, "Chiều cao nút", 32, 96, { cfg.heightDp }, "dp") { cfg = cfg.copy(heightDp = it); changed() }
        seekRow(g, "Thu phóng chung", 60, 160, { cfg.scale }, "%") { cfg = cfg.copy(scale = it); changed() }
        seekRow(g, "Khoảng cách ngang giữa các nút", 0, 24, { cfg.gapDp }, "dp") { cfg = cfg.copy(gapDp = it); changed() }
        label(g, "Căn lề")
        chips(g, listOf("Trái", "Giữa", "Phải", "Dàn đều", "Cách đều"), { cfg.align }) { cfg = cfg.copy(align = it); changed() }
        val gridRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        gridRow.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@ButtonLayoutActivity).apply { text = "Căn theo cột (lưới)"; textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, android.graphics.Typeface.BOLD) })
            addView(TextView(this@ButtonLayoutActivity).apply { text = "Bật: các nút thẳng cột với nhau giữa các hàng. Tắt: mỗi hàng tự căn riêng (Dàn đều / Cách đều chỉ có tác dụng khi tắt)."; textSize = 12f; setTextColor(col(R.color.muted)) })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = Switch(this).apply { isChecked = cfg.grid; setOnCheckedChangeListener { _, on -> cfg = cfg.copy(grid = on); changed() } }
        gridRow.addView(sw)
        g.addView(gridRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })

        // --- Danh sách nút ---
        val l = card(root, "DANH SÁCH NÚT")
        l.addView(TextView(this).apply {
            text = "Giữ và kéo biểu tượng ≡ (hoặc dùng ▲ ▼) để đổi thứ tự. Chạm một nút để chỉnh hình ảnh và thu phóng riêng."
            textSize = 12f; setTextColor(col(R.color.muted)); setPadding(0, dp(4), 0, dp(4))
        })
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        l.addView(listBox)
        editorBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        l.addView(editorBox)

        // --- Hành động ---
        root.addView(button("ÁP DỤNG", true) { applyLayout() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(16) })
        root.addView(button("KHÔI PHỤC MẶC ĐỊNH", false) { confirmReset() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(8) })
        Fonts.apply(root)
        return sv
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

    /** Thanh trượt có nhãn hiển thị giá trị. */
    private fun seekRow(parent: LinearLayout, title: String, min: Int, max: Int, current: () -> Int, unit: String, onChange: (Int) -> Unit) {
        val tv = TextView(this).apply { textSize = 14f; setTextColor(col(R.color.text)); setTypeface(null, android.graphics.Typeface.BOLD) }
        fun show(v: Int) { tv.text = "$title: $v$unit" }
        show(current())
        parent.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        val sb = SeekBar(this).apply {
            this.max = max - min; progress = current() - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) { show(p + min); onChange(p + min) } }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        parent.addView(sb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
    }

    // ------------------------------------------------------------------ cập nhật
    private fun changed() {
        refreshPreview()
        tvDirty.visibility = if (cfg != saved) View.VISIBLE else View.GONE
    }

    private fun refreshAll() {
        refreshList()
        refreshEditor()
        changed()
    }

    private fun refreshPreview() {
        previewHost.removeAllViews()
        previewHost.addView(ScanButtons.buildRows(this, cfg, ScanButtons.makeAll(this, cfg, ThemeStore(this).active().scan)))
    }

    // ------------------------------------------------------------------ danh sách nút + kéo thả
    private fun refreshList() {
        listBox.removeAllViews()
        rowViews.clear()
        val n = cfg.order.size
        cfg.order.forEachIndexed { idx, key ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(col(R.color.surface2))
                    if (key == selected) setStroke(dp(2), col(R.color.accent))
                }
                isClickable = true
                setOnClickListener { selected = key; refreshList(); refreshEditor() }
            }
            val handle = TextView(this).apply {
                text = "≡"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted))
            }
            row.addView(handle, LinearLayout.LayoutParams(dp(48), dp(52)))
            row.addView(ImageView(this).apply {
                setImageResource(ScanLayout.ICONS[key] ?: R.drawable.ic_tune)
                imageTintList = ContextCompat.getColorStateList(this@ButtonLayoutActivity, R.color.icon_tint)
            }, LinearLayout.LayoutParams(dp(26), dp(26)))
            row.addView(TextView(this).apply {
                text = ScanLayout.LABELS[key] + if (cfg.cfg(key).image != null) "  (ảnh riêng)" else ""
                textSize = 14f; setTextColor(col(R.color.text)); setPadding(dp(10), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(arrow("▲", idx > 0) { move(idx, idx - 1) }, LinearLayout.LayoutParams(dp(40), dp(40)))
            row.addView(arrow("▼", idx < n - 1) { move(idx, idx + 1) }, LinearLayout.LayoutParams(dp(40), dp(40)))
            listBox.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(6) })
            rowViews.add(row)
            attachDrag(handle, idx)
        }
        Fonts.apply(listBox)
    }

    private fun arrow(t: String, enabled: Boolean, click: () -> Unit) = TextView(this).apply {
        text = t; textSize = 14f; gravity = Gravity.CENTER; setTextColor(col(R.color.text)); alpha = if (enabled) 1f else 0.25f
        isClickable = enabled; setOnClickListener { if (enabled) click() }
    }

    private fun move(from: Int, to: Int) {
        val o = cfg.order.toMutableList()
        val k = o.removeAt(from)
        o.add(to.coerceIn(0, o.size), k)
        cfg = cfg.copy(order = o)
        refreshList(); changed()
    }

    /** Kéo thả: hàng đang kéo đi theo ngón tay, các hàng khác nhường chỗ; thả tay thì chốt vị trí mới. */
    private fun attachDrag(handle: View, origIdx: Int) {
        var startY = 0f
        var desired = origIdx
        handle.setOnTouchListener { v, e ->
            val n = rowViews.size
            val rowH = (rowViews[origIdx].height + dp(6)).toFloat()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY; desired = origIdx
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    rowViews[origIdx].elevation = dp(8).toFloat()
                    rowViews[origIdx].alpha = 0.85f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - startY
                    rowViews[origIdx].translationY = dy.coerceIn(-origIdx * rowH, (n - 1 - origIdx) * rowH)
                    desired = (origIdx + (dy / rowH).roundToInt()).coerceIn(0, n - 1)
                    for (i in 0 until n) {
                        if (i == origIdx) continue
                        rowViews[i].translationY = when {
                            origIdx < desired && i in (origIdx + 1)..desired -> -rowH
                            origIdx > desired && i in desired until origIdx -> rowH
                            else -> 0f
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    for (r in rowViews) { r.translationY = 0f; r.alpha = 1f; r.elevation = 0f }
                    if (e.actionMasked == MotionEvent.ACTION_UP && desired != origIdx) move(origIdx, desired)
                    true
                }
                else -> true
            }
        }
    }

    // ------------------------------------------------------------------ chỉnh riêng từng nút
    private fun refreshEditor() {
        editorBox.removeAllViews()
        val key = selected
        val c = cfg.cfg(key)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(col(R.color.surface)); setStroke(dp(1), col(R.color.stroke)) }
        }
        editorBox.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        box.addView(TextView(this).apply {
            text = "Chỉnh nút: ${ScanLayout.LABELS[key]}"; textSize = 14f; setTextColor(col(R.color.accent)); setTypeface(null, android.graphics.Typeface.BOLD)
        })

        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btns.addView(button("CHỌN ẢNH PNG", true) { pickImage(key) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(0, 0, dp(4), 0) })
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
        prefs.scanLayoutJson = cfg.toJson()
        saved = cfg
        cleanupImages(cfg.usedImages())
        changed()
        Toast.makeText(this, "Đã áp dụng. Mở lại bàn phím để thấy thay đổi.", Toast.LENGTH_SHORT).show()
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
        if (cfg == saved) { super.onBackPressed(); return }
        AlertDialog.Builder(this)
            .setTitle("Có thay đổi chưa áp dụng")
            .setNegativeButton("Ở lại", null)
            .setNeutralButton("Bỏ thay đổi") { _, _ -> cleanupImages(saved.usedImages()); finish() }
            .setPositiveButton("Áp dụng") { _, _ -> applyLayout(); finish() }
            .show()
    }
}
