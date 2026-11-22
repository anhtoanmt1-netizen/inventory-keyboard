package com.inventory.scannerkeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Giao diện và chức năng riêng của phím Space, lưu theo từng giao diện (KbTheme.space).
 * Mặc định [custom] = false: phím Space giữ đúng kiểu phím thường như bản gốc.
 * Phần chức năng (vuốt để di chuyển con trỏ) luôn có hiệu lực, không phụ thuộc [custom].
 */
data class SpaceStyle(
    // ---- chức năng ----
    val weight: Int = 42,                   // độ rộng phím (x0.1 so với phím chữ) 20..70
    val swipe: Boolean = true,              // vuốt ngang trên phím Space để di chuyển con trỏ
    val swipeStep: Int = 14,                // quãng vuốt cho mỗi ký tự (dp) 6..40
    val swipeHaptic: Boolean = true,        // rung nhẹ mỗi bước di chuyển con trỏ

    // ---- giao diện riêng (chỉ có tác dụng khi custom = true) ----
    val custom: Boolean = false,
    val cornerDp: Int = 10,                 // bo góc 0..30
    val borderDp: Int = 0,                  // viền 0..4
    val borderColor: Int = 0x66FFFFFF.toInt(),
    val color: Int = 0xFF363F55.toInt(),    // màu phím
    val alpha: Int = 100,                   // độ đặc 20..100 (%)
    val depthDp: Int = 0,                   // cạnh nổi 3D 0..8
    val shadowDp: Int = 0,                  // bóng đổ 0..10
    val shadowColor: Int = 0xFF000000.toInt(),
    val glossPct: Int = 0,                  // bóng phản chiếu 0..60

    // ---- ảnh trên phím ----
    val image: String? = null,              // tên file ảnh trong thư mục theme_images
    val imgMode: Int = 0,                   // 0 phủ kín (cắt), 1 vừa khít, 2 kéo giãn
    val imgAlpha: Int = 100,                // độ đặc của ảnh 10..100 (%)
    val imgPosX: Int = 50,                  // vị trí ảnh khi phủ kín 0..100 (%)
    val imgPosY: Int = 50,

    // ---- chữ trên phím ----
    val labelMode: Int = 0,                 // 0 không chữ, 1 chữ tự đặt, 2 tên ngôn ngữ đang gõ
    val labelText: String = "SPX",
    val labelSize: Int = 14,                // sp 8..28
    val labelColor: Int = 0xFFE4E8F1.toInt(),
    val fontFamily: Int = 0,                // xem ThemeRender.FAMILY_NAMES
    val fontWeight: Int = 500,              // 300..800
    val letterSpacing: Int = 0,             // x0.01 em, 0..40
    val labelShadow: Int = 0                // bóng chữ 0..8
) {
    fun toJson(): JSONObject = JSONObject()
        .put("weight", weight).put("swipe", swipe).put("swipeStep", swipeStep).put("swipeHaptic", swipeHaptic)
        .put("custom", custom).put("cornerDp", cornerDp).put("borderDp", borderDp).put("borderColor", borderColor)
        .put("color", color).put("alpha", alpha).put("depthDp", depthDp).put("shadowDp", shadowDp)
        .put("shadowColor", shadowColor).put("glossPct", glossPct)
        .put("image", image ?: JSONObject.NULL).put("imgMode", imgMode).put("imgAlpha", imgAlpha)
        .put("imgPosX", imgPosX).put("imgPosY", imgPosY)
        .put("labelMode", labelMode).put("labelText", labelText).put("labelSize", labelSize)
        .put("labelColor", labelColor).put("fontFamily", fontFamily).put("fontWeight", fontWeight)
        .put("letterSpacing", letterSpacing).put("labelShadow", labelShadow)

    companion object {
        fun fromJson(o: JSONObject?): SpaceStyle {
            val d = SpaceStyle()
            if (o == null) return d
            return SpaceStyle(
                weight = o.optInt("weight", d.weight).coerceIn(20, 70),
                swipe = o.optBoolean("swipe", d.swipe),
                swipeStep = o.optInt("swipeStep", d.swipeStep).coerceIn(6, 40),
                swipeHaptic = o.optBoolean("swipeHaptic", d.swipeHaptic),
                custom = o.optBoolean("custom", d.custom),
                cornerDp = o.optInt("cornerDp", d.cornerDp).coerceIn(0, 30),
                borderDp = o.optInt("borderDp", d.borderDp).coerceIn(0, 4),
                borderColor = o.optInt("borderColor", d.borderColor),
                color = o.optInt("color", d.color),
                alpha = o.optInt("alpha", d.alpha).coerceIn(20, 100),
                depthDp = o.optInt("depthDp", d.depthDp).coerceIn(0, 8),
                shadowDp = o.optInt("shadowDp", d.shadowDp).coerceIn(0, 10),
                shadowColor = o.optInt("shadowColor", d.shadowColor),
                glossPct = o.optInt("glossPct", d.glossPct).coerceIn(0, 60),
                image = if (o.has("image") && !o.isNull("image")) o.getString("image") else null,
                imgMode = o.optInt("imgMode", d.imgMode).coerceIn(0, 2),
                imgAlpha = o.optInt("imgAlpha", d.imgAlpha).coerceIn(10, 100),
                imgPosX = o.optInt("imgPosX", d.imgPosX).coerceIn(0, 100),
                imgPosY = o.optInt("imgPosY", d.imgPosY).coerceIn(0, 100),
                labelMode = o.optInt("labelMode", d.labelMode).coerceIn(0, 2),
                labelText = o.optString("labelText", d.labelText).take(24),
                labelSize = o.optInt("labelSize", d.labelSize).coerceIn(8, 28),
                labelColor = o.optInt("labelColor", d.labelColor),
                fontFamily = o.optInt("fontFamily", d.fontFamily).coerceIn(0, ThemeRender.FAMILY_NAMES.size - 1),
                fontWeight = o.optInt("fontWeight", d.fontWeight).coerceIn(300, 800),
                letterSpacing = o.optInt("letterSpacing", d.letterSpacing).coerceIn(0, 40),
                labelShadow = o.optInt("labelShadow", d.labelShadow).coerceIn(0, 8)
            )
        }
    }
}

