package com.inventory.scannerkeyboard

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.min

/** ScrollView có chiều cao tối đa: danh sách ngắn thì ôm vừa, danh sách dài thì cuộn. */
private class CappedScroll(ctx: android.content.Context, private val maxPx: Int) : ScrollView(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.AT_MOST))
    }
}

/**
 * Các bảng danh sách nút trực quan: mỗi nút hiện bằng đúng hình nút thật (cùng giao diện, bo góc, ảnh riêng),
 * kèm tên, mô tả chức năng và vị trí hiện tại. Dùng chung phong cách thẻ của ứng dụng.
 */
object ButtonPicker {

    private fun col(a: Activity, id: Int) = ContextCompat.getColor(a, id)

    /** Một dòng thông tin nút: [hình nút] tên, mô tả, nhãn vị trí, [phần phụ bên phải]. */
    fun richRow(a: Activity, lay: ScanLayout, key: String, st: PanelStyle, tag: String?, trailing: View?): LinearLayout {
        val d = a.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val row = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat(); setColor(col(a, R.color.surface2)); setStroke(dp(1), col(a, R.color.stroke))
            }
        }
        row.addView(ScanButtons.tile(a, lay, key, 44, st), LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        val texts = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(a).apply {
            text = ScanLayout.LABELS[key] ?: key
            textSize = 14f; setTextColor(col(a, R.color.text)); setTypeface(null, Typeface.BOLD)
        })
        texts.addView(TextView(a).apply {
            text = ScanLayout.DESCS[key] ?: ""
            textSize = 11f; setTextColor(col(a, R.color.muted))
        })
        if (tag != null) texts.addView(TextView(a).apply {
            text = tag; textSize = 10f; setTextColor(col(a, R.color.accent)); setTypeface(null, Typeface.BOLD)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0x22EE4D2D); setStroke(dp(1), 0x55EE4D2D) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (trailing != null) row.addView(trailing, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { marginStart = dp(8) })
        return row
    }

    private fun actionButton(a: Activity, text: String, primary: Boolean, enabled: Boolean = true, click: () -> Unit): TextView {
        val d = a.resources.displayMetrics.density
        return TextView(a).apply {
            this.text = text; textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD); setSingleLine()
            setBackgroundResource(if (primary) R.drawable.bg_btn_primary else R.drawable.bg_btn_secondary)
            setPadding((12 * d).toInt(), 0, (12 * d).toInt(), 0)
            alpha = if (enabled) 1f else 0.35f
            isClickable = enabled
            if (enabled) setOnClickListener { click() }
        }
    }

    /** Khung hộp thoại nổi giữa màn hình: tiêu đề, mô tả, danh sách cuộn được, nút Đóng. */
    private fun frame(a: Activity, title: String, subtitle: String): Pair<Dialog, LinearLayout> {
        val d = a.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val dm = a.resources.displayMetrics
        val dlg = Dialog(a)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val card = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(col(a, R.color.surface)); cornerRadius = dp(18).toFloat(); setStroke(dp(1), col(a, R.color.stroke))
            }
            setPadding(dp(16), dp(16), dp(16), dp(14))
        }
        card.addView(TextView(a).apply {
            text = title; textSize = 16f; setTextColor(col(a, R.color.accent)); setTypeface(null, Typeface.BOLD)
        })
        card.addView(TextView(a).apply {
            text = subtitle; textSize = 12f; setTextColor(col(a, R.color.muted)); setPadding(0, dp(2), 0, dp(10))
        })
        val list = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val sv = CappedScroll(a, min((dm.heightPixels * 0.6f).toInt(), dp(480))).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        sv.addView(list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(actionButton(a, "ĐÓNG", false) { dlg.dismiss() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(12) })
        val w = min((dm.widthPixels * 0.94f).toInt(), dp(520))
        dlg.setContentView(card, ViewGroup.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT))
        dlg.window?.let {
            it.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            it.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dlg.setCanceledOnTouchOutside(true)
        Fonts.apply(card)
        return Pair(dlg, list)
    }

    private fun groupHeader(a: Activity, text: String): TextView {
        val d = a.resources.displayMetrics.density
        return TextView(a).apply {
            this.text = text; textSize = 11f; setTextColor(col(a, R.color.accent)); setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.06f; setPadding(0, (10 * d).toInt(), 0, (4 * d).toInt())
        }
    }

    /**
     * Danh sách các nút CÓ THỂ THÊM vào nhóm Công cụ, chia theo nơi đang đặt. Bấm THÊM để chuyển nút vào nhóm
     * (dòng đó biến mất khỏi danh sách); có thể thêm nhiều nút liên tiếp.
     */
    fun showAddToTools(a: Activity, lay: ScanLayout, st: PanelStyle, onPick: (String) -> Unit) {
        val d = a.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val cand = ScanLayout.KEYS.filter { it != "tools" && lay.areaOf(it) != ScanLayout.AREA_TOOLS }
        val (dlg, list) = frame(a, "Thêm vào nhóm Công cụ", "Chọn nút muốn đưa vào bảng công cụ nổi phía trên bàn phím.")
        if (cand.isEmpty()) {
            list.addView(TextView(a).apply {
                text = "Tất cả nút đã nằm trong nhóm Công cụ."; textSize = 13f; setTextColor(col(a, R.color.muted)); setPadding(0, dp(8), 0, dp(8))
            })
            dlg.show()
            return
        }
        for (area in listOf(ScanLayout.AREA_TOP, ScanLayout.AREA_BOTTOM)) {
            val keys = cand.filter { lay.areaOf(it) == area }
            if (keys.isEmpty()) continue
            val header = groupHeader(a, "ĐANG Ở " + ScanLayout.AREA_NAMES[area].uppercase())
            list.addView(header)
            val rows = ArrayList<View>()
            for (k in keys) {
                lateinit var row: LinearLayout
                val add = actionButton(a, "+ THÊM", true) {
                    onPick(k)
                    list.removeView(row)
                    rows.remove(row)
                    if (rows.isEmpty()) list.removeView(header)
                    if (list.childCount == 0) dlg.dismiss()
                }
                row = richRow(a, lay, k, st, null, add)
                rows.add(row)
                list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            }
        }
        dlg.show()
    }

    /** Chọn khu vực để chuyển một nút tới (hàng trên, hàng dưới, nhóm Công cụ), mỗi khu vực có mô tả. */
    fun showMoveTo(a: Activity, key: String, lay: ScanLayout, st: PanelStyle, onPick: (Int) -> Unit) {
        val d = a.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val (dlg, list) = frame(a, "Chuyển nút tới", "Chọn nơi muốn đặt nút này.")
        list.addView(richRow(a, lay, key, st, "Đang ở: " + ScanLayout.AREA_NAMES[lay.areaOf(key)], null))
        val info = listOf(
            "Hiện ở mọi chế độ: Quét, Gõ phím, Clipboard.",
            "Nằm dưới khung camera ở bảng Quét.",
            "Bảng nổi phía trên bàn phím, mở khi bấm nút Công cụ."
        )
        val cur = lay.areaOf(key)
        for (area in 0..2) {
            val blocked = area == cur || (area == ScanLayout.AREA_TOOLS && key == "tools")
            val row = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat(); setColor(col(a, R.color.surface2)); setStroke(dp(1), col(a, R.color.stroke))
                }
                alpha = if (blocked) 0.4f else 1f
                isClickable = !blocked
                if (!blocked) setOnClickListener { onPick(area); dlg.dismiss() }
            }
            row.addView(TextView(a).apply {
                text = ScanLayout.AREA_NAMES[area] + when {
                    area == cur -> "  (đang ở đây)"
                    blocked -> "  (không áp dụng cho nút này)"
                    else -> ""
                }
                textSize = 14f; setTextColor(col(a, R.color.text)); setTypeface(null, Typeface.BOLD)
            })
            row.addView(TextView(a).apply { text = info[area]; textSize = 11f; setTextColor(col(a, R.color.muted)) })
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
        dlg.show()
    }
}
