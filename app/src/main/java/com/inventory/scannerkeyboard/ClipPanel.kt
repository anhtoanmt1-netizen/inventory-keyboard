package com.inventory.scannerkeyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.HashSet
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Bảng Clipboard trên bàn phím: hai khu vực độc lập (Văn bản / Hình ảnh), thẻ dạng ghi chú,
 * ghim + màu pastel, chọn nhiều + xóa (có xác nhận), tìm kiếm không phân biệt dấu, hiển thị thời gian.
 */
class ClipPanel(
    private val ctx: Context,
    private val store: ClipStore,
    private val cb: Callbacks
) {
    interface Callbacks {
        fun onPaste(item: ClipItem)
        /** Gửi nhiều mục cùng lúc, đúng thứ tự người dùng đã chọn (văn bản và hình ảnh đều được). */
        fun onPasteAll(items: List<ClipItem>)
        fun onSearch()
        fun onSearchCleared()
        /** Mở bảng xem nội dung đầy đủ của một mục (nút con mắt). */
        fun onView(item: ClipItem)
    }

    companion object {
        /** Bảng màu nhẹ: hồng, cam, vàng, xanh lá, xanh ngọc, xanh dương, tím, xám. */
        val PASTEL = intArrayOf(
            0xFFFFD6D6.toInt(), 0xFFFFE2BF.toInt(), 0xFFFFF4B5.toInt(), 0xFFD9F2C8.toInt(),
            0xFFCDEFE6.toInt(), 0xFFCFE3FF.toInt(), 0xFFE3D8FF.toInt(), 0xFFE6E6E6.toInt()
        )
        private const val NOTE_TEXT = 0xFF2F2B26.toInt()
        private const val NOTE_MUTED = 0xFF6B645B.toInt()

        fun normalize(s: String): String {
            val a = s.replace('đ', 'd').replace('Đ', 'D')
            return Normalizer.normalize(a, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)
        }
    }

    val view = LinearLayout(ctx)
    var query = ""
        private set

    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()
    private val main = Handler(Looper.getMainLooper())

    private var tab = ClipStore.TYPE_TEXT
    private var selecting = false
    private var confirmDelete = false
    private val selected = LinkedHashSet<Long>() // giữ đúng thứ tự người dùng chọn
    private val paletteOpen = HashSet<Long>()
    private var dirty = true

    private val tabText = TextView(ctx)
    private val tabImage = TextView(ctx)
    private val btnSearch = ImageView(ctx)
    private val btnSelect = ImageView(ctx)
    private val bar = LinearLayout(ctx)
    private val scroll = ScrollView(ctx)
    private val list = LinearLayout(ctx)

    private val thumbs = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val io = Executors.newSingleThreadExecutor()
    private val timeToday = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val timeOther = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    init { build() }

    // ------------------------------------------------------------------ dựng khung
    private fun col(id: Int) = ContextCompat.getColor(ctx, id)

    private fun build() {
        view.orientation = LinearLayout.VERTICAL
        view.setPadding(dp(4), 0, dp(4), 0)

        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        fun styleTab(t: TextView, label: String, type: Int) {
            t.text = label
            t.gravity = Gravity.CENTER
            t.textSize = 13f
            t.setTypeface(null, android.graphics.Typeface.BOLD)
            t.setTextColor(ColorStateList.valueOf(col(R.color.text)))
            t.setBackgroundResource(R.drawable.bg_toggle)
            t.isClickable = true
            t.setOnClickListener { tab = type; if (!selecting) selected.clear(); confirmDelete = false; refresh(true) }
            head.addView(t, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) })
        }
        styleTab(tabText, "Văn bản", ClipStore.TYPE_TEXT)
        styleTab(tabImage, "Hình ảnh", ClipStore.TYPE_IMAGE)
        fun styleIcon(v: ImageView, icon: Int, click: () -> Unit) {
            v.setImageResource(icon)
            v.imageTintList = ContextCompat.getColorStateList(ctx, R.color.icon_tint)
            v.setPadding(dp(10), dp(10), dp(10), dp(10))
            v.setBackgroundResource(R.drawable.bg_toggle)
            v.isClickable = true
            v.setOnClickListener { click() }
            head.addView(v, LinearLayout.LayoutParams(dp(42), dp(42)).apply { setMargins(dp(3), dp(2), dp(3), dp(2)) })
        }
        styleIcon(btnSearch, R.drawable.ic_search) { cb.onSearch() }
        styleIcon(btnSelect, R.drawable.ic_select) { if (selecting) endSelect() else startSelect(null) }
        view.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))

        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.visibility = View.GONE
        bar.minimumHeight = dp(44)
        view.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, dp(2), 0, dp(6))
        scroll.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        scroll.isVerticalScrollBarEnabled = false
        view.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)))
    }

    fun setListHeight(px: Int) {
        scroll.layoutParams.height = px
        scroll.requestLayout()
    }

    val listHeight: Int get() = scroll.layoutParams.height

    // ------------------------------------------------------------------ trạng thái
    fun setQuery(q: String) {
        query = q.trim()
        refresh(true)
    }

    private fun startSelect(first: ClipItem?) {
        selecting = true
        confirmDelete = false
        selected.clear()
        if (first != null) selected.add(first.id)
        refresh(true)
    }

    fun endSelect() {
        selecting = false
        confirmDelete = false
        selected.clear()
        refresh(true)
    }

    /** Gọi khi bảng được hiện ra. */
    fun onShown() { if (dirty) refresh(true) else renderBar() }

    /** Gọi khi dữ liệu thay đổi từ bên ngoài (có mục mới được sao chép). */
    fun refresh(force: Boolean = false) {
        if (!view.isShown && !force) { dirty = true; return }
        dirty = false
        val y = scroll.scrollY
        render()
        if (!force) scroll.post { scroll.scrollTo(0, y) }
        else scroll.post { scroll.scrollTo(0, 0) }
    }

    // ------------------------------------------------------------------ thanh công cụ phụ
    private fun barText(t: String, weight: Float = 1f, into: LinearLayout = bar): TextView = TextView(ctx).apply {
        text = t; textSize = 13f; setTextColor(col(R.color.text)); gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        into.addView(this, LinearLayout.LayoutParams(if (weight > 0) 0 else ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, weight))
    }

    /** fill = true: nút chia đều chiều ngang của hàng (dùng cho hàng nút hành động khi chọn nhiều). */
    private fun barButton(label: String, accent: Boolean, into: LinearLayout = bar, fill: Boolean = false, click: () -> Unit) {
        val t = TextView(ctx).apply {
            text = label; textSize = 13f; gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (accent) Color.WHITE else col(R.color.text))
            setBackgroundResource(if (accent) R.drawable.bg_key_accent else R.drawable.bg_key_special)
            isClickable = true
            setPadding(dp(if (fill) 4 else 14), 0, dp(if (fill) 4 else 14), 0)
            setOnClickListener { click() }
        }
        val lp = if (fill) LinearLayout.LayoutParams(0, dp(38), 1f) else LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38))
        into.addView(t, lp.apply { setMargins(dp(3), 0, dp(3), 0) })
    }

    private fun renderBar() {
        bar.removeAllViews()
        bar.orientation = LinearLayout.HORIZONTAL
        when {
            selecting && confirmDelete -> {
                bar.visibility = View.VISIBLE
                barText("Xóa ${selected.size} mục đã chọn?")
                barButton("Hủy", false) { confirmDelete = false; renderBar() }
                barButton("Xóa", true) {
                    store.delete(ArrayList(selected))
                    selected.clear(); confirmDelete = false; selecting = false
                    refresh()
                }
            }
            selecting -> {
                bar.visibility = View.VISIBLE
                bar.orientation = LinearLayout.VERTICAL
                val items = selected.mapNotNull { store.get(it) }
                val nText = items.count { it.type == ClipStore.TYPE_TEXT }
                val nImg = items.size - nText
                val status = when {
                    items.isEmpty() -> "Chạm để chọn mục"
                    nText > 0 && nImg > 0 -> "Đã chọn ${items.size} ($nText văn bản, $nImg ảnh)"
                    else -> "Đã chọn ${items.size}"
                }
                // Hàng 1: trạng thái + Xong. Hàng 2: các nút hành động chia đều.
                val row1 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val row2 = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                bar.addView(row1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
                bar.addView(row2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
                barText(status, 1f, row1)
                barButton("Xong", false, row1) { endSelect() }
                barButton("Tất cả", false, row2, true) {
                    filtered().forEach { selected.add(it.id) }; refresh()
                }
                barButton("Ghim", false, row2, true) {
                    if (selected.isNotEmpty()) {
                        val all = selected.mapNotNull { store.get(it) }
                        store.setPinned(ArrayList(selected), !all.all { it.pinned })
                        refresh()
                    }
                }
                barButton("Xóa", false, row2, true) { if (selected.isNotEmpty()) { confirmDelete = true; renderBar() } }
                barButton("Gửi tất cả", true, row2, true) {
                    // selected là LinkedHashSet nên danh sách này đúng thứ tự người dùng đã chạm chọn.
                    val ordered = selected.mapNotNull { store.get(it) }
                    if (ordered.isNotEmpty()) {
                        cb.onPasteAll(ordered)
                        ordered.forEach { store.touch(it.id) }
                        endSelect()
                    }
                }
            }
            query.isNotEmpty() -> {
                bar.visibility = View.VISIBLE
                barText("Tìm: $query")
                barButton("Xóa tìm", false) { query = ""; cb.onSearchCleared(); refresh(true) }
            }
            else -> bar.visibility = View.GONE
        }
        btnSelect.isSelected = selecting
        Fonts.apply(bar)
    }

    // ------------------------------------------------------------------ danh sách
    private fun filtered(): List<ClipItem> {
        val q = normalize(query)
        return store.all()
            .filter { it.type == tab && (q.isEmpty() || tab == ClipStore.TYPE_IMAGE || normalize(it.text).contains(q)) }
            .sortedWith(compareByDescending<ClipItem> { it.pinned }.thenByDescending { it.updatedAt })
    }

    private fun render() {
        tabText.isSelected = tab == ClipStore.TYPE_TEXT
        tabImage.isSelected = tab == ClipStore.TYPE_IMAGE
        renderBar()
        list.removeAllViews()
        val items = filtered()
        if (items.isEmpty()) {
            list.addView(TextView(ctx).apply {
                text = when {
                    query.isNotEmpty() && tab == ClipStore.TYPE_TEXT -> "Không có mục nào khớp \"$query\"."
                    tab == ClipStore.TYPE_TEXT -> "Chưa có văn bản nào.\nSao chép văn bản ở bất kỳ ứng dụng nào, nó sẽ tự xuất hiện ở đây."
                    else -> "Chưa có hình ảnh nào.\nSao chép hình ảnh ở ứng dụng khác, nó sẽ tự xuất hiện ở đây."
                }
                setTextColor(col(R.color.muted)); textSize = 13f; gravity = Gravity.CENTER
                setPadding(dp(16), dp(40), dp(16), dp(40))
            })
        } else {
            val pinned = items.filter { it.pinned }
            val recent = items.filter { !it.pinned }
            if (pinned.isNotEmpty()) { addHeader("ĐÃ GHIM"); addGrid(pinned) }
            if (recent.isNotEmpty()) { if (pinned.isNotEmpty()) addHeader("GẦN ĐÂY"); addGrid(recent) }
        }
        Fonts.apply(list)
    }

    private fun addHeader(t: String) {
        list.addView(TextView(ctx).apply {
            text = t; textSize = 11f; setTextColor(col(R.color.accent))
            setTypeface(null, android.graphics.Typeface.BOLD); letterSpacing = 0.08f
            setPadding(dp(6), dp(6), dp(6), dp(2))
        })
    }

    private fun addGrid(items: List<ClipItem>) {
        for (i in items.indices step 2) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in 0..1) {
                val entry = items.getOrNull(i + k)
                val cell: View = if (entry != null) card(entry) else View(ctx)
                row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun formatTime(t: Long): String {
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = t }
        val same = now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
        return (if (same) timeToday else timeOther).format(Date(t))
    }

    private fun card(item: ClipItem): View {
        val note = item.pinned
        val bg = if (note) PASTEL[item.color.coerceIn(0, PASTEL.size - 1)] else col(R.color.surface2)
        val fg = if (note) NOTE_TEXT else col(R.color.text)
        val muted = if (note) NOTE_MUTED else col(R.color.muted)
        val isSel = selected.contains(item.id)

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(bg); cornerRadius = dp(10).toFloat()
                if (isSel) setStroke(dp(3), col(R.color.accent)) else if (note) setStroke(dp(1), 0x33000000)
            }
            isClickable = true
            setOnClickListener {
                if (selecting) {
                    if (!selected.add(item.id)) selected.remove(item.id)
                    confirmDelete = false
                    refresh()
                } else {
                    cb.onPaste(item)
                    store.touch(item.id)
                    refresh()
                }
            }
            setOnLongClickListener {
                if (!selecting) startSelect(item) else { if (!selected.add(item.id)) selected.remove(item.id); refresh() }
                true
            }
        }

        if (item.type == ClipStore.TYPE_TEXT) {
            box.addView(TextView(ctx).apply {
                text = item.text; textSize = 12f; setTextColor(fg)
                maxLines = 4; ellipsize = TextUtils.TruncateAt.END
                minHeight = dp(54)
                setPadding(dp(9), dp(8), dp(9), dp(4))
            })
        } else {
            val iv = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0x22000000)
                tag = item.id
            }
            box.addView(iv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)).apply { setMargins(dp(4), dp(4), dp(4), 0) })
            loadThumb(item, iv)
        }

        // chân thẻ: thời gian + màu + ghim
        val foot = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        foot.addView(TextView(ctx).apply {
            text = (if (isSel) "✓ " else "") + formatTime(item.updatedAt)
            textSize = 10f; setTextColor(if (isSel) col(R.color.accent) else muted)
            setPadding(dp(9), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        foot.addView(iconBtn(R.drawable.ic_eye, if (note) NOTE_MUTED else muted) { cb.onView(item) })
        if (note) foot.addView(iconBtn(R.drawable.ic_palette, NOTE_MUTED) {
            if (!paletteOpen.add(item.id)) paletteOpen.remove(item.id); refresh()
        })
        foot.addView(iconBtn(R.drawable.ic_pin, if (note) col(R.color.accent) else muted) {
            store.setPinned(listOf(item.id), !item.pinned); paletteOpen.remove(item.id); refresh()
        })
        box.addView(foot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)))

        if (note && paletteOpen.contains(item.id)) {
            val pal = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            PASTEL.forEachIndexed { idx, c ->
                val dot = View(ctx).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(c)
                        setStroke(if (idx == item.color) dp(2) else dp(1), if (idx == item.color) 0xFF2F2B26.toInt() else 0x44000000)
                    }
                    setOnClickListener { store.setColor(item.id, idx); refresh() }
                }
                pal.addView(dot, LinearLayout.LayoutParams(0, dp(20), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
            }
            box.addView(pal, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)).apply { setMargins(dp(4), 0, dp(4), dp(4)) })
        }
        return box
    }

    private fun iconBtn(icon: Int, tint: Int, click: () -> Unit): ImageView = ImageView(ctx).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(tint)
        setPadding(dp(6), dp(6), dp(6), dp(6))
        isClickable = true
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
    }

    // ------------------------------------------------------------------ ảnh thu nhỏ
    private fun loadThumb(item: ClipItem, iv: ImageView) {
        val f = store.imageFile(item) ?: return
        val key = f.name
        val cached = thumbs.get(key)
        if (cached != null) { iv.setImageBitmap(cached); return }
        io.execute {
            val bmp = try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, o)
                var s = 1
                while (o.outWidth / (s * 2) >= 360 && o.outHeight / (s * 2) >= 360) s *= 2
                BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
            } catch (_: Throwable) { null }
            if (bmp != null) {
                thumbs.put(key, bmp)
                main.post { if (iv.tag == item.id) iv.setImageBitmap(bmp) }
            }
        }
    }

    fun release() { io.shutdown() }
}
