package com.inventory.scannerkeyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils

/** Biến một [StyleSet] thành giao diện thật: nền phím, chữ, bố cục bảng. Dùng chung cho bàn phím và Live Preview. */
object KbStyler {

    enum class Kind { NORMAL, SPECIAL, ACCENT, TOGGLE }

    val FONT_NAMES = listOf("Montserrat", "Sans", "Serif", "Mono", "Gọn (Condensed)", "Mảnh (Light)", "Casual", "Viết tay")

    // ------------------------------------------------------------------ nền phím
    private fun px(ctx: Context, dp: Float) = dp * ctx.resources.displayMetrics.density

    private fun body(ctx: Context, s: StyleSet, color: Int): GradientDrawable {
        val g = GradientDrawable()
        if (s.bool("d3")) {
            g.orientation = GradientDrawable.Orientation.TOP_BOTTOM
            g.colors = intArrayOf(ColorUtils.blendARGB(color, Color.WHITE, 0.16f), ColorUtils.blendARGB(color, Color.BLACK, 0.14f))
        } else g.setColor(color)
        g.cornerRadius = px(ctx, s.int("corner").toFloat())
        if (s.bool("borderOn") && s.int("borderW") > 0) g.setStroke(px(ctx, s.int("borderW").toFloat()).toInt().coerceAtLeast(1), s.int("borderColor"))
        return g
    }

    /** Phím dạng nổi: một lớp "đế" tối nằm dưới thân phím; khi nhấn thân phím lún xuống. */
    private fun layered(ctx: Context, s: StyleSet, color: Int, pressed: Boolean): Drawable {
        val depthDp = maxOf(s.int("depth"), if (s.bool("shadowOn")) 2 else 0)
        if (depthDp <= 0) return body(ctx, s, if (pressed) ColorUtils.blendARGB(color, Color.WHITE, 0.15f) else color)
        val d = px(ctx, depthDp.toFloat()).toInt().coerceAtLeast(1)
        val slab = GradientDrawable().apply {
            cornerRadius = px(ctx, s.int("corner").toFloat())
            val a = if (s.bool("shadowOn")) (s.int("shadow") * 2.55f).toInt().coerceIn(0, 255) else 90
            setColor(ColorUtils.setAlphaComponent(ColorUtils.blendARGB(color, Color.BLACK, 0.7f), maxOf(a, 40)))
        }
        val layer = LayerDrawable(arrayOf<Drawable>(slab, body(ctx, s, if (pressed) ColorUtils.blendARGB(color, Color.WHITE, 0.10f) else color)))
        // thân phím cao hơn đế; khi nhấn thì lún xuống chạm đế
        if (pressed) layer.setLayerInset(1, 0, d, 0, 0) else layer.setLayerInset(1, 0, 0, 0, d)
        return layer
    }

    private fun scaled(s: StyleSet, d: Drawable): Drawable {
        val sw = s.int("keyW").coerceIn(40, 100)
        val sh = s.int("keyH").coerceIn(40, 100)
        if (sw == 100 && sh == 100) return d
        val fx = (100 - sw) / 200f
        val fy = (100 - sh) / 200f
        return InsetDrawable(d, fx, fy, fx, fy)
    }

    fun background(ctx: Context, s: StyleSet, kind: Kind): Drawable {
        val base = when (kind) {
            Kind.NORMAL, Kind.TOGGLE -> s.int("keyColor")
            Kind.SPECIAL -> s.int("specialColor")
            Kind.ACCENT -> s.int("accentColor")
        }
        val accent = s.int("accentColor")
        val sel = StateListDrawable()
        sel.addState(intArrayOf(android.R.attr.state_pressed), scaled(s, layered(ctx, s, if (kind == Kind.ACCENT) accent else base, true)))
        if (kind == Kind.SPECIAL || kind == Kind.TOGGLE) sel.addState(intArrayOf(android.R.attr.state_selected), scaled(s, layered(ctx, s, accent, false)))
        sel.addState(intArrayOf(), scaled(s, layered(ctx, s, base, false)))
        return sel
    }

