package com.inventory.scannerkeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Một gói dữ liệu để chia sẻ: JSON mô tả + các file ảnh đi kèm (chỉ có khi xuất ra file). */
class ShareBundle(val kind: String, val json: JSONObject, val images: Map<String, ByteArray>)

/** Một mảnh mã QR (dữ liệu lớn được chia thành nhiều mã). */
class QrPart(val kind: String, val id: String, val index: Int, val total: Int, val data: String)

/**
 * Xuất / nhập giao diện và cài đặt để chia sẻ giữa các thiết bị, bằng file (.spx, có cả ảnh) hoặc mã QR
 * (gọn nhẹ, không kèm ảnh, tự chia thành nhiều mã nếu dữ liệu dài). Giao diện và cài đặt được xuất / nhập riêng.
 * Khi nhập, mọi giá trị đều được kiểm tra và giới hạn trong khoảng hợp lệ; chỉ nhận các khóa cài đặt đã biết.
 */
object Transfer {
    const val KIND_THEME = "theme"
    const val KIND_SETTINGS = "settings"

    private const val FORMAT = 1
    private const val MAX_IMAGE = 8 * 1024 * 1024
    private const val MAX_JSON = 4 * 1024 * 1024
    private const val MAX_TOTAL = 48 * 1024 * 1024
    private const val QR_CHUNK = 650          // số ký tự dữ liệu trong mỗi mã QR (đủ nhỏ để quét dễ từ màn hình)
    private const val QR_PREFIX = "SPX1"

    private val INT_KEYS = mapOf(
        "debounce" to (0..60000), "scanDelay" to (0..10000), "vibrateMs" to (0..2000), "enterDelay" to (0..5000),
        "dimPercent" to (5..100), "camHeight" to (120..420), "idleOff" to (0..3600), "keyRowDp" to (20..120),
        "keySoundType" to (0..20), "keySoundVol" to (0..100), "keyHapticStrength" to (1..100), "clipHeightDp" to (100..600)
    )
    private val BOOL_KEYS = listOf("sendEnter", "beep", "vibrate", "autoStart", "keepOn", "dim", "keySound", "keyHaptic")

    // ------------------------------------------------------------------ tiện ích
    private fun readImage(f: File): ByteArray? = try {
        if (f.exists() && f.length() in 1..MAX_IMAGE.toLong()) f.readBytes() else null
    } catch (_: Throwable) { null }

