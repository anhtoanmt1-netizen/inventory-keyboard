package com.inventory.scannerkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import org.json.JSONObject
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Kiểu chữ và hiệu ứng của dòng "Created by". Mặc định tái tạo đúng dòng chữ gốc. */
data class CreditStyle(
    val text: String = DEFAULT_TEXT,
    val sizeSp: Int = 10,                   // 6..30
    val color: Int = 0xFFEE4D2D.toInt(),
    val color2: Int = 0xFFFFD166.toInt(),   // màu thứ hai (chuyển màu)
    val fontFamily: Int = 0,                // xem ThemeRender.FAMILY_NAMES
    val weight: Int = 500,                  // 300..800
    val textStyle: Int = 0,                 // 0 thường, 1 đậm, 2 nghiêng, 3 đậm nghiêng
    val underline: Boolean = false,
    val caps: Int = 0,                      // 0 giữ nguyên, 1 IN HOA, 2 in thường
    val letterSpacing: Int = 0,             // x0.01 em, -5..50
    val align: Int = 1,                     // 0 trái, 1 giữa, 2 phải
    val alpha: Int = 100,                   // độ đặc 10..100
    val anim: Int = 0,                      // xem ANIM_NAMES
    val speed: Int = 50,                    // tốc độ chạy / xuất hiện 1..100
    val repeat: Boolean = true,             // hiệu ứng xuất hiện lặp lại sau một lúc nghỉ
    val glow: Int = 0,                      // bán kính quầng sáng 0..20 dp
    val glowColor: Int = 0xFFEE4D2D.toInt(),
    val glowPulse: Boolean = false,         // quầng sáng thở
    val outline: Int = 0,                   // độ dày viền chữ 0..6 (x0.5 dp)
    val outlineColor: Int = 0xFFFFFFFF.toInt(),
    val light: Int = 0,                     // bit: 1 vệt sáng quét, 2 cầu vồng, 4 chuyển màu, 8 nhấp nháy
    val lightSpeed: Int = 50,               // tốc độ hiệu ứng ánh sáng 1..100
    val shadow: Int = 0,                    // bóng đổ 0..8
    val shadowColor: Int = 0xB3000000.toInt()
) {
    companion object {
        const val DEFAULT_TEXT = "Created by: Toan.nguyenanh@spxexpress.com"
        const val LIGHT_SWEEP = 1
        const val LIGHT_RAINBOW = 2
        const val LIGHT_GRADIENT = 4
        const val LIGHT_BLINK = 8

        val ANIM_NAMES = listOf(
            "Tĩnh", "Chạy trái → phải", "Chạy phải → trái", "Rơi chữ", "Nhảy nhót", "Gợn sóng",
            "Gõ chữ", "Hiện dần", "Nhịp đập", "Lắc nhẹ", "Trượt vào từ trái", "Trượt vào từ phải", "Phóng to từng chữ"
        )

        fun fromJson(text: String?): CreditStyle {
            if (text.isNullOrBlank()) return CreditStyle()
            return try {
                val o = JSONObject(text)
                val d = CreditStyle()
                CreditStyle(
                    text = o.optString("text", d.text).take(120),
                    sizeSp = o.optInt("sizeSp", d.sizeSp).coerceIn(6, 30),
                    color = o.optInt("color", d.color),
                    color2 = o.optInt("color2", d.color2),
                    fontFamily = o.optInt("fontFamily", d.fontFamily).coerceIn(0, ThemeRender.FAMILY_NAMES.size - 1),
                    weight = o.optInt("weight", d.weight).coerceIn(300, 800),
                    textStyle = o.optInt("textStyle", d.textStyle).coerceIn(0, 3),
                    underline = o.optBoolean("underline", d.underline),
                    caps = o.optInt("caps", d.caps).coerceIn(0, 2),
                    letterSpacing = o.optInt("letterSpacing", d.letterSpacing).coerceIn(-5, 50),
                    align = o.optInt("align", d.align).coerceIn(0, 2),
                    alpha = o.optInt("alpha", d.alpha).coerceIn(10, 100),
                    anim = o.optInt("anim", d.anim).coerceIn(0, ANIM_NAMES.size - 1),
                    speed = o.optInt("speed", d.speed).coerceIn(1, 100),
                    repeat = o.optBoolean("repeat", d.repeat),
                    glow = o.optInt("glow", d.glow).coerceIn(0, 20),
                    glowColor = o.optInt("glowColor", d.glowColor),
                    glowPulse = o.optBoolean("glowPulse", d.glowPulse),
                    outline = o.optInt("outline", d.outline).coerceIn(0, 6),
                    outlineColor = o.optInt("outlineColor", d.outlineColor),
                    light = o.optInt("light", d.light).coerceIn(0, 15),
                    lightSpeed = o.optInt("lightSpeed", d.lightSpeed).coerceIn(1, 100),
                    shadow = o.optInt("shadow", d.shadow).coerceIn(0, 8),
                    shadowColor = o.optInt("shadowColor", d.shadowColor)
                )
            } catch (_: Throwable) { CreditStyle() }
        }
    }

    fun toJson(): String = JSONObject()
        .put("text", text).put("sizeSp", sizeSp).put("color", color).put("color2", color2)
        .put("fontFamily", fontFamily).put("weight", weight).put("textStyle", textStyle).put("underline", underline)
        .put("caps", caps).put("letterSpacing", letterSpacing).put("align", align).put("alpha", alpha)
        .put("anim", anim).put("speed", speed).put("repeat", repeat)
        .put("glow", glow).put("glowColor", glowColor).put("glowPulse", glowPulse)
        .put("outline", outline).put("outlineColor", outlineColor)
        .put("light", light).put("lightSpeed", lightSpeed)
        .put("shadow", shadow).put("shadowColor", shadowColor).toString()
}

