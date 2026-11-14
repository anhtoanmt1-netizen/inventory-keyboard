package com.inventory.scannerkeyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Bộ thông số giao diện cho MỘT bảng (Scan hoặc Bàn phím). Lưu dưới dạng bảng khóa -> giá trị;
 * khóa nào chưa được đặt sẽ lấy giá trị mặc định trong [DEF], nên thêm tính năng mới không làm hỏng
 * các giao diện đã lưu.
 */
class StyleSet(val m: Map<String, Any> = emptyMap()) {

    fun int(k: String): Int = (m[k] as? Int) ?: (DEF[k] as? Int) ?: 0
    fun bool(k: String): Boolean = (m[k] as? Boolean) ?: (DEF[k] as? Boolean) ?: false
    fun str(k: String): String? = m[k] as? String

    /** Trả về bản mới với một thông số đã đổi (null = xóa, quay về mặc định). */
    fun with(k: String, v: Any?): StyleSet = StyleSet(if (v == null) m - k else m + (k to v))

    override fun equals(other: Any?) = other is StyleSet && other.m == m
    override fun hashCode() = m.hashCode()

    fun toJson(): JSONObject {
        val o = JSONObject()
        for ((k, v) in m) o.put(k, v)
        return o
    }

    /** Các file ảnh nền đang được dùng. */
    fun usedFiles(): Set<String> = listOfNotNull(str("bgImage")).toSet()

    companion object {
        private fun c(v: Long) = v.toInt()

        val DEF: Map<String, Any> = mapOf(
            // ---- Nhóm 1: hình dạng và thiết kế phím
            "corner" to 10, "keyW" to 100, "keyH" to 100, "gap" to 6,
            "depth" to 0, "shadowOn" to false, "shadow" to 40,
            "borderOn" to false, "borderW" to 1, "borderColor" to c(0xFF445069),
            "d3" to false,
            "keyColor" to c(0xFF363F55), "specialColor" to c(0xFF414B65), "accentColor" to c(0xFFDC7F62),
            // ---- Nhóm 2: hình nền
            "bgType" to 0, "bgColor" to c(0xFF232A3B), "bgColor2" to c(0xFF3B2A5A), "bgAngle" to 90,
            "bgBright" to 0, "bgContrast" to 100, "bgBlur" to 0, "bgFit" to 0, "bgFocusX" to 50, "bgFocusY" to 50,
            // ---- Nhóm 3: font chữ và ký tự
            "font" to 0, "textSp" to 19, "weight" to 500, "textColor" to c(0xFFE4E8F1),
            "textFx" to 0, "fxStrength" to 50, "caseMode" to 0, "subPct" to 100,
            // ---- Nhóm 7: bố cục và vị trí
            "widthPct" to 100, "align" to 1, "oneHand" to 0, "bottomDp" to 0
        ) + StyleExtra.DEF

        fun fromJson(o: JSONObject?): StyleSet {
            if (o == null) return StyleSet()
            val m = HashMap<String, Any>()
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                val d = DEF[k]
                val v = o.opt(k)
                when {
                    d is Int && v is Int -> m[k] = v
                    d is Int && v is Long -> m[k] = v.toInt()
                    d is Boolean && v is Boolean -> m[k] = v
                    d == null && k == "bgImage" && v is String -> m[k] = v
                }
            }
            return StyleSet(m)
        }
    }
}

/** Thông số mở rộng (đợt 4B bổ sung hiệu ứng chạm, chuyển động nền, ánh sáng). */
object StyleExtra { val DEF: Map<String, Any> = emptyMap() }

/** Một giao diện: có tên, và bộ thông số riêng cho bảng Scan và bảng Bàn phím. */
data class KbTheme(val id: String, val name: String, val scan: StyleSet, val kb: StyleSet) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name)
        .put("scan", scan.toJson()).put("kb", kb.toJson())

    fun usedFiles(): Set<String> = scan.usedFiles() + kb.usedFiles()

    companion object {
        const val DEFAULT_ID = "default"
        fun default() = KbTheme(DEFAULT_ID, "Mặc định", StyleSet(), StyleSet())
        fun fromJson(o: JSONObject) = KbTheme(
            o.getString("id"), o.optString("name", "Giao diện"),
            StyleSet.fromJson(o.optJSONObject("scan")), StyleSet.fromJson(o.optJSONObject("kb"))
        )
    }
}

object ThemeImages {
    fun dir(ctx: Context): File = File(ctx.applicationContext.filesDir, "theme_images").apply { mkdirs() }
}

/**
 * Kho các giao diện đã tạo, lưu bền vững. Luôn có giao diện "Mặc định" (không xóa được).
 */
class ThemeStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("themes", Context.MODE_PRIVATE)
    private val ctx = context.applicationContext

    /** Chữ ký thay đổi khi giao diện đang dùng hoặc nội dung đổi: để bàn phím biết cần dựng lại. */
    fun signature(): Int = (sp.getString("json", "") ?: "").hashCode() * 31 + (sp.getString("active", "") ?: "").hashCode()

    fun all(): List<KbTheme> {
        val list = ArrayList<KbTheme>()
        try {
            val arr = JSONArray(sp.getString("json", "[]"))
            for (i in 0 until arr.length()) try { list.add(KbTheme.fromJson(arr.getJSONObject(i))) } catch (_: Throwable) { }
        } catch (_: Throwable) { }
        if (list.none { it.id == KbTheme.DEFAULT_ID }) list.add(0, KbTheme.default())
        return list
    }

    fun activeId(): String = sp.getString("active", KbTheme.DEFAULT_ID) ?: KbTheme.DEFAULT_ID

    fun active(): KbTheme {
        val l = all()
        return l.firstOrNull { it.id == activeId() } ?: l.first()
    }

    fun get(id: String): KbTheme? = all().firstOrNull { it.id == id }

    fun setActive(id: String) { sp.edit().putString("active", id).apply() }

    /** Thêm mới hoặc ghi đè theo id. */
    fun save(t: KbTheme) {
        val l = all().toMutableList()
        val i = l.indexOfFirst { it.id == t.id }
        if (i >= 0) l[i] = t else l.add(t)
        write(l)
    }

    fun delete(id: String) {
        if (id == KbTheme.DEFAULT_ID) return
        val l = all().filter { it.id != id }
        write(l)
        if (activeId() == id) setActive(KbTheme.DEFAULT_ID)
        cleanupFiles()
    }

    fun newId(): String = "t" + System.currentTimeMillis()

    private fun write(l: List<KbTheme>) {
        val arr = JSONArray()
        l.forEach { arr.put(it.toJson()) }
        sp.edit().putString("json", arr.toString()).apply()
    }

    /** Xóa các file ảnh nền không còn giao diện nào dùng. */
    fun cleanupFiles(extraKeep: Set<String> = emptySet()) {
        try {
            val keep = all().flatMap { it.usedFiles() }.toSet() + extraKeep
            ThemeImages.dir(ctx).listFiles()?.forEach { if (it.name !in keep) it.delete() }
        } catch (_: Throwable) { }
    }
}
