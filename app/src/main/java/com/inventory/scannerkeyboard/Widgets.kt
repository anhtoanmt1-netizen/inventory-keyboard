package com.inventory.scannerkeyboard

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Tiện ích cho các thanh điều chỉnh trong màn hình tùy chỉnh:
 *  - rung nhẹ mỗi khi giá trị thay đổi (theo công tắc và cường độ "rung khi chạm / gõ phím");
 *  - hai nút (−) và (+) ở hai đầu thanh, bấm để tăng / giảm từng đơn vị, giữ để lặp lại.
 */
object SeekAssist {

    private var lastTick = 0L

    /** Rung nhẹ khi giá trị thay đổi (cách nhau tối thiểu 25 ms để kéo nhanh không bị rung dồn). */
    fun tick(ctx: Context) {
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 25L) return
        lastTick = now
        Haptics.tickIfEnabled(ctx)
    }

    /**
     * Nút bấm − / +. [onStep] trả về true nếu giá trị còn đổi được; trả về false (đã chạm đầu mút)
     * thì dừng lặp. Chạm một lần = một đơn vị; giữ = lặp lại, nhanh dần.
     */
    fun stepButton(ctx: Context, label: String, onStep: () -> Boolean): TextView {
        val handler = Handler(Looper.getMainLooper())
        var count = 0
        val repeat = object : Runnable {
            override fun run() {
                if (onStep()) {
                    count++
                    handler.postDelayed(this, if (count > 10) 30L else 75L)
                }
            }
        }
        val t = TextView(ctx)
        t.text = label
        t.textSize = 20f
        t.gravity = Gravity.CENTER
        t.setTextColor(ContextCompat.getColor(ctx, R.color.text))
        t.setTypeface(null, Typeface.BOLD)
        t.setBackgroundResource(R.drawable.bg_toggle)
        t.isClickable = true
        t.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    count = 0
                    onStep()
                    handler.postDelayed(repeat, 420L)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeat)
                }
            }
            true
        }
        return t
    }

    /**
     * Bọc một SeekBar có sẵn (khai báo trong XML) thành hàng [−] thanh [+].
     * [after] được gọi sau mỗi lần bấm − / + (ví dụ để nghe thử âm thanh).
     */
    fun wrap(sb: SeekBar, after: (() -> Unit)? = null) {
        val parent = sb.parent as? ViewGroup ?: return
        val ctx = sb.context
        val d = ctx.resources.displayMetrics.density
        val idx = parent.indexOfChild(sb)
        val lp = sb.layoutParams
        parent.removeView(sb)

        fun nudge(delta: Int): Boolean {
            val n = (sb.progress + delta).coerceIn(0, sb.max)
            if (n == sb.progress) return false
            sb.progress = n
            tick(ctx)
            after?.invoke()
            return true
        }

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val size = (38 * d).toInt()
        row.addView(stepButton(ctx, "−") { nudge(-1) }, LinearLayout.LayoutParams(size, size))
        row.addView(sb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(stepButton(ctx, "+") { nudge(1) }, LinearLayout.LayoutParams(size, size))
        parent.addView(row, idx, lp)
    }
}

/**
 * Hàng thanh điều chỉnh dựng bằng code: [−] thanh [+], rung nhẹ mỗi khi giá trị đổi.
 * [onValue] được gọi khi người dùng kéo thanh hoặc bấm − / +.
 */
class SeekRow(
    private val ctx: Context,
    val min: Int,
    val max: Int,
    start: Int,
    private val onValue: (Int) -> Unit
) {
    val seek = SeekBar(ctx)
    val view = LinearLayout(ctx)

    var value: Int = start.coerceIn(min, max)
        private set

    init {
        val d = ctx.resources.displayMetrics.density
        seek.max = max - min
        seek.progress = value - min
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                val v = p + min
                if (v != value) {
                    value = v
                    SeekAssist.tick(ctx)
                    onValue(v)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        view.orientation = LinearLayout.HORIZONTAL
        view.gravity = Gravity.CENTER_VERTICAL
        val size = (38 * d).toInt()
        view.addView(SeekAssist.stepButton(ctx, "−") { nudge(-1) }, LinearLayout.LayoutParams(size, size))
        view.addView(seek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        view.addView(SeekAssist.stepButton(ctx, "+") { nudge(1) }, LinearLayout.LayoutParams(size, size))
    }

    /** Đặt giá trị từ code (không gọi onValue, không rung). */
    fun set(v: Int) {
        value = v.coerceIn(min, max)
        seek.progress = value - min
    }

    private fun nudge(delta: Int): Boolean {
        val n = (value + delta).coerceIn(min, max)
        if (n == value) return false
        value = n
        seek.progress = n - min
        SeekAssist.tick(ctx)
        onValue(n)
        return true
    }
}


/**
 * ScrollView ổn định cho màn hình tùy chỉnh: không tự cuộn / nhảy lên khi nội dung được dựng lại hoặc khi một ô
 * (thanh trượt, nút) nhận tiêu điểm. Chỉ cuộn theo thao tác của người dùng (và khi đang gõ vào ô nhập).
 */
class StableScrollView(ctx: android.content.Context) : ScrollView(ctx) {
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int =
        if (findFocus() is EditText) super.computeScrollDeltaToGetChildRectOnScreen(rect) else 0

    /**
     * Dựng lại nội dung bên trong [block] mà vẫn giữ nguyên vị trí cuộn: khôi phục ngay trước khi vẽ khung hình kế tiếp
     * nên không bị nhảy.
     */
    fun keepScroll(block: () -> Unit) {
        val y = scrollY
        block()
        viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                viewTreeObserver.removeOnPreDrawListener(this)
                if (scrollY != y) scrollTo(0, y)
                return true
            }
        })
    }
}
