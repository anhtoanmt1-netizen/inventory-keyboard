package com.inventory.scannerkeyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Một mục trong Clipboard: văn bản hoặc hình ảnh. */
data class ClipItem(
    val id: Long,
    val type: Int,            // ClipStore.TYPE_TEXT / TYPE_IMAGE
    val text: String,         // nội dung (văn bản)
    val file: String?,        // tên file ảnh trong thư mục clip_images (hình ảnh)
    val pinned: Boolean,
    val color: Int,           // chỉ số màu pastel (cho mục đã ghim)
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Kho lưu Clipboard (văn bản + hình ảnh), lưu bền vững trong bộ nhớ riêng của ứng dụng:
 * danh sách ở clip_store.json, ảnh ở thư mục clip_images. Mục chưa ghim được giới hạn số lượng,
 * mục cũ nhất sẽ bị dọn; mục đã ghim thì không bao giờ bị dọn tự động.
 */
class ClipStore(context: Context) {
    companion object {
        const val TYPE_TEXT = 0
        const val TYPE_IMAGE = 1
        const val MAX_TEXT = 100
        const val MAX_IMAGE = 30
        const val MAX_TEXT_LEN = 20_000
    }

    private val dir = File(context.filesDir, "clip_images").apply { mkdirs() }
    private val db = File(context.filesDir, "clip_store.json")
    private val list = ArrayList<ClipItem>()
    private val seq = AtomicLong(System.currentTimeMillis())

    init { load() }

    fun imageFile(item: ClipItem): File? = item.file?.let { File(dir, it) }

    /** Tạo đường dẫn file mới cho một ảnh sắp lưu (chưa thêm vào danh sách). */
    fun newImageFile(ext: String): File = File(dir, "${seq.incrementAndGet()}.$ext")

    @Synchronized fun all(): List<ClipItem> = ArrayList(list)

    @Synchronized fun get(id: Long): ClipItem? = list.firstOrNull { it.id == id }

    /** Thêm văn bản. Nếu đã có đúng nội dung này thì chỉ đưa lên đầu (giữ nguyên ghim và màu). */
    @Synchronized fun addText(raw: String): Boolean {
        val t = if (raw.length > MAX_TEXT_LEN) raw.substring(0, MAX_TEXT_LEN) else raw
        if (t.isBlank()) return false
        val now = System.currentTimeMillis()
        val i = list.indexOfFirst { it.type == TYPE_TEXT && it.text == t }
        if (i >= 0) list[i] = list[i].copy(updatedAt = now)
        else list.add(ClipItem(seq.incrementAndGet(), TYPE_TEXT, t, null, false, 2, now, now))
        trim(); save()
        return true
    }

    @Synchronized fun addImage(fileName: String) {
        val now = System.currentTimeMillis()
        list.add(ClipItem(seq.incrementAndGet(), TYPE_IMAGE, "", fileName, false, 2, now, now))
        trim(); save()
    }

    @Synchronized fun touch(id: Long) {
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) { list[i] = list[i].copy(updatedAt = System.currentTimeMillis()); save() }
    }

    @Synchronized fun setPinned(ids: Collection<Long>, pinned: Boolean) {
        val now = System.currentTimeMillis()
        for (i in list.indices) if (list[i].id in ids) list[i] = list[i].copy(pinned = pinned, updatedAt = now)
        save()
    }

    @Synchronized fun setColor(id: Long, color: Int) {
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) { list[i] = list[i].copy(color = color); save() }
    }

    @Synchronized fun delete(ids: Collection<Long>) {
        val it = list.iterator()
        while (it.hasNext()) {
            val x = it.next()
            if (x.id in ids) { removeFile(x); it.remove() }
        }
        save()
    }

    // ------------------------------------------------------------------
    private fun removeFile(x: ClipItem) {
        try { x.file?.let { File(dir, it).delete() } } catch (_: Throwable) { }
    }

    private fun trim() {
        for ((type, max) in listOf(TYPE_TEXT to MAX_TEXT, TYPE_IMAGE to MAX_IMAGE)) {
            val loose = list.filter { it.type == type && !it.pinned }.sortedByDescending { it.updatedAt }
            if (loose.size > max) loose.drop(max).forEach { removeFile(it); list.remove(it) }
        }
    }

    private fun load() {
        try {
            if (!db.exists()) return
            val arr = JSONArray(db.readText())
            for (n in 0 until arr.length()) {
                val o = arr.getJSONObject(n)
                val type = o.optInt("type", TYPE_TEXT)
                val file = if (o.has("file") && !o.isNull("file")) o.getString("file") else null
                if (type == TYPE_IMAGE && (file == null || !File(dir, file).exists())) continue // ảnh đã mất
                list.add(
                    ClipItem(
                        o.getLong("id"), type, o.optString("text", ""), file,
                        o.optBoolean("pinned", false), o.optInt("color", 2),
                        o.optLong("createdAt", 0L), o.optLong("updatedAt", 0L)
                    )
                )
            }
            val maxId = list.maxOfOrNull { it.id } ?: 0L
            if (maxId > seq.get()) seq.set(maxId)
        } catch (_: Throwable) { list.clear() }
    }

    private fun save() {
        try {
            val arr = JSONArray()
            for (x in list) arr.put(
                JSONObject().put("id", x.id).put("type", x.type).put("text", x.text)
                    .put("file", x.file ?: JSONObject.NULL).put("pinned", x.pinned)
                    .put("color", x.color).put("createdAt", x.createdAt).put("updatedAt", x.updatedAt)
            )
            val tmp = File(db.parentFile, "clip_store.json.tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(db)) { db.writeText(arr.toString()); tmp.delete() }
        } catch (_: Throwable) { }
    }
}
