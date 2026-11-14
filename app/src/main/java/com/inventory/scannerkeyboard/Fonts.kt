package com.inventory.scannerkeyboard

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * Áp dụng font Montserrat cho mọi TextView/Button/EditText.
 * File font nằm ở assets/fonts/Montserrat.ttf (font biến thiên, được tải tự động khi build trên GitHub).
 * Nếu không có file font thì giữ font mặc định của hệ thống, ứng dụng vẫn chạy bình thường.
 */
object Fonts {
    private var regular: Typeface? = null
    private var bold: Typeface? = null
    private var loaded = false

    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val am = ctx.applicationContext.assets
            regular = Typeface.Builder(am, "fonts/Montserrat.ttf")
                .setFontVariationSettings("'wght' 500").build()
            bold = Typeface.Builder(am, "fonts/Montserrat.ttf")
                .setFontVariationSettings("'wght' 700").build()
        } catch (_: Throwable) { /* không có font: dùng mặc định */ }
    }

    fun apply(root: View) {
        load(root.context)
        val r = regular ?: return
        walk(root, r, bold ?: r)
    }

    private fun walk(v: View, r: Typeface, b: Typeface) {
        if (v is TextView) {
            val wasBold = v.tag == "bold" || v.typeface?.isBold == true
            if (wasBold) v.tag = "bold"
            v.typeface = if (wasBold) b else r
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), r, b)
    }
}
