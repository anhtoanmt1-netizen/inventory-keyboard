package com.inventory.scannerkeyboard

import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Bảng chọn màu HSV: ô Bão hòa - Độ sáng, thanh Sắc độ (Hue), thanh Độ trong suốt (chỉ khi cần), ô mã HEX và 4 màu mẫu.
 * Tay cầm nhún lò xo khi nhấn và lướt có độ nảy khi đổi vị trí; chip xem trước tự đổi chữ trắng / đen theo độ tương phản;
 * dùng được bằng phím mũi tên. Hộp thoại nhập số chính xác ([askNumber]) nằm cuối file.
 */
object ColorPickerDialog {

    /**
     * Chọn màu. [onChange] được gọi liên tục khi kéo (để xem trước trực tiếp);
     * bấm Hủy thì trả về màu ban đầu.
     */
    fun show(activity: Activity, title: String, initial: Int, withAlpha: Boolean, onChange: (Int) -> Unit) {
        val d = activity.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val panel = ColorPickerPanel(activity, initial, withAlpha, onChange)
        val cardW = min(dp(284), activity.resources.displayMetrics.widthPixels - dp(88))
        val frame = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addView(panel.view, FrameLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
        }
        val sv = ScrollView(activity).apply {
            clipChildren = false
            clipToPadding = false
            addView(frame)
        }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(sv)
            .setNegativeButton("Hủy") { _, _ -> onChange(initial) }
            .setPositiveButton("Chọn") { _, _ -> onChange(panel.current()) }
            .setOnCancelListener { onChange(initial) }
            .setOnDismissListener { panel.release() }
            .show()
    }

    /** Nhập trực tiếp một số trong khoảng cho phép. */
    fun askNumber(activity: Activity, title: String, unit: String, min: Int, max: Int, current: Int, onValue: (Int) -> Unit) {
        val d = activity.resources.displayMetrics.density
        val et = EditText(activity).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(current.toString())
            setSelection(text.length)
            gravity = Gravity.CENTER
        }
        val wrap = LinearLayout(activity).apply { setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0); addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage("Nhập giá trị từ $min đến $max$unit")
            .setView(wrap)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("OK") { _, _ ->
                val v = et.text.toString().trim().toIntOrNull()
                if (v != null) onValue(v.coerceIn(min, max))
            }.show()
    }
}

// ====================================================================================================================
// Bảng chọn màu: hằng số chuyển động lấy nguyên từ đặc tả thiết kế (thời lượng và đường cong lò xo)
// ====================================================================================================================
private val SPRING_POS = PathInterpolator(0.34f, 1.45f, 0.5f, 1f)     // tay cầm đổi vị trí: 550 ms, nảy nhẹ
private val SPRING_SCALE = PathInterpolator(0.34f, 1.9f, 0.5f, 1f)    // tay cầm phình khi nhấn: 450 ms
private val SPRING_SW = PathInterpolator(0.34f, 1.8f, 0.5f, 1f)       // màu mẫu: 400 ms (nhấn 100 ms)
private val SHAKE = PathInterpolator(0.36f, 0.07f, 0.19f, 0.97f)      // ô HEX rung khi mã sai: 380 ms

private const val KIND_SV = 0
private const val KIND_HUE = 1
private const val KIND_ALPHA = 2

/** Tôn trọng cài đặt "tắt / giảm hiệu ứng chuyển động" của hệ thống (thang thời lượng animation = 0). */
private fun motionOn(ctx: Context): Boolean = try {
    Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
} catch (_: Throwable) { true }