    /** Màu biểu tượng: theo màu chữ, khi được chọn thì trắng. */
    fun iconTint(s: StyleSet): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
        intArrayOf(Color.WHITE, s.int("textColor"))
    )

    fun styleIconButton(v: ImageView, s: StyleSet, kind: Kind) {
        v.background = background(v.context, s, kind)
        v.imageTintList = iconTint(s)
    }

    // ------------------------------------------------------------------ chữ
    private val montserrat = HashMap<Int, Typeface?>()

    fun typeface(ctx: Context, s: StyleSet): Typeface {
        val weight = s.int("weight").coerceIn(100, 900)
        return when (val f = s.int("font")) {
            0 -> montserrat.getOrPut(weight) {
                try {
                    Typeface.Builder(ctx.applicationContext.assets, "fonts/Montserrat.ttf")
                        .setFontVariationSettings("'wght' $weight").build()
                } catch (_: Throwable) { null }
            } ?: Typeface.DEFAULT
            else -> {
                val family = when (f) { 1 -> "sans-serif"; 2 -> "serif"; 3 -> "monospace"; 4 -> "sans-serif-condensed"; 5 -> "sans-serif-light"; 6 -> "casual"; else -> "cursive" }
                val base = Typeface.create(family, Typeface.NORMAL)
                if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false)
                else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
            }
        }
    }

    /** Áp kiểu chữ cho một nhãn. [scale] dùng cho Live Preview (phím nhỏ hơn). */
    fun styleText(tv: TextView, s: StyleSet, sizeSp: Float, scale: Float = 1f) {
        val ctx = tv.context
        tv.typeface = typeface(ctx, s)
        tv.setTextColor(s.int("textColor"))
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp * scale)
        val st = s.int("fxStrength").coerceIn(0, 100) / 100f
        when (s.int("textFx")) {
            1 -> tv.setShadowLayer(px(ctx, 1f + 3f * st), px(ctx, 1f * st + 0.5f), px(ctx, 1.5f * st + 0.5f), 0xCC000000.toInt())
            2 -> tv.setShadowLayer(px(ctx, 2f + 10f * st), 0f, 0f, s.int("textColor"))
            3 -> tv.setShadowLayer(px(ctx, 0.6f + 0.8f * st), 0f, -px(ctx, 0.5f + 1.2f * st), 0xAAFFFFFF.toInt())
            else -> tv.setShadowLayer(0f, 0f, 0f, 0)
        }
        tv.setTag(R.id.tag_themed, true)
    }

    fun keyTextSize(s: StyleSet, label: String): Float =
        if (label.length > 1) s.int("textSp") * 0.74f * s.int("subPct") / 100f else s.int("textSp").toFloat()

    fun displayLabel(s: StyleSet, c: Char, letter: Boolean, shifted: Boolean): String = when {
        !letter -> c.toString()
        s.int("caseMode") == 1 -> c.uppercaseChar().toString()
        s.int("caseMode") == 2 -> c.toString()
        shifted -> c.uppercaseChar().toString()
        else -> c.toString()
    }

    // ------------------------------------------------------------------ bố cục bảng
    /** Đặt chiều rộng, vị trí và lề dưới của "board" theo thông số bố cục. */
    fun applyBoardLayout(board: View, s: StyleSet) {
        val dm = board.resources.displayMetrics
        var pct = s.int("widthPct").coerceIn(60, 100)
        var align = s.int("align").coerceIn(0, 2)
        when (s.int("oneHand")) { 1 -> { pct = minOf(pct, 78); align = 0 }; 2 -> { pct = minOf(pct, 78); align = 2 } }
        val lp = (board.layoutParams as? FrameLayout.LayoutParams) ?: FrameLayout.LayoutParams(-1, -2)
        val parentW = (board.parent as? View)?.width?.takeIf { it > 0 } ?: dm.widthPixels
        lp.width = if (pct >= 100) FrameLayout.LayoutParams.MATCH_PARENT else (parentW * pct / 100f).toInt()
        lp.gravity = when (align) { 0 -> Gravity.START; 2 -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL } or Gravity.BOTTOM
        lp.bottomMargin = (s.int("bottomDp").coerceIn(0, 64) * dm.density).toInt()
        board.layoutParams = lp
    }
}
