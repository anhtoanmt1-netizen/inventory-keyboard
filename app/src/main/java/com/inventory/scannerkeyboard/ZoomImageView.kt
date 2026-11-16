package com.inventory.scannerkeyboard

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView

/**
 * Ảnh phóng to / thu nhỏ: hai ngón để zoom, một ngón để kéo, chạm đúp để phóng to hoặc về vừa khung.
 * Không bao giờ thu nhỏ hơn mức vừa khung và không cho kéo ảnh ra khỏi khung.
 */
class ZoomImageView(ctx: Context) : ImageView(ctx) {

    private val m = Matrix()
    private val vals = FloatArray(9)
    private val rect = RectF()
    private var minScale = 1f
    private val maxZoom = 8f

    private val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomAt(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    })

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (currentScale() > minScale * 1.05f) fit() else zoomAt(2.5f, e.x, e.y)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!scaleDetector.isInProgress) {
                m.postTranslate(-distanceX, -distanceY)
                clamp()
                imageMatrix = m
            }
            return true
        }
    })

    init { scaleType = ScaleType.MATRIX }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fit()
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        fit()
    }

    private fun currentScale(): Float {
        m.getValues(vals)
        return vals[Matrix.MSCALE_X]
    }

    /** Đưa ảnh về vừa khung, nằm giữa. */
    fun fit() {
        val dr = drawable ?: return
        val dw = dr.intrinsicWidth.toFloat()
        val dh = dr.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f || width == 0 || height == 0) return
        val s = minOf(width / dw, height / dh)
        minScale = s
        m.reset()
        m.postScale(s, s)
        m.postTranslate((width - dw * s) / 2f, (height - dh * s) / 2f)
        imageMatrix = m
    }

    /** Phóng to / thu nhỏ quanh tâm khung. */
    fun zoomBy(factor: Float) = zoomAt(factor, width / 2f, height / 2f)

    private fun zoomAt(factor: Float, fx: Float, fy: Float) {
        if (drawable == null) return
        val cur = currentScale()
        val target = (cur * factor).coerceIn(minScale, minScale * maxZoom)
        val f = target / cur
        m.postScale(f, f, fx, fy)
        clamp()
        imageMatrix = m
    }

    private fun clamp() {
        val dr = drawable ?: return
        rect.set(0f, 0f, dr.intrinsicWidth.toFloat(), dr.intrinsicHeight.toFloat())
        m.mapRect(rect)
        var dx = 0f
        var dy = 0f
        val w = width.toFloat()
        val h = height.toFloat()
        dx = when {
            rect.width() <= w -> (w - rect.width()) / 2f - rect.left
            rect.left > 0f -> -rect.left
            rect.right < w -> w - rect.right
            else -> 0f
        }
        dy = when {
            rect.height() <= h -> (h - rect.height()) / 2f - rect.top
            rect.top > 0f -> -rect.top
            rect.bottom < h -> h - rect.bottom
            else -> 0f
        }
        m.postTranslate(dx, dy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        gestures.onTouchEvent(event)
        return true
    }
}
