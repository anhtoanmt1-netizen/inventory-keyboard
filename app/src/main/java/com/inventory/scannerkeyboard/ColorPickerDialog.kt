package com.inventory.scannerkeyboard

import android.app.AlertDialog
import android.content.Context
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
import android.widget.SeekBar
import android.widget.TextView

/** Bảng chọn màu: ô màu nhanh, thanh Sắc độ / Bão hòa / Độ sáng, và nhập mã HEX trực tiếp. */
object ColorPickerDialog {

    private val QUICK = intArrayOf(
        0xFF232A3B.toInt(), 0xFF363F55.toInt(), 0xFF000000.toInt(), 0xFF424242.toInt(), 0xFF9E9E9E.toInt(), 0xFFFFFFFF.toInt(),
        0xFFDC7F62.toInt(), 0xFFE53935.toInt(), 0xFFFF9800.toInt(), 0xFFFFEB3B.toInt(), 0xFF7BD389.toInt(), 0xFF00BFA5.toInt(),
        0xFF29B6F6.toInt(), 0xFF3F51B5.toInt(), 0xFF8E24AA.toInt(), 0xFFEC407A.toInt(), 0xFFFFD6D6.toInt(), 0xFFCFE3FF.toInt()
    )

    fun show(ctx: Context, title: String, initial: Int, onPick: (Int) -> Unit) {
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv)
        var updating = false

        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(4)) }
        val swatch = View(ctx)
        root.addView(swatch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        val hex = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(); hint = "Mã màu HEX, ví dụ DC7F62"
        }
        val hueBar = SeekBar(ctx).apply { max = 360 }
        val satBar = SeekBar(ctx).apply { max = 100 }
        val valBar = SeekBar(ctx).apply { max = 100 }

        fun current() = Color.HSVToColor(hsv)
        fun refresh(fromHex: Boolean) {
            updating = true
            swatch.background = GradientDrawable().apply { setColor(current()); cornerRadius = dp(8).toFloat(); setStroke(dp(1), 0x66888888) }
            hueBar.progress = hsv[0].toInt(); satBar.progress = (hsv[1] * 100).toInt(); valBar.progress = (hsv[2] * 100).toInt()
            if (!fromHex) hex.setText(String.format("%06X", current() and 0xFFFFFF))
            updating = false
        }
        fun bar(label: String, b: SeekBar, apply: (Int) -> Unit) {
            root.addView(TextView(ctx).apply { text = label; textSize = 12f; setPadding(0, dp(8), 0, 0) })
            root.addView(b)
            b.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser && !updating) { apply(p); refresh(false) } }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        bar("Sắc độ", hueBar) { hsv[0] = it.toFloat() }
        bar("Bão hòa", satBar) { hsv[1] = it / 100f }
        bar("Độ sáng", valBar) { hsv[2] = it / 100f }

        // ô màu nhanh
        val grid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        QUICK.toList().chunked(6).forEach { rowColors ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            rowColors.forEach { c ->
                val v = View(ctx).apply {
                    background = GradientDrawable().apply { setColor(c); cornerRadius = dp(6).toFloat(); setStroke(dp(1), 0x66888888) }
                    setOnClickListener { Color.colorToHSV(c, hsv); refresh(false) }
                }
                row.addView(v, LinearLayout.LayoutParams(0, dp(34), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            grid.addView(row)
        }
        root.addView(TextView(ctx).apply { text = "Màu nhanh"; textSize = 12f; setPadding(0, dp(12), 0, 0) })
        root.addView(grid)
        root.addView(hex, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                val t = s.toString().trim().removePrefix("#")
                if (t.length == 6) try { Color.colorToHSV(Color.parseColor("#$t"), hsv); refresh(true) } catch (_: Throwable) { }
            }
        })
        refresh(false)

        AlertDialog.Builder(ctx).setTitle(title).setView(root)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Chọn") { _, _ -> onPick(current()) }
            .show()
    }
}
