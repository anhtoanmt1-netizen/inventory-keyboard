package com.inventory.scannerkeyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Nạp ảnh nền đã lưu (có làm mờ), giữ lại vài ảnh gần nhất cho khỏi nạp đi nạp lại. */
object ThemeImages {
    private val cache = HashMap<String, Bitmap>()

    fun load(ctx: Context, name: String, blur: Int): Bitmap? {
        val key = "$name#$blur"
        cache[key]?.let { return it }
        return try {
            val f = File(ThemeStore.imageDir(ctx), name)
            if (!f.exists()) return null
            var bmp: Bitmap = BitmapFactory.decodeFile(f.absolutePath) ?: return null
            if (blur > 0) {
                // Thu nhỏ rồi vẽ phóng lại bằng lọc song tuyến: cho hiệu ứng mờ, nhẹ và chạy được trên mọi đời máy.
                val k = 1 + blur / 3
                bmp = Bitmap.createScaledBitmap(bmp, max(1, bmp.width / k), max(1, bmp.height / k), true)
            }
            if (cache.size > 5) cache.clear()
            cache[key] = bmp
            bmp
        } catch (_: Throwable) { null }
    }

    fun forget() = cache.clear()
}

/**
 * Hình nền của một bảng: màu đơn sắc, gradient hoặc ảnh, có độ sáng, tương phản, mờ, vị trí và chế độ hiển thị.
 * Chỉ được đặt làm nền phía sau các phím nên không bao giờ che chữ hay ký hiệu trên phím.
 */
