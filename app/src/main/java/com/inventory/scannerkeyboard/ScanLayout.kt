package com.inventory.scannerkeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.min

/** Cấu hình riêng của một nút trên bàn phím. */
data class BtnCfg(
    val image: String? = null,   // tên file PNG trong thư mục btn_images (null = biểu tượng mặc định)
    val imgW: Int = 0,           // kích thước ảnh (px), 0 = tự vừa khít nút
    val imgH: Int = 0,
    val imgH_align: Int = 1,     // căn ngang trong nút: 0 trái, 1 giữa, 2 phải
    val imgV_align: Int = 1,     // căn dọc trong nút: 0 trên, 1 giữa, 2 dưới
    val scale: Int = 100         // thu phóng riêng của nút (%)
)

/**
 * Bố cục các nút chức năng, chia làm ba khu vực:
 *  - HÀNG TRÊN: hiện ở mọi chế độ (Quét / Gõ phím / Clipboard);
 *  - HÀNG DƯỚI: nằm dưới khung camera ở bảng Scan;
 *  - NHÓM CÔNG CỤ: các nút mở ra khi bấm nút Công cụ.
 * Mỗi nút nằm đúng một khu vực, di chuyển được giữa các khu vực. Lưu dạng JSON trong Prefs,
 * dùng chung cho bàn phím thật và khu vực xem trước trong màn hình chỉnh sửa.
 */