/** Vẽ ảnh trên mặt phím Space, bo góc theo phím, có độ đặc, vị trí và chế độ hiển thị. */
class SpaceImageDrawable(
    private val bmp: Bitmap,
    private val sp: SpaceStyle,
    private val corner: Float
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private val box = RectF()
    private val clip = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        box.set(b)
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val w = box.width()
        val h = box.height()
        if (bw <= 0f || bh <= 0f) return
        paint.alpha = sp.imgAlpha * 255 / 100
        canvas.save()
        clip.reset()
        clip.addRoundRect(box, corner, corner, Path.Direction.CW)
        canvas.clipPath(clip)
        when (sp.imgMode) {
            2 -> {
                src.set(0, 0, bmp.width, bmp.height)
                dst.set(box)
            }
            1 -> {
                val s = min(w / bw, h / bh)
                val dw = bw * s
                val dh = bh * s
                src.set(0, 0, bmp.width, bmp.height)
                dst.set(box.left + (w - dw) / 2f, box.top + (h - dh) / 2f, box.left + (w + dw) / 2f, box.top + (h + dh) / 2f)
            }
            else -> {
                val s = max(w / bw, h / bh)
                val vw = w / s
                val vh = h / s
                val sx = (bw - vw) * sp.imgPosX / 100f
                val sy = (bh - vh) * sp.imgPosY / 100f
                src.set(sx.toInt(), sy.toInt(), (sx + vw).toInt().coerceAtMost(bmp.width), (sy + vh).toInt().coerceAtMost(bmp.height))
                dst.set(box)
            }
        }
        canvas.drawBitmap(bmp, src, dst, paint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** Dựng nền, chữ và hình ảnh của phím Space từ [SpaceStyle]. Dùng chung cho bàn phím thật và khung xem trước. */
object SpaceRender {
    private val bitmaps = HashMap<String, Bitmap>()

    /** Kiểu phím tương ứng để tái dùng bộ dựng phím của ThemeRender. */
    fun derived(kb: PanelStyle, sp: SpaceStyle): PanelStyle = kb.copy(
        cornerDp = sp.cornerDp, borderDp = sp.borderDp, borderColor = sp.borderColor,
        keyColor = sp.color, keyAlpha = sp.alpha, depthDp = sp.depthDp, glossPct = sp.glossPct,
        shadowDp = sp.shadowDp, shadowColor = sp.shadowColor
    )

    /** Kiểu dùng để đổ bóng cho phím Space. */
    fun shapeStyle(kb: PanelStyle, sp: SpaceStyle): PanelStyle = if (sp.custom) derived(kb, sp) else kb

    fun forget() = bitmaps.clear()

    /** Ảnh của phím Space (đã nạp, có bộ nhớ đệm). */
    fun bitmapOf(ctx: Context, name: String): Bitmap? = loadBitmap(ctx, name)

    private fun loadBitmap(ctx: Context, name: String): Bitmap? {
        bitmaps[name]?.let { return it }
        return try {
            val f = File(ThemeStore.imageDir(ctx), name)
            if (!f.exists()) return null
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, o)
            var s = 1
            while (o.outWidth / (s * 2) >= 1024 || o.outHeight / (s * 2) >= 1024) s *= 2
            val bmp = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
            if (bitmaps.size > 4) bitmaps.clear()
            bitmaps[name] = bmp
            bmp
        } catch (_: Throwable) { null }
    }

    fun background(ctx: Context, kb: PanelStyle, sp: SpaceStyle): Drawable {
        if (!sp.custom) return ThemeRender.keyBg(ctx, kb, ThemeRender.Kind.NORMAL)
        val st = derived(kb, sp)
        val d = ctx.resources.displayMetrics.density
        val face = ThemeRender.faceOnly(ctx, st, ThemeRender.Kind.NORMAL)
        val bmp = sp.image?.let { loadBitmap(ctx, it) }
        if (bmp == null) return ThemeRender.withRipple(ctx, st, face)
        val ld = LayerDrawable(arrayOf(face, SpaceImageDrawable(bmp, sp, sp.cornerDp * d)))
        ld.setLayerInset(1, 0, 0, 0, (sp.depthDp * d).toInt())
        return ThemeRender.withRipple(ctx, st, ld)
    }

    fun labelFor(sp: SpaceStyle, vn: Boolean): String = if (!sp.custom) "" else when (sp.labelMode) {
        1 -> sp.labelText
        2 -> if (vn) "Tiếng Việt" else "English"
        else -> ""
    }

    /** Chữ trên phím Space (phím gốc không có chữ). */
    fun styleLabel(tv: TextView, kb: PanelStyle, sp: SpaceStyle, vn: Boolean) {
        if (!sp.custom) {
            ThemeRender.styleText(tv, kb)
            tv.text = ""
            return
        }
        val d = tv.resources.displayMetrics.density
        tv.text = labelFor(sp, vn)
        tv.textSize = sp.labelSize * kb.fontScale / 100f
        tv.typeface = ThemeRender.typeface(tv.context, kb.copy(fontFamily = sp.fontFamily, fontWeight = sp.fontWeight))
        tv.setTextColor(sp.labelColor)
        tv.letterSpacing = sp.letterSpacing / 100f
        if (sp.labelShadow > 0) tv.setShadowLayer(max(0.1f, sp.labelShadow * d), 0f, kb.textRaise * d, kb.textShadowColor)
        else tv.setShadowLayer(0f, 0f, 0f, 0)
    }
}