/** Màu của bảng chọn, ánh xạ từ các token thiết kế sang bảng màu tối của ứng dụng. */
private class Tokens(ctx: Context) {
    val card = ContextCompat.getColor(ctx, R.color.surface2)     // nền thẻ (token surface-2 ở giao diện tối)
    val input = ContextCompat.getColor(ctx, R.color.surface)     // nền ô HEX (token surface ở giao diện tối)
    val line = 0x14FFFFFF                                        // viền thẻ
    val line2 = 0x29FFFFFF                                       // viền ô HEX
    val ink = ContextCompat.getColor(ctx, R.color.text)
    val ink3 = ContextCompat.getColor(ctx, R.color.muted)        // dấu # trong ô HEX
    val accent = ContextCompat.getColor(ctx, R.color.accent)
    val accentSoft = (accent and 0x00FFFFFF) or 0x26000000       // vòng sáng khi ô HEX đang nhập
    val danger = 0xFFFF7D8C.toInt()                              // mã HEX sai
}

private fun hsv2rgb(h: Float, s: Float, v: Float): IntArray {
    fun f(n: Int): Int {
        val k = (n + h / 60f) % 6f
        val x = v - v * s * max(0f, min(min(k, 4f - k), 1f))
        return Math.round(x * 255f)
    }
    return intArrayOf(f(5), f(3), f(1))
}

private fun rgb2hsv(r0: Int, g0: Int, b0: Int): FloatArray {
    val r = r0 / 255f
    val g = g0 / 255f
    val b = b0 / 255f
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    val d = mx - mn
    var h = 0f
    if (d != 0f) h = if (mx == r) ((g - b) / d) % 6f else if (mx == g) (b - r) / d + 2f else (r - g) / d + 4f
    return floatArrayOf((h * 60f + 360f) % 360f, if (mx != 0f) d / mx else 0f, mx)
}

private fun hex6(rgb: IntArray): String = String.format("%02X%02X%02X", rgb[0], rgb[1], rgb[2])

