package com.inventory.scannerkeyboard

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Hướng dẫn nhanh có hình minh họa. Mọi hình đều được dựng từ chính các thành phần thật của bàn phím
 * (nút, bàn phím, phím Space, thẻ Clipboard) theo giao diện và bố cục hiện tại của người dùng, nên luôn đồng bộ
 * với giao diện thực tế. Mỗi hình nằm trong cùng một khung kích thước cố định để các mục đều và cân đối.
 */
object GuidePanel {
    fun build(a: Activity, host: LinearLayout) = GuideBuilder(a, host).build()
}

/** Khung ngắm quét mã minh họa. */
private class ScanMock(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.color = 0xFF0B0F17.toInt()
        c.drawRoundRect(0f, 0f, w, h, 12f * d, 12f * d, p)
        // mã vạch giả
        p.color = 0x66FFFFFF
        val bars = intArrayOf(3, 1, 2, 1, 4, 1, 1, 3, 2, 1, 3, 1, 2, 4, 1, 2, 1, 3, 2, 1)
        var x = w * 0.22f
        for (b in bars) {
            if (x > w * 0.78f) break
            c.drawRect(x, h * 0.3f, x + b * d, h * 0.7f, p)
            x += (b + 2) * d
        }
        // bốn góc khung ngắm
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f * d
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0xFFEE4D2D.toInt()
        val l = w * 0.14f
        val t = h * 0.16f
        val r = w * 0.86f
        val b = h * 0.84f
        val s = min(14f * d, h * 0.2f)
        c.drawLine(l, t, l + s, t, p); c.drawLine(l, t, l, t + s, p)
        c.drawLine(r, t, r - s, t, p); c.drawLine(r, t, r, t + s, p)
        c.drawLine(l, b, l + s, b, p); c.drawLine(l, b, l, b - s, p)
        c.drawLine(r, b, r - s, b, p); c.drawLine(r, b, r, b - s, p)
        // tia quét
        p.strokeWidth = 2f * d
        p.color = 0xCCFF5A5A.toInt()
        c.drawLine(l + 4f * d, h / 2f, r - 4f * d, h / 2f, p)
    }
}

/** Các vạch thưa dần thành dày dần: minh họa "giữ xóa càng lâu càng nhanh". */
private class SpeedBars(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density
    override fun onDraw(c: Canvas) {
        var x = 4f * d
        var gap = 24f * d
        var i = 0
        val h = height.toFloat()
        p.color = 0xFFEE4D2D.toInt()
        while (x < width - 4f * d) {
            p.alpha = (90 + i * 14).coerceAtMost(255)
            c.drawRoundRect(x, h * 0.2f, x + 3f * d, h * 0.8f, 2f * d, 2f * d, p)
            x += gap
            gap = (gap * 0.8f).coerceAtLeast(5f * d)
            i++
        }
    }
}

private class GuideBuilder(private val a: Activity, private val host: LinearLayout) {
    private val d = a.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()
    private fun col(id: Int) = ContextCompat.getColor(a, id)
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    private val lay = ScanLayout.fromJson(Prefs(a).scanLayoutJson)
    private val theme = ThemeStore.load(a).current()
    private val scanSt = ScanButtons.themeScanStyle(a)
    private val kbSt = theme?.kb ?: PanelStyle()
    private val space = theme?.space ?: SpaceStyle()

    private val shortNames = mapOf(
        "tools" to "Công cụ", "scan" to "Quét", "type" to "Gõ phím", "clip" to "Clipboard", "settings" to "Cài đặt",
        "switch" to "Đổi phím", "hide" to "Ẩn", "cam" to "Camera", "flash" to "Flash", "keepOn" to "Luôn sáng",
        "dim" to "Giảm sáng", "del" to "Xóa", "enter" to "Enter", "autoEnter" to "Tự Enter", "sound" to "Âm thanh",
        "soundPick" to "Chọn âm"
    )

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(a).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    // ------------------------------------------------------------------ thành phần dùng chung
    /** Nút thật (đúng giao diện hiện tại) kèm nhãn nhỏ phía dưới. */
    private fun labeled(key: String, size: Int, cellW: Int, label: String? = null, selected: Boolean = false, st: PanelStyle = scanSt, l: ScanLayout = lay): LinearLayout {
        val cell = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val t = ScanButtons.tile(a, l, key, size, st)
        t.isSelected = selected
        cell.addView(t, lp(dp(size), dp(size)))
        cell.addView(text(label ?: shortNames[key] ?: key, 8.5f, col(R.color.muted)).apply { gravity = Gravity.CENTER; maxLines = 2 },
            lp(dp(cellW), WRAP).apply { topMargin = dp(3) })
        return cell
    }

