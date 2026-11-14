package com.inventory.scannerkeyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Bộ thông số giao diện của MỘT bảng (Scan hoặc Bàn phím).
 * Giá trị mặc định tái tạo đúng giao diện gốc của ứng dụng, nên chưa tùy chỉnh gì thì không có gì thay đổi.
 */
data class PanelStyle(
    // ---- Nhóm 1: hình dạng và thiết kế phím ----
    val cornerDp: Int = 10,                 // bo góc 0..30
    val gapDp: Int = 3,                     // khoảng cách giữa các phím 0..10 (chỉ bảng Bàn phím)
    val borderDp: Int = 0,                  // độ dày viền 0..4
    val borderColor: Int = 0x66FFFFFF.toInt(),
    val keyColor: Int = 0xFF363F55.toInt(),     // phím thường
    val specialColor: Int = 0xFF414B65.toInt(), // phím chức năng
    val accentColor: Int = 0xFFDC7F62.toInt(),  // phím nhấn / đang chọn
    val keyAlpha: Int = 100,                // độ đặc của phím 20..100 (%)
    val depthDp: Int = 0,                   // độ dày cạnh nổi 3D 0..8
    val shadowDp: Int = 0,                  // bóng đổ (độ nổi) 0..10
    val shadowColor: Int = 0xFF000000.toInt(),
    val glossPct: Int = 0,                  // độ bóng phản chiếu 0..60

    // ---- Nhóm 2: hình nền (nằm phía sau phím) ----
    val bgType: Int = 0,                    // 0 mặc định, 1 đơn sắc, 2 gradient, 3 ảnh
    val bgColor1: Int = 0xFF232A3B.toInt(),
    val bgColor2: Int = 0xFF414B65.toInt(),
    val bgAngle: Int = 90,                  // hướng gradient (độ) 0..360
    val bgImage: String? = null,            // tên file ảnh trong thư mục theme_images
    val bgBrightness: Int = 100,            // 30..170
    val bgContrast: Int = 100,              // 50..170
    val bgBlur: Int = 0,                    // làm mờ ảnh 0..30
    val bgMode: Int = 0,                    // 0 phủ kín (cắt), 1 vừa khít, 2 giữ kích thước gốc, 3 kéo giãn
    val bgPosX: Int = 50,                   // vị trí ảnh 0..100 (%)
    val bgPosY: Int = 50,

    // ---- Nhóm 3: font chữ ----
    val fontFamily: Int = 0,                // xem ThemeRender.FAMILY_NAMES
    val fontWeight: Int = 500,              // độ đậm 300..800
    val fontScale: Int = 100,               // cỡ chữ 70..140 (%)
    val textColor: Int = 0xFFE4E8F1.toInt(),
    val iconColor: Int = 0xFFCBD2E4.toInt(),
    val textShadow: Int = 0,                // bóng chữ 0..8
    val textShadowColor: Int = 0xB3000000.toInt(),
    val textRaise: Int = 0,                 // độ nổi của chữ (lệch bóng xuống) 0..4

    // ---- Nhóm 4: hiệu ứng khi chạm và gõ (bật / tắt độc lập, kết hợp tự do) ----
    val fxBounce: Boolean = true,           // nhún phím (mặc định giống bản gốc)
    val fxBounceScale: Int = 94,            // mức nhún 80..100 (%)
    val fxElastic: Boolean = false,         // đàn hồi khi nhả
    val fxRipple: Boolean = false,          // gợn sóng
    val fxFlash: Boolean = false,           // phát sáng phím khi chạm
    val fxSparks: Boolean = false,          // hạt sáng
    val fxSparkCount: Int = 10,             // số hạt 4..24
    val fxColor: Int = 0xFFFFE066.toInt(),  // màu hiệu ứng chạm
    val fxIntensity: Int = 70,              // cường độ 10..100
    val fxDuration: Int = 450,              // thời gian hiệu ứng 150..1000 ms

    // ---- Nhóm 6: ánh sáng và màu sắc (chạy liên tục, chỉ khi bàn phím đang hiện) ----
    val lightFx: Int = 0,                   // bit: 1 RGB, 2 Neon, 4 Rainbow, 8 Glow, 16 Gradient, 32 Lan tỏa
    val lightColor: Int = 0xFF4DA3FF.toInt(),
    val lightColor2: Int = 0xFFFF6BCB.toInt(),
    val lightIntensity: Int = 80,           // cường độ 10..100
    val lightSpeed: Int = 30,               // tốc độ đổi màu 1..100
    val lightSpread: Int = 4                // độ lan tỏa / mờ của quầng sáng 0..12
) {
    fun toJson(): JSONObject = JSONObject()
        .put("cornerDp", cornerDp).put("gapDp", gapDp).put("borderDp", borderDp).put("borderColor", borderColor)
        .put("keyColor", keyColor).put("specialColor", specialColor).put("accentColor", accentColor)
        .put("keyAlpha", keyAlpha).put("depthDp", depthDp).put("shadowDp", shadowDp).put("shadowColor", shadowColor)
        .put("glossPct", glossPct)
        .put("bgType", bgType).put("bgColor1", bgColor1).put("bgColor2", bgColor2).put("bgAngle", bgAngle)
        .put("bgImage", bgImage ?: JSONObject.NULL)
        .put("bgBrightness", bgBrightness).put("bgContrast", bgContrast).put("bgBlur", bgBlur)
        .put("bgMode", bgMode).put("bgPosX", bgPosX).put("bgPosY", bgPosY)
        .put("fontFamily", fontFamily).put("fontWeight", fontWeight).put("fontScale", fontScale)
        .put("textColor", textColor).put("iconColor", iconColor)
        .put("textShadow", textShadow).put("textShadowColor", textShadowColor).put("textRaise", textRaise)
        .put("fxBounce", fxBounce).put("fxBounceScale", fxBounceScale).put("fxElastic", fxElastic)
        .put("fxRipple", fxRipple).put("fxFlash", fxFlash).put("fxSparks", fxSparks).put("fxSparkCount", fxSparkCount)
        .put("fxColor", fxColor).put("fxIntensity", fxIntensity).put("fxDuration", fxDuration)
        .put("lightFx", lightFx).put("lightColor", lightColor).put("lightColor2", lightColor2)
        .put("lightIntensity", lightIntensity).put("lightSpeed", lightSpeed).put("lightSpread", lightSpread)

    companion object {
        fun fromJson(o: JSONObject?): PanelStyle {
            val d = PanelStyle()
            if (o == null) return d
            return PanelStyle(
                cornerDp = o.optInt("cornerDp", d.cornerDp).coerceIn(0, 30),
                gapDp = o.optInt("gapDp", d.gapDp).coerceIn(0, 10),
                borderDp = o.optInt("borderDp", d.borderDp).coerceIn(0, 4),
                borderColor = o.optInt("borderColor", d.borderColor),
                keyColor = o.optInt("keyColor", d.keyColor),
                specialColor = o.optInt("specialColor", d.specialColor),
                accentColor = o.optInt("accentColor", d.accentColor),
                keyAlpha = o.optInt("keyAlpha", d.keyAlpha).coerceIn(20, 100),
                depthDp = o.optInt("depthDp", d.depthDp).coerceIn(0, 8),
                shadowDp = o.optInt("shadowDp", d.shadowDp).coerceIn(0, 10),
                shadowColor = o.optInt("shadowColor", d.shadowColor),
                glossPct = o.optInt("glossPct", d.glossPct).coerceIn(0, 60),
                bgType = o.optInt("bgType", d.bgType).coerceIn(0, 3),
                bgColor1 = o.optInt("bgColor1", d.bgColor1),
                bgColor2 = o.optInt("bgColor2", d.bgColor2),
                bgAngle = o.optInt("bgAngle", d.bgAngle).coerceIn(0, 360),
                bgImage = if (o.has("bgImage") && !o.isNull("bgImage")) o.getString("bgImage") else null,
                bgBrightness = o.optInt("bgBrightness", d.bgBrightness).coerceIn(30, 170),
                bgContrast = o.optInt("bgContrast", d.bgContrast).coerceIn(50, 170),
                bgBlur = o.optInt("bgBlur", d.bgBlur).coerceIn(0, 30),
                bgMode = o.optInt("bgMode", d.bgMode).coerceIn(0, 3),
                bgPosX = o.optInt("bgPosX", d.bgPosX).coerceIn(0, 100),
                bgPosY = o.optInt("bgPosY", d.bgPosY).coerceIn(0, 100),
                fontFamily = o.optInt("fontFamily", d.fontFamily).coerceIn(0, ThemeRender.FAMILY_NAMES.size - 1),
                fontWeight = o.optInt("fontWeight", d.fontWeight).coerceIn(300, 800),
                fontScale = o.optInt("fontScale", d.fontScale).coerceIn(70, 140),
                textColor = o.optInt("textColor", d.textColor),
                iconColor = o.optInt("iconColor", d.iconColor),
                textShadow = o.optInt("textShadow", d.textShadow).coerceIn(0, 8),
                textShadowColor = o.optInt("textShadowColor", d.textShadowColor),
                textRaise = o.optInt("textRaise", d.textRaise).coerceIn(0, 4),
                fxBounce = o.optBoolean("fxBounce", d.fxBounce),
                fxBounceScale = o.optInt("fxBounceScale", d.fxBounceScale).coerceIn(80, 100),
                fxElastic = o.optBoolean("fxElastic", d.fxElastic),
                fxRipple = o.optBoolean("fxRipple", d.fxRipple),
                fxFlash = o.optBoolean("fxFlash", d.fxFlash),
                fxSparks = o.optBoolean("fxSparks", d.fxSparks),
                fxSparkCount = o.optInt("fxSparkCount", d.fxSparkCount).coerceIn(4, 24),
                fxColor = o.optInt("fxColor", d.fxColor),
                fxIntensity = o.optInt("fxIntensity", d.fxIntensity).coerceIn(10, 100),
                fxDuration = o.optInt("fxDuration", d.fxDuration).coerceIn(150, 1000),
                lightFx = o.optInt("lightFx", d.lightFx).coerceIn(0, 63),
                lightColor = o.optInt("lightColor", d.lightColor),
                lightColor2 = o.optInt("lightColor2", d.lightColor2),
                lightIntensity = o.optInt("lightIntensity", d.lightIntensity).coerceIn(10, 100),
                lightSpeed = o.optInt("lightSpeed", d.lightSpeed).coerceIn(1, 100),
                lightSpread = o.optInt("lightSpread", d.lightSpread).coerceIn(0, 12)
            )
        }
    }
}