/** Độ sáng tương đối (WCAG) để chọn chữ trắng hay đen cho chip xem trước. */
private fun luminance(rgb: IntArray): Float {
    fun ch(x: Int): Float {
        val v = x / 255f
        return if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
    return 0.2126f * ch(rgb[0]) + 0.7152f * ch(rgb[1]) + 0.0722f * ch(rgb[2])
}

private fun checkerShader(d: Float): BitmapShader {
    val cell = (4f * d).toInt().coerceAtLeast(2)
    val bmp = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint()
    p.color = 0xFFFFFFFF.toInt()
    c.drawRect(0f, 0f, (cell * 2).toFloat(), (cell * 2).toFloat(), p)
    p.color = 0xFFD5D8DF.toInt()
    c.drawRect(0f, 0f, cell.toFloat(), cell.toFloat(), p)
    c.drawRect(cell.toFloat(), cell.toFloat(), (cell * 2).toFloat(), (cell * 2).toFloat(), p)
    return BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
}

/**
 * Vùng kéo chọn giá trị với tay cầm tròn: ô Bão hòa - Độ sáng (2 chiều), thanh Sắc độ hoặc thanh Độ trong suốt (1 chiều).
 * Tay cầm phình lên khi nhấn; chạm chỗ khác hoặc đổi màu từ ô HEX / màu mẫu thì lướt tới vị trí mới có độ nảy;
 * đang kéo thì bám sát ngón tay (không trễ).
 */
private class PickArea(ctx: Context, private val kind: Int) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val tk = Tokens(ctx)
    private val motion = motionOn(ctx)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val inner = RectF()
    private val barH = 14f * d
    private var hueColor = 0xFFFF0000.toInt()
    private var curColor = 0xFF000000.toInt()
    private var tx = 0f
    private var ty = 0f
    private var px = 0f
    private var py = 0f
    private var pk = 1f
    private var pressed = false
    private var dragging = false
    private var posAnim: ValueAnimator? = null
    private var scaleAnim: ValueAnimator? = null
    private var downX = 0f
    private var downY = 0f
    private var shaderKey = ""
    private var shader1: Shader? = null
    private var shader2: Shader? = null
    private val checker: BitmapShader by lazy { checkerShader(d) }
    private val rainbow = intArrayOf(
        0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(), 0xFF00FFFF.toInt(),
        0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt()
    )
    private val rainbowPos = floatArrayOf(0f, 1f / 6f, 2f / 6f, 0.5f, 4f / 6f, 5f / 6f, 1f)

    /** Người dùng kéo / chạm: toạ độ chuẩn hóa 0..1 trong vùng. */
    var onUser: ((Float, Float) -> Unit)? = null

    /** Phím mũi tên / Home / End: bước (đã nhân 10 nếu giữ Shift). */
    var onKey: ((Int, Int) -> Unit)? = null

    init {
        isFocusable = true
        isClickable = true
    }

    fun setColors(hue: Int, cur: Int) {
        hueColor = hue
        curColor = cur
        invalidate()
    }

    fun setTarget(x: Float, y: Float, instant: Boolean) {
        tx = x
        ty = y
        if (instant || dragging || !motion) {
            posAnim?.cancel()
            px = x
            py = y
            invalidate()
        } else glide()
    }

    private fun glide() {
        posAnim?.cancel()
        val sx = px
        val sy = py
        posAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 550L
            interpolator = SPRING_POS
            addUpdateListener { a ->
                val v = a.animatedValue as Float
                px = sx + (tx - sx) * v
                py = sy + (ty - sy) * v
                invalidate()
            }
            start()
        }
    }

    private fun scaleTo(k: Float) {
        scaleAnim?.cancel()
        if (!motion) { pk = 1f; invalidate(); return }
        val s = pk
        scaleAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450L
            interpolator = SPRING_SCALE
            addUpdateListener { a ->
                pk = s + (k - s) * (a.animatedValue as Float)
                invalidate()
            }
            start()
        }
    }

    fun release() {
        posAnim?.cancel()
        scaleAnim?.cancel()
    }

    private fun area(): RectF {
        if (kind == KIND_SV) box.set(0f, 0f, width.toFloat(), height.toFloat())
        else box.set(0f, (height - barH) / 2f, width.toFloat(), (height + barH) / 2f)
        return box
    }

    private fun ensureShaders(a: RectF) {
        val key = "$kind|${width}|${height}|$hueColor|$curColor"
        if (key == shaderKey) return
        shaderKey = key
        when (kind) {
            KIND_SV -> {
                shader1 = LinearGradient(0f, 0f, a.width(), 0f, 0xFFFFFFFF.toInt(), hueColor, Shader.TileMode.CLAMP)
                shader2 = LinearGradient(0f, a.height(), 0f, 0f, 0xFF000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
            }
            KIND_HUE -> shader1 = LinearGradient(a.left, 0f, a.right, 0f, rainbow, rainbowPos, Shader.TileMode.CLAMP)
            else -> shader1 = LinearGradient(a.left, 0f, a.right, 0f, curColor and 0x00FFFFFF, curColor or 0xFF000000.toInt(), Shader.TileMode.CLAMP)
        }
    }

    override fun onDraw(c: Canvas) {
        val a = area()
        val rad = if (kind == KIND_SV) 12f * d else 7f * d
        ensureShaders(a)
        paint.style = Paint.Style.FILL
        when (kind) {
            KIND_SV -> {
                paint.shader = shader1
                c.drawRoundRect(a, rad, rad, paint)
                paint.shader = shader2
                c.drawRoundRect(a, rad, rad, paint)
            }
            KIND_HUE -> {
                paint.shader = shader1
                c.drawRoundRect(a, rad, rad, paint)
            }
            else -> {
                paint.shader = checker
                c.drawRoundRect(a, rad, rad, paint)
                paint.shader = shader1
                c.drawRoundRect(a, rad, rad, paint)
            }
        }
        paint.shader = null
        // viền trong mảnh
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, d)
        paint.color = 0x14000000
        inner.set(a)
        inner.inset(paint.strokeWidth / 2f, paint.strokeWidth / 2f)
        c.drawRoundRect(inner, rad - paint.strokeWidth / 2f, rad - paint.strokeWidth / 2f, paint)

        // tay cầm
        val cx = a.left + px * a.width()
        val cy = if (kind == KIND_SV) a.top + py * a.height() else a.centerY()
        val k = pk
        val r = 11f * d * k
        paint.style = Paint.Style.FILL
        paint.color = 0x12000000; c.drawCircle(cx, cy + 4f * d * k, r + 9f * d * k, paint)   // bóng đổ mềm
        paint.color = 0x18000000; c.drawCircle(cx, cy + 4f * d * k, r + 6f * d * k, paint)
        paint.color = 0x22000000; c.drawCircle(cx, cy + 4f * d * k, r + 3f * d * k, paint)
        if (isFocused && !isInTouchMode) {                                                    // vòng lấy nét khi dùng bàn phím
            paint.color = tk.accent
            c.drawCircle(cx, cy, r + 5f * d * k, paint)
        } else {
            paint.color = 0x2E000000
            c.drawCircle(cx, cy, r + 4f * d * k, paint)
        }
        paint.color = 0xFFFFFFFF.toInt()
        c.drawCircle(cx, cy, r + 3f * d * k, paint)
        paint.color = if (kind == KIND_HUE) hueColor else curColor or 0xFF000000.toInt()
        c.drawCircle(cx, cy, r, paint)
    }

    private fun at(e: MotionEvent) {
        val a = area()
        val nx = ((e.x - a.left) / a.width()).coerceIn(0f, 1f)
        val ny = if (kind == KIND_SV) ((e.y - a.top) / a.height()).coerceIn(0f, 1f) else 0.5f
        onUser?.invoke(nx, ny)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pressed = true
                dragging = false
                downX = e.x
                downY = e.y
                scaleTo(1.28f)
                at(e)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pressed) return true
                if (!dragging && abs(e.x - downX) + abs(e.y - downY) > 3f * d) {
                    dragging = true
                    posAnim?.cancel()
                }
                at(e)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pressed) {
                    pressed = false
                    dragging = false
                    scaleTo(1f)
                    if (e.actionMasked == MotionEvent.ACTION_UP) performClick()
                }
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onKeyDown(keyCode: Int, ev: KeyEvent): Boolean {
        val k = if (ev.isShiftPressed) 10 else 1
        val step: IntArray = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> intArrayOf(-k, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> intArrayOf(k, 0)
            KeyEvent.KEYCODE_DPAD_UP -> intArrayOf(0, k)
            KeyEvent.KEYCODE_DPAD_DOWN -> intArrayOf(0, -k)
            KeyEvent.KEYCODE_MOVE_HOME -> intArrayOf(-1000, -1000)
            KeyEvent.KEYCODE_MOVE_END -> intArrayOf(1000, 1000)
            else -> return super.onKeyDown(keyCode, ev)
        }
        onKey?.invoke(step[0], step[1])
        invalidate()
        return true
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (kind != KIND_SV) {
            val top = if (kind == KIND_HUE) 360f else 100f
            info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, top, tx * top)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
    }

    override fun performAccessibilityAction(action: Int, args: android.os.Bundle?): Boolean {
        if (kind != KIND_SV) {
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) { onKey?.invoke(1, 0); return true }
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { onKey?.invoke(-1, 0); return true }
        }
        return super.performAccessibilityAction(action, args)
    }
}