class ThemeBgDrawable(ctx: Context, private val st: PanelStyle) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()
    private var shader: Shader? = null
    private val bmp: Bitmap? = st.bgImage.let { name ->
        if (st.bgType == 3 && name != null) ThemeImages.load(ctx.applicationContext, name, st.bgBlur) else null
    }

    init {
        if (st.bgBrightness != 100 || st.bgContrast != 100) {
            val c = st.bgContrast / 100f
            val off = 128f * (1f - c) + (st.bgBrightness - 100) * 2.55f
            paint.colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, off,
                        0f, c, 0f, 0f, off,
                        0f, 0f, c, 0f, off,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }

    private fun opaque(c: Int) = c or 0xFF000000.toInt()

    override fun onBoundsChange(b: Rect) {
        shader = if (st.bgType == 2 && !b.isEmpty) {
            val a = Math.toRadians(st.bgAngle.toDouble())
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            val half = (abs(b.width() * dx) + abs(b.height() * dy)) / 2f
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            LinearGradient(
                cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half,
                opaque(st.bgColor1), opaque(st.bgColor2), Shader.TileMode.CLAMP
            )
        } else null
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        paint.shader = shader
        paint.color = opaque(st.bgColor1)
        canvas.drawRect(b, paint)
        val bm = bmp ?: return
        paint.shader = null
        paint.color = Color.WHITE
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val bw = bm.width.toFloat()
        val bh = bm.height.toFloat()
        var sx: Float
        var sy: Float
        when (st.bgMode) {
            1 -> { val s = min(w / bw, h / bh); sx = s; sy = s }
            2 -> { sx = 1f; sy = 1f }
            3 -> { sx = w / bw; sy = h / bh }
            else -> { val s = max(w / bw, h / bh); sx = s; sy = s }
        }
        val px = if (st.bgMode == 0) st.bgPosX / 100f else 0.5f
        val py = if (st.bgMode == 0) st.bgPosY / 100f else 0.5f
        val dx = (w - bw * sx) * px
        val dy = (h - bh * sy) * py
        matrix.setScale(sx, sy)
        matrix.postTranslate(b.left + dx, b.top + dy)
        canvas.save()
        canvas.clipRect(b)
        canvas.drawBitmap(bm, matrix, paint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

/** Dựng nền, phím, chữ và biểu tượng theo một [PanelStyle]. Dùng chung cho bàn phím thật và khu vực xem trước. */
object ThemeRender {
    enum class Kind { NORMAL, SPECIAL, ACCENT, TOGGLE }

    val FAMILY_NAMES = listOf("Montserrat", "Sans", "Serif", "Mono", "Gọn", "Viết tay", "Thư pháp")

    private val faces = HashMap<String, Typeface>()

    fun typeface(ctx: Context, st: PanelStyle): Typeface {
        val key = "${st.fontFamily}:${st.fontWeight}"
        faces[key]?.let { return it }
        val w = st.fontWeight.coerceIn(100, 900)
        var tf: Typeface? = null
        if (st.fontFamily == 0) {
            tf = try {
                Typeface.Builder(ctx.applicationContext.assets, "fonts/Montserrat.ttf")
                    .setFontVariationSettings("'wght' $w").build()
            } catch (_: Throwable) { null }
        }
        if (tf == null) {
            val name = when (st.fontFamily) {
                2 -> "serif"; 3 -> "monospace"; 4 -> "sans-serif-condensed"; 5 -> "casual"; 6 -> "cursive"
                else -> "sans-serif"
            }
            val base = Typeface.create(name, Typeface.NORMAL)
            tf = if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, w, false)
            else Typeface.create(base, if (w >= 600) Typeface.BOLD else Typeface.NORMAL)
        }
        val result: Typeface = tf ?: Typeface.DEFAULT
        faces[key] = result
        return result
    }

    // ------------------------------------------------------------------ màu
    private fun withPct(c: Int, pct: Int): Int = Color.argb(Color.alpha(c) * pct / 100, Color.red(c), Color.green(c), Color.blue(c))
    private fun darken(c: Int, f: Float): Int =
        Color.argb(Color.alpha(c), (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    // ------------------------------------------------------------------ phím
    private fun rect(color: Int, corner: Float, strokePx: Int, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = corner
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    /** Mặt phím: màu nền + viền, thêm cạnh nổi 3D (độ sâu) và lớp bóng phản chiếu nếu được bật. */
    private fun face(d: Float, st: PanelStyle, color: Int): Drawable {
        val corner = st.cornerDp * d
        val depth = (st.depthDp * d).toInt()
        val main = rect(color, corner, (st.borderDp * d).toInt(), st.borderColor)
        if (depth == 0 && st.glossPct == 0) return main
        val layers = ArrayList<Drawable>()
        if (depth > 0) layers.add(rect(darken(color, 0.55f), corner, 0, 0))
        val firstTop = layers.size
        layers.add(main)
        if (st.glossPct > 0) {
            val a = st.glossPct * 255 / 100
            layers.add(
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.argb(a, 255, 255, 255), Color.argb(0, 255, 255, 255))
                ).apply { cornerRadius = corner }
            )
        }
        val ld = LayerDrawable(layers.toTypedArray())
        for (i in firstTop until layers.size) ld.setLayerInset(i, 0, 0, 0, depth)
        return ld
    }

    fun keyBg(ctx: Context, st: PanelStyle, kind: Kind): Drawable {
        val d = ctx.resources.displayMetrics.density
        val base = when (kind) {
            Kind.NORMAL, Kind.TOGGLE -> st.keyColor
            Kind.SPECIAL -> st.specialColor
            Kind.ACCENT -> st.accentColor
        }
        val normal = face(d, st, withPct(base, st.keyAlpha))
        val content: Drawable = if (kind == Kind.SPECIAL || kind == Kind.TOGGLE) {
            StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_selected), face(d, st, withPct(st.accentColor, st.keyAlpha)))
                addState(intArrayOf(), normal)
            }
        } else normal
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = st.cornerDp * d
        }
        return RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), content, mask)
    }

    /** Độ nổi: bóng đổ dưới phím. Hình bóng bám theo bo góc và chừa phần cạnh 3D. */
    fun elevate(v: View, st: PanelStyle) {
        val d = v.resources.displayMetrics.density
        if (st.shadowDp <= 0) { v.elevation = 0f; return }
        val corner = st.cornerDp * d
        val depth = (st.depthDp * d).toInt()
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, max(1, view.height - depth), corner)
            }
        }
        v.elevation = st.shadowDp * d
        if (Build.VERSION.SDK_INT >= 28) {
            v.outlineAmbientShadowColor = st.shadowColor
            v.outlineSpotShadowColor = st.shadowColor
        }
    }

    // ------------------------------------------------------------------ chữ và biểu tượng
    fun styleText(tv: TextView, st: PanelStyle, setColor: Boolean = true) {
        val d = tv.resources.displayMetrics.density
        tv.typeface = typeface(tv.context, st)
        if (setColor) tv.setTextColor(st.textColor)
        if (st.textShadow > 0 || st.textRaise > 0) {
            tv.setShadowLayer(max(0.1f, st.textShadow * d), 0f, st.textRaise * d, st.textShadowColor)
        } else tv.setShadowLayer(0f, 0f, 0f, 0)
    }

    fun iconTint(st: PanelStyle): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
        intArrayOf(Color.WHITE, st.iconColor)
    )

    fun styleIcon(iv: ImageView, st: PanelStyle) { iv.imageTintList = iconTint(st) }

    /** Nút biểu tượng (hàng nút ở bảng Scan): nền phím + bóng + màu biểu tượng (giữ nguyên nếu dùng ảnh riêng). */
    fun styleButton(ctx: Context, v: ImageView, st: PanelStyle, kind: Kind, customImage: Boolean) {
        v.background = keyBg(ctx, st, kind)
        elevate(v, st)
        if (!customImage) styleIcon(v, st)
    }

    /** Hình nền cho một bảng, hoặc null nếu để mặc định (bàn phím dùng nền gốc). */
    fun panelBackground(ctx: Context, st: PanelStyle): Drawable? =
        if (st.bgType == 0) null else ThemeBgDrawable(ctx, st)

    // ------------------------------------------------------------------ khu vực xem trước
    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun previewKey(ctx: Context, st: PanelStyle, label: String?, icon: Int, kind: Kind, weight: Float): View {
        val v: View
        if (icon != 0) {
            v = ImageView(ctx).apply {
                setImageResource(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(ctx, 9), dp(ctx, 9), dp(ctx, 9), dp(ctx, 9))
                styleIcon(this, st)
            }
        } else {
            v = TextView(ctx).apply {
                text = label ?: ""
                gravity = Gravity.CENTER
                textSize = (if ((label?.length ?: 0) > 1) 12f else 16f) * st.fontScale / 100f
                styleText(this, st)
            }
        }
        v.background = keyBg(ctx, st, kind)
        elevate(v, st)
        val m = dp(ctx, st.gapDp)
        v.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply { setMargins(m, 0, m, 0) }
        return v
    }

    /** Gắn lớp hiệu ứng (ánh sáng chạy liên tục) vào khung xem trước và cho phép chạm thử vào các phím. */
    private fun attachFx(box: ViewGroup, st: PanelStyle, keys: List<View>) {
        val fx = FxDrawable(box) { keys }
        fx.style = st
        fx.attach()
        for (k in keys) {
            k.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> fx.down(v, e.x, e.y)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> fx.up(v)
                }
                true
            }
        }
    }

    private fun collectKeys(box: ViewGroup): List<View> {
        val out = ArrayList<View>()
        for (i in 0 until box.childCount) {
            val row = box.getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until row.childCount) {
                val c = row.getChildAt(j)
                if (c is TextView || c is ImageView) out.add(c)
            }
        }
        return out
    }

    /** Mô phỏng bảng Bàn phím (3 hàng phím chữ + hàng dưới) với đúng kiểu đang chỉnh. */
    fun previewKeyboard(ctx: Context, st: PanelStyle, rowDp: Int): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(ctx, 2), dp(ctx, 4), dp(ctx, 2), dp(ctx, 4))
            background = panelBackground(ctx, st)
        }
        fun row(): LinearLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(ctx, st.gapDp * 2 / 3), 0, dp(ctx, st.gapDp * 2 / 3))
            box.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, rowDp)))
        }
        val r1 = row()
        "qwertyuiop".forEach { r1.addView(previewKey(ctx, st, it.toString(), 0, Kind.NORMAL, 1f)) }
        val r2 = row()
        r2.addView(Space(ctx), LinearLayout.LayoutParams(0, 1, 0.5f))
        "asdfghjkl".forEach { r2.addView(previewKey(ctx, st, it.toString(), 0, Kind.NORMAL, 1f)) }
        r2.addView(Space(ctx), LinearLayout.LayoutParams(0, 1, 0.5f))
        val r3 = row()
        r3.addView(previewKey(ctx, st, null, R.drawable.ic_shift, Kind.SPECIAL, 1.5f))
        "zxcvbnm".forEach { r3.addView(previewKey(ctx, st, it.toString(), 0, Kind.NORMAL, 1f)) }
        r3.addView(previewKey(ctx, st, null, R.drawable.ic_backspace, Kind.SPECIAL, 1.5f))
        val r4 = row()
        r4.addView(previewKey(ctx, st, "?123", 0, Kind.SPECIAL, 1.5f))
        r4.addView(previewKey(ctx, st, "VN", 0, Kind.SPECIAL, 1.2f))
        r4.addView(previewKey(ctx, st, "", 0, Kind.NORMAL, 4f))
        r4.addView(previewKey(ctx, st, null, R.drawable.ic_enter, Kind.ACCENT, 1.5f))
        attachFx(box, st, collectKeys(box))
        return box
    }

    /** Mô phỏng bảng Scan: khung camera + dòng trạng thái + hàng nút chức năng. */
    fun previewScan(ctx: Context, st: PanelStyle): View {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6))
            background = panelBackground(ctx, st)
        }
        val cam = FrameLayout(ctx).apply { setBackgroundResource(R.drawable.bg_camera) }
        cam.addView(TextView(ctx).apply {
            text = "Đang quét… đưa mã vào khung"
            textSize = 11f * st.fontScale / 100f
            setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4))
            setBackgroundResource(R.drawable.bg_pill)
            styleText(this, st)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        box.addView(cam, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 50)))
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            clipChildren = false
            clipToPadding = false
        }
        for (key in ScanLayout.KEYS) {
            val iv = ImageView(ctx).apply {
                setImageResource(ScanLayout.ICONS[key] ?: R.drawable.ic_tune)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(ctx, 11), dp(ctx, 11), dp(ctx, 11), dp(ctx, 11))
            }
            styleButton(ctx, iv, st, if (key == "tools") Kind.TOGGLE else Kind.NORMAL, false)
            row.addView(iv, LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44)).apply { setMargins(dp(ctx, 4), 0, dp(ctx, 4), 0) })
        }
        box.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(ctx, 6) })
        val btns = ArrayList<View>()
        for (i in 0 until row.childCount) btns.add(row.getChildAt(i))
        attachFx(box, st, btns)
        return box
    }
}