/** Một giao diện đã lưu: có tên, và bộ thông số riêng cho bảng Scan và bảng Bàn phím. */
data class KbTheme(
    val id: String,
    val name: String,
    val scan: PanelStyle = PanelStyle(),
    val kb: PanelStyle = PanelStyle()
)

/** Toàn bộ danh sách giao diện và giao diện đang dùng. */
data class ThemeState(
    val active: String = "",
    val themes: List<KbTheme> = emptyList()
) {
    /** Giao diện đang dùng (nếu id không còn thì lấy cái đầu tiên; danh sách rỗng thì null). */
    fun current(): KbTheme? = themes.firstOrNull { it.id == active } ?: themes.firstOrNull()
}

/** Lưu / đọc giao diện. Dữ liệu nằm riêng trong "kb_themes", không đụng tới các cài đặt khác của ứng dụng. */
object ThemeStore {
    private const val SP = "kb_themes"
    private const val KEY = "json"

    fun raw(ctx: Context): String =
        ctx.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun load(ctx: Context): ThemeState = parse(raw(ctx))

    fun save(ctx: Context, st: ThemeState) {
        ctx.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE).edit().putString(KEY, toJson(st)).apply()
    }

    fun parse(text: String?): ThemeState {
        if (text.isNullOrBlank()) return ThemeState()
        return try {
            val o = JSONObject(text)
            val arr = o.optJSONArray("themes") ?: JSONArray()
            val list = ArrayList<KbTheme>()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val id = t.optString("id", "")
                if (id.isEmpty()) continue
                list.add(
                    KbTheme(
                        id, t.optString("name", "Giao diện"),
                        PanelStyle.fromJson(t.optJSONObject("scan")), PanelStyle.fromJson(t.optJSONObject("kb"))
                    )
                )
            }
            ThemeState(o.optString("active", ""), list)
        } catch (_: Throwable) { ThemeState() }
    }

    fun toJson(st: ThemeState): String {
        val arr = JSONArray()
        for (t in st.themes) arr.put(
            JSONObject().put("id", t.id).put("name", t.name).put("scan", t.scan.toJson()).put("kb", t.kb.toJson())
        )
        return JSONObject().put("active", st.active).put("themes", arr).toString()
    }

    fun newId(): String = java.lang.Long.toString(System.currentTimeMillis(), 36) + (0..999).random()

    // ---- ảnh nền ----
    fun imageDir(ctx: Context): File = File(ctx.applicationContext.filesDir, "theme_images").apply { mkdirs() }

    /** Xóa các file ảnh không còn giao diện nào dùng. */
    fun cleanupImages(ctx: Context, st: ThemeState) {
        try {
            val used = HashSet<String>()
            for (t in st.themes) { t.scan.bgImage?.let { used.add(it) }; t.kb.bgImage?.let { used.add(it) } }
            imageDir(ctx).listFiles()?.forEach { if (it.name !in used) it.delete() }
        } catch (_: Throwable) { }
    }
}