/** Một màu mẫu tròn: nhún lò xo khi nhấn, vòng viền hiện ra (mờ dần + nảy) khi đang được chọn. */
private class SwatchView(ctx: Context, private val color: Int, val hex: String) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val motion = motionOn(ctx)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sc = 1f
    private var ringA = 0f
    private var ringS = 0.7f
    private var scAnim: ValueAnimator? = null
    private var aAnim: ValueAnimator? = null
    private var sAnim: ValueAnimator? = null
    private var on = false
    var onPick: (() -> Unit)? = null

    init {
        isClickable = true
        contentDescription = "#$hex"
    }

    private fun scaleTo(k: Float, ms: Long) {
        scAnim?.cancel()
        if (!motion) { sc = k; invalidate(); return }
        val s = sc
        scAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ms
            interpolator = SPRING_SW
            addUpdateListener { a -> sc = s + (k - s) * (a.animatedValue as Float); invalidate() }
            start()
        }
    }

    /** Đánh dấu đang được chọn (vòng viền hiện ra) hay không. */
    fun mark(selected: Boolean, animate: Boolean) {
        if (selected == on) return
        on = selected
        aAnim?.cancel()
        sAnim?.cancel()
        val toA = if (selected) 1f else 0f
        val toS = if (selected) 1f else 0.7f
        if (!motion || !animate) { ringA = toA; ringS = toS; invalidate(); return }
        val a0 = ringA
        val s0 = ringS
        aAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200L
            addUpdateListener { a -> ringA = a0 + (toA - a0) * (a.animatedValue as Float); invalidate() }
            start()
        }
        sAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400L
            interpolator = SPRING_SW
            addUpdateListener { a -> ringS = s0 + (toS - s0) * (a.animatedValue as Float); invalidate() }
            start()
        }
    }

    fun release() {
        scAnim?.cancel()
        aAnim?.cancel()
        sAnim?.cancel()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        c.save()
        c.scale(sc, sc, cx, cy)
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawCircle(cx, cy, 10.5f * d, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, d)
        paint.color = 0x1A000000
        c.drawCircle(cx, cy, 10.5f * d - paint.strokeWidth / 2f, paint)
        if (ringA > 0.01f) {
            c.save()
            c.scale(ringS, ringS, cx, cy)
            paint.strokeWidth = 1.5f * d
            paint.color = (color and 0x00FFFFFF) or ((ringA.coerceIn(0f, 1f) * 255f).toInt() shl 24)
            c.drawCircle(cx, cy, 14.75f * d, paint)
            c.restore()
        }
        c.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { scaleTo(0.9f, 100L); return true }
            MotionEvent.ACTION_UP -> {
                scaleTo(1f, 400L)
                if (e.x >= 0f && e.x <= width && e.y >= 0f && e.y <= height) { onPick?.invoke(); performClick() }
                return true
            }
            MotionEvent.ACTION_CANCEL -> { scaleTo(1f, 400L); return true }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean = super.performClick()
}

