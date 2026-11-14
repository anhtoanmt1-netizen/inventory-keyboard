package com.inventory.scannerkeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Cấu hình riêng của một nút trên bảng Scan. */
data class BtnCfg(
    val image: String? = null,   // tên file PNG trong thư mục btn_images (null = biểu tượng mặc định)
    val imgW: Int = 0,           // kích thước ảnh (px), 0 = tự vừa khít nút
    val imgH: Int = 0,
    val imgH_align: Int = 1,     // căn ngang trong nút: 0 trái, 1 giữa, 2 phải
    val imgV_align: Int = 1,     // căn dọc trong nút: 0 trên, 1 giữa, 2 dưới
    val scale: Int = 100         // thu phóng riêng của nút (%)
)

/**
 * Bố cục các nút ở hàng chức năng dưới bảng Scan. Lưu dạng JSON trong Prefs, dùng chung cho
 * bàn phím thật và khu vực xem trước trong màn hình chỉnh sửa.
 */
data class ScanLayout(
    val order: List<String> = DEFAULT_ORDER,
    val perRow: Int = 5,          // số nút mỗi hàng (1..5)
    val widthDp: Int = 46,        // chiều rộng nút (dp)
    val heightDp: Int = 46,       // chiều cao nút (dp)
    val scale: Int = 100,         // thu phóng chung (%)
    val gapDp: Int = 6,           // khoảng cách ngang giữa các nút (dp), dùng khi căn Trái/Giữa/Phải
    val align: Int = ALIGN_CENTER,
    val grid: Boolean = true,     // true: căn theo cột (lưới), false: mỗi hàng tự căn riêng
    val buttons: Map<String, BtnCfg> = emptyMap()
) {
    companion object {
        const val ALIGN_LEFT = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_RIGHT = 2
        const val ALIGN_BETWEEN = 3   // dàn đều: hai đầu sát mép, khoảng giữa bằng nhau
        const val ALIGN_AROUND = 4    // cách đều: có khoảng ở cả hai đầu

        val KEYS = listOf("tools", "del", "enter", "settings", "switch")
        val DEFAULT_ORDER = KEYS
        val LABELS = mapOf(
            "tools" to "Công cụ", "del" to "Xóa", "enter" to "Enter",
            "settings" to "Cài đặt", "switch" to "Đổi bàn phím"
        )
        val ICONS = mapOf(
            "tools" to R.drawable.ic_tune, "del" to R.drawable.ic_backspace, "enter" to R.drawable.ic_enter,
            "settings" to R.drawable.ic_settings, "switch" to R.drawable.ic_globe
        )

        fun fromJson(text: String?): ScanLayout {
            if (text.isNullOrBlank()) return ScanLayout()
            return try {
                val o = JSONObject(text)
                val d = ScanLayout()
                val ord = ArrayList<String>()
                o.optJSONArray("order")?.let { a -> for (i in 0 until a.length()) if (a.getString(i) in KEYS && a.getString(i) !in ord) ord.add(a.getString(i)) }
                for (k in KEYS) if (k !in ord) ord.add(k) // nút thiếu thì thêm lại, không bao giờ mất nút
                val btns = HashMap<String, BtnCfg>()
                o.optJSONObject("buttons")?.let { b ->
                    for (k in KEYS) b.optJSONObject(k)?.let { x ->
                        btns[k] = BtnCfg(
                            image = if (x.has("image") && !x.isNull("image")) x.getString("image") else null,
                            imgW = x.optInt("imgW", 0).coerceIn(0, 400), imgH = x.optInt("imgH", 0).coerceIn(0, 400),
                            imgH_align = x.optInt("ha", 1).coerceIn(0, 2), imgV_align = x.optInt("va", 1).coerceIn(0, 2),
                            scale = x.optInt("scale", 100).coerceIn(50, 200)
                        )
                    }
                }
                ScanLayout(
                    ord, o.optInt("perRow", d.perRow).coerceIn(1, 5), o.optInt("w", d.widthDp).coerceIn(32, 96),
                    o.optInt("h", d.heightDp).coerceIn(32, 96), o.optInt("scale", d.scale).coerceIn(60, 160),
                    o.optInt("gap", d.gapDp).coerceIn(0, 24), o.optInt("align", d.align).coerceIn(0, 4),
                    o.optBoolean("grid", d.grid), btns
                )
            } catch (_: Throwable) { ScanLayout() }
        }
    }

    fun cfg(key: String): BtnCfg = buttons[key] ?: BtnCfg()

    fun withButton(key: String, c: BtnCfg): ScanLayout = copy(buttons = buttons + (key to c))

    fun toJson(): String {
        val b = JSONObject()
        for ((k, c) in buttons) b.put(
            k, JSONObject().put("image", c.image ?: JSONObject.NULL).put("imgW", c.imgW).put("imgH", c.imgH)
                .put("ha", c.imgH_align).put("va", c.imgV_align).put("scale", c.scale)
        )
        return JSONObject().put("order", JSONArray(order)).put("perRow", perRow).put("w", widthDp).put("h", heightDp)
            .put("scale", scale).put("gap", gapDp).put("align", align).put("grid", grid).put("buttons", b).toString()
    }

    /** Tên các file ảnh đang được dùng. */
    fun usedImages(): Set<String> = buttons.values.mapNotNull { it.image }.toSet()
}