    private fun rowOf(keys: List<String>, size: Int, selected: String? = null): LinearLayout {
        val shown = keys.take(9)
        val cellW = min(size + 8, 268 / shown.size.coerceAtLeast(1))
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        for (k in shown) row.addView(labeled(k, size.coerceAtMost(cellW - 4), cellW, selected = k == selected), lp(dp(cellW), WRAP))
        return row
    }

    private fun pill(t: String, accent: Boolean = true) = TextView(a).apply {
        text = t; textSize = 10f; gravity = Gravity.CENTER
        setTextColor(if (accent) col(R.color.accent) else col(R.color.muted)); setTypeface(null, Typeface.BOLD)
        setPadding(dp(10), dp(3), dp(10), dp(3))
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (accent) 0x22EE4D2D else 0x14FFFFFF)
            setStroke(dp(1), if (accent) 0x55EE4D2D else col(R.color.stroke))
        }
    }

    private fun field(t: String) = TextView(a).apply {
        text = t; textSize = 13f; setTextColor(col(R.color.text)); setSingleLine()
        setBackgroundResource(R.drawable.bg_input); setPadding(dp(12), dp(8), dp(12), dp(8))
    }

    private fun arrow(t: String = "▼") = text(t, 13f, col(R.color.accent), true).apply { gravity = Gravity.CENTER }

    private fun v(vararg children: View, gravity: Int = Gravity.CENTER_HORIZONTAL): LinearLayout {
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; this.gravity = gravity }
        for (c in children) box.addView(c)
        return box
    }

    private fun sampleBitmap(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF3A7BD5.toInt(), 0xFFFFB27A.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.color = 0xFFFFE08A.toInt()
        c.drawCircle(w * 0.75f, h * 0.3f, h * 0.14f, p)
        p.color = 0xFF2F3B52.toInt()
        val path = Path().apply {
            moveTo(0f, h.toFloat()); lineTo(w * 0.28f, h * 0.45f); lineTo(w * 0.5f, h * 0.75f)
            lineTo(w * 0.72f, h * 0.5f); lineTo(w.toFloat(), h.toFloat()); close()
        }
        c.drawPath(path, p)
        return b
    }

    // ------------------------------------------------------------------ khung nhóm / mục
    private fun group(n: Int, title: String, sub: String) {
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(a).apply {
            text = n.toString(); textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(col(R.color.accent)) }
        }, lp(dp(30), dp(30)))
        val t = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        t.addView(text(title, 14f, col(R.color.accent), true).apply { letterSpacing = 0.05f })
        t.addView(text(sub, 11f, col(R.color.muted)))
        row.addView(t, lp(0, WRAP, 1f).apply { marginStart = dp(10) })
        host.addView(row, lp(MATCH, WRAP).apply { topMargin = dp(22) })
    }

    /** Một mục hướng dẫn: hình minh họa trong khung cố định + tiêu đề + mô tả + mẹo. */
    private fun item(title: String, desc: String, tip: String?, illus: View, fill: Boolean = false) {
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(col(R.color.surface)); setStroke(dp(1), col(R.color.stroke)) }
            setPadding(dp(12), dp(12), dp(12), dp(14))
        }
        val stage = FrameLayout(a).apply {
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(col(R.color.bg)); setStroke(dp(1), col(R.color.stroke)) }
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        stage.addView(illus, FrameLayout.LayoutParams(if (fill) MATCH else WRAP, WRAP, Gravity.CENTER))
        box.addView(stage, lp(MATCH, dp(172)))
        box.addView(text(title, 15f, col(R.color.text), true), lp(MATCH, WRAP).apply { topMargin = dp(12) })
        box.addView(text(desc, 13f, col(R.color.text)).apply { setLineSpacing(dp(3).toFloat(), 1f); alpha = 0.88f },
            lp(MATCH, WRAP).apply { topMargin = dp(6) })
        if (tip != null) box.addView(pill("MẸO: $tip").apply { gravity = Gravity.START },
            lp(WRAP, WRAP).apply { topMargin = dp(10) })
        host.addView(box, lp(MATCH, WRAP).apply { topMargin = dp(10) })
    }

    // ------------------------------------------------------------------ nội dung
    fun build() {
        host.addView(text(
            "Hình minh họa được dựng từ chính các nút và bàn phím thật theo giao diện hiện tại của bạn. " +
                "Mục lục: 1 Làm quen · 2 Quét mã · 3 Công cụ · 4 Gõ phím · 5 Clipboard · 6 Tùy chỉnh và chia sẻ.",
            12f, col(R.color.muted)
        ).apply { setLineSpacing(dp(2).toFloat(), 1f) }, lp(MATCH, WRAP).apply { topMargin = dp(10) })

        // ===== 1. LÀM QUEN =====
        group(1, "LÀM QUEN", "Ba chế độ và hàng nút phía trên")
        item(
            "Ba chế độ: Quét · Gõ phím · Clipboard",
            "• QUÉT: camera đọc mã QR / mã vạch, mã tự nhập vào ô đang chọn.\n" +
                "• GÕ PHÍM: bàn phím đầy đủ, gõ tiếng Việt kiểu Telex (phím VN / EN).\n" +
                "• CLIPBOARD: xem lại chữ và ảnh đã sao chép để dán lại.",
            null,
            LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                addView(labeled("scan", 54, 84, "QUÉT", true), lp(dp(84), WRAP))
                addView(labeled("type", 54, 84, "GÕ PHÍM"), lp(dp(84), WRAP))
                addView(labeled("clip", 54, 84, "CLIPBOARD"), lp(dp(84), WRAP))
            }
        )
        item(
            "Hàng nút phía trên (luôn hiện)",
            "• Mặc định: Công cụ · Quét · Gõ phím · Clipboard · Cài đặt · Đổi bàn phím · Ẩn bàn phím.\n" +
                "• ĐỔI BÀN PHÍM là một nút chung, dùng được ở mọi bảng.\n" +
                "• Vị trí, kích thước, bo góc và hình ảnh từng nút đổi được trong Tùy chỉnh nút Scan.",
            "Giữ lâu một nút để xem hướng dẫn chức năng của nút đó.",
            rowOf(lay.top, 38, "scan")
        )

        // ===== 2. QUÉT MÃ =====
        group(2, "QUÉT MÃ", "Đưa mã vào khung, mã tự nhập vào ô")
        item(
            "Quét mã vào ô nhập",
            "• Đưa mã vào khung: ứng dụng đọc ngay, tự nhập vào ô đang chọn và tự gửi Enter.\n" +
                "• Mỗi mã chỉ nhận một lần trong thời gian chống trùng để tránh nhập lặp.\n" +
                "• Độ nhạy, độ trễ sau khi quét chỉnh trong màn Cài đặt.",
            null,
            v(
                ScanMock(a).also { it.layoutParams = lp(dp(210), dp(80)) },
                arrow(),
                field("SPX0123456789").also { it.layoutParams = lp(dp(210), WRAP) }
            )
        )
        item(
            "Hàng nút dưới (bảng Quét)",
            "• Camera: bật / tắt.   • Flash: đèn pin khi thiếu sáng.\n" +
                "• Luôn sáng: giữ màn hình không tắt.   • Giảm sáng: hạ độ sáng.\n" +
                "• Xóa: giữ để xóa nhanh dần.   • Enter: gửi phím Enter thủ công.",
            "Tắt camera khi không quét để tiết kiệm pin.",
            v(
                ScanMock(a).also { it.layoutParams = lp(dp(250), dp(56)) },
                rowOf(lay.bottom, 40).also { it.layoutParams = lp(WRAP, WRAP).apply { topMargin = dp(8) } }
            )
        )
        item(
            "Kéo để đổi chiều cao",
            "• Chế độ Quét: kéo thanh nhỏ trên cùng của bàn phím để đổi chiều cao khung camera.\n" +
                "• Chế độ Gõ phím: kéo để đổi độ cao các hàng phím cho vừa tay.",
            null,
            v(
                View(a).apply {
                    background = GradientDrawable().apply { cornerRadius = dp(3).toFloat(); setColor(0x99FFFFFF.toInt()) }
                    layoutParams = lp(dp(56), dp(6))
                },
                text("▲\n▼", 12f, col(R.color.accent), true).apply { gravity = Gravity.CENTER },
                ScanMock(a).also { it.layoutParams = lp(dp(210), dp(66)) }
            )
        )

        // ===== 3. CÔNG CỤ =====
        group(3, "CÔNG CỤ", "Bảng nổi phía trên bàn phím")
        item(
            "Bảng Công cụ nổi",
            "• Chạm nút Công cụ để mở bảng nổi ngay phía trên bàn phím. Bảng nằm ngoài khung bàn phím nên không làm bàn phím cao thêm.\n" +
                "• Dùng xong một công cụ, bảng tự thu lại.\n" +
                "• Mặc định: Tự Enter, Bật / tắt âm thanh phím, Chọn nhanh âm thanh. Thêm bớt nút trong Tùy chỉnh nút Scan.",
            null,
            v(
                LinearLayout(a).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                    setBackgroundResource(R.drawable.bg_flyout); setPadding(dp(8), dp(6), dp(8), dp(4))
                    for (k in lay.tools.take(6)) addView(labeled(k, 36, 52), lp(dp(52), WRAP))
                },
                arrow(),
                rowOf(lay.top, 32, "tools")
            )
        )
        item(
            "Chọn nhanh âm thanh",
            "• Chạm Chọn âm thanh để hiện dải số 1 - 8: mỗi số là một kiểu âm thanh, chạm để chọn và nghe thử ngay.\n" +
                "• Chạm ‹ để quay lại các công cụ.\n" +
                "• Nút Âm thanh bật / tắt tiếng gõ phím.",
            null,
            LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_flyout); setPadding(dp(6), dp(6), dp(6), dp(6))
                val labels = listOf("‹", "1", "2", "3", "4", "5", "6", "7", "8")
                labels.forEachIndexed { i, s ->
                    addView(TextView(a).apply {
                        text = s; textSize = 13f; gravity = Gravity.CENTER; setTextColor(col(R.color.text)); setTypeface(null, Typeface.BOLD)
                        setBackgroundResource(R.drawable.bg_toggle); isSelected = i == 3
                    }, lp(dp(26), dp(32)).apply { setMargins(dp(1), 0, dp(1), 0) })
                }
            }
        )

        // ===== 4. GÕ PHÍM =====
        group(4, "GÕ PHÍM", "Bàn phím, phím Space và phím Xóa")
        item(
            "Bàn phím đầy đủ",
            "• VN / EN: đổi giữa tiếng Việt (Telex) và tiếng Anh.\n" +
                "• ?123 / ABC: đổi giữa chữ và ký hiệu.\n" +
                "• Phím Enter màu nổi: xuống dòng hoặc thực hiện.",
            null,
            KbSimView(a).also {
                it.showType(kbSt, space, KbMetrics.rowHeightPx(a, Prefs(a), kbSt))
                it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(154))
            },
            fill = true
        )
        item(
            "Phím Space: chạm và vuốt",
            "• Chạm: gõ dấu cách.\n" +
                "• Đặt ngón tay lên phím Space rồi vuốt trái / phải: con trỏ dịch từng ký tự, kèm rung nhẹ.\n" +
                "• Độ nhạy, giao diện riêng và ảnh cho phím Space chỉnh trong Tùy chỉnh giao diện › Space.",
            null,
            v(
                field("SPX-04│12").also { it.layoutParams = lp(dp(190), WRAP) },
                FrameLayout(a).apply {
                    val key = TextView(a).apply {
                        gravity = Gravity.CENTER
                        background = SpaceRender.background(a, kbSt, space)
                        SpaceRender.styleLabel(this, kbSt, space, true)
                    }
                    addView(key, FrameLayout.LayoutParams(dp(190), dp(44)))
                    addView(text("‹ ‹   vuốt   › ›", 12f, col(R.color.muted), true), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
                    layoutParams = lp(dp(190), dp(44)).apply { topMargin = dp(12) }
                }
            )
        )
        item(
            "Phím Xóa: giữ để xóa nhanh dần",
            "• Chạm: xóa từng ký tự, kèm rung nhẹ.\n" +
                "• Giữ: xóa liên tục và nhanh dần theo thời gian giữ.\n" +
                "• Nút Xóa ở hàng dưới bảng Quét hoạt động giống hệt.",
            null,
            LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(labeled("del", 52, 60, "Xóa"), lp(dp(60), WRAP))
                addView(v(
                    SpeedBars(a).also { it.layoutParams = lp(dp(180), dp(44)) },
                    LinearLayout(a).apply {
                        orientation = LinearLayout.HORIZONTAL
                        addView(text("chậm", 10f, col(R.color.muted)), lp(0, WRAP, 1f))
                        addView(text("nhanh", 10f, col(R.color.accent), true))
                        layoutParams = lp(dp(180), WRAP)
                    }
                ), lp(WRAP, WRAP).apply { marginStart = dp(12) })
            }
        )

        // ===== 5. CLIPBOARD =====
        group(5, "CLIPBOARD", "Lưu, xem và dán lại nội dung đã chép")
        fun clipCard(content: View, pinned: Boolean): LinearLayout {
            val card = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(6))
                background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(col(R.color.surface2)); setStroke(dp(1), col(R.color.stroke)) }
            }
            card.addView(content)
            val foot = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
            for (ic in listOf(R.drawable.ic_eye, R.drawable.ic_pin)) foot.addView(ImageView(a).apply {
                setImageResource(ic); imageTintList = android.content.res.ColorStateList.valueOf(if (pinned && ic == R.drawable.ic_pin) col(R.color.accent) else col(R.color.muted))
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }, lp(dp(24), dp(24)))
            card.addView(foot, lp(MATCH, WRAP).apply { topMargin = dp(4) })
            return card
        }
        item(
            "Lịch sử Clipboard",
            "• Chữ và ảnh vừa sao chép tự được lưu lại. Chạm một thẻ để dán ngay vào ô đang nhập.\n" +
                "• Ghim để giữ lại (thẻ ghim có màu pastel, bấm biểu tượng bảng màu để đổi màu).\n" +
                "• Giữ lâu một thẻ hoặc bấm dấu tích kép để chọn nhiều rồi Ghim / Xóa / Gửi tất cả (đúng thứ tự đã chọn).\n" +
                "• Biểu tượng kính lúp để tìm kiếm.",
            null,
            LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                addView(clipCard(v(text("Mã đơn SPX0412", 12f, col(R.color.text), true), text("Giao trước 17h, gọi người nhận…", 11f, col(R.color.muted)).apply { maxLines = 3 }), true),
                    lp(dp(128), WRAP).apply { setMargins(dp(4), 0, dp(4), 0) })
                addView(clipCard(ImageView(a).apply { setImageBitmap(sampleBitmap(240, 120)); scaleType = ImageView.ScaleType.CENTER_CROP }.also { it.layoutParams = LinearLayout.LayoutParams(dp(112), dp(58)) }, false),
                    lp(dp(128), WRAP).apply { setMargins(dp(4), 0, dp(4), 0) })
            }
        )
        item(
            "Xem đầy đủ bằng nút con mắt",
            "• Chạm biểu tượng con mắt trên thẻ để mở bảng xem nổi giữa màn hình.\n" +
                "• Văn bản dài cuộn được để đọc hết.\n" +
                "• Nút Dán, Sao chép, Ghim, Xóa nằm ngay trong bảng (Xóa cần chạm hai lần để tránh nhầm).",
            null,
            LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8))
                background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(col(R.color.surface)); setStroke(dp(1), col(R.color.stroke)) }
                addView(LinearLayout(a).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    addView(text("Xem văn bản", 12f, col(R.color.accent), true), lp(0, WRAP, 1f))
                    addView(text("✕", 12f, col(R.color.muted), true))
                })
                addView(text("Mã đơn SPX0412 · Giao trước 17h.\nGọi người nhận trước khi giao…", 11f, col(R.color.text)).apply {
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(col(R.color.surface2)) }
                }, lp(MATCH, WRAP).apply { topMargin = dp(4) })
                addView(LinearLayout(a).apply {
                    orientation = LinearLayout.HORIZONTAL
                    for ((i, n) in listOf("Dán", "Sao chép", "Ghim", "Xóa").withIndex()) addView(TextView(a).apply {
                        text = n; textSize = 10f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); setSingleLine()
                        setBackgroundResource(if (i == 0) R.drawable.bg_btn_primary else R.drawable.bg_btn_secondary)
                    }, lp(0, dp(30), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
                }, lp(MATCH, WRAP).apply { topMargin = dp(8) })
                layoutParams = lp(dp(240), WRAP)
            }
        )
        item(
            "Xem ảnh và phóng to",
            "• Hai ngón để phóng to / thu nhỏ, một ngón để kéo ảnh.\n" +
                "• Chạm đúp để zoom nhanh, chạm đúp lần nữa để về vừa khung.\n" +
                "• Hoặc dùng các nút −, Vừa khung, + dưới ảnh.",
            null,
            LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8))
                background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(col(R.color.surface)); setStroke(dp(1), col(R.color.stroke)) }
                addView(text("Xem hình ảnh", 12f, col(R.color.accent), true))
                addView(ImageView(a).apply { setImageBitmap(sampleBitmap(360, 160)); scaleType = ImageView.ScaleType.CENTER_CROP },
                    lp(MATCH, dp(76)).apply { topMargin = dp(4) })
                addView(LinearLayout(a).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                    for (n in listOf("−", "Vừa khung", "+")) addView(TextView(a).apply {
                        text = n; textSize = 11f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
                        setBackgroundResource(R.drawable.bg_btn_secondary)
                    }, lp(if (n.length > 1) dp(84) else dp(40), dp(30)).apply { setMargins(dp(3), 0, dp(3), 0) })
                }, lp(MATCH, WRAP).apply { topMargin = dp(8) })
                layoutParams = lp(dp(240), WRAP)
            }
        )

        // ===== 6. TÙY CHỈNH & CHIA SẺ =====
        group(6, "TÙY CHỈNH VÀ CHIA SẺ", "Giao diện, nút Scan, xuất / nhập")
        item(
            "Giao diện bàn phím",
            "• Bảng Scan và Bảng Bàn phím tùy chỉnh độc lập: mỗi bảng có các tab Phím, Nền, Chữ, Chạm, Sáng, Động, Bố cục (Space chỉ có ở Bàn phím).\n" +
                "• Lưu được nhiều giao diện và đổi qua lại.\n" +
                "• Thanh trượt nào cũng có nút − / + và rung nhẹ khi đổi giá trị.",
            null,
            LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                val tabs = listOf(
                    "Phím" to R.drawable.ic_keyboard, "Nền" to R.drawable.ic_palette, "Chữ" to R.drawable.ic_tune, "Chạm" to R.drawable.ic_sparkle,
                    "Sáng" to R.drawable.ic_sun, "Động" to R.drawable.ic_motion, "Bố cục" to R.drawable.ic_layout, "Space" to R.drawable.ic_space
                )
                tabs.forEachIndexed { i, (n, ic) ->
                    addView(v(
                        ImageView(a).apply {
                            setImageResource(ic); setPadding(dp(8), dp(8), dp(8), dp(8))
                            imageTintList = ContextCompat.getColorStateList(a, R.color.icon_tint)
                            setBackgroundResource(R.drawable.bg_toggle); isSelected = i == 0
                            layoutParams = lp(dp(30), dp(30))
                        },
                        text(n, 8f, col(R.color.muted)).apply { gravity = Gravity.CENTER; setSingleLine(); layoutParams = lp(dp(33), WRAP).apply { topMargin = dp(3) } }
                    ), lp(dp(33), WRAP))
                }
            }
        )
        item(
            "Tùy chỉnh nút Scan",
            "• Ba khu vực: Hàng trên, Hàng dưới và Công cụ. Kéo ≡ để đổi thứ tự, bấm ⇄ để chuyển nút sang khu vực khác.\n" +
                "• Hàng trên, Hàng dưới, Nhóm Công cụ có bộ thông số riêng, độc lập nhau: số nút mỗi hàng (tối đa 10), kích thước, bo góc, khoảng cách, căn lề, vị trí.\n" +
                "• Khung xem trước là bản mô phỏng nhẹ, ghim cố định phía trên và cập nhật ngay khi chỉnh (hiệu ứng ánh sáng, chuyển động chỉ chạy trên bàn phím thật).\n" +
                "• Mọi thay đổi tự lưu và áp dụng ngay cho bàn phím.\n" +
                "• Chạm một nút ở mục Chỉnh sửa nút để thêm hình ảnh PNG riêng cho nút đó.",
            null,
            v(
                text("HÀNG DƯỚI", 9f, col(R.color.accent), true).apply { layoutParams = lp(dp(250), WRAP) },
                ButtonPicker.richRow(a, lay, "cam", scanSt, null, text("▲ ▼ ⇄", 11f, col(R.color.muted), true))
                    .also { it.layoutParams = lp(dp(250), WRAP).apply { topMargin = dp(4) } },
                ButtonPicker.richRow(a, lay, "del", scanSt, null, text("▲ ▼ ⇄", 11f, col(R.color.muted), true))
                    .also { it.layoutParams = lp(dp(250), WRAP).apply { topMargin = dp(6) } }
            )
        )
        item(
            "Bo góc nút đồng bộ",
            "• Mỗi nhóm có thanh Bo góc nút riêng; đặt cùng giá trị cho các nhóm để giao diện đồng bộ.\n" +
                "• Kéo về 0 để nút vuông góc, tăng lên để bo tròn.\n" +
                "• Mức \"theo giao diện\" giữ bo góc của giao diện đang dùng.",
            null,
            LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
                for (c in listOf(0, 8, 16, 30)) addView(labeled("cam", 48, 62, "$c dp", l = lay.withCorner(c)), lp(dp(62), WRAP))
            }
        )
        item(
            "Xuất / Nhập giao diện và cài đặt",
            "• Giao diện và Cài đặt được xuất / nhập riêng.\n" +
                "• File .spx kèm cả ảnh; mã QR gọn nhẹ, tự chia nhiều mã nếu dữ liệu dài.\n" +
                "• Trên máy nhận chọn QUÉT MÃ QR hoặc NHẬP TỪ FILE.",
            "Vào Cài đặt › Xuất / Nhập để bắt đầu.",
            LinearLayout(a).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                val qr = Transfer.qrBitmap("SPX Keyboard", 240)
                addView(ImageView(a).apply { setImageBitmap(qr); setBackgroundColor(Color.WHITE); setPadding(dp(4), dp(4), dp(4), dp(4)) }, lp(dp(110), dp(110)))
                addView(v(
                    pill("MÃ QR"), text("hoặc", 10f, col(R.color.muted)).apply { gravity = Gravity.CENTER }, pill("FILE .spx")
                ), lp(WRAP, WRAP).apply { marginStart = dp(16) })
            }
        )
        Fonts.apply(host)
    }
}
