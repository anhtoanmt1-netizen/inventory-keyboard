package com.inventory.scannerkeyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 10 hiệu ứng chuyển động nền, vẽ phía sau các phím: sao băng, đom đóm, bong bóng nước, tuyết rơi, cánh hoa,
 * bụi phép thuật, dải cực quang, ngân hà, tia sáng, các hạt chuyển động. Bật / tắt riêng từng cái, kết hợp tự do.
 *
 * Cách làm nhẹ máy: mỗi hạt được tính thẳng từ thời gian bằng công thức (không lưu trạng thái, không cấp phát
 * bộ nhớ trong lúc vẽ), chạy khoảng 30 khung hình/giây, và tự giảm số hạt nếu máy vẽ chậm.
 */
class MotionBgDrawable(private val st: PanelStyle, private val d: Float) : Drawable() {

    companion object {
        const val SHOOTING = 1
        const val FIREFLY = 2
        const val BUBBLE = 4
        const val SNOW = 8
        const val PETAL = 16
        const val MAGIC = 32
        const val AURORA = 64
        const val GALAXY = 128
        const val RAYS = 256
        const val PARTICLES = 512

        private const val FRAME_MS = 33L
        private val WHITE = 0xFFFFFFFF.toInt()
        private val FIREFLY_COL = 0xFFD6FF5A.toInt()
        private val BUBBLE_COL = 0xFF9FD8FF.toInt()
        private val PETALS = intArrayOf(0xFFFFB7D5.toInt(), 0xFFFF9CC2.toInt(), 0xFFFFD1E3.toInt())
        private val MAGIC_COLS = intArrayOf(0xFFE9B6FF.toInt(), 0xFFFFE08A.toInt())
        private val AURORA_COLS = intArrayOf(0xFF3CFFB0.toInt(), 0xFF38C8FF.toInt(), 0xFFB070FF.toInt())
        private val STAR_COLS = intArrayOf(0xFFFFFFFF.toInt(), 0xFFB9C8FF.toInt(), 0xFFD6B8FF.toInt(), 0xFFFFE6B0.toInt())
        private val DUST_COLS = intArrayOf(0xFFFFFFFF.toInt(), 0xFF9FE8FF.toInt(), 0xFFC3B2FF.toInt())
        private val RAY_COL = 0xFFFFF2C0.toInt()
        private val METEOR_COL = 0xFFE8F0FF.toInt()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val oval = RectF()
    private val wave = FloatArray(17)
    private val t0 = SystemClock.uptimeMillis()
    private val tick = Runnable { invalidateSelf() }
    private var galaxyShader: Shader? = null
    private var load = 1f                              // 0.3..1: hệ số giảm số hạt khi máy yếu
    private val br = st.motionBrightness / 100f
    private val sizeF = st.motionSize / 100f

    // ------------------------------------------------------------------ tiện ích
    /** Số ngẫu nhiên cố định 0..1 theo (chỉ số hạt, mã thuộc tính): cùng hạt thì luôn cùng giá trị. */
    private fun r(i: Int, k: Int): Float {
        var x = i * 374761393 + k * 668265263 + 0x9E3779B9.toInt()
        // trộn bit kiểu murmur3 để các hạt rải đều
        x = x xor (x ushr 16)
        x *= 0x85EBCA6B.toInt()
        x = x xor (x ushr 13)
        x *= 0xC2B2AE35.toInt()
        x = x xor (x ushr 16)
        return (x and 0x7FFFFFFF) / 2147483647f
    }

    private fun frac(x: Float): Float = x - floor(x)

    private fun cnt(base: Int): Int = max(1, (base * st.motionDensity / 100f * load).toInt())

    private fun withA(c: Int, f: Float): Int =
        Color.argb((255f * f * br).toInt().coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))

    /** Chấm sáng có quầng mềm; độ mờ càng cao thì quầng càng nhiều lớp. */
    private fun dot(c: Canvas, x: Float, y: Float, rad: Float, color: Int, a: Float) {
        val halos = st.motionBlur / 3
        for (k in halos downTo 1) {
            paint.color = withA(color, a * 0.22f / k)
            c.drawCircle(x, y, rad * (1f + 0.7f * k), paint)
        }
        paint.color = withA(color, a)
        c.drawCircle(x, y, rad, paint)
    }

