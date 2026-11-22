package com.inventory.scannerkeyboard

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Mã dựng bàn phím DÙNG CHUNG cho bàn phím thật ([ScannerKeyboardService]) và các khung xem trước trong màn hình
 * tùy chỉnh. Cả hai cùng gọi những hàm này nên kích thước, nền, bo góc, khoảng cách, phím, phím Space, hiệu ứng
 * đều giống nhau tuyệt đối: giá trị đang xem trước chính là giá trị sẽ có trên bàn phím sau khi áp dụng.
 */
object KbMetrics {
    const val KEY_ROW_MIN = 36
    const val KEY_ROW_MAX = 72

    fun autoRowScale(ctx: Context, st: PanelStyle): Float {
        if (!st.layoutAuto) return 1f
        val m = ctx.resources.displayMetrics
        return (minOf(m.widthPixels, m.heightPixels) / m.density / 360f).coerceIn(0.9f, 1.15f)
    }

    /** Chiều cao một hàng phím (px) theo cài đặt "chiều cao hàng phím" và giao diện. */
    fun rowHeightPx(ctx: Context, prefs: Prefs, st: PanelStyle): Int {
        val d = ctx.resources.displayMetrics.density
        return (((prefs.keyRowDp.coerceIn(KEY_ROW_MIN, KEY_ROW_MAX) * autoRowScale(ctx, st)).toInt()) * d).toInt()
    }
}

/** Gắn thao tác thật cho từng loại phím. Bản xem trước không truyền binder nên các phím chỉ để hiển thị. */
interface KeyBinder {
    fun onChar(v: View, c: Char, letter: Boolean)
    fun onLayer(v: View, target: Int)
    fun onShift(v: View)
    fun onVn(v: View)
    fun onSymbol(v: View, s: String)
    fun onEnter(v: View)
    fun onDelete(v: View)
    fun onSpace(v: View)
}