/** Chip xem trước màu đang chọn: chữ "Aa" tự đổi trắng / đen theo độ tương phản (chuyển màu 250 ms). */
private class PreviewChip(ctx: Context, private val checker: Boolean) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val motion = motionOn(ctx)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val path = Path()
    private var color = 0
    private var fg = 0xFFFFFFFF.toInt()
    private var fgAnim: ValueAnimator? = null
    private val shader: BitmapShader by lazy { checkerShader(d) }

    init {
        contentDescription = "Xem trước màu"
        text.textSize = 14f * ctx.resources.displayMetrics.scaledDensity
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text.textAlign = Paint.Align.CENTER
        text.letterSpacing = -0.02f
    }

    fun set(argb: Int, fgTarget: Int) {
        color = argb
        if (fgTarget != fg) {
            fgAnim?.cancel()
            if (!motion) fg = fgTarget
            else {
                fgAnim = ValueAnimator.ofArgb(fg, fgTarget).apply {
                    duration = 250L
                    addUpdateListener { a -> fg = a.animatedValue as Int; invalidate() }
                    start()
                }
            }
        }
        invalidate()
    }

    fun release() { fgAnim?.cancel() }

    override fun onDraw(c: Canvas) {
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        val rad = 10f * d
        path.reset()
        path.addRoundRect(box, rad, rad, Path.Direction.CW)
        c.save()
        c.clipPath(path)
        if (checker) {
            paint.shader = shader
            c.drawRect(box, paint)
            paint.shader = null
        }
        paint.style = Paint.Style.FILL
        paint.color = color
        c.drawRect(box, paint)
        c.restore()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, d)
        paint.color = 0x14000000
        box.inset(paint.strokeWidth / 2f, paint.strokeWidth / 2f)
        c.drawRoundRect(box, rad - paint.strokeWidth / 2f, rad - paint.strokeWidth / 2f, paint)
        text.color = fg
        val fm = text.fontMetrics
        c.drawText("Aa", width / 2f, height / 2f - (fm.ascent + fm.descent) / 2f, text)
    }
}

