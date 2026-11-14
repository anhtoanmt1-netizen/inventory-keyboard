package com.inventory.scannerkeyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Lớp phủ trên camera: làm tối vùng ngoài khung, 4 góc khung "thở" nhẹ,
 * tia laser quét lên xuống có vệt sáng, và hiệu ứng dấu ✓ xanh khi quét thành công.
 */
class ScanOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private val accent = Color.parseColor("#F0A18A")
    private val green = Color.parseColor("#7BD389")

    private val frame = RectF()
    private val scrimPath = Path()
    private val cornerPath = Path()
    private val checkPath = Path()

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#59141A2A"); style = Paint.Style.FILL }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4.5f * d
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * d; strokeCap = Paint.Cap.ROUND }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f * d; color = Color.WHITE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private var laserShader: LinearGradient? = null
    private val trailHeight = 34f * d
    private val trailShader = LinearGradient(
        0f, -trailHeight, 0f, 0f, Color.parseColor("#00F0A18A"), Color.parseColor("#55F0A18A"), Shader.TileMode.CLAMP
    )

    private var laserT = 0f
    private var breathe = 0f
    private var successUntil = 0L

    var active: Boolean = false
        set(value) { field = value; updateAnimators(); invalidate() }

    private val laserAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1900; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { laserT = it.animatedValue as Float; invalidate() }
    }
    private val breatheAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1300; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { breathe = it.animatedValue as Float }
    }

    /** Gọi khi quét thành công: khung chuyển xanh và hiện dấu ✓ trong chốc lát. */
    fun flashSuccess() {
        successUntil = SystemClock.uptimeMillis() + 650
        invalidate()
    }

    private fun updateAnimators() {
        if (active && isAttachedToWindow) {
            if (!laserAnim.isRunning) laserAnim.start()
            if (!breatheAnim.isRunning) breatheAnim.start()
        } else {
            laserAnim.cancel(); breatheAnim.cancel()
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimators() }
    override fun onDetachedFromWindow() { laserAnim.cancel(); breatheAnim.cancel(); super.onDetachedFromWindow() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val fw = w * 0.80f
        val fh = h * 0.66f
        frame.set((w - fw) / 2f, (h - fh) / 2f - h * 0.02f, (w + fw) / 2f, (h + fh) / 2f - h * 0.02f)
        scrimPath.reset()
        scrimPath.fillType = Path.FillType.EVEN_ODD
        scrimPath.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        scrimPath.addRoundRect(frame, 16f * d, 16f * d, Path.Direction.CW)
        laserShader = LinearGradient(
            frame.left, 0f, frame.right, 0f,
            intArrayOf(Color.TRANSPARENT, accent, accent, Color.TRANSPARENT),
            floatArrayOf(0f, 0.25f, 0.75f, 1f), Shader.TileMode.CLAMP
        )
    }

    private fun corner(x: Float, y: Float, dx: Float, dy: Float, len: Float, r: Float) {
        cornerPath.moveTo(x, y + dy * len)
        cornerPath.lineTo(x, y + dy * r)
        cornerPath.quadTo(x, y, x + dx * r, y)
        cornerPath.lineTo(x + dx * len, y)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(scrimPath, scrimPaint)

        val now = SystemClock.uptimeMillis()
        val ok = now < successUntil
        val inset = if (ok) 0f else 3f * d * breathe          // 4 góc "thở" nhẹ
        val len = 28f * d
        val r = 14f * d

        cornerPath.reset()
        corner(frame.left + inset, frame.top + inset, 1f, 1f, len, r)
        corner(frame.right - inset, frame.top + inset, -1f, 1f, len, r)
        corner(frame.left + inset, frame.bottom - inset, 1f, -1f, len, r)
        corner(frame.right - inset, frame.bottom - inset, -1f, -1f, len, r)
        cornerPaint.color = if (ok) green else accent
        cornerPaint.alpha = if (ok) 255 else (175 + 80 * breathe).toInt()
        canvas.drawPath(cornerPath, cornerPaint)

        if (active && !ok) {
            val pad = 12f * d
            val y = frame.top + pad + laserT * (frame.height() - 2 * pad)
            canvas.save()
            canvas.translate(0f, y)
            trailPaint.shader = trailShader
            canvas.drawRect(frame.left + 6f * d, -trailHeight, frame.right - 6f * d, 0f, trailPaint)
            canvas.restore()
            laserPaint.shader = laserShader
            canvas.drawLine(frame.left + 4f * d, y, frame.right - 4f * d, y, laserPaint)
        }

        if (ok) {
            val remain = ((successUntil - now) / 650f).coerceIn(0f, 1f)
            val cx = frame.centerX(); val cy = frame.centerY()
            val rad = 30f * d * (1.25f - 0.25f * remain)
            circlePaint.color = green
            circlePaint.alpha = (230 * remain).toInt()
            canvas.drawCircle(cx, cy, rad, circlePaint)
            checkPaint.alpha = (255 * remain).toInt()
            checkPath.reset()
            checkPath.moveTo(cx - 12f * d, cy + 1f * d)
            checkPath.lineTo(cx - 3f * d, cy + 10f * d)
            checkPath.lineTo(cx + 13f * d, cy - 9f * d)
            canvas.drawPath(checkPath, checkPaint)
            postInvalidateOnAnimation()
        }
    }
}
