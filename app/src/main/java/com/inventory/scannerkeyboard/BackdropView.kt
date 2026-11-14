package com.inventory.scannerkeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Lớp nền nằm SAU các phím: màu đơn, chuyển màu hoặc ảnh (có chỉnh độ sáng, tương phản, độ mờ, vị trí).
 * Chỉ vẽ phía sau, không đụng tới chữ và ký hiệu trên phím.
 * (Đợt 4B sẽ vẽ thêm hiệu ứng chuyển động và ánh sáng trên cùng lớp này.)
 */
class BackdropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var style = StyleSet()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint()
    private var raw: Bitmap? = null
    private var rawName: String? = null
    private var processed: Bitmap? = null
    private var procKey = ""

    fun setStyle(s: StyleSet) {
        if (s == style) return
        style = s
        invalidate()
    }

    // ------------------------------------------------------------------ vẽ
    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        when (style.int("bgType")) {
            1 -> drawGradient(canvas, w, h)
            2 -> if (!drawImage(canvas, w, h)) canvas.drawColor(style.int("bgColor"))
            else -> canvas.drawColor(style.int("bgColor"))
        }
    }

    private fun drawGradient(canvas: Canvas, w: Int, h: Int) {
        val a = Math.toRadians(style.int("bgAngle").toDouble())
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        val half = (abs(w * dx) + abs(h * dy)) / 2f
        val cx = w / 2f
        val cy = h / 2f
        fill.shader = LinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half,
            style.int("bgColor"), style.int("bgColor2"), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        fill.shader = null
    }

    private fun loadRaw(): Bitmap? {
        val name = style.str("bgImage") ?: return null
        if (name == rawName && raw != null) return raw
        rawName = name
        raw = try {
            val f = File(ThemeImages.dir(context), name)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        } catch (_: Throwable) { null }
        procKey = ""
        return raw
    }

    private fun drawImage(canvas: Canvas, w: Int, h: Int): Boolean {
        val src = loadRaw() ?: return false
        val key = "$rawName|$w|$h|${style.int("bgFit")}|${style.int("bgBlur")}|${style.int("bgFocusX")}|${style.int("bgFocusY")}|${style.int("bgColor")}"
        if (key != procKey || processed == null) {
            processed = process(src, w, h)
            procKey = key
        }
        val p = processed ?: return false
        // độ sáng / tương phản chỉ là bộ lọc khi vẽ, chỉnh thanh trượt không phải xử lý lại ảnh
        val c = style.int("bgContrast").coerceIn(0, 200) / 100f
        val t = (-0.5f * c + 0.5f) * 255f + style.int("bgBright").coerceIn(-100, 100) * 2.55f
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        canvas.drawBitmap(p, 0f, 0f, paint)
        paint.colorFilter = null
        return true
    }

    /** Dàn ảnh theo chế độ hiển thị, rồi làm mờ (thu nhỏ rồi phóng lại: nhanh, đỡ tốn pin). */
    private fun process(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(style.int("bgColor"))
        val dst = Rect(0, 0, w, h)
        val fx = style.int("bgFocusX").coerceIn(0, 100) / 100f
        val fy = style.int("bgFocusY").coerceIn(0, 100) / 100f
        when (style.int("bgFit")) {
            1 -> { // vừa khung: thấy trọn ảnh
                val sc = minOf(w / src.width.toFloat(), h / src.height.toFloat())
                val dw = (src.width * sc).toInt()
                val dh = (src.height * sc).toInt()
                val l = ((w - dw) * fx).toInt()
                val t = ((h - dh) * fy).toInt()
                c.drawBitmap(src, null, Rect(l, t, l + dw, t + dh), paint)
            }
            2 -> c.drawBitmap(src, null, dst, paint) // kéo giãn
            else -> { // phủ kín: cắt phần thừa theo điểm lấy nét
                val sc = max(w / src.width.toFloat(), h / src.height.toFloat())
                val vw = (w / sc).toInt().coerceAtMost(src.width)
                val vh = (h / sc).toInt().coerceAtMost(src.height)
                val l = ((src.width - vw) * fx).toInt()
                val t = ((src.height - vh) * fy).toInt()
                c.drawBitmap(src, Rect(l, t, l + vw, t + vh), dst, paint)
            }
        }
        val blur = style.int("bgBlur").coerceIn(0, 100)
        if (blur <= 0) return out
        val f = 1 + blur / 6 // 1..17
        val sw = max(1, w / f)
        val sh = max(1, h / f)
        val small = Bitmap.createScaledBitmap(out, sw, sh, true)
        val up = Bitmap.createScaledBitmap(small, w, h, true)
        if (small !== up) small.recycle()
        out.recycle()
        return up
    }
}