/** Dựng các hàng phím của bảng Gõ phím. */
class TypePanelBuilder(
    private val ctx: Context,
    private val st: PanelStyle,
    private val space: SpaceStyle,
    private val rowPx: Int,
    private val vn: Boolean,
    private val layer: Int,
    private val shift: Int,
    private val binder: KeyBinder?
) {
    val keyViews = ArrayList<View>()
    val letterKeys = ArrayList<Pair<TextView, Char>>()
    var shiftKey: ImageView? = null
    var spaceView: TextView? = null

    private enum class KS { NORMAL, SPECIAL, ACCENT }

    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    fun build(panel: LinearLayout) {
        panel.removeAllViews()
        keyViews.clear()
        letterKeys.clear()
        shiftKey = null
        spaceView = null

        when (layer) {
            0 -> {
                panel.addView(charRow("1234567890", false, false))
                panel.addView(charRow("qwertyuiop", true, false))
                panel.addView(charRow("asdfghjkl", true, true))
                val r = newRow()
                shiftKey = addIconKey(r, R.drawable.ic_shift, 1.5f, KS.SPECIAL) { binder?.onShift(it) }
                "zxcvbnm".forEach { addChar(r, it, true) }
                addIconKey(r, R.drawable.ic_backspace, 1.5f, KS.SPECIAL) { binder?.onDelete(it) }
                panel.addView(r)
            }
            1 -> {
                panel.addView(charRow("1234567890", false, false))
                panel.addView(charRow("@#$%&-+()/", false, false))
                val r = newRow()
                addKey(r, "=\\<", 1.5f, KS.SPECIAL) { binder?.onLayer(it, 2) }
                "*\"':;!?".forEach { addChar(r, it, false) }
                addIconKey(r, R.drawable.ic_backspace, 1.5f, KS.SPECIAL) { binder?.onDelete(it) }
                panel.addView(r)
            }
            else -> {
                panel.addView(charRow("[]{}<>^~|\\", false, false))
                panel.addView(charRow("_=+-*/&%$€", false, false))
                val r = newRow()
                addKey(r, "?123", 1.5f, KS.SPECIAL) { binder?.onLayer(it, 1) }
                ".,?!'\":".forEach { addChar(r, it, false) }
                addIconKey(r, R.drawable.ic_backspace, 1.5f, KS.SPECIAL) { binder?.onDelete(it) }
                panel.addView(r)
            }
        }

        val b = newRow()
        addKey(b, if (layer == 0) "?123" else "ABC", 1.5f, KS.SPECIAL) { binder?.onLayer(it, if (layer == 0) 1 else 0) }
        addKey(b, if (vn) "VN" else "EN", 1.2f, KS.SPECIAL) { binder?.onVn(it) }
        addKey(b, ",", 1f, KS.NORMAL) { binder?.onSymbol(it, ",") }
        addSpaceKey(b)
        addKey(b, ".", 1f, KS.NORMAL) { binder?.onSymbol(it, ".") }
        addIconKey(b, R.drawable.ic_enter, 1.5f, KS.ACCENT) { binder?.onEnter(it) }
        panel.addView(b)

        Fonts.apply(panel)
        applyText()
    }

    /** Màu / font chữ của phím theo giao diện; phím Space có kiểu chữ riêng. */
    private fun applyText() {
        for (k in keyViews) if (k is TextView) {
            if (k === spaceView) SpaceRender.styleLabel(k, st, space, vn) else ThemeRender.styleText(k, st)
        }
    }

    private fun newRow(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowPx)
        setPadding(0, dp(st.gapDp * 2 / 3), 0, dp(st.gapDp * 2 / 3))
        clipChildren = false
        clipToPadding = false
    }

    private fun charRow(chars: String, letters: Boolean, pad: Boolean): LinearLayout {
        val r = newRow()
        if (pad) r.addView(Space(ctx), LinearLayout.LayoutParams(0, 1, 0.5f))
        chars.forEach { addChar(r, it, letters) }
        if (pad) r.addView(Space(ctx), LinearLayout.LayoutParams(0, 1, 0.5f))
        return r
    }

    private fun kindOf(style: KS) = when (style) {
        KS.NORMAL -> ThemeRender.Kind.NORMAL
        KS.SPECIAL -> ThemeRender.Kind.SPECIAL
        KS.ACCENT -> ThemeRender.Kind.ACCENT
    }

    private fun place(row: LinearLayout, v: View, weight: Float) {
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
        lp.setMargins(dp(st.gapDp), 0, dp(st.gapDp), 0)
        row.addView(v, lp)
    }

    private fun addKey(row: LinearLayout, label: String, weight: Float, style: KS, bind: (View) -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setTextColor(ContextCompat.getColor(ctx, R.color.text))
        tv.textSize = (if (label.length > 1) 14f else 19f) * st.fontScale / 100f
        tv.isClickable = true
        tv.background = ThemeRender.keyBg(ctx, st, kindOf(style))
        ThemeRender.elevate(tv, st)
        keyViews.add(tv)
        place(row, tv, weight)
        bind(tv)
        return tv
    }

    /** Phím dùng biểu tượng (kích thước icon luôn 24dp, giống hàng nút ở màn hình quét). */
    private fun addIconKey(row: LinearLayout, iconRes: Int, weight: Float, style: KS, bind: (View) -> Unit): ImageView {
        val iv = ImageView(ctx)
        iv.setImageResource(iconRes)
        ThemeRender.styleIcon(iv, st)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setPadding(dp(13), dp(13), dp(13), dp(13))
        iv.isClickable = true
        iv.background = ThemeRender.keyBg(ctx, st, kindOf(style))
        ThemeRender.elevate(iv, st)
        keyViews.add(iv)
        place(row, iv, weight)
        bind(iv)
        return iv
    }

    private fun addChar(row: LinearLayout, c: Char, letter: Boolean) {
        val label = if (letter && shift > 0) c.uppercaseChar().toString() else c.toString()
        val key = addKey(row, label, 1f, KS.NORMAL) { binder?.onChar(it, c, letter) }
        if (letter) letterKeys.add(key to c)
    }

    private fun addSpaceKey(row: LinearLayout) {
        val sp = space
        val tv = TextView(ctx)
        tv.gravity = Gravity.CENTER
        tv.setSingleLine()
        tv.setTextColor(ContextCompat.getColor(ctx, R.color.text))
        tv.textSize = 14f * st.fontScale / 100f
        tv.isClickable = true
        tv.background = SpaceRender.background(ctx, st, sp)
        ThemeRender.elevate(tv, SpaceRender.shapeStyle(st, sp))
        spaceView = tv
        keyViews.add(tv)
        place(row, tv, sp.weight / 10f)
        binder?.onSpace(tv)
    }
}

/** Áp giao diện cho các khung của bàn phím: bố cục (rộng, căn lề, nâng đáy...), nền, chữ trạng thái. */
object KbLook {
    fun layoutPanel(panel: View, st: PanelStyle, fullPx: Int, d: Float) {
        val lp = panel.layoutParams as? LinearLayout.LayoutParams ?: return
        ThemeRender.applyPanelLayout(lp, st, fullPx, d)
        panel.layoutParams = lp
    }

    fun styleTypePanel(ctx: Context, panelType: LinearLayout, st: PanelStyle, fullPx: Int) {
        layoutPanel(panelType, st, fullPx, ctx.resources.displayMetrics.density)
        panelType.clipChildren = false
        panelType.background = ThemeRender.panelBackground(ctx, st)
    }

    fun styleScanPanel(
        ctx: Context, panelScan: View, tvStatus: TextView, tvHint: TextView,
        st: PanelStyle, fullPx: Int, statusColor0: Int, hintColor0: Int
    ) {
        layoutPanel(panelScan, st, fullPx, ctx.resources.displayMetrics.density)
        panelScan.background = ThemeRender.panelBackground(ctx, st)
        val custom = st.textColor != PanelStyle().textColor
        for ((t, c0) in listOf(tvStatus to statusColor0, tvHint to hintColor0)) {
            Fonts.apply(t)                       // trả về font gốc trước khi áp font theo giao diện
            t.setTextColor(c0)
            t.setShadowLayer(0f, 0f, 0f, 0)
            ThemeRender.styleText(t, st, custom)
        }
    }
}