    override fun onBoundsChange(b: Rect) {
        galaxyShader = if ((st.motionFx and GALAXY) != 0 && !b.isEmpty) {
            RadialGradient(
                b.width() / 2f, b.height() / 2f, max(b.width(), b.height()) * 0.55f,
                intArrayOf(withA(0xFF9678FF.toInt(), 0.30f), withA(0xFF3C50C8.toInt(), 0.12f), 0),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
        } else null
    }

    // ------------------------------------------------------------------ vẽ
    override fun draw(canvas: Canvas) {
        val b = bounds
        val bits = st.motionFx
        if (b.isEmpty || bits == 0) return
        val startNs = System.nanoTime()
        val now = SystemClock.uptimeMillis()
        val t = (now - t0) / 1000f * st.motionSpeed / 100f
        val w = b.width().toFloat()
        val h = b.height().toFloat()

        canvas.save()
        canvas.clipRect(b)
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        paint.style = Paint.Style.FILL
        paint.shader = null

        // từ xa tới gần
        if ((bits and GALAXY) != 0) galaxy(canvas, t, w, h)
        if ((bits and AURORA) != 0) aurora(canvas, t, w, h)
        if ((bits and RAYS) != 0) rays(canvas, t, w, h)
        if ((bits and MAGIC) != 0) magic(canvas, t, w, h)
        if ((bits and SNOW) != 0) snow(canvas, t, w, h)
        if ((bits and PETAL) != 0) petals(canvas, t, w, h)
        if ((bits and BUBBLE) != 0) bubbles(canvas, t, w, h)
        if ((bits and FIREFLY) != 0) fireflies(canvas, t, w, h)
        if ((bits and PARTICLES) != 0) dust(canvas, t, w, h)
        if ((bits and SHOOTING) != 0) meteors(canvas, t, w, h)
        canvas.restore()

        // Tự giảm tải: vẽ chậm thì bớt hạt, nhẹ lại thì tăng dần về mức người dùng đặt
        val ms = (System.nanoTime() - startNs) / 1_000_000f
        load = if (ms > 12f) max(0.3f, load * 0.92f) else min(1f, load * 1.01f)

        unscheduleSelf(tick)
        scheduleSelf(tick, now + FRAME_MS)
    }

    // ------------------------------------------------------------------ 8. ngân hà
    private fun galaxy(c: Canvas, t: Float, w: Float, h: Float) {
        val cx = w * 0.5f
        val cy = h * 0.5f
        val rr0 = max(w, h) * 0.55f
        galaxyShader?.let {
            paint.shader = it
            c.drawCircle(cx, cy, rr0, paint)
            paint.shader = null
        }
        for (i in 0 until cnt(60)) {
            val rr = (0.08f + 0.92f * r(i, 2)) * rr0
            val ang = (i % 3) * 2.0944f + rr / rr0 * 3.2f + t * 0.12f * (1.2f - rr / rr0) + (r(i, 3) - 0.5f) * 0.5f
            val tw = 0.55f + 0.45f * sin(t * (1f + r(i, 4) * 2f) + r(i, 5) * 6.2832f)
            dot(c, cx + cos(ang) * rr, cy + sin(ang) * rr * 0.55f, (0.8f + r(i, 6) * 1.4f) * d * sizeF, STAR_COLS[i % 4], tw * 0.9f)
        }
    }

    // ------------------------------------------------------------------ 7. dải cực quang
    private fun aurora(c: Canvas, t: Float, w: Float, h: Float) {
        val steps = 16
        for (k in 0 until 3) {
            val base = h * (0.16f + 0.17f * k)
            val amp = h * 0.10f * min(sizeF, 2f)
            for (s in 0..steps) wave[s] = base + sin(s / steps.toFloat() * 5.5f + t * (0.5f + 0.2f * k) + k * 1.7f) * amp
            for (layer in 3 downTo 1) {
                val th = h * 0.24f * layer / 3f
                path.reset()
                path.moveTo(0f, wave[0])
                for (s in 1..steps) path.lineTo(w * s / steps, wave[s])
                for (s in steps downTo 0) path.lineTo(w * s / steps, wave[s] + th)
                path.close()
                paint.color = withA(AURORA_COLS[k], 0.13f)
                c.drawPath(path, paint)
            }
        }
    }

    // ------------------------------------------------------------------ 9. tia sáng
    private fun rays(c: Canvas, t: Float, w: Float, h: Float) {
        val n = max(3, cnt(6))
        val px = w * 0.5f
        val py = -h * 0.15f
        val len = max(w, h) * 1.6f
        for (i in 0 until n) {
            val ang = (PI / 2.0).toFloat() + (i / (n - 1f) - 0.5f) * 1.6f + sin(t * 0.3f + i) * 0.12f
            val hw = 0.05f + 0.04f * r(i, 1)
            path.reset()
            path.moveTo(px, py)
            path.lineTo(px + cos(ang - hw) * len, py + sin(ang - hw) * len)
            path.lineTo(px + cos(ang + hw) * len, py + sin(ang + hw) * len)
            path.close()
            paint.color = withA(RAY_COL, 0.10f + 0.10f * (0.5f + 0.5f * sin(t * 0.8f + i * 1.3f)))
            c.drawPath(path, paint)
        }
    }

    // ------------------------------------------------------------------ 6. bụi phép thuật
    private fun magic(c: Canvas, t: Float, w: Float, h: Float) {
        for (i in 0 until cnt(30)) {
            val x = (r(i, 1) + sin(t * 0.15f + r(i, 2) * 6.2832f) * 0.03f) * w
            val y = (r(i, 3) + cos(t * 0.13f + r(i, 2) * 6.2832f) * 0.03f) * h
            val a = max(0f, sin(t * (1.2f + r(i, 4) * 2f) + r(i, 5) * 6.2832f))
            if (a < 0.05f) continue
            val s = (1.5f + r(i, 6) * 2.5f) * d * sizeF
            val col = MAGIC_COLS[i % 2]
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = d
            paint.color = withA(col, a * 0.8f)
            c.drawLine(x - s * 1.8f, y, x + s * 1.8f, y, paint)
            c.drawLine(x, y - s * 1.8f, x, y + s * 1.8f, paint)
            paint.style = Paint.Style.FILL
            dot(c, x, y, s * 0.5f, col, a)
        }
    }

    // ------------------------------------------------------------------ 4. tuyết rơi
    private fun snow(c: Canvas, t: Float, w: Float, h: Float) {
        for (i in 0 until cnt(40)) {
            val rad = (1.2f + 1.8f * r(i, 3)) * d * sizeF
            val fall = frac(t * (0.08f + 0.10f * r(i, 1)) + r(i, 2))
            val y = fall * (h + 2f * rad) - rad
            val x = r(i, 4) * w + sin(t * 0.8f + r(i, 5) * 6.2832f) * 8f * d
            dot(c, x, y, rad, WHITE, 0.55f + 0.35f * r(i, 6))
        }
    }

    // ------------------------------------------------------------------ 5. cánh hoa
    private fun petals(c: Canvas, t: Float, w: Float, h: Float) {
        for (i in 0 until cnt(14)) {
            val len = (4f + 3f * r(i, 3)) * d * sizeF
            val fall = frac(t * (0.05f + 0.05f * r(i, 1)) + r(i, 2))
            val y = fall * (h + 2f * len) - len
            val x = r(i, 4) * w + sin(t * 0.6f + r(i, 5) * 6.2832f) * 20f * d
            val rot = t * 60f * (r(i, 6) - 0.5f) * 2f + r(i, 7) * 360f
            c.save()
            c.rotate(rot, x, y)
            oval.set(x - len, y - len * 0.55f, x + len, y + len * 0.55f)
            paint.color = withA(PETALS[i % 3], 0.75f)
            c.drawOval(oval, paint)
            c.restore()
        }
    }

    // ------------------------------------------------------------------ 3. bong bóng nước
    private fun bubbles(c: Canvas, t: Float, w: Float, h: Float) {
        for (i in 0 until cnt(12)) {
            val rad = (4f + 10f * r(i, 1)) * d * sizeF
            val y = h + rad - frac(t * (0.05f + 0.07f * r(i, 2)) + r(i, 3)) * (h + 2f * rad)
            val x = r(i, 4) * w + sin(t * 0.9f + r(i, 5) * 6.2832f) * 6f * d
            paint.style = Paint.Style.FILL
            paint.color = withA(BUBBLE_COL, 0.10f)
            c.drawCircle(x, y, rad, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * d
            paint.color = withA(BUBBLE_COL, 0.55f)
            c.drawCircle(x, y, rad, paint)
            paint.style = Paint.Style.FILL
            paint.color = withA(WHITE, 0.7f)
            c.drawCircle(x - rad * 0.35f, y - rad * 0.35f, rad * 0.18f, paint)
        }
    }

    // ------------------------------------------------------------------ 2. đom đóm
    private fun fireflies(c: Canvas, t: Float, w: Float, h: Float) {
        for (i in 0 until cnt(14)) {
            val x = r(i, 1) * w + sin(t * 0.5f + r(i, 2) * 6.2832f) * 18f * d + sin(t * 1.3f + r(i, 3) * 6.2832f) * 6f * d
            val y = r(i, 4) * h + cos(t * 0.4f + r(i, 5) * 6.2832f) * 14f * d
            val blink = 0.5f + 0.5f * sin(t * (0.8f + r(i, 6) * 1.2f) + r(i, 7) * 6.2832f)
            val a = blink * blink
            val rad = (1.6f + 1.4f * r(i, 8)) * d * sizeF
            paint.color = withA(FIREFLY_COL, a * 0.15f)
            c.drawCircle(x, y, rad * 4f, paint)
            dot(c, x, y, rad, FIREFLY_COL, a)
        }
    }

    // ------------------------------------------------------------------ 10. các hạt chuyển động
    private fun dust(c: Canvas, t: Float, w: Float, h: Float) {
        for (i in 0 until cnt(30)) {
            val x = frac(r(i, 3) + t * (r(i, 1) - 0.5f) * 0.06f) * w
            val y = frac(r(i, 4) + t * (r(i, 2) - 0.5f) * 0.06f) * h
            dot(c, x, y, (1.2f + 1.8f * r(i, 5)) * d * sizeF, DUST_COLS[i % 3], 0.35f + 0.4f * r(i, 6))
        }
    }

    // ------------------------------------------------------------------ 1. sao băng
    private fun meteors(c: Canvas, t: Float, w: Float, h: Float) {
        for (i in 0 until cnt(3)) {
            val period = 3.5f + 4f * r(i, 1)
            val local = frac(t / period + r(i, 2))
            if (local > 0.18f) continue
            val p = local / 0.18f
            var ang = 0.45f + 0.35f * r(i, 3)
            val mirror = r(i, 4) > 0.5f
            if (mirror) ang = PI.toFloat() - ang
            val dx = cos(ang)
            val dy = sin(ang)
            val sx = r(i, 5) * w * 0.7f + (if (mirror) w * 0.3f else 0f)
            val sy = r(i, 6) * h * 0.5f
            val dist = p * max(w, h) * 0.55f
            val hx = sx + dx * dist
            val hy = sy + dy * dist
            val tail = (30f + 50f * r(i, 7)) * d * sizeF
            val fade = sin(p * PI.toFloat())
            paint.style = Paint.Style.STROKE
            for (k in 0 until 6) {
                val f0 = k / 6f
                val f1 = (k + 1) / 6f
                paint.strokeWidth = (2.2f - 1.6f * f0) * d
                paint.color = withA(METEOR_COL, (1f - f0) * fade)
                c.drawLine(hx - dx * tail * f0, hy - dy * tail * f0, hx - dx * tail * f1, hy - dy * tail * f1, paint)
            }
            paint.style = Paint.Style.FILL
            dot(c, hx, hy, 1.8f * d * sizeF, WHITE, fade)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