/**
 * Toàn bộ thẻ chọn màu và trạng thái HSV. [onChange] được gọi mỗi khi màu đổi (sau khi dựng xong), để xem trước trực tiếp.
 * Ô HEX nhận 3 hoặc 6 chữ số (có hoặc không có #), thêm 8 chữ số AARRGGBB khi có độ trong suốt; gõ đủ 6 chữ số là cập nhật
 * ngay, Enter hoặc rời ô thì chốt; mã sai thì ô rung và trả về màu hiện tại.
 */
private class ColorPickerPanel(
    private val a: Activity,
    initial: Int,
    private val withAlpha: Boolean,
    private val onChange: (Int) -> Unit
) {
    private val d = a.resources.displayMetrics.density
    private fun dp(v: Float) = (v * d).toInt()
    private val tk = Tokens(a)
    private val motion = motionOn(a)

    private var hh = 0f          // sắc độ 0..360
    private var ss = 0f          // bão hòa 0..1
    private var vv = 0f          // độ sáng 0..1
    private var aa = 1f          // độ trong suốt 0..1
    private var ready = false    // chỉ phát onChange / rung sau khi dựng xong
    private var typing = false   // đang ghi chữ vào ô HEX từ mã nguồn (không coi là người dùng gõ)
    private var bad = false
    private var lastTick = ""
    private var shakeAnim: ObjectAnimator? = null

    private val sv = PickArea(a, KIND_SV)
    private val hue = PickArea(a, KIND_HUE)
    private val alpha: PickArea? = if (withAlpha) PickArea(a, KIND_ALPHA) else null
    private val chip = PreviewChip(a, withAlpha)
    private lateinit var hexWrap: FrameLayout
    private lateinit var hex: EditText
    private val swatches = ArrayList<SwatchView>()
    private val liveRe = Regex(if (withAlpha) "^#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$" else "^#?[0-9a-fA-F]{6}$")
    private val re36 = Regex("^#?([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")
    private val re8 = Regex("^#?([0-9a-fA-F]{8})$")

    val view: LinearLayout = build()

    init {
        val c = initial
        val hsv = rgb2hsv(Color.red(c), Color.green(c), Color.blue(c))
        if (hsv[1] > 0f && hsv[2] > 0f) hh = hsv[0]
        ss = hsv[1]
        vv = hsv[2]
        aa = if (withAlpha) Color.alpha(c) / 255f else 1f
        sv.onUser = { x, y -> ss = x; vv = 1f - y; draw(false) }
        sv.onKey = { dx, dy -> ss = (ss + dx / 100f).coerceIn(0f, 1f); vv = (vv + dy / 100f).coerceIn(0f, 1f); draw(false) }
        hue.onUser = { x, _ -> hh = min(359.9f, x * 360f); draw(false) }
        hue.onKey = { dx, dy -> hh = (hh + dx + dy).coerceIn(0f, 359f); draw(false) }
        alpha?.onUser = { x, _ -> aa = x; draw(false) }
        alpha?.onKey = { dx, dy -> aa = (aa + (dx + dy) / 100f).coerceIn(0f, 1f); draw(false) }
        sv.contentDescription = "Độ bão hòa và độ sáng"
        hue.contentDescription = "Sắc độ"
        alpha?.contentDescription = "Độ trong suốt"
        draw(false)
        ready = true
    }

    private fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)

    private fun build(): LinearLayout {
        val card = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            isFocusableInTouchMode = true     // nhận tiêu điểm đầu tiên: ô HEX không tự bật bàn phím khi mở hộp thoại
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
            background = GradientDrawable().apply {
                setColor(tk.card)
                cornerRadius = 20f * d
                setStroke(max(1, d.toInt()), tk.line)
            }
        }
        card.addView(sv, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(140f)))
        // Thanh cao 28dp (vùng chạm rộng hơn), phần thanh vẽ 14dp ở giữa; khoảng cách nhìn thấy vẫn là 14dp
        card.addView(hue, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(28f)).apply { topMargin = dp(7f) })
        alpha?.let { card.addView(it, lp(ViewGroup.LayoutParams.MATCH_PARENT, dp(28f))) }
        card.addView(buildRow(), lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(7f) })
        return card
    }

    private fun buildRow(): LinearLayout {
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, 0, dp(2f), 0)
        }
        row.addView(chip, lp(dp(44f), dp(32f)))

        hexWrap = FrameLayout(a).apply {
            clipChildren = false
            setPadding(dp(3f), dp(3f), dp(3f), dp(3f))      // chừa chỗ cho vòng sáng khi ô đang nhập
        }
        hex = EditText(a).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(if (withAlpha) 9 else 7))
            textSize = 13f
            includeFontPadding = false
            typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.DEFAULT_BOLD
            letterSpacing = 0.02f
            fontFeatureSettings = "tnum"
            setTextColor(tk.ink)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(21f), 0, dp(4f), 0)
            contentDescription = "Mã màu HEX"
        }
        hexWrap.addView(hex, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32f)))
        hexWrap.addView(TextView(a).apply {
            text = "#"
            textSize = 13f
            setTextColor(tk.ink3)
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.START).apply {
            marginStart = dp(12f)
        })
        updateHexBg()
        hex.setOnFocusChangeListener { _, focused -> updateHexBg(); if (!focused) commit() }
        hex.setOnEditorActionListener { _, id, ev ->
            if (id == EditorInfo.IME_ACTION_DONE || (ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)) {
                commit(); true
            } else false
        }
        hex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (typing) return
                bad = false
                updateHexBg()
                val t = s?.toString()?.trim() ?: return
                if (liveRe.matches(t)) setHex(t, true)
            }
        })
        row.addView(hexWrap, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            weight = 1f
            setMargins(dp(5f), -dp(3f), dp(5f), -dp(3f))
        })

        val sws = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        for (hx in listOf("FF5A5F", "FFB020", "16C784", "2F80ED")) {
            val s = SwatchView(a, Color.parseColor("#$hx"), hx)
            s.onPick = { setHex(hx, false) }
            swatches.add(s)
            sws.addView(s, lp(dp(32f), dp(32f)).apply { setMargins(-dp(2f), 0, -dp(2f), 0) })
        }
        row.addView(sws, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(-dp(3.5f), 0, -dp(3.5f), 0)
        })
        return row
    }

    private fun updateHexBg() {
        val focused = hex.hasFocus()
        hex.background = GradientDrawable().apply {
            setColor(tk.input)
            cornerRadius = 10f * d
            setStroke(max(1, d.toInt()), when { bad -> tk.danger; focused -> tk.accent; else -> tk.line2 })
        }
        hexWrap.background = if (focused && !bad) GradientDrawable().apply { setColor(tk.accentSoft); cornerRadius = 13f * d } else null
    }

    fun current(): Int {
        val rgb = hsv2rgb(hh, ss, vv)
        return Color.argb(Math.round(aa * 255f), rgb[0], rgb[1], rgb[2])
    }

    /** Đặt màu từ chuỗi HEX. Trả về false nếu chuỗi không hợp lệ. Giữ nguyên sắc độ khi màu là xám / đen (bão hòa hoặc độ sáng = 0). */
    private fun setHex(t: String, fromInput: Boolean): Boolean {
        var newAlpha = -1
        var digits: String
        val m8 = if (withAlpha) re8.find(t) else null
        if (m8 != null) {
            val all = m8.groupValues[1]
            newAlpha = all.substring(0, 2).toInt(16)
            digits = all.substring(2)
        } else {
            val m = re36.find(t) ?: return false
            digits = m.groupValues[1]
            if (digits.length == 3) digits = digits.map { "$it$it" }.joinToString("")
        }
        val hsv = rgb2hsv(digits.substring(0, 2).toInt(16), digits.substring(2, 4).toInt(16), digits.substring(4, 6).toInt(16))
        if (hsv[1] > 0f && hsv[2] > 0f) hh = hsv[0]
        ss = hsv[1]
        vv = hsv[2]
        if (newAlpha >= 0) aa = newAlpha / 255f
        draw(fromInput)
        return true
    }

    private fun commit() {
        if (!setHex(hex.text.toString().trim(), false)) {
            shake()
            draw(false)
        }
    }

    private fun shake() {
        bad = true
        updateHexBg()
        if (!motion) return
        shakeAnim?.cancel()
        val x = 3f * d
        fun kf(f: Float, v: Float) = Keyframe.ofFloat(f, v).apply { interpolator = SHAKE }
        val pvh = PropertyValuesHolder.ofKeyframe("translationX", kf(0f, 0f), kf(0.25f, -x), kf(0.5f, x), kf(0.75f, -x), kf(1f, 0f))
        shakeAnim = ObjectAnimator.ofPropertyValuesHolder(hexWrap, pvh).apply { duration = 380L; start() }
    }

    /** Cập nhật mọi thành phần theo (hh, ss, vv, aa): tay cầm, chip, ô HEX, màu mẫu đang chọn, mô tả trợ năng. */
    private fun draw(fromInput: Boolean) {
        val rgb = hsv2rgb(hh, ss, vv)
        val h6 = hex6(rgb)
        val opaque = Color.rgb(rgb[0], rgb[1], rgb[2])
        val hr = hsv2rgb(hh, 1f, 1f)
        val hueColor = Color.rgb(hr[0], hr[1], hr[2])
        val instant = !ready
        sv.setColors(hueColor, opaque)
        sv.setTarget(ss, 1f - vv, instant)
        hue.setColors(hueColor, opaque)
        hue.setTarget(hh / 360f, 0.5f, instant)
        alpha?.setColors(hueColor, opaque)
        alpha?.setTarget(aa, 0.5f, instant)
        val l = luminance(rgb)
        val fg = if ((l + 0.05f) / 0.05f > 1.05f / (l + 0.05f)) 0xFF16151B.toInt() else 0xFFFFFFFF.toInt()
        chip.set(current(), fg)
        if (!fromInput) {
            val shown = if (withAlpha) String.format("%02X", Math.round(aa * 255f)) + h6 else h6
            if (hex.text.toString() != shown) {
                typing = true
                hex.setText(shown)
                hex.setSelection(shown.length)
                typing = false
            }
        }
        for (s in swatches) s.mark(s.hex == h6, ready)
        ViewCompat.setStateDescription(sv, "Bão hòa ${Math.round(ss * 100f)}%, độ sáng ${Math.round(vv * 100f)}%")
        ViewCompat.setStateDescription(hue, "${Math.round(hh)}°")
        alpha?.let { ViewCompat.setStateDescription(it, "${Math.round(aa * 100f)}%") }
        if (ready) {
            val key = "${Math.round(hh)}|${Math.round(ss * 100f)}|${Math.round(vv * 100f)}|${Math.round(aa * 100f)}"
            if (key != lastTick) { lastTick = key; SeekAssist.tick(a) }   // rung nhẹ mỗi khi giá trị đổi
            onChange(current())
        }
    }

    /** Hủy các hoạt ảnh đang chạy khi đóng hộp thoại (không để rò rỉ tham chiếu tới Activity). */
    fun release() {
        sv.release()
        hue.release()
        alpha?.release()
        chip.release()
        for (s in swatches) s.release()
        shakeAnim?.cancel()
    }
}