/** Dựng các nút và các hàng nút từ một [ScanLayout]. */
object ScanButtons {
    fun imageDir(ctx: Context): File = File(ctx.applicationContext.filesDir, "btn_images").apply { mkdirs() }

    private val bitmapCache = HashMap<String, Bitmap>()

    private fun loadBitmap(ctx: Context, name: String): Bitmap? {
        bitmapCache[name]?.let { return it }
        return try {
            val f = File(imageDir(ctx), name)
            if (!f.exists()) null else BitmapFactory.decodeFile(f.absolutePath)?.also { bitmapCache[name] = it }
        } catch (_: Throwable) { null }
    }

    /** Kích thước thật của nút (px) sau khi nhân thu phóng chung và thu phóng riêng. */
    fun sizePx(ctx: Context, lay: ScanLayout, key: String): Pair<Int, Int> {
        val d = ctx.resources.displayMetrics.density
        val k = lay.scale / 100f * lay.cfg(key).scale / 100f
        return Pair((lay.widthDp * d * k).toInt().coerceAtLeast(1), (lay.heightDp * d * k).toInt().coerceAtLeast(1))
    }

    fun hasCustomImage(ctx: Context, lay: ScanLayout, key: String): Boolean {
        val n = lay.cfg(key).image ?: return false
        return loadBitmap(ctx, n) != null
    }

    /**
     * Tạo ImageView cho một nút. Có ảnh PNG riêng thì dùng ảnh đó (giữ tỉ lệ, không bị méo),
     * không thì dùng biểu tượng mặc định.
     */
    fun makeButton(ctx: Context, lay: ScanLayout, key: String, style: StyleSet = StyleSet()): ImageView {
        val d = ctx.resources.displayMetrics.density
        val (w, h) = sizePx(ctx, lay, key)
        val c = lay.cfg(key)
        val iv = ImageView(ctx)
        iv.isClickable = true
        iv.background = KbStyler.background(ctx, style, if (key == "tools") KbStyler.Kind.TOGGLE else KbStyler.Kind.NORMAL)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        val bmp = c.image?.let { loadBitmap(ctx, it) }
        if (bmp != null) {
            iv.imageTintList = null
            iv.setImageBitmap(bmp)
            // Vùng chứa ảnh: kích thước người dùng nhập (px), không vượt quá nút
            val cw = (if (c.imgW > 0) c.imgW else w).coerceIn(1, w)
            val ch = (if (c.imgH > 0) c.imgH else h).coerceIn(1, h)
            val remW = w - cw
            val remH = h - ch
            val left = when (c.imgH_align) { 0 -> 0; 2 -> remW; else -> remW / 2 }
            val top = when (c.imgV_align) { 0 -> 0; 2 -> remH; else -> remH / 2 }
            iv.setPadding(left, top, remW - left, remH - top)
        } else {
            iv.setImageResource(ScanLayout.ICONS[key] ?: R.drawable.ic_tune)
            iv.imageTintList = KbStyler.iconTint(style)
            // biểu tượng mặc định ~24dp ở kích thước chuẩn 46dp, lớn lên theo nút
            val icon = (minOf(w, h) * 0.52f).toInt().coerceAtLeast((12 * d).toInt())
            iv.setPadding((w - icon) / 2, (h - icon) / 2, (w - icon) / 2, (h - icon) / 2)
        }
        iv.layoutParams = LinearLayout.LayoutParams(w, h)
        return iv
    }

