package com.inventory.scannerkeyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Lớp hiệu ứng nằm trên các phím của một bảng (vẽ bằng ViewOverlay, không đổi bố cục, không chặn cảm ứng).
 *  - Hiệu ứng chạm: nhún, đàn hồi, gợn sóng, phát sáng, hạt sáng (bật / tắt độc lập, kết hợp tự do).
 *  - Ánh sáng: RGB, Neon, Rainbow, Glow, Gradient, ánh sáng lan tỏa (kết hợp được).
 * Khi không có hiệu ứng nào đang chạy thì không vẽ và không lên lịch gì thêm, nên không tốn pin.
 * Hiệu ứng động chạy khoảng 30 khung hình/giây và tự dừng khi bảng bị ẩn.
 */
class FxDrawable(private val host: ViewGroup, private val views: () -> List<View>) : Drawable() {

    companion object {
        const val RGB = 1
        const val NEON = 2
        const val RAINBOW = 4
        const val GLOW = 8
        const val GRADIENT = 16
        const val DIFFUSE = 32

        private const val K_RIPPLE = 0
        private const val K_FLASH = 1
        private const val K_SPARK = 2
        private const val FRAME_MS = 33L
        private const val MAX_EVENTS = 40
    }

    var style: PanelStyle = PanelStyle()
        set(v) { field = v; invalidateSelf() }

    private class Ev(val kind: Int, val v: View, val x: Float, val y: Float, val start: Long, val seed: Long)

    private val d = host.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val events = ArrayList<Ev>()
    private val hsv = FloatArray(3)
    private val rnd = java.util.Random()
    private val t0 = SystemClock.uptimeMillis()
    private val tick = Runnable { invalidateSelf() }

