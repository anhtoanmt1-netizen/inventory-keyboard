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
 * Bộ thông số bố cục và vị trí RIÊNG của một khu vực nút (hàng trên, hàng dưới hoặc nhóm Công cụ).
 * Ba khu vực có ba bộ thông số độc lập; đổi khu vực nào chỉ ảnh hưởng khu vực đó.
 */
data class AreaStyle(
    val perRow: Int = 7,                       // số nút mỗi hàng (1..10)
    val widthDp: Int = 46,                     // chiều rộng nút (dp)
    val heightDp: Int = 46,                    // chiều cao nút (dp)
    val scale: Int = 100,                      // thu phóng chung của khu vực (%)
    val gapDp: Int = 6,                        // khoảng cách ngang giữa các nút (dp)
    val rowGapDp: Int = 4,                     // khoảng cách dọc giữa các hàng nút (dp)
    val align: Int = ScanLayout.ALIGN_CENTER,  // căn lề (nhóm Công cụ: 0 sát trái, 1 theo nút Công cụ, 2 sát phải, 3 giữa màn hình)
    val grid: Boolean = true,                  // căn theo cột (lưới); nhóm Công cụ không dùng
    val cornerDp: Int = -1,                    // bo góc nút (dp, 0..30); -1 = theo giao diện đang dùng
    val padTopDp: Int = 0,                     // khoảng cách phía trên khu vực (dp)
    val padBottomDp: Int = 0,                  // khoảng cách phía dưới khu vực (dp); nhóm Công cụ: cách mép trên bàn phím
    val offsetXDp: Int = 0                     // dịch ngang khu vực sang trái (âm) / phải (dương) (dp)
) {
    fun toJson(): JSONObject = JSONObject()
        .put("perRow", perRow).put("w", widthDp).put("h", heightDp).put("scale", scale).put("gap", gapDp)
        .put("rowGap", rowGapDp).put("align", align).put("grid", grid).put("corner", cornerDp)
        .put("padTop", padTopDp).put("padBottom", padBottomDp).put("offsetX", offsetXDp)

    companion object {
        fun fromJson(o: JSONObject?, d: AreaStyle, tools: Boolean): AreaStyle {
            if (o == null) return d
            return AreaStyle(
                perRow = o.optInt("perRow", d.perRow).coerceIn(1, ScanLayout.MAX_PER_ROW),
                widthDp = o.optInt("w", d.widthDp).coerceIn(32, 96),
                heightDp = o.optInt("h", d.heightDp).coerceIn(32, 96),
                scale = o.optInt("scale", d.scale).coerceIn(60, 160),
                gapDp = o.optInt("gap", d.gapDp).coerceIn(0, 24),
                rowGapDp = o.optInt("rowGap", d.rowGapDp).coerceIn(0, 24),
                align = o.optInt("align", d.align).coerceIn(0, if (tools) 3 else 4),
                grid = o.optBoolean("grid", d.grid),
                cornerDp = o.optInt("corner", d.cornerDp).coerceIn(-1, 30),
                padTopDp = o.optInt("padTop", d.padTopDp).coerceIn(0, 48),
                padBottomDp = o.optInt("padBottom", d.padBottomDp).coerceIn(0, 48),
                offsetXDp = o.optInt("offsetX", d.offsetXDp).coerceIn(-80, 80)
            )
        }
    }
}

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
    val topStyle: AreaStyle = DEFAULT_TOP_STYLE,        // bộ thông số riêng của hàng trên
    val bottomStyle: AreaStyle = DEFAULT_BOTTOM_STYLE,  // bộ thông số riêng của hàng dưới
    val toolsStyle: AreaStyle = DEFAULT_TOOLS_STYLE,    // bộ thông số riêng của nhóm Công cụ
    val buttons: Map<String, BtnCfg> = emptyMap()
) {
    companion object {
        const val ALIGN_LEFT = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_RIGHT = 2
        const val ALIGN_BETWEEN = 3   // dàn đều: hai đầu sát mép, khoảng giữa bằng nhau
        const val ALIGN_AROUND = 4    // cách đều: có khoảng ở cả hai đầu

        const val MAX_PER_ROW = 10

        // Mặc định của từng khu vực: giữ đúng giao diện ban đầu
        val DEFAULT_TOP_STYLE = AreaStyle(perRow = 7)
        val DEFAULT_BOTTOM_STYLE = AreaStyle(perRow = 6, padTopDp = 6)
        val DEFAULT_TOOLS_STYLE = AreaStyle(perRow = 10, grid = false, padBottomDp = 6)

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
                val styles = o.optJSONObject("styles")
                val topS: AreaStyle
                val botS: AreaStyle
                val toolS: AreaStyle
                if (styles != null) {
                    topS = AreaStyle.fromJson(styles.optJSONObject("top"), DEFAULT_TOP_STYLE, false)
                    botS = AreaStyle.fromJson(styles.optJSONObject("bottom"), DEFAULT_BOTTOM_STYLE, false)
                    toolS = AreaStyle.fromJson(styles.optJSONObject("tools"), DEFAULT_TOOLS_STYLE, true)
                } else {
                    // Bố cục lưu bởi bản cũ (thông số chung): chuyển thành bộ thông số riêng cho từng khu vực, giữ nguyên giao diện
                    val w = o.optInt("w", 46).coerceIn(32, 96)
                    val h = o.optInt("h", 46).coerceIn(32, 96)
                    val sc = o.optInt("scale", 100).coerceIn(60, 160)
                    val gap = o.optInt("gap", 6).coerceIn(0, 24)
                    val al = o.optInt("align", ALIGN_CENTER).coerceIn(0, 4)
                    val grid = o.optBoolean("grid", true)
                    val corner = o.optInt("corner", -1).coerceIn(-1, 30)
                    val rg = o.optInt("rGap", 4).coerceIn(0, 24)
                    topS = AreaStyle(
                        perRow = o.optInt("pTop", 7).coerceIn(1, MAX_PER_ROW), widthDp = w, heightDp = h, scale = sc, gapDp = gap,
                        rowGapDp = rg, align = al, grid = grid, cornerDp = corner, padBottomDp = o.optInt("gTop", 0).coerceIn(0, 48)
                    )
                    botS = AreaStyle(
                        perRow = o.optInt("pBot", 6).coerceIn(1, MAX_PER_ROW), widthDp = w, heightDp = h, scale = sc, gapDp = gap,
                        rowGapDp = rg, align = al, grid = grid, cornerDp = corner, padTopDp = o.optInt("gBot", 6).coerceIn(0, 48)
                    )
                    toolS = DEFAULT_TOOLS_STYLE.copy(widthDp = w, heightDp = h, scale = sc, gapDp = gap, rowGapDp = rg, cornerDp = corner)
                }
                ScanLayout(t, b, tl, topS, botS, toolS, btns)
            } catch (_: Throwable) { ScanLayout() }
        }
    }

    fun cfg(key: String): BtnCfg = buttons[key] ?: BtnCfg()

    fun withButton(key: String, c: BtnCfg): ScanLayout = copy(buttons = buttons + (key to c))

    /** Bộ thông số của một khu vực. */
    fun styleOf(area: Int): AreaStyle = when (area) { AREA_TOP -> topStyle; AREA_BOTTOM -> bottomStyle; else -> toolsStyle }

    fun withStyle(area: Int, st: AreaStyle): ScanLayout = when (area) {
        AREA_TOP -> copy(topStyle = st)
        AREA_BOTTOM -> copy(bottomStyle = st)
        else -> copy(toolsStyle = st)
    }

    /** Đặt cùng một mức bo góc cho cả ba khu vực (dùng cho hình minh họa). */
    fun withCorner(c: Int): ScanLayout = copy(
        topStyle = topStyle.copy(cornerDp = c), bottomStyle = bottomStyle.copy(cornerDp = c), toolsStyle = toolsStyle.copy(cornerDp = c)
    )

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
        val styles = JSONObject().put("top", topStyle.toJson()).put("bottom", bottomStyle.toJson()).put("tools", toolsStyle.toJson())
        return JSONObject().put("ver", 3)
            .put("top", JSONArray(top)).put("bottom", JSONArray(bottom)).put("tools", JSONArray(tools))
            .put("styles", styles).put("buttons", b).toString()
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
        val area = lay.areaOf(key)
        val st = lay.styleOf(area)
        val d = ctx.resources.displayMetrics.density
        val screen = ctx.resources.displayMetrics.widthPixels
        // Nhóm Công cụ là bảng nổi nên trừ lề màn hình và đệm của bảng
        val avail = if (area == ScanLayout.AREA_TOOLS) screen - (28 * d).toInt() else screen - (8 * d).toInt()
        val grid = area != ScanLayout.AREA_TOOLS && st.grid
        // Lưới chia đủ số cột; còn lại chỉ tính theo số nút thực có trên hàng
        val cols = if (grid) st.perRow else minOf(st.perRow, maxOf(1, lay.listFor(area).size))
        val gap = if (grid) 0 else (st.gapDp * d).toInt()
        return (avail / cols - (2 * d).toInt() - gap).coerceAtLeast((24 * d).toInt())
    }

    /** Kích thước thật của nút (px) sau khi nhân thu phóng của khu vực và thu phóng riêng của nút. */
    fun sizePx(ctx: Context, lay: ScanLayout, key: String): Pair<Int, Int> {
        val d = ctx.resources.displayMetrics.density
        val st = lay.styleOf(lay.areaOf(key))
        val k = st.scale / 100f * lay.cfg(key).scale / 100f
        val w = min((st.widthDp * d * k).toInt().coerceAtLeast(1), maxWidthPx(ctx, lay, key))
        return Pair(w, (st.heightDp * d * k).toInt().coerceAtLeast(1))
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

    /** Dựng các hàng nút (dọc, mỗi hàng ngang) của một khu vực theo bộ thông số RIÊNG của khu vực đó. */
    fun buildRows(ctx: Context, lay: ScanLayout, buttons: Map<String, ImageView>, keys: List<String>, area: Int): LinearLayout {
        val d = ctx.resources.displayMetrics.density
        val st = lay.styleOf(area)
        val tools = area == ScanLayout.AREA_TOOLS
        val grid = !tools && st.grid
        val align = if (tools) ScanLayout.ALIGN_CENTER else st.align
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val usable = keys.filter { buttons.containsKey(it) }
        val n = st.perRow.coerceIn(1, ScanLayout.MAX_PER_ROW)
        val gap = (st.gapDp * d / 2).toInt()
        val vpad = (st.rowGapDp * d / 2f).toInt()
        for (chunk in usable.chunked(n)) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((4 * d).toInt(), vpad, (4 * d).toInt(), vpad)
            }
            val rowH = chunk.maxOf { buttons[it]!!.layoutParams.height }
            if (grid) {
                // Căn theo cột: mỗi ô rộng bằng nhau, nút nằm trong ô theo kiểu căn lề đã chọn
                val g = when (align) { ScanLayout.ALIGN_LEFT -> Gravity.START; ScanLayout.ALIGN_RIGHT -> Gravity.END; else -> Gravity.CENTER_HORIZONTAL }
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
                row.gravity = Gravity.CENTER_VERTICAL or when (align) {
                    ScanLayout.ALIGN_LEFT -> Gravity.START
                    ScanLayout.ALIGN_RIGHT -> Gravity.END
                    else -> Gravity.CENTER_HORIZONTAL
                }
                fun spacer(weight: Float) = row.addView(Space(ctx), LinearLayout.LayoutParams(0, 1, weight))
                if (align == ScanLayout.ALIGN_AROUND) spacer(0.5f)
                chunk.forEachIndexed { i, key ->
                    val b = buttons[key]!!
                    (b.parent as? ViewGroup)?.removeView(b)
                    val lp = LinearLayout.LayoutParams(b.layoutParams.width, b.layoutParams.height)
                    if (align == ScanLayout.ALIGN_BETWEEN || align == ScanLayout.ALIGN_AROUND) {
                        row.addView(b, lp)
                        if (i < chunk.size - 1) spacer(1f)
                    } else {
                        lp.setMargins(gap, 0, gap, 0)
                        row.addView(b, lp)
                    }
                }
                if (align == ScanLayout.ALIGN_AROUND) spacer(0.5f)
            }
            root.addView(row, LinearLayout.LayoutParams(
                if (tools) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT, rowH + vpad * 2
            ))
        }
        if (!tools) root.translationX = st.offsetXDp * d     // dịch ngang cả khu vực
        return root
    }

    /** Kiểu nút Scan của giao diện đang chọn (chưa áp bo góc riêng của từng khu vực). */
    fun themeScanStyle(ctx: Context): PanelStyle = ThemeStore.load(ctx).current()?.scan ?: PanelStyle()

    /** Kiểu nút của một khu vực: theo giao diện, riêng bo góc lấy theo bộ thông số của khu vực nếu người dùng đã đặt. */
    fun btnStyle(base: PanelStyle, lay: ScanLayout, area: Int): PanelStyle {
        val c = lay.styleOf(area).cornerDp
        return if (c >= 0) base.copy(cornerDp = c) else base
    }

    /**
     * Một nút cỡ chuẩn [sizeDp] đã áp đúng giao diện thật (nền, bo góc của khu vực chứa nó, màu biểu tượng, ảnh riêng).
     * Dùng cho danh sách chọn nút, khung chỉnh nút và hình minh họa hướng dẫn. [base] là kiểu nút của giao diện.
     */
    fun tile(ctx: Context, lay: ScanLayout, key: String, sizeDp: Int, base: PanelStyle): ImageView {
        val area = lay.areaOf(key)
        val ts = AreaStyle(perRow = 1, widthDp = sizeDp, heightDp = sizeDp, scale = 100, cornerDp = lay.styleOf(area).cornerDp)
        val t = lay.copy(
            topStyle = ts, bottomStyle = ts, toolsStyle = ts,
            buttons = lay.buttons.mapValues { (_, c) -> c.copy(scale = 100, imgW = 0, imgH = 0) }
        )
        val iv = makeButton(ctx, t, key)
        ThemeRender.styleButton(ctx, iv, btnStyle(base, lay, area), if (ScanLayout.isToggle(key)) ThemeRender.Kind.TOGGLE else ThemeRender.Kind.NORMAL, hasCustomImage(ctx, t, key))
        // Ô nút chỉ để xem: không nhận chạm riêng, để chạm vào giữa nút cũng chọn được dòng / ô chứa nó
        iv.isClickable = false
        iv.isFocusable = false
        return iv
    }

    /** Kết quả dựng: các nút, hàng trên, hàng dưới và danh sách nút theo từng khu vực. */
    class Assembled(
        val buttons: Map<String, ImageView>,
        val custom: Set<String>,          // các nút đang dùng ảnh riêng (không đổi biểu tượng theo trạng thái)
        val top: View,
        val bottom: View,
        val topViews: List<View>,
        val bottomViews: List<View>,
        val toolViews: List<View>
    )

    /**
     * Dựng toàn bộ nút theo bố cục [lay] với kiểu [st]: tạo nút, áp giao diện, xếp hàng trên và hàng dưới.
     * Bàn phím thật và các bản xem trước đều gọi hàm này nên hình dạng, kích thước, khoảng cách giống nhau tuyệt đối.
     */
    fun assemble(ctx: Context, lay: ScanLayout, base: PanelStyle): Assembled {
        forgetBitmaps()
        val m = makeAll(ctx, lay)
        val custom = HashSet<String>()
        for ((k, b) in m) {
            val hasImg = hasCustomImage(ctx, lay, k)
            if (hasImg) custom.add(k)
            ThemeRender.styleButton(
                ctx, b, btnStyle(base, lay, lay.areaOf(k)),
                if (ScanLayout.isToggle(k)) ThemeRender.Kind.TOGGLE else ThemeRender.Kind.NORMAL,
                hasImg
            )
        }
        val top = buildRows(ctx, lay, m, lay.top, ScanLayout.AREA_TOP)
        val bottom = buildRows(ctx, lay, m, lay.bottom, ScanLayout.AREA_BOTTOM)
        return Assembled(
            m, custom, top, bottom,
            lay.top.mapNotNull { m[it] }, lay.bottom.mapNotNull { m[it] }, lay.tools.mapNotNull { m[it] }
        )
    }

    /** Khoảng cách phía trên / dưới của hàng trên và hàng dưới theo bộ thông số của từng khu vực. */
    fun applyAreaPads(ctx: Context, lay: ScanLayout, topArea: View, btnArea: View, gapBelowCamera: View) {
        val d = ctx.resources.displayMetrics.density
        topArea.setPadding(0, (lay.topStyle.padTopDp * d).toInt(), 0, (lay.topStyle.padBottomDp * d).toInt())
        btnArea.setPadding(0, 0, 0, (lay.bottomStyle.padBottomDp * d).toInt())
        gapBelowCamera.layoutParams = gapBelowCamera.layoutParams.apply { height = (lay.bottomStyle.padTopDp * d).toInt() }
    }

    /**
     * Vị trí ngang (px) của bảng Công cụ nổi. [btnCenterX] là tâm nút Công cụ, [containerW] là bề rộng vùng chứa,
     * [panelW] là bề rộng bảng. Bàn phím thật và bản xem trước dùng chung hàm này nên luôn trùng khớp.
     */
    fun toolsPanelX(st: AreaStyle, d: Float, btnCenterX: Int, panelW: Int, containerW: Int): Int {
        val m = (8 * d).toInt()
        val base = when (st.align) {
            0 -> m
            2 -> containerW - panelW - m
            3 -> (containerW - panelW) / 2
            else -> btnCenterX - panelW / 2
        }
        return (base + (st.offsetXDp * d).toInt()).coerceIn(m, (containerW - panelW - m).coerceAtLeast(m))
    }

    /** Khung của nhóm Công cụ (nền, đệm, cuộn ngang). Trả về khung ngoài và hàng nút bên trong. */
    fun newToolsBox(ctx: Context): Pair<FrameLayout, LinearLayout> {
        val d = ctx.resources.displayMetrics.density
        val box = FrameLayout(ctx).apply {
            setBackgroundResource(R.drawable.bg_flyout)
            setPadding((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt())
        }
        val sc = android.widget.HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        sc.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(sc, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return Pair(box, row)
    }

    /** Xếp các nút công cụ vào bảng Công cụ theo bộ thông số riêng của nhóm (số nút mỗi hàng, kích thước, khoảng cách...). */
    fun fillTools(ctx: Context, holder: LinearLayout, lay: ScanLayout, buttons: Map<String, ImageView>) {
        holder.addView(buildRows(ctx, lay, buttons, lay.tools, ScanLayout.AREA_TOOLS))
    }

    fun makeAll(ctx: Context, lay: ScanLayout): Map<String, ImageView> =
        ScanLayout.KEYS.associateWith { makeButton(ctx, lay, it) }

    /** Ảnh PNG riêng của nút (đã nạp, có bộ nhớ đệm). */
    fun bitmapOf(ctx: Context, name: String): Bitmap? = loadBitmap(ctx, name)

    fun forgetBitmaps() = bitmapCache.clear()
}