    /** Dựng toàn bộ các hàng nút (dọc, mỗi hàng ngang) từ danh sách nút đã tạo sẵn. */
    fun buildRows(ctx: Context, lay: ScanLayout, buttons: Map<String, ImageView>): LinearLayout {
        val d = ctx.resources.displayMetrics.density
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val keys = lay.order.filter { buttons.containsKey(it) }
        val per = lay.perRow.coerceIn(1, 5)
        val gap = (lay.gapDp * d / 2).toInt()
        for (chunk in keys.chunked(per)) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((4 * d).toInt(), (2 * d).toInt(), (4 * d).toInt(), (2 * d).toInt())
            }
            val rowH = chunk.maxOf { buttons[it]!!.layoutParams.height }
            if (lay.grid) {
                // Căn theo cột: mỗi ô rộng bằng nhau, nút nằm trong ô theo kiểu căn lề đã chọn
                val g = when (lay.align) { ScanLayout.ALIGN_LEFT -> Gravity.START; ScanLayout.ALIGN_RIGHT -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL }
                for (i in 0 until per) {
                    val cell = FrameLayout(ctx)
                    val key = chunk.getOrNull(i)
                    if (key != null) {
                        val b = buttons[key]!!
                        (b.parent as? ViewGroup)?.removeView(b)
                        cell.addView(b, FrameLayout.LayoutParams(b.layoutParams.width, b.layoutParams.height, g or Gravity.CENTER_VERTICAL))
                    }
                    row.addView(cell, LinearLayout.LayoutParams(0, rowH, 1f))
                }
            } else {
                row.gravity = Gravity.CENTER_VERTICAL or when (lay.align) {
                    ScanLayout.ALIGN_LEFT -> Gravity.START
                    ScanLayout.ALIGN_RIGHT -> Gravity.END
                    else -> Gravity.CENTER_HORIZONTAL
                }
                fun spacer(weight: Float) = row.addView(Space(ctx), LinearLayout.LayoutParams(0, 1, weight))
                if (lay.align == ScanLayout.ALIGN_AROUND) spacer(0.5f)
                chunk.forEachIndexed { i, key ->
                    val b = buttons[key]!!
                    (b.parent as? ViewGroup)?.removeView(b)
                    val lp = LinearLayout.LayoutParams(b.layoutParams.width, b.layoutParams.height)
                    if (lay.align == ScanLayout.ALIGN_BETWEEN || lay.align == ScanLayout.ALIGN_AROUND) {
                        row.addView(b, lp)
                        if (i < chunk.size - 1) spacer(1f)
                    } else {
                        lp.setMargins(gap, 0, gap, 0)
                        row.addView(b, lp)
                    }
                }
                if (lay.align == ScanLayout.ALIGN_AROUND) spacer(0.5f)
            }
            root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowH + (4 * d).toInt()))
        }
        return root
    }

    fun makeAll(ctx: Context, lay: ScanLayout, style: StyleSet = StyleSet()): Map<String, ImageView> =
        ScanLayout.KEYS.associateWith { makeButton(ctx, lay, it, style) }

    fun forgetBitmaps() = bitmapCache.clear()
}
