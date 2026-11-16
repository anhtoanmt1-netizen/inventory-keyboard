package com.inventory.scannerkeyboard

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/** ScrollView có chiều cao tối đa: nội dung ngắn thì ôm vừa, nội dung dài thì cuộn. */
private class MaxHeightScrollView(ctx: android.content.Context, private val maxPx: Int) : ScrollView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.AT_MOST))
    }
}

/**
 * Bảng xem nội dung một mục Clipboard: cửa sổ nổi chính giữa màn hình.
 * Văn bản: xem đầy đủ, cuộn được. Hình ảnh: phóng to / thu nhỏ bằng hai ngón, chạm đúp, hoặc nút + / −.
 * Có các nút Dán, Sao chép, Ghim, Xóa phù hợp với nội dung.
 */
class ClipViewer(
    private val svc: InputMethodService,
    private val store: ClipStore,
    private val host: Host
) {
    interface Host {
        fun paste(item: ClipItem)
        fun copy(item: ClipItem)
        fun changed()
    }

    private var dialog: Dialog? = null
    private val main = Handler(Looper.getMainLooper())
    private val d = svc.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()
    private fun col(id: Int) = ContextCompat.getColor(svc, id)
    private val timeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun dismiss() {
        try { dialog?.dismiss() } catch (_: Throwable) { }
        dialog = null
    }

    fun show(item: ClipItem) {
        dismiss()
        try {
            build(item)
        } catch (_: Throwable) {
            Toast.makeText(svc, "Không mở được bảng xem.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun button(text: String, primary: Boolean, click: (TextView) -> Unit): TextView = TextView(svc).apply {
        this.text = text
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        setSingleLine()
        setBackgroundResource(if (primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_secondary)
        isClickable = true
        setOnClickListener { click(this) }
    }

    private fun build(item: ClipItem) {
        val dm = svc.resources.displayMetrics
        val cardW = min((dm.widthPixels * 0.92f).toInt(), dp(520))
        val maxContent = min((dm.heightPixels * 0.5f).toInt(), dp(380))
        val isText = item.type == ClipStore.TYPE_TEXT

        val dlg = Dialog(svc)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(svc).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(col(R.color.surface)); cornerRadius = dp(18).toFloat(); setStroke(dp(1), col(R.color.stroke))
            }
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }

        // ---- tiêu đề + nút đóng ----
        val head = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(svc).apply {
            text = if (isText) "Xem văn bản" else "Xem hình ảnh"
            textSize = 15f; setTextColor(col(R.color.accent)); setTypeface(null, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(ImageView(svc).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ContextCompat.getColorStateList(svc, R.color.icon_tint)
            setPadding(dp(7), dp(7), dp(7), dp(7))
            setBackgroundResource(R.drawable.bg_key)
            isClickable = true
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(dp(36), dp(36)))
        card.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- thông tin ngắn ----
        val meta = TextView(svc).apply { textSize = 11f; setTextColor(col(R.color.muted)) }
        card.addView(meta, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })

        // ---- nội dung ----
        val panelBg = GradientDrawable().apply { setColor(col(R.color.surface2)); cornerRadius = dp(12).toFloat() }
        if (isText) {
            val lines = item.text.count { it == '\n' } + 1
            meta.text = "${item.text.length} ký tự · $lines dòng · ${timeFmt.format(Date(item.updatedAt))}"
            val sv = MaxHeightScrollView(svc, maxContent).apply {
                background = panelBg
                isVerticalScrollBarEnabled = true
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            sv.addView(TextView(svc).apply {
                text = item.text; textSize = 15f; setTextColor(col(R.color.text))
                setLineSpacing(dp(2).toFloat(), 1f)
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            card.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        } else {
            val zoom = ZoomImageView(svc).apply { background = panelBg }
            card.addView(zoom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxContent).apply { topMargin = dp(10) })
            loadImage(item, zoom, meta)

            val zr = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            zr.addView(button("−", false) { zoom.zoomBy(0.8f) }, LinearLayout.LayoutParams(dp(56), dp(38)).apply { setMargins(dp(3), 0, dp(3), 0) })
            zr.addView(button("Vừa khung", false) { zoom.fit() }, LinearLayout.LayoutParams(dp(110), dp(38)).apply { setMargins(dp(3), 0, dp(3), 0) })
            zr.addView(button("+", false) { zoom.zoomBy(1.25f) }, LinearLayout.LayoutParams(dp(56), dp(38)).apply { setMargins(dp(3), 0, dp(3), 0) })
            card.addView(zr, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            card.addView(TextView(svc).apply {
                text = "Hai ngón để phóng to / thu nhỏ · chạm đúp để zoom nhanh"
                textSize = 10f; gravity = Gravity.CENTER; setTextColor(col(R.color.muted))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
        }

        // ---- nút chức năng ----
        val actions = LinearLayout(svc).apply { orientation = LinearLayout.HORIZONTAL }
        fun lp() = LinearLayout.LayoutParams(0, dp(44), 1f).apply { setMargins(dp(3), 0, dp(3), 0) }
        actions.addView(button("Dán", true) { host.paste(item); dismiss() }, lp())
        actions.addView(button("Sao chép", false) { host.copy(item) }, lp())
        var pinned = store.get(item.id)?.pinned ?: item.pinned
        actions.addView(button(if (pinned) "Bỏ ghim" else "Ghim", false) { b ->
            pinned = !pinned
            store.setPinned(listOf(item.id), pinned)
            b.text = if (pinned) "Bỏ ghim" else "Ghim"
            host.changed()
        }, lp())
        var armed = false
        actions.addView(button("Xóa", false) { b ->
            if (!armed) {
                armed = true
                b.text = "Chạm lại"
                b.setTextColor(0xFFFF8A80.toInt())
                main.postDelayed({ if (dialog === dlg) { armed = false; b.text = "Xóa"; b.setTextColor(Color.WHITE) } }, 3000L)
            } else {
                store.delete(listOf(item.id))
                host.changed()
                dismiss()
            }
        }, lp())
        card.addView(actions, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })

        dlg.setContentView(card, ViewGroup.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT))
        val w = dlg.window ?: return
        w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val attrs = w.attributes
        // Cửa sổ con của bàn phím: không cướp tiêu điểm nhập liệu, nằm chính giữa màn hình.
        attrs.token = svc.window?.window?.decorView?.windowToken
        attrs.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        attrs.width = cardW
        attrs.height = ViewGroup.LayoutParams.WRAP_CONTENT
        attrs.gravity = Gravity.CENTER
        attrs.dimAmount = 0.6f
        attrs.flags = attrs.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        w.attributes = attrs
        dlg.setCanceledOnTouchOutside(true)
        dlg.setOnDismissListener { if (dialog === dlg) dialog = null }
        dialog = dlg
        dlg.show()
    }

    /** Đọc ảnh ở luồng nền (tối đa ~2048px) rồi đưa lên khung xem. */
    private fun loadImage(item: ClipItem, zoom: ZoomImageView, meta: TextView) {
        val f = store.imageFile(item)
        if (f == null || !f.exists()) { meta.text = "Không tìm thấy ảnh."; return }
        Thread {
            var bmp: Bitmap? = null
            var w = 0
            var h = 0
            try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, o)
                w = o.outWidth; h = o.outHeight
                var s = 1
                while (w / (s * 2) >= 2048 || h / (s * 2) >= 2048) s *= 2
                bmp = BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
            } catch (_: Throwable) { }
            val kb = f.length() / 1024
            main.post {
                if (bmp != null) {
                    zoom.setImageBitmap(bmp)
                    meta.text = "$w × $h px · $kb KB · ${timeFmt.format(Date(item.updatedAt))}"
                } else meta.text = "Không đọc được ảnh."
            }
        }.start()
    }
}