data class ScanLayout(
    val top: List<String> = DEFAULT_TOP,
    val bottom: List<String> = DEFAULT_BOTTOM,
    val tools: List<String> = DEFAULT_TOOLS,
    val perRowTop: Int = 7,       // số nút mỗi hàng ở khu vực trên (1..10)
    val perRowBottom: Int = 6,    // số nút mỗi hàng ở khu vực dưới (1..10)
    val widthDp: Int = 46,        // chiều rộng nút (dp)
    val heightDp: Int = 46,       // chiều cao nút (dp)
    val scale: Int = 100,         // thu phóng chung (%)
    val gapDp: Int = 6,           // khoảng cách ngang giữa các nút (dp), dùng khi căn Trái/Giữa/Phải
    val align: Int = ALIGN_CENTER,
    val grid: Boolean = true,     // true: căn theo cột (lưới), false: mỗi hàng tự căn riêng
    val buttons: Map<String, BtnCfg> = emptyMap(),
    val cornerDp: Int = -1        // bo góc các nút (dp, 0..30); -1 = theo giao diện đang dùng
) {
    companion object {
        const val ALIGN_LEFT = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_RIGHT = 2
        const val ALIGN_BETWEEN = 3   // dàn đều: hai đầu sát mép, khoảng giữa bằng nhau
        const val ALIGN_AROUND = 4    // cách đều: có khoảng ở cả hai đầu

        const val MAX_PER_ROW = 10

        const val AREA_TOP = 0
        const val AREA_BOTTOM = 1
        const val AREA_TOOLS = 2

        val DEFAULT_TOP = listOf("tools", "scan", "type", "clip", "settings", "switch", "hide")
        val DEFAULT_BOTTOM = listOf("cam", "flash", "keepOn", "dim", "del", "enter")
        val DEFAULT_TOOLS = listOf("autoEnter", "sound", "soundPick")

        /** Mọi nút có trên bàn phím; mỗi nút xuất hiện đúng một lần ở một trong ba khu vực. */
        val KEYS = DEFAULT_TOP + DEFAULT_BOTTOM + DEFAULT_TOOLS

        /** Nút có trạng thái bật / tắt (dùng nền kiểu công tắc, đổi màu khi đang bật). */
        val TOGGLES = setOf("tools", "scan", "type", "clip", "cam", "flash", "keepOn", "dim", "autoEnter", "sound")
        fun isToggle(key: String) = key in TOGGLES

        val LABELS = mapOf(
            "tools" to "Công cụ", "scan" to "Quét (Scan)", "type" to "Bàn phím", "clip" to "Clipboard",
            "settings" to "Cài đặt", "switch" to "Đổi bàn phím", "hide" to "Ẩn bàn phím",
            "cam" to "Bật / tắt camera", "flash" to "Đèn flash", "keepOn" to "Luôn sáng", "dim" to "Giảm độ sáng",
            "del" to "Xóa", "enter" to "Enter",
            "autoEnter" to "Tự động Enter", "sound" to "Âm thanh bàn phím", "soundPick" to "Chọn nhanh âm thanh"
        )

        val ICONS = mapOf(
            "tools" to R.drawable.ic_tune, "scan" to R.drawable.ic_scan, "type" to R.drawable.ic_keyboard,
            "clip" to R.drawable.ic_clipboard, "settings" to R.drawable.ic_settings, "switch" to R.drawable.ic_globe,
            "hide" to R.drawable.ic_chevron_down,
            "cam" to R.drawable.ic_camera, "flash" to R.drawable.ic_flash, "keepOn" to R.drawable.ic_sun,
            "dim" to R.drawable.ic_moon, "del" to R.drawable.ic_backspace, "enter" to R.drawable.ic_enter,
            "autoEnter" to R.drawable.ic_enter_auto, "sound" to R.drawable.ic_volume, "soundPick" to R.drawable.ic_music
        )

        /** Mô tả ngắn một dòng cho từng nút (dùng trong danh sách chọn nút và hướng dẫn). */
        val DESCS = mapOf(
            "tools" to "Mở bảng công cụ nổi phía trên bàn phím.",
            "scan" to "Chuyển sang chế độ quét mã bằng camera.",
            "type" to "Chuyển sang bàn phím gõ chữ, số, ký hiệu.",
            "clip" to "Mở lịch sử Clipboard: dán lại chữ và ảnh đã chép.",
            "settings" to "Mở bảng cài đặt của ứng dụng.",
            "switch" to "Đổi sang bàn phím khác (dùng chung mọi bảng).",
            "hide" to "Thu gọn, ẩn bàn phím.",
            "cam" to "Bật / tắt camera quét mã.",
            "flash" to "Bật / tắt đèn pin khi quét nơi thiếu sáng.",
            "keepOn" to "Giữ màn hình luôn sáng khi dùng bàn phím.",
            "dim" to "Hạ độ sáng màn hình để đỡ chói.",
            "del" to "Xóa ký tự; giữ để xóa nhanh dần.",
            "enter" to "Gửi phím Enter vào ô đang nhập.",
            "autoEnter" to "Tự gửi Enter sau mỗi mã quét được.",
            "sound" to "Bật / tắt âm thanh khi gõ phím.",
            "soundPick" to "Chọn nhanh kiểu âm thanh gõ phím (1 - 8)."
        )

        val AREA_NAMES = listOf("Hàng trên", "Hàng dưới (bảng Scan)", "Nhóm Công cụ")

        /** Hướng dẫn hiện ra khi giữ lâu một nút. */
        val HINTS = mapOf(
            "tools" to "CÔNG CỤ: mở bảng công cụ nổi phía trên bàn phím (tự Enter, âm thanh phím, chọn nhanh âm thanh...). Chạm lại để thu gọn.",
            "scan" to "QUÉT: dùng camera quét mã QR / mã vạch, mã tự nhập vào ô đang chọn.",
            "type" to "GÕ PHÍM: bàn phím đầy đủ để gõ chữ, số, ký hiệu. Camera tự tắt để tiết kiệm pin.",
            "clip" to "CLIPBOARD: xem lại văn bản và hình ảnh đã sao chép, chạm để dán, ghim mục quan trọng.",
            "settings" to "CÀI ĐẶT: mở bảng tùy chỉnh chính của ứng dụng.",
            "switch" to "ĐỔI BÀN PHÍM: chuyển sang bàn phím khác (dùng chung cho mọi bảng).",
            "hide" to "ẨN BÀN PHÍM: thu gọn bàn phím lại.",
            "cam" to "CAMERA: bật / tắt camera quét. Nên tắt khi không dùng để tiết kiệm pin.",
            "flash" to "FLASH: bật / tắt đèn pin khi quét nơi thiếu sáng. Chỉ dùng được khi camera đang bật.",
            "keepOn" to "LUÔN SÁNG: giữ màn hình không tự tắt khi bàn phím đang hiện.",
            "dim" to "GIẢM SÁNG: hạ độ sáng màn hình xuống mức đã đặt (mặc định 50%). Chỉnh mức trong ứng dụng.",
            "del" to "XÓA: xóa ký tự trước con trỏ. Giữ để xóa liên tục, càng giữ lâu càng nhanh.",
            "enter" to "ENTER: gửi phím Enter thủ công vào ô đang chọn.",
            "autoEnter" to "TỰ ENTER: tự gửi Enter sau mỗi mã quét được. Tắt nếu chỉ muốn chèn mã.",
            "sound" to "ÂM THANH PHÍM: bật / tắt âm thanh khi gõ phím.",
            "soundPick" to "CHỌN NHANH ÂM THANH: chọn kiểu âm thanh gõ phím và nghe thử ngay."
        )

        private fun readList(a: JSONArray?, seen: MutableSet<String>, allowTools: Boolean): List<String> {
            val out = ArrayList<String>()
            if (a == null) return out
            for (i in 0 until a.length()) {
                val k = a.optString(i, "")
                if (k !in KEYS || k in seen) continue
                if (!allowTools && k == "tools") continue   // nút Công cụ không nằm trong chính nhóm Công cụ
                seen.add(k)
                out.add(k)
            }
            return out
        }

        fun fromJson(text: String?): ScanLayout {
            if (text.isNullOrBlank()) return ScanLayout()
            return try {
                val o = JSONObject(text)
                val d = ScanLayout()
                val seen = HashSet<String>()
                val t = readList(o.optJSONArray("top"), seen, true).toMutableList()
                val b = readList(o.optJSONArray("bottom"), seen, true).toMutableList()
                val tl = readList(o.optJSONArray("tools"), seen, false).toMutableList()
                // Nút thiếu (bản cài đặt cũ hoặc dữ liệu hỏng) thì trả về đúng khu vực mặc định: không bao giờ mất nút.
                for (k in KEYS) if (k !in seen) when (k) {
                    in DEFAULT_TOP -> t.add(k)
                    in DEFAULT_BOTTOM -> b.add(k)
                    else -> tl.add(k)
                }
                val btns = HashMap<String, BtnCfg>()
                o.optJSONObject("buttons")?.let { bo ->
                    for (k in KEYS) bo.optJSONObject(k)?.let { x ->
                        btns[k] = BtnCfg(
                            image = if (x.has("image") && !x.isNull("image")) x.getString("image") else null,
                            imgW = x.optInt("imgW", 0).coerceIn(0, 400), imgH = x.optInt("imgH", 0).coerceIn(0, 400),
                            imgH_align = x.optInt("ha", 1).coerceIn(0, 2), imgV_align = x.optInt("va", 1).coerceIn(0, 2),
                            scale = x.optInt("scale", 100).coerceIn(50, 200)
                        )
                    }
                }
                ScanLayout(
                    t, b, tl,
                    o.optInt("pTop", d.perRowTop).coerceIn(1, MAX_PER_ROW),
                    o.optInt("pBot", d.perRowBottom).coerceIn(1, MAX_PER_ROW),
                    o.optInt("w", d.widthDp).coerceIn(32, 96),
                    o.optInt("h", d.heightDp).coerceIn(32, 96), o.optInt("scale", d.scale).coerceIn(60, 160),
                    o.optInt("gap", d.gapDp).coerceIn(0, 24), o.optInt("align", d.align).coerceIn(0, 4),
                    o.optBoolean("grid", d.grid), btns,
                    o.optInt("corner", d.cornerDp).coerceIn(-1, 30)
                )
            } catch (_: Throwable) { ScanLayout() }
        }
    }

    fun cfg(key: String): BtnCfg = buttons[key] ?: BtnCfg()

    fun withButton(key: String, c: BtnCfg): ScanLayout = copy(buttons = buttons + (key to c))

    /** Danh sách nút của một khu vực. */
    fun listFor(area: Int): List<String> = when (area) { AREA_TOP -> top; AREA_BOTTOM -> bottom; else -> tools }

    /** Nút đang nằm ở khu vực nào. */
    fun areaOf(key: String): Int = when (key) {
        in top -> AREA_TOP
        in bottom -> AREA_BOTTOM
        else -> AREA_TOOLS
    }

    fun withList(area: Int, l: List<String>): ScanLayout = when (area) {
        AREA_TOP -> copy(top = l)
        AREA_BOTTOM -> copy(bottom = l)
        else -> copy(tools = l)
    }

    /** Chuyển một nút sang khu vực khác (thêm vào cuối). Nút Công cụ không được nằm trong nhóm Công cụ. */
    fun moved(key: String, to: Int): ScanLayout {
        if (to == AREA_TOOLS && key == "tools") return this
        if (areaOf(key) == to) return this
        val cleaned = copy(top = top - key, bottom = bottom - key, tools = tools - key)
        return cleaned.withList(to, cleaned.listFor(to) + key)
    }

    fun toJson(): String {
        val b = JSONObject()
        for ((k, c) in buttons) b.put(
            k, JSONObject().put("image", c.image ?: JSONObject.NULL).put("imgW", c.imgW).put("imgH", c.imgH)
                .put("ha", c.imgH_align).put("va", c.imgV_align).put("scale", c.scale)
        )
        return JSONObject().put("ver", 2)
            .put("top", JSONArray(top)).put("bottom", JSONArray(bottom)).put("tools", JSONArray(tools))
            .put("pTop", perRowTop).put("pBot", perRowBottom).put("w", widthDp).put("h", heightDp)
            .put("scale", scale).put("gap", gapDp).put("align", align).put("grid", grid).put("corner", cornerDp).put("buttons", b).toString()
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

    /**
     * Bề rộng tối đa của một nút để đủ chỗ cho nhiều nút trên một hàng (đặc biệt khi chọn 8 - 10 nút mỗi hàng
     * trên màn hình nhỏ). Nút tự thu hẹp chiều ngang cho vừa, chiều cao giữ nguyên.
     */
    private fun maxWidthPx(ctx: Context, lay: ScanLayout, key: String): Int {
        val per = when (key) {
            in lay.top -> lay.perRowTop
            in lay.bottom -> lay.perRowBottom
            else -> return Int.MAX_VALUE   // nhóm Công cụ cuộn ngang, không cần thu hẹp
        }
        val d = ctx.resources.displayMetrics.density
        val avail = ctx.resources.displayMetrics.widthPixels - (8 * d).toInt()
        val gap = if (lay.grid) 0 else (lay.gapDp * d).toInt()
        return (avail / per - (2 * d).toInt() - gap).coerceAtLeast((24 * d).toInt())
    }

    /** Kích thước thật của nút (px) sau khi nhân thu phóng chung và thu phóng riêng. */
    fun sizePx(ctx: Context, lay: ScanLayout, key: String): Pair<Int, Int> {
        val d = ctx.resources.displayMetrics.density
        val k = lay.scale / 100f * lay.cfg(key).scale / 100f
        val w = min((lay.widthDp * d * k).toInt().coerceAtLeast(1), maxWidthPx(ctx, lay, key))
        return Pair(w, (lay.heightDp * d * k).toInt().coerceAtLeast(1))
    }

    fun hasCustomImage(ctx: Context, lay: ScanLayout, key: String): Boolean {
        val n = lay.cfg(key).image ?: return false
        return loadBitmap(ctx, n) != null
    }

    /**
     * Tạo ImageView cho một nút. Có ảnh PNG riêng thì dùng ảnh đó (giữ tỉ lệ, không bị méo),
     * không thì dùng biểu tượng mặc định.
     */
    fun makeButton(ctx: Context, lay: ScanLayout, key: String): ImageView {
        val d = ctx.resources.displayMetrics.density
        val (w, h) = sizePx(ctx, lay, key)
        val c = lay.cfg(key)
        val iv = ImageView(ctx)
        iv.isClickable = true
        iv.setBackgroundResource(if (ScanLayout.isToggle(key)) R.drawable.bg_toggle else R.drawable.bg_key)
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
            iv.imageTintList = ContextCompat.getColorStateList(ctx, R.color.icon_tint)
            // biểu tượng mặc định ~24dp ở kích thước chuẩn 46dp, lớn lên theo nút
            val icon = (min(w, h) * 0.52f).toInt().coerceAtLeast((12 * d).toInt())
            iv.setPadding((w - icon) / 2, (h - icon) / 2, (w - icon) / 2, (h - icon) / 2)
        }
        iv.layoutParams = LinearLayout.LayoutParams(w, h)
        return iv
    }

    /** Dựng các hàng nút (dọc, mỗi hàng ngang) cho danh sách [keys], mỗi hàng tối đa [per] nút. */
    fun buildRows(ctx: Context, lay: ScanLayout, buttons: Map<String, ImageView>, keys: List<String>, per: Int): LinearLayout {
        val d = ctx.resources.displayMetrics.density
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val usable = keys.filter { buttons.containsKey(it) }
        val n = per.coerceIn(1, ScanLayout.MAX_PER_ROW)
        val gap = (lay.gapDp * d / 2).toInt()
        for (chunk in usable.chunked(n)) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((4 * d).toInt(), (2 * d).toInt(), (4 * d).toInt(), (2 * d).toInt())
            }
            val rowH = chunk.maxOf { buttons[it]!!.layoutParams.height }
            if (lay.grid) {
                // Căn theo cột: mỗi ô rộng bằng nhau, nút nằm trong ô theo kiểu căn lề đã chọn
                val g = when (lay.align) { ScanLayout.ALIGN_LEFT -> Gravity.START; ScanLayout.ALIGN_RIGHT -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL }
                for (i in 0 until n) {
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

    /** Dựng nhóm Công cụ: một dải nút nằm ngang (cuộn ngang được nếu nhiều nút). */
    fun buildStrip(ctx: Context, buttons: Map<String, ImageView>, keys: List<String>): LinearLayout {
        val d = ctx.resources.displayMetrics.density
        val strip = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        for (k in keys) {
            val b = buttons[k] ?: continue
            (b.parent as? ViewGroup)?.removeView(b)
            strip.addView(b, LinearLayout.LayoutParams(b.layoutParams.width, b.layoutParams.height).apply {
                setMargins((3 * d).toInt(), 0, (3 * d).toInt(), 0)
            })
        }
        return strip
    }

    /** Kiểu nút Scan đang dùng: theo giao diện đang chọn, riêng bo góc lấy theo bố cục nếu người dùng đã đặt. */
    fun styleFor(ctx: Context, lay: ScanLayout): PanelStyle {
        val base = ThemeStore.load(ctx).current()?.scan ?: PanelStyle()
        return if (lay.cornerDp >= 0) base.copy(cornerDp = lay.cornerDp) else base
    }

    /**
     * Một nút cỡ chuẩn [sizeDp] đã áp đúng giao diện thật (nền, bo góc, màu biểu tượng, ảnh riêng nếu có).
     * Dùng cho danh sách chọn nút, khung chỉnh nút và hình minh họa hướng dẫn: nhìn giống hệt nút trên bàn phím.
     */
    fun tile(ctx: Context, lay: ScanLayout, key: String, sizeDp: Int, st: PanelStyle): ImageView {
        val t = lay.copy(
            widthDp = sizeDp, heightDp = sizeDp, scale = 100, perRowTop = 1, perRowBottom = 1,
            buttons = lay.buttons.mapValues { (_, c) -> c.copy(scale = 100, imgW = 0, imgH = 0) }
        )
        val iv = makeButton(ctx, t, key)
        ThemeRender.styleButton(ctx, iv, st, if (ScanLayout.isToggle(key)) ThemeRender.Kind.TOGGLE else ThemeRender.Kind.NORMAL, hasCustomImage(ctx, t, key))
        return iv
    }

    fun makeAll(ctx: Context, lay: ScanLayout): Map<String, ImageView> =
        ScanLayout.KEYS.associateWith { makeButton(ctx, lay, it) }

    fun forgetBitmaps() = bitmapCache.clear()
}