/**
 * Dòng chữ "Created by" ở cuối bàn phím. Vẽ trực tiếp, hỗ trợ chạy chữ, rơi chữ, nhảy nhót, gợn sóng, gõ chữ...
 * cùng viền phát sáng, cầu vồng, vệt sáng quét. Chỉ chạy hoạt ảnh khi đang hiện trên màn hình (không tốn pin
 * khi bàn phím ẩn) và dừng hẳn nếu để kiểu Tĩnh không có hiệu ứng ánh sáng.
 */
class CreditView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var st = CreditStyle()
    private val dm = resources.displayMetrics
    private val d = dm.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix = Matrix()

    private var shown = ""
    private var offsets = FloatArray(1)   // vị trí x của từng ký tự (cộng dồn), offsets[n] = tổng chiều rộng
    private var textW = 0f
    private var shader: Shader? = null
    private var sweepShader: Shader? = null
    private var shaderW = -1f
    private var shaderKind = -1

    private val t0 = SystemClock.uptimeMillis()
    private var running = false
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            invalidate()
            postDelayed(this, 33L)
        }
    }

    private val rainbow = intArrayOf(
        0xFFFF3B30.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(), 0xFF00C7BE.toInt(),
        0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt(), 0xFFFF3B30.toInt()
    )

    init { setStyle(CreditStyle()) }

    fun setStyle(s: CreditStyle) {
        st = s
        shown = when (s.caps) {
            1 -> s.text.uppercase(Locale.getDefault())
            2 -> s.text.lowercase(Locale.getDefault())
            else -> s.text
        }
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, s.sizeSp.toFloat(), dm)
        val base = ThemeRender.typeface(context, PanelStyle(fontFamily = s.fontFamily, fontWeight = s.weight))
        paint.typeface = if (s.textStyle == 0) base else Typeface.create(base, s.textStyle)
        paint.letterSpacing = s.letterSpacing / 100f
        paint.isUnderlineText = s.underline
        offsets = FloatArray(shown.length + 1)
        for (i in 0..shown.length) offsets[i] = paint.measureText(shown, 0, i)
        textW = offsets[shown.length]
        shaderW = -1f
        requestLayout()
        invalidate()
        updateRunning()
    }

    // ------------------------------------------------------------------ đo
    private fun pad(): Float = (2f + max(st.glow.toFloat(), st.shadow.toFloat() + st.outline * 0.5f)) * d

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fm = paint.fontMetrics
        val h = ceil((fm.descent - fm.ascent) + 2f * pad()).toInt()
        val w = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY, MeasureSpec.AT_MOST -> MeasureSpec.getSize(widthMeasureSpec)
            else -> ceil(textW + 2f * pad()).toInt()
        }
        setMeasuredDimension(w, h)
    }

    // ------------------------------------------------------------------ chạy / dừng hoạt ảnh
    private fun needsAnim(): Boolean = st.anim != 0 || st.light != 0 || (st.glow > 0 && st.glowPulse)

    private fun updateRunning() {
        // isAttachedToWindow phải xét trước: hàm này có thể bị gọi từ phần khởi tạo của View khi các trường chưa sẵn sàng.
        val want = isAttachedToWindow && windowVisibility == VISIBLE && visibility == VISIBLE && needsAnim() && shown.isNotEmpty()
        if (want == running) return
        running = want
        removeCallbacks(ticker)
        if (running) post(ticker)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateRunning() }
    override fun onDetachedFromWindow() { running = false; removeCallbacks(ticker); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateRunning() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); updateRunning() }

    // ------------------------------------------------------------------ hàm hỗ trợ chuyển động
    private fun clamp01(v: Float) = if (v < 0f) 0f else if (v > 1f) 1f else v
    private fun easeOut(p: Float): Float { val q = 1f - clamp01(p); return 1f - q * q * q }
    private fun easeOutBack(p: Float): Float { val c = 1.70158f; val q = clamp01(p) - 1f; return 1f + (c + 1f) * q * q * q + c * q * q }
    private fun bounce(pIn: Float): Float {
        var p = clamp01(pIn)
        val n = 7.5625f
        val dd = 2.75f
        return when {
            p < 1f / dd -> n * p * p
            p < 2f / dd -> { p -= 1.5f / dd; n * p * p + 0.75f }
            p < 2.5f / dd -> { p -= 2.25f / dd; n * p * p + 0.9375f }
            else -> { p -= 2.625f / dd; n * p * p + 0.984375f }
        }
    }

    private fun ensureShaders(x0: Float) {
        val w = max(textW, 1f)
        val kind = if ((st.light and CreditStyle.LIGHT_RAINBOW) != 0) 1 else if ((st.light and CreditStyle.LIGHT_GRADIENT) != 0) 2 else 0
        if (w == shaderW && kind == shaderKind && sweepShader != null) return
        shaderW = w
        shaderKind = kind
        shader = when (kind) {
            1 -> LinearGradient(0f, 0f, w, 0f, rainbow, null, Shader.TileMode.REPEAT)
            2 -> LinearGradient(0f, 0f, w, 0f, intArrayOf(st.color, st.color2, st.color), null, Shader.TileMode.REPEAT)
            else -> null
        }
        val band = max(w * 0.28f, 24f * d)
        sweepShader = LinearGradient(
            0f, 0f, band, 0f, intArrayOf(0x00FFFFFF, 0xDDFFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP
        )
    }

    private fun withAlpha(c: Int, f: Float): Int =
        Color.argb((Color.alpha(c) * f).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    // ------------------------------------------------------------------ vẽ
    override fun onDraw(canvas: Canvas) {
        val n = shown.length
        if (n == 0 || width == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val tt = (SystemClock.uptimeMillis() - t0) / 1000f
        val sp = st.speed / 50f
        val ls = st.lightSpeed / 50f
        val fm = paint.fontMetrics
        val baseY = (h - (fm.descent - fm.ascent)) / 2f - fm.ascent
        val pd = pad()
        val avail = max(1f, w - 2f * pd)
        val marquee = st.anim == 1 || st.anim == 2

        // co chữ lại cho vừa khung nếu quá dài (trừ kiểu chạy chữ)
        val fit = if (!marquee && textW > avail) avail / textW else 1f
        val drawW = textW * fit
        val x0 = when {
            marquee -> {
                val period = w + textW
                val off = (tt * 60f * d * sp) % period
                if (st.anim == 1) -textW + off else w - off
            }
            st.align == 0 -> pd
            st.align == 2 -> w - pd - drawW
            else -> (w - drawW) / 2f
        }

        canvas.save()
        if (fit != 1f) {
            canvas.translate(x0, 0f); canvas.scale(fit, fit, 0f, h / 2f); canvas.translate(-x0, 0f)
        }
        if (st.anim == 8) {
            val pulse = 1f + 0.12f * sin(2.0 * PI * tt * sp * 0.8).toFloat()
            canvas.scale(pulse, pulse, x0 + textW / 2f, h / 2f)
        }

        // ---- chuẩn bị màu: cầu vồng / chuyển màu / màu thường + nhấp nháy ----
        ensureShaders(x0)
        var alphaF = st.alpha / 100f
        if ((st.light and CreditStyle.LIGHT_BLINK) != 0) alphaF *= 0.35f + 0.65f * abs(sin(tt * ls * 2.2f))
        paint.style = Paint.Style.FILL
        val sh = shader
        if (sh != null) {
            matrix.reset()
            matrix.postTranslate(x0 + tt * ls * textW * 0.35f, 0f)
            sh.setLocalMatrix(matrix)
            paint.shader = sh
            paint.color = Color.WHITE
        } else {
            paint.shader = null
            paint.color = st.color
        }
        paint.alpha = (alphaF * Color.alpha(if (sh != null) Color.WHITE else st.color)).toInt().coerceIn(0, 255)

        // ---- quầng sáng ----
        var glowR = st.glow * d
        if (st.glow > 0 && st.glowPulse) glowR *= 0.55f + 0.45f * (0.5f + 0.5f * sin(2.0 * PI * tt * 0.8 * sp).toFloat())

        val perChar = st.anim in 3..7 || st.anim in 9..12
        if (!perChar) {
            drawLine(canvas, x0, baseY, glowR, alphaF, 0, n, 1f)
        } else {
            drawPerChar(canvas, x0, baseY, tt, sp, glowR, alphaF)
        }

        // ---- vệt sáng quét ----
        val ss = sweepShader
        if ((st.light and CreditStyle.LIGHT_SWEEP) != 0 && ss != null) {
            val band = max(textW * 0.28f, 24f * d)
            val travel = textW + band
            val pos = x0 - band + (tt * ls * textW * 0.5f) % travel
            matrix.reset()
            matrix.postTranslate(pos, 0f)
            ss.setLocalMatrix(matrix)
            sweepPaint.set(paint)
            sweepPaint.shader = ss
            sweepPaint.clearShadowLayer()
            sweepPaint.style = Paint.Style.FILL
            sweepPaint.color = Color.WHITE
            sweepPaint.alpha = (255 * st.alpha / 100)
            canvas.drawText(shown, 0, n, x0, baseY, sweepPaint)
        }
        canvas.restore()
        paint.shader = null
    }

    /** Vẽ một đoạn chữ [from, to) bắt đầu từ x0 với viền, quầng sáng và bóng. */
    private fun drawLine(canvas: Canvas, x0: Float, y: Float, glowR: Float, alphaF: Float, from: Int, to: Int, scaleA: Float) {
        val xs = x0 + offsets[from]
        val oldAlpha = paint.alpha
        if (st.outline > 0) {
            val fillColor = paint.color
            val fillShader = paint.shader
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = st.outline * 0.5f * d
            paint.strokeJoin = Paint.Join.ROUND
            paint.shader = null
            paint.color = withAlpha(st.outlineColor, alphaF * scaleA)
            if (glowR > 0f) paint.setShadowLayer(glowR, 0f, 0f, withAlpha(st.glowColor, alphaF)) else paint.clearShadowLayer()
            canvas.drawText(shown, from, to, xs, y, paint)
            paint.style = Paint.Style.FILL
            paint.shader = fillShader
            paint.color = fillColor
            paint.alpha = (oldAlpha * scaleA).toInt().coerceIn(0, 255)
            if (st.shadow > 0) paint.setShadowLayer(max(0.1f, st.shadow * d), 0f, d, st.shadowColor) else paint.clearShadowLayer()
            canvas.drawText(shown, from, to, xs, y, paint)
        } else {
            paint.alpha = (oldAlpha * scaleA).toInt().coerceIn(0, 255)
            when {
                glowR > 0f -> paint.setShadowLayer(glowR, 0f, 0f, withAlpha(st.glowColor, alphaF))
                st.shadow > 0 -> paint.setShadowLayer(max(0.1f, st.shadow * d), 0f, d, st.shadowColor)
                else -> paint.clearShadowLayer()
            }
            canvas.drawText(shown, from, to, xs, y, paint)
        }
        paint.alpha = oldAlpha
        paint.clearShadowLayer()
    }

    /** Hiệu ứng riêng từng ký tự: rơi, nhảy nhót, gợn sóng, gõ chữ, hiện dần, lắc, trượt vào, phóng to. */
    private fun drawPerChar(canvas: Canvas, x0: Float, baseY: Float, tt: Float, sp: Float, glowR: Float, alphaF: Float) {
        val n = shown.length
        val size = paint.textSize
        val appear = st.anim == 3 || st.anim == 6 || st.anim == 7 || st.anim == 10 || st.anim == 11 || st.anim == 12
        var tc = 0f
        val stagger = 0.07f / sp
        val dur = 0.55f / sp
        if (appear) {
            val total = n * stagger + dur
            val cycle = total + 1.6f
            tc = if (st.repeat) tt % cycle else min(tt, total + 0.01f)
        }
        for (i in 0 until n) {
            val ch = shown[i]
            if (ch == ' ') continue
            var dx = 0f
            var dy = 0f
            var a = 1f
            var s = 1f
            val p = if (appear) clamp01((tc - i * stagger) / dur) else 1f
            when (st.anim) {
                3 -> { dy = -(1f - bounce(p)) * size * 3f; a = clamp01(p * 4f) }
                4 -> { val ph = tt * sp * 2.2f - i * 0.12f; dy = -abs(sin(PI * ph).toFloat()) * size * 0.35f }
                5 -> dy = sin(2.0 * PI * (tt * sp * 0.9f - i * 0.08f)).toFloat() * size * 0.22f
                6 -> a = if (p > 0f) 1f else 0f
                7 -> a = p
                9 -> { dx = sin(tt * sp * 30f + i * 1.7f) * 0.6f * d; dy = cos(tt * sp * 27f + i * 2.3f) * 0.6f * d }
                10 -> { dx = -(1f - easeOut(p)) * 40f * d; a = p }
                11 -> { dx = (1f - easeOut(p)) * 40f * d; a = p }
                12 -> { s = max(0.01f, easeOutBack(p)); a = clamp01(p * 3f) }
            }
            if (a <= 0.01f) continue
            val cx = x0 + offsets[i] + (offsets[i + 1] - offsets[i]) / 2f
            canvas.save()
            if (s != 1f) canvas.scale(s, s, cx, baseY - size * 0.35f)
            drawChar(canvas, x0 + dx, baseY + dy, glowR, alphaF, i, a)
            canvas.restore()
        }
    }

    private fun drawChar(canvas: Canvas, x0: Float, y: Float, glowR: Float, alphaF: Float, i: Int, a: Float) {
        drawLine(canvas, x0, y, glowR, alphaF, i, i + 1, a)
    }
}
