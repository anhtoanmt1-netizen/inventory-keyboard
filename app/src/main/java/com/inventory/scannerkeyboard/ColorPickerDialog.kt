package com.inventory.scannerkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/** Hộp thoại chọn màu (thanh trượt sắc độ / bão hòa / sáng, ô nhập mã HEX, màu mẫu) và hộp thoại nhập số chính xác. */
object ColorPickerDialog {
    private val PRESETS = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFE4E8F1.toInt(), 0xFF9AA3B8.toInt(), 0xFF363F55.toInt(), 0xFF232A3B.toInt(), 0xFF000000.toInt(),
        0xFFEE4D2D.toInt(), 0xFFDC7F62.toInt(), 0xFFFFB84D.toInt(), 0xFFFFE066.toInt(), 0xFF7BD389.toInt(), 0xFF2EC4B6.toInt(),
        0xFF4DA3FF.toInt(), 0xFF3A6BFF.toInt(), 0xFF8E6BFF.toInt(), 0xFFFF6BCB.toInt(), 0xFFFF4D6D.toInt(), 0xFF00E5FF.toInt()
    )

    private fun hex(c: Int, withAlpha: Boolean): String =
        if (withAlpha) String.format("#%08X", c) else String.format("#%06X", c and 0xFFFFFF)

    /**
     * Chọn màu. onChange được gọi liên tục khi kéo (để xem trước trực tiếp);
     * bấm Hủy thì trả về màu ban đầu.
     */
    fun show(activity: Activity, title: String, initial: Int, withAlpha: Boolean, onChange: (Int) -> Unit) {
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv)
        var alpha = if (withAlpha) Color.alpha(initial) else 255
        var updating = false

        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(4)) }

        val swatch = View(activity)
        box.addView(swatch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))

        val hexBox = EditText(activity).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            textSize = 14f
            hint = if (withAlpha) "#AARRGGBB" else "#RRGGBB"
        }
        box.addView(hexBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        fun current(): Int = Color.HSVToColor(alpha, hsv)

        val sbH = SeekBar(activity).apply { max = 360 }
        val sbS = SeekBar(activity).apply { max = 100 }
        val sbV = SeekBar(activity).apply { max = 100 }
        val sbA = SeekBar(activity).apply { max = 100 }

        fun refresh(fromHex: Boolean) {
            updating = true
            val c = current()
            swatch.background = GradientDrawable().apply { setColor(c); cornerRadius = dp(10).toFloat(); setStroke(dp(1), 0x66FFFFFF) }
            sbH.progress = hsv[0].toInt().coerceIn(0, 360)
            sbS.progress = (hsv[1] * 100).toInt().coerceIn(0, 100)
            sbV.progress = (hsv[2] * 100).toInt().coerceIn(0, 100)
            sbA.progress = (alpha * 100 / 255).coerceIn(0, 100)
            if (!fromHex) hexBox.setText(hex(c, withAlpha))
            updating = false
            onChange(c)
        }

        fun label(text: String) = box.addView(TextView(activity).apply {
            this.text = text; textSize = 12f; setTextColor(Color.LTGRAY)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        fun bar(sb: SeekBar, name: String, set: (Int) -> Unit) {
            label(name)
            box.addView(sb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser && !updating) { set(p); refresh(false) } }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        bar(sbH, "Màu (sắc độ)") { hsv[0] = it.toFloat().coerceIn(0f, 359.99f) }
        bar(sbS, "Độ đậm màu (bão hòa)") { hsv[1] = it / 100f }
        bar(sbV, "Độ sáng") { hsv[2] = it / 100f }
        if (withAlpha) bar(sbA, "Độ trong suốt") { alpha = (it * 255 / 100).coerceIn(0, 255) }

        hexBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                val t = s?.toString()?.trim() ?: return
                val ok = t.length == 7 || (withAlpha && t.length == 9)
                if (!ok || !t.startsWith("#")) return
                try {
                    val c = Color.parseColor(t)
                    Color.colorToHSV(c, hsv)
                    alpha = if (withAlpha) Color.alpha(c) else 255
                    refresh(true)
                } catch (_: Throwable) { }
            }
        })

        label("Màu mẫu")
        val grid = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        for (rowColors in PRESETS.toList().chunked(6)) {
            val r = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            for (pc in rowColors) {
                val dot = View(activity).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(pc); setStroke(dp(1), 0x66FFFFFF) }
                    setOnClickListener {
                        Color.colorToHSV(pc, hsv)
                        alpha = if (withAlpha) alpha else 255
                        refresh(false)
                    }
                }
                r.addView(dot, LinearLayout.LayoutParams(0, dp(34), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            grid.addView(r)
        }
        box.addView(grid)

        refresh(false)
        val sv = ScrollView(activity).apply { addView(box) }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(sv)
            .setNegativeButton("Hủy") { _, _ -> onChange(initial) }
            .setPositiveButton("Chọn") { _, _ -> onChange(current()) }
            .setOnCancelListener { onChange(initial) }
            .show()
    }

    /** Nhập trực tiếp một số trong khoảng cho phép. */
    fun askNumber(activity: Activity, title: String, unit: String, min: Int, max: Int, current: Int, onValue: (Int) -> Unit) {
        val d = activity.resources.displayMetrics.density
        val et = EditText(activity).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(current.toString())
            setSelection(text.length)
            gravity = Gravity.CENTER
        }
        val wrap = LinearLayout(activity).apply { setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0); addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage("Nhập giá trị từ $min đến $max$unit")
            .setView(wrap)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("OK") { _, _ ->
                val v = et.text.toString().trim().toIntOrNull()
                if (v != null) onValue(v.coerceIn(min, max))
            }.show()
    }
}