    private val layoutListener = View.OnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
        setBounds(0, 0, r - l, b - t)
        invalidateSelf()
    }

    fun attach() {
        host.addOnLayoutChangeListener(layoutListener)
        setBounds(0, 0, host.width, host.height)
        host.overlay.add(this)
    }

    fun detach() {
        host.removeOnLayoutChangeListener(layoutListener)
        host.overlay.remove(this)
        unscheduleSelf(tick)
        events.clear()
    }

    /** View này có nằm trong bảng mà lớp hiệu ứng này đang phủ không. */
    fun owns(v: View): Boolean {
        var p: View? = v
        while (p != null) {
            if (p === host) return true
            p = p.parent as? View
        }
        return false
    }

    // ------------------------------------------------------------------ cảm ứng
    fun down(v: View, x: Float, y: Float) {
        val s = style
        v.animate().cancel()
        if (s.fxBounce) {
            val sc = s.fxBounceScale / 100f
            v.animate().scaleX(sc).scaleY(sc).setDuration(70).setInterpolator(AccelerateDecelerateInterpolator()).start()
        }
        val now = SystemClock.uptimeMillis()
        if (s.fxRipple) events.add(Ev(K_RIPPLE, v, x, y, now, 0L))
        if (s.fxFlash) events.add(Ev(K_FLASH, v, x, y, now, 0L))
        if (s.fxSparks) events.add(Ev(K_SPARK, v, x, y, now, now xor v.hashCode().toLong()))
        while (events.size > MAX_EVENTS) events.removeAt(0)
        if (events.isNotEmpty()) invalidateSelf()
    }

    fun up(v: View) {
        val s = style
        v.animate().cancel()
        if (s.fxBounce && s.fxElastic) {
            v.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(OvershootInterpolator(3.5f)).start()
        } else {
            v.animate().scaleX(1f).scaleY(1f).setDuration(110).setInterpolator(AccelerateDecelerateInterpolator()).start()
        }
    }

    // ------------------------------------------------------------------ vẽ
    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val s = style
        val now = SystemClock.uptimeMillis()
        if (s.lightFx != 0) drawLight(canvas, s, now)
        if (events.isNotEmpty()) drawEvents(canvas, s, now)
        val animating = s.lightFx != 0 || events.isNotEmpty()
        if (animating && host.isAttachedToWindow) {
            unscheduleSelf(tick)
            scheduleSelf(tick, now + FRAME_MS)
        }
    }

    /** Vị trí phím trong hệ tọa độ của bảng (có tính đến độ co khi đang nhún). */
    private fun rectOf(v: View, out: RectF): Boolean {
        if (v.visibility != View.VISIBLE || v.width == 0 || v.height == 0) return false
        var x = 0f
        var y = 0f
        var p: View = v
        while (p !== host) {
            x += p.left + p.translationX
            y += p.top + p.translationY
            p = p.parent as? View ?: return false
        }
        val w = v.width * v.scaleX
        val h = v.height * v.scaleY
        val cx = x + v.width / 2f
        val cy = y + v.height / 2f
        out.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        return true
    }

    private fun withA(c: Int, f: Float): Int =
        Color.argb((255f * f.coerceIn(0f, 1f)).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    private fun mix(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt()
        )
    }

    private fun hue(h: Float): Int {
        hsv[0] = ((h % 360f) + 360f) % 360f
        hsv[1] = 1f
        hsv[2] = 1f
        return Color.HSVToColor(hsv)
    }

    /**
     * Màu ánh sáng tại vị trí frac (0..1 theo chiều ngang bảng). Thứ tự ưu tiên khi bật nhiều kiểu:
     * Rainbow, rồi RGB, rồi Gradient, cuối cùng là màu đã chọn.
     */
    private fun colorAt(s: PanelStyle, frac: Float, phase: Float): Int {
        val bits = s.lightFx
        return when {
            (bits and RAINBOW) != 0 -> hue(phase + frac * 360f)
            (bits and RGB) != 0 -> hue(phase)
            (bits and GRADIENT) != 0 -> {
                var u = (frac + phase / 360f) % 1f
                if (u < 0f) u += 1f
                mix(s.lightColor, s.lightColor2, if (u < 0.5f) u * 2f else (1f - u) * 2f)
            }
            else -> s.lightColor
        }
    }

    private fun drawLight(c: Canvas, s: PanelStyle, now: Long) {
        val bits = s.lightFx
        val w = bounds.width().toFloat().coerceAtLeast(1f)
        val h = bounds.height().toFloat().coerceAtLeast(1f)
        val phase = ((now - t0) / 1000f * s.lightSpeed * 1.8f) % 360f
        val inten = s.lightIntensity / 100f

        // Ánh sáng lan tỏa: vài vệt sáng mềm trôi qua lại trên bảng
        if ((bits and DIFFUSE) != 0) {
            val n = 3
            for (i in 0 until n) {
                val f = (phase / 360f + i / n.toFloat()) % 1f
                val cx = w * (0.5f + 0.45f * sin(2.0 * PI * f).toFloat())
                val col = colorAt(s, f, phase)
                val a = (inten * 70f).toInt().coerceIn(0, 255)
                paint.style = Paint.Style.FILL
                paint.shader = RadialGradient(
                    cx, h * 0.5f, max(w, h) * 0.55f,
                    Color.argb(a, Color.red(col), Color.green(col), Color.blue(col)),
                    Color.argb(0, Color.red(col), Color.green(col), Color.blue(col)),
                    Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, w, h, paint)
            }
            paint.shader = null
        }

        // Viền sáng quanh từng phím
        val strokeBits = RGB or NEON or RAINBOW or GLOW or GRADIENT
        if ((bits and strokeBits) == 0) return
        val neon = (bits and NEON) != 0
        val halo = (bits and (GLOW or NEON)) != 0
        val corner = s.cornerDp * d
        val sw = (if (neon) 1.6f else 2f) * d
        val spread = s.lightSpread * d
        paint.style = Paint.Style.STROKE
        for (v in views()) {
            if (!rectOf(v, rect)) continue
            val col = colorAt(s, (rect.centerX() / w).coerceIn(0f, 1f), phase)
            if (halo && spread > 0.5f) {
                // Quầng sáng giả lập bằng vài nét viền chồng nhau, mỏng dần ra ngoài (không cần làm mờ nên rất nhẹ)
                for (i in 3 downTo 1) {
                    paint.strokeWidth = sw + spread * i * 2f / 3f
                    paint.color = withA(col, inten * (0.10f + 0.06f * (4 - i)))
                    c.drawRoundRect(rect, corner, corner, paint)
                }
            }
            paint.strokeWidth = sw
            paint.color = withA(col, inten)
            c.drawRoundRect(rect, corner, corner, paint)
            if (neon) {
                paint.strokeWidth = sw * 0.45f
                paint.color = withA(mix(col, Color.WHITE, 0.75f), inten)
                c.drawRoundRect(rect, corner, corner, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawEvents(c: Canvas, s: PanelStyle, now: Long) {
        events.removeAll { now - it.start > s.fxDuration }
        val inten = s.fxIntensity / 100f
        val corner = s.cornerDp * d
        val col = s.fxColor
        paint.style = Paint.Style.FILL
        paint.shader = null
        for (e in events) {
            if (!rectOf(e.v, rect)) continue
            val p = ((now - e.start).toFloat() / s.fxDuration).coerceIn(0f, 1f)
            when (e.kind) {
                K_FLASH -> {
                    paint.color = withA(col, inten * 0.6f * (1f - p))
                    c.drawRoundRect(rect, corner, corner, paint)
                }
                K_RIPPLE -> {
                    path.reset()
                    path.addRoundRect(rect, corner, corner, Path.Direction.CW)
                    c.save()
                    c.clipPath(path)
                    paint.color = withA(col, inten * 0.55f * (1f - p))
                    c.drawCircle(rect.left + e.x, rect.top + e.y, max(rect.width(), rect.height()) * 1.1f * p, paint)
                    c.restore()
                }
                else -> {
                    rnd.setSeed(e.seed)
                    val cx = rect.left + e.x
                    val cy = rect.top + e.y
                    for (i in 0 until s.fxSparkCount) {
                        val ang = rnd.nextFloat() * 6.2831855f
                        val spd = (35f + rnd.nextFloat() * 70f) * d
                        val life = 0.55f + rnd.nextFloat() * 0.45f
                        val pp = p / life
                        if (pp >= 1f) continue
                        val dist = spd * (s.fxDuration / 1000f) * pp * 0.9f
                        paint.color = withA(col, inten * (1f - pp))
                        c.drawCircle(
                            cx + cos(ang) * dist,
                            cy + sin(ang) * dist + 14f * d * pp * pp,
                            (2.2f - 1.2f * pp) * d + 0.5f, paint
                        )
                    }
                }
            }
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