    private fun validImage(data: ByteArray): Boolean = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, o)
        o.outWidth > 0 && o.outHeight > 0
    } catch (_: Throwable) { false }

    private fun ext(name: String): String = if (name.substringAfterLast('.', "jpg").lowercase(Locale.ROOT) == "png") "png" else "jpg"

    private fun safeName(n: String): String = n.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

    fun fileName(kind: String): String {
        val t = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "SPX-" + (if (kind == KIND_THEME) "giaodien-" else "caidat-") + t + ".spx"
    }

    // ------------------------------------------------------------------ dựng gói xuất
    private fun stripImages(t: KbTheme): KbTheme {
        fun fix(p: PanelStyle) = p.copy(bgImage = null, bgType = if (p.bgType == 3) 1 else p.bgType)
        return t.copy(scan = fix(t.scan), kb = fix(t.kb), space = t.space.copy(image = null))
    }

    /** Gói giao diện: [onlyActive] = chỉ giao diện đang dùng. [withImages] = kèm ảnh nền / ảnh phím Space (xuất file). */
    fun themeBundle(ctx: Context, onlyActive: Boolean, withImages: Boolean): ShareBundle {
        val st = ThemeStore.load(ctx)
        var themes = if (onlyActive) listOfNotNull(st.current()) else st.themes
        val images = HashMap<String, ByteArray>()
        if (withImages) {
            val dir = ThemeStore.imageDir(ctx)
            for (t in themes) for (n in listOfNotNull(t.scan.bgImage, t.kb.bgImage, t.space.image)) {
                if (n !in images) readImage(File(dir, n))?.let { images[n] = it }
            }
        } else {
            themes = themes.map { stripImages(it) }
        }
        val active = themes.firstOrNull { it.id == st.active }?.id ?: themes.firstOrNull()?.id ?: ""
        val json = JSONObject().put("fmt", FORMAT).put("kind", KIND_THEME)
            .put("themes", JSONObject(ThemeStore.toJson(ThemeState(active, themes))))
        return ShareBundle(KIND_THEME, json, images)
    }

    /** Gói cài đặt: các tùy chọn quét mã, rung, âm thanh, kích thước, bố cục nút Scan, kiểu dòng "Created by". */
    fun settingsBundle(ctx: Context, withImages: Boolean): ShareBundle {
        val sp = ctx.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val o = JSONObject()
        for (k in INT_KEYS.keys) if (sp.contains(k)) try { o.put(k, sp.getInt(k, 0)) } catch (_: Throwable) { }
        for (k in BOOL_KEYS) if (sp.contains(k)) try { o.put(k, sp.getBoolean(k, false)) } catch (_: Throwable) { }
        val images = HashMap<String, ByteArray>()
        val lj = sp.getString("scanLayout", "") ?: ""
        if (lj.isNotBlank()) {
            var lay = ScanLayout.fromJson(lj)
            if (withImages) {
                val dir = ScanButtons.imageDir(ctx)
                for (n in lay.usedImages()) readImage(File(dir, n))?.let { images[n] = it }
            } else {
                lay = lay.copy(buttons = lay.buttons.mapValues { (_, c) -> c.copy(image = null) })
            }
            o.put("scanLayout", lay.toJson())
        }
        val cj = sp.getString("credit", "") ?: ""
        if (cj.isNotBlank()) o.put("credit", cj)
        val json = JSONObject().put("fmt", FORMAT).put("kind", KIND_SETTINGS).put("prefs", o)
        return ShareBundle(KIND_SETTINGS, json, images)
    }

    // ------------------------------------------------------------------ file .spx (zip)
    fun toZip(b: ShareBundle): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("data.json"))
            z.write(b.json.toString().toByteArray(Charsets.UTF_8))
            z.closeEntry()
            for ((name, data) in b.images) {
                z.putNextEntry(ZipEntry("images/" + safeName(name)))
                z.write(data)
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private fun readLimited(ins: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun checkJson(o: JSONObject, images: Map<String, ByteArray>): ShareBundle? {
        val kind = o.optString("kind", "")
        if (kind != KIND_THEME && kind != KIND_SETTINGS) return null
        if (o.optInt("fmt", 0) !in 1..FORMAT) return null
        return ShareBundle(kind, o, images)
    }

    /** Đọc nội dung một file .spx (zip) hoặc file JSON thuần. Trả về null nếu không đúng định dạng. */
    fun readBundle(bytes: ByteArray): ShareBundle? {
        return try {
            val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
            if (!isZip) return checkJson(JSONObject(String(bytes, Charsets.UTF_8)), emptyMap())
            val images = HashMap<String, ByteArray>()
            var json: JSONObject? = null
            var total = 0
            ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val isData = e.name == "data.json"
                        val data = readLimited(zin, if (isData) MAX_JSON else MAX_IMAGE) ?: return null
                        total += data.size
                        if (total > MAX_TOTAL) return null
                        if (isData) json = JSONObject(String(data, Charsets.UTF_8))
                        else if (e.name.startsWith("images/")) images[safeName(e.name)] = data
                    }
                    e = zin.nextEntry
                }
            }
            val j = json ?: return null
            checkJson(j, images)
        } catch (_: Throwable) { null }
    }

    // ------------------------------------------------------------------ mã QR (chia nhiều mảnh)
    private fun deflate(raw: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION)
        d.setInput(raw)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun inflate(z: ByteArray): ByteArray? {
        val inf = Inflater()
        return try {
            inf.setInput(z)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
                if (out.size() > MAX_JSON) return null
            }
            out.toByteArray()
        } catch (_: Throwable) { null } finally { inf.end() }
    }

    /** Chia gói thành các chuỗi để vẽ mã QR (không kèm ảnh). */
    fun toQrChunks(b: ShareBundle): List<String> {
        val z = deflate(b.json.toString().toByteArray(Charsets.UTF_8))
        val b64 = Base64.encodeToString(z, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        val id = Integer.toString((Math.random() * 1679615).toInt() + 1, 36)
        val parts = b64.chunked(QR_CHUNK)
        val k = if (b.kind == KIND_THEME) "t" else "s"
        return parts.mapIndexed { i, p -> "$QR_PREFIX|$k|$id|${i + 1}|${parts.size}|$p" }
    }

    fun parseQr(text: String): QrPart? {
        if (!text.startsWith("$QR_PREFIX|")) return null
        val f = text.split("|", limit = 6)
        if (f.size != 6) return null
        val kind = when (f[1]) { "t" -> KIND_THEME; "s" -> KIND_SETTINGS; else -> return null }
        val idx = f[3].toIntOrNull() ?: return null
        val total = f[4].toIntOrNull() ?: return null
        if (total < 1 || total > 200 || idx < 1 || idx > total || f[2].isEmpty()) return null
        return QrPart(kind, f[2], idx, total, f[5])
    }

    /** Ghép đủ các mảnh QR thành một gói; trả về null nếu thiếu mảnh hoặc dữ liệu hỏng. */
    fun assemble(parts: Collection<QrPart>): ShareBundle? {
        if (parts.isEmpty()) return null
        val total = parts.first().total
        val byIdx = parts.associateBy { it.index }
        if (byIdx.size != total) return null
        val sb = StringBuilder()
        for (i in 1..total) sb.append((byIdx[i] ?: return null).data)
        return try {
            val raw = inflate(Base64.decode(sb.toString(), Base64.URL_SAFE or Base64.NO_PADDING)) ?: return null
            checkJson(JSONObject(String(raw, Charsets.UTF_8)), emptyMap())
        } catch (_: Throwable) { null }
    }

    fun qrBitmap(text: String, size: Int): Bitmap? = try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val w = m.width
        val h = m.height
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) px[y * w + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    } catch (_: Throwable) { null }

    // ------------------------------------------------------------------ áp dụng gói nhập
    /** Thêm các giao diện trong gói vào danh sách (đổi id mới, chép ảnh vào kho ảnh). Trả về số giao diện đã thêm. */
    fun applyThemes(ctx: Context, b: ShareBundle, activate: Boolean): Int {
        val inc = ThemeStore.parse(b.json.optJSONObject("themes")?.toString())
        if (inc.themes.isEmpty()) return 0
        val cur = ThemeStore.load(ctx)
        val dir = ThemeStore.imageDir(ctx)
        val map = HashMap<String, String>()
        var counter = 0
        fun bring(name: String?): String? {
            if (name == null) return null
            map[name]?.let { return it }
            val data = b.images[safeName(name)] ?: return null
            if (!validImage(data)) return null
            val nn = "imp_${System.currentTimeMillis()}_${counter++}.${ext(name)}"
            return try { File(dir, nn).writeBytes(data); map[name] = nn; nn } catch (_: Throwable) { null }
        }
        fun fix(p: PanelStyle): PanelStyle {
            val img = bring(p.bgImage)
            return p.copy(bgImage = img, bgType = if (p.bgType == 3 && img == null) 1 else p.bgType)
        }
        val existing = cur.themes.map { it.name }.toSet()
        val added = inc.themes.mapIndexed { i, t ->
            val name = if (t.name in existing) t.name + " (nhập)" else t.name
            KbTheme(ThemeStore.newId() + "i" + i, name, fix(t.scan), fix(t.kb), t.space.copy(image = bring(t.space.image)))
        }
        val active = if (activate) added.first().id else (cur.current()?.id ?: added.first().id)
        ThemeStore.save(ctx, ThemeState(active, cur.themes + added))
        return added.size
    }

    /** Ghi các cài đặt trong gói vào ứng dụng. Trả về số mục đã áp dụng. */
    fun applySettings(ctx: Context, b: ShareBundle): Int {
        val o = b.json.optJSONObject("prefs") ?: return 0
        val ed = ctx.applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE).edit()
        var n = 0
        for ((k, r) in INT_KEYS) if (o.has(k)) {
            val v = o.optInt(k, Int.MIN_VALUE)
            if (v != Int.MIN_VALUE) { ed.putInt(k, v.coerceIn(r.first, r.last)); n++ }
        }
        for (k in BOOL_KEYS) if (o.has(k)) { ed.putBoolean(k, o.optBoolean(k)); n++ }
        val lj = o.optString("scanLayout", "")
        if (lj.isNotBlank()) {
            val lay = ScanLayout.fromJson(lj)
            val dir = ScanButtons.imageDir(ctx)
            val map = HashMap<String, String>()
            var counter = 0
            for (name in lay.usedImages()) {
                val data = b.images[safeName(name)] ?: continue
                if (!validImage(data)) continue
                val nn = "imp_${System.currentTimeMillis()}_${counter++}.${ext(name)}"
                try { File(dir, nn).writeBytes(data); map[name] = nn } catch (_: Throwable) { }
            }
            val fixed = lay.copy(buttons = lay.buttons.mapValues { (_, cfg) -> cfg.copy(image = cfg.image?.let { map[it] }) })
            ed.putString("scanLayout", fixed.toJson())
            ScanButtons.forgetBitmaps()
            n++
        }
        val cj = o.optString("credit", "")
        if (cj.isNotBlank()) { ed.putString("credit", CreditStyle.fromJson(cj).toJson()); n++ }
        ed.apply()
        return n
    }
}
