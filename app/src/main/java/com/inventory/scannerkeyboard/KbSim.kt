package com.inventory.scannerkeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.min

/**
 * Khung xem trước MÔ PHỎNG, nhẹ cho các màn hình tùy chỉnh. Chỉ là MỘT View duy nhất vẽ trực tiếp bằng Canvas theo đúng
 * giá trị đang chỉnh (màu, bo góc, viền, khoảng cách, số nút mỗi hàng, căn lề, vị trí, phím Space...), không dựng layout thật,
 * không chạy hiệu ứng động. Kích thước khung CỐ ĐỊNH (do nơi dùng đặt một lần) nên đổi giá trị chỉ làm vẽ lại, không bao giờ
 * làm khung nhảy, đổi cỡ hay đẩy phần điều chỉnh bên dưới. Hiệu ứng ánh sáng / chuyển động / chạm chỉ chạy trên bàn phím thật.
 */
class KbSimView(ctx: Context) : View(ctx) {

    private val d = ctx.resources.displayMetrics.density
    private val realW = ctx.resources.displayMetrics.widthPixels.toFloat()   // bề rộng bàn phím thật (px)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val icons = HashMap<Int, Drawable?>()
    private val hits = ArrayList<Pair<String, RectF>>()                     // vùng chạm theo toạ độ của View

    private var mode = MODE_SCAN
    private var lay = ScanLayout()
    private var scanSt = PanelStyle()
    private var kbSt = PanelStyle()
    private var space = SpaceStyle()
    private var rowPx = 0
    private var selected: String? = null
    private var toolsOpen = false
    private var hitScale = 1f                                                // toạ độ thật -> toạ độ View (đặt trong onDraw)
    private var hitOffX = 0f

    /** Chạm một nút trong bảng mô phỏng Scan để chọn nút đó. */
    var onPick: ((String) -> Unit)? = null

    companion object {
        private const val MODE_SCAN = 0
        private const val MODE_TYPE = 1
        private const val SCALE_SCAN = 0.86f     // tỉ lệ cố định khi mô phỏng bảng Scan
        private const val SCALE_TYPE = 0.62f     // tỉ lệ cố định khi mô phỏng bảng Bàn phím (đủ chỗ cho hàng phím cao nhất)
    }

    private fun dp(v: Float) = v * d

    /** Mô phỏng bảng Scan: hàng trên, khung camera, hàng dưới và (nếu mở) bảng Công cụ nổi, theo bố cục [layout]. */
    fun showScan(layout: ScanLayout, st: PanelStyle, selectedKey: String?, withTools: Boolean) {
        mode = MODE_SCAN
        lay = layout
        scanSt = st
        selected = selectedKey
        toolsOpen = withTools
        invalidate()
    }

    /** Mô phỏng bảng Bàn phím với kiểu phím [st], phím Space riêng [sp] và chiều cao hàng phím [rowHeightPx]. */
    fun showType(st: PanelStyle, sp: SpaceStyle, rowHeightPx: Int) {
        mode = MODE_TYPE
        kbSt = st
        space = sp
        rowPx = rowHeightPx
        invalidate()
    }

    // ------------------------------------------------------------------ tiện ích vẽ
    private fun icon(res: Int): Drawable? = icons.getOrPut(res) { ContextCompat.getDrawable(context, res)?.mutate() }

    private fun drawIcon(c: Canvas, res: Int, cx: Float, cy: Float, size: Float, color: Int) {
        val dr = icon(res) ?: return
        dr.setTint(color)
        dr.setBounds((cx - size / 2f).toInt(), (cy - size / 2f).toInt(), (cx + size / 2f).toInt(), (cy + size / 2f).toInt())
        dr.draw(c)
    }

    private fun drawFace(c: Canvas, st: PanelStyle, kind: ThemeRender.Kind, l: Float, t: Float, r: Float, b: Float) {
        val f = ThemeRender.faceOnly(context, st, kind)
        f.setBounds(l.toInt(), t.toInt(), r.toInt(), b.toInt())
        f.draw(c)
    }

    private fun spPx(sp: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)

    private fun drawCentered(c: Canvas, text: String, cx: Float, cy: Float, sizePx: Float, color: Int, st: PanelStyle) {
        paint.style = Paint.Style.FILL
        paint.typeface = ThemeRender.typeface(context, st)
        paint.textSize = sizePx
        paint.color = color
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        c.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, paint)
    }

    /** Bề rộng, vị trí ngang và độ nâng đáy của một bảng: dùng đúng hàm bố cục của bàn phím thật. */
    private fun panelBox(st: PanelStyle): Triple<Float, Float, Float> {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ThemeRender.applyPanelLayout(lp, st, realW.toInt(), d)
        val w = if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT) realW else lp.width.toFloat()
        val x = when (lp.gravity) {
            Gravity.START -> 0f
            Gravity.END -> realW - w
            else -> (realW - w) / 2f
        }
        return Triple(w, x, lp.bottomMargin.toFloat())
    }

    private fun drawPanelBg(c: Canvas, st: PanelStyle, l: Float, t: Float, r: Float, b: Float) {
        if (st.bgType == 0) return
        val bg = ThemeBgDrawable(context, st)
        bg.setBounds(l.toInt(), t.toInt(), r.toInt(), b.toInt())
        bg.draw(c)
    }

    private fun drawHandle(c: Canvas, y: Float) {
        paint.style = Paint.Style.FILL
        paint.color = 0x66FFFFFF
        rect.set(realW / 2f - dp(18f), y + dp(11f), realW / 2f + dp(18f), y + dp(15f))
        c.drawRoundRect(rect, dp(2f), dp(2f), paint)
    }

    // ------------------------------------------------------------------ bảng Scan
    private class Placed(val key: String, val x: Float, val y: Float, val w: Float, val h: Float)

    /**
     * Xếp các nút của một khu vực theo bộ thông số RIÊNG của khu vực đó (cùng quy tắc với bộ dựng nút của bàn phím thật).
     * Trả về danh sách nút (toạ độ tương đối với góc trái-trên khu vực), chiều cao các hàng và bề rộng nội dung rộng nhất.
     */
    private fun layoutArea(area: Int, areaW: Float): Triple<List<Placed>, Float, Float> {
        val st = lay.styleOf(area)
        val tools = area == ScanLayout.AREA_TOOLS
        val grid = !tools && st.grid
        val align = if (tools) ScanLayout.ALIGN_CENTER else st.align
        val n = st.perRow.coerceIn(1, ScanLayout.MAX_PER_ROW)
        val vpad = dp(st.rowGapDp.toFloat()) / 2f
        val gap = dp(st.gapDp.toFloat()) / 2f
        val rowPad = dp(4f)
        val out = ArrayList<Placed>()
        val chunks = lay.listFor(area).chunked(n)
        // Với nhóm Công cụ (bảng tự co theo nội dung) cần biết bề rộng nội dung trước khi căn giữa từng hàng
        var maxContent = 0f
        val contents = ArrayList<Float>()
        for (chunk in chunks) {
            val ws = chunk.map { ScanButtons.sizePx(context, lay, it).first.toFloat() }
            val content = ws.sum() + chunk.size * 2f * gap
            contents.add(content)
            maxContent = max(maxContent, content)
        }
        val innerW = if (tools) maxContent else areaW - 2f * rowPad
        var y = 0f
        chunks.forEachIndexed { ri, chunk ->
            val sizes = chunk.map { ScanButtons.sizePx(context, lay, it) }
            val rowH = sizes.maxOf { it.second }.toFloat()
            val xs = FloatArray(chunk.size)
            if (grid) {
                val cell = innerW / n
                for (i in chunk.indices) {
                    val w = sizes[i].first.toFloat()
                    xs[i] = when (align) {
                        ScanLayout.ALIGN_LEFT -> cell * i
                        ScanLayout.ALIGN_RIGHT -> cell * i + cell - w
                        else -> cell * i + (cell - w) / 2f
                    }
                }
            } else {
                val total = sizes.sumOf { it.first }.toFloat()
                when (align) {
                    ScanLayout.ALIGN_BETWEEN -> {
                        val g = if (chunk.size > 1) (innerW - total) / (chunk.size - 1) else 0f
                        var x = if (chunk.size > 1) 0f else (innerW - total) / 2f
                        for (i in chunk.indices) { xs[i] = x; x += sizes[i].first + g }
                    }
                    ScanLayout.ALIGN_AROUND -> {
                        val unit = (innerW - total) / chunk.size
                        var x = unit / 2f
                        for (i in chunk.indices) { xs[i] = x; x += sizes[i].first + unit }
                    }
                    else -> {
                        val content = contents[ri]
                        var x = when (align) {
                            ScanLayout.ALIGN_LEFT -> 0f
                            ScanLayout.ALIGN_RIGHT -> innerW - content
                            else -> (innerW - content) / 2f
                        }
                        for (i in chunk.indices) { xs[i] = x + gap; x += sizes[i].first + 2f * gap }
                    }
                }
            }
            chunk.forEachIndexed { i, key ->
                val w = sizes[i].first.toFloat()
                val h = sizes[i].second.toFloat()
                out.add(Placed(key, rowPad + xs[i], y + vpad + (rowH - h) / 2f, w, h))
            }
            y += rowH + vpad * 2f
        }
        val width = if (tools) maxContent + 2f * rowPad else areaW
        return Triple(out, y, width)
    }

    private fun areaStyle(area: Int) = ScanButtons.btnStyle(scanSt, lay, area)

    private fun drawButtons(c: Canvas, area: Int, list: List<Placed>, ox: Float, oy: Float, hot: String?) {
        val st = areaStyle(area)
        for (p in list) {
            val l = ox + p.x
            val t = oy + p.y
            val kind = when {
                p.key == "tools" && toolsOpen -> ThemeRender.Kind.ACCENT
                ScanLayout.isToggle(p.key) -> ThemeRender.Kind.TOGGLE
                else -> ThemeRender.Kind.NORMAL
            }
            drawFace(c, st, kind, l, t, l + p.w, t + p.h)
            val img = lay.cfg(p.key).image?.let { ScanButtons.bitmapOf(context, it) }
            if (img != null) drawBitmapFit(c, img, l, t, l + p.w, t + p.h)
            else {
                val size = max(min(p.w, p.h) * 0.52f, dp(12f))
                val tint = if (kind == ThemeRender.Kind.ACCENT) 0xFFFFFFFF.toInt() else st.iconColor
                drawIcon(c, ScanLayout.ICONS[p.key] ?: R.drawable.ic_tune, l + p.w / 2f, t + p.h / 2f, size, tint)
            }
            if (p.key == hot) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(2f)
                paint.color = ContextCompat.getColor(context, R.color.accent)
                rect.set(l - dp(2f), t - dp(2f), l + p.w + dp(2f), t + p.h + dp(2f))
                val rad = dp(st.cornerDp.toFloat()) + dp(2f)
                c.drawRoundRect(rect, rad, rad, paint)
            }
            // vùng chạm theo toạ độ View (không tính bảng Công cụ: chỉ để chọn nút ở hàng trên / hàng dưới)
            if (area != ScanLayout.AREA_TOOLS) {
                hits.add(p.key to RectF(
                    hitOffX + l * hitScale, t * hitScale, hitOffX + (l + p.w) * hitScale, (t + p.h) * hitScale
                ))
            }
        }
    }

    private fun drawBitmapFit(c: Canvas, bmp: Bitmap, l: Float, t: Float, r: Float, b: Float) {
        val pad = dp(3f)
        val s = min((r - l - 2 * pad) / bmp.width, (b - t - 2 * pad) / bmp.height)
        val w = bmp.width * s
        val h = bmp.height * s
        rect.set((l + r) / 2f - w / 2f, (t + b) / 2f - h / 2f, (l + r) / 2f + w / 2f, (t + b) / 2f + h / 2f)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        c.drawBitmap(bmp, null, rect, paint)
    }

    private class Plan(
        val top: Triple<List<Placed>, Float, Float>,
        val bot: Triple<List<Placed>, Float, Float>,
        val tools: Triple<List<Placed>, Float, Float>,
        val panel: Triple<Float, Float, Float>
    )

    private fun planScan(): Plan = Plan(
        layoutArea(ScanLayout.AREA_TOP, realW),
        layoutArea(ScanLayout.AREA_BOTTOM, panelBox(scanSt).first),
        layoutArea(ScanLayout.AREA_TOOLS, realW),
        panelBox(scanSt)
    )

    private fun scanHeight(pl: Plan): Float {
        var h = 0f
        if (toolsOpen) h += pl.tools.second + dp(12f) + dp(lay.toolsStyle.padBottomDp.toFloat())
        h += dp(26f) + dp(lay.topStyle.padTopDp.toFloat()) + pl.top.second + dp(lay.topStyle.padBottomDp.toFloat())
        h += dp(34f) + dp(lay.bottomStyle.padTopDp.toFloat()) + pl.bot.second + dp(lay.bottomStyle.padBottomDp.toFloat())
        return h + pl.panel.third + dp(4f)
    }

    private fun drawScan(c: Canvas, pl: Plan) {
        val topX = dp(lay.topStyle.offsetXDp.toFloat())
        var y = if (toolsOpen) pl.tools.second + dp(12f) + dp(lay.toolsStyle.padBottomDp.toFloat()) else 0f
        drawHandle(c, y)
        y += dp(26f) + dp(lay.topStyle.padTopDp.toFloat())
        drawButtons(c, ScanLayout.AREA_TOP, pl.top.first, topX, y, selected)
        y += pl.top.second + dp(lay.topStyle.padBottomDp.toFloat())

        // Bảng Scan: nền riêng của bảng, khung camera, hàng dưới
        val px = pl.panel.second
        val pw = pl.panel.first
        val camH = dp(34f)
        val panelBottom = y + camH + dp(lay.bottomStyle.padTopDp.toFloat()) + pl.bot.second + dp(lay.bottomStyle.padBottomDp.toFloat())
        drawPanelBg(c, scanSt, px, y, px + pw, panelBottom)
        paint.style = Paint.Style.FILL
        paint.color = 0xFF0B0F17.toInt()
        rect.set(px + dp(6f), y, px + pw - dp(6f), y + camH)
        c.drawRoundRect(rect, dp(12f), dp(12f), paint)
        drawScanFrame(c, rect)
        val botX = px + dp(lay.bottomStyle.offsetXDp.toFloat())
        drawButtons(c, ScanLayout.AREA_BOTTOM, pl.bot.first, botX, y + camH + dp(lay.bottomStyle.padTopDp.toFloat()), selected)

        if (toolsOpen) {
            // Bảng Công cụ nổi phía trên bàn phím; vị trí ngang theo đúng hàm của bàn phím thật
            val t = pl.top.first.firstOrNull { it.key == "tools" }
            val b = pl.bot.first.firstOrNull { it.key == "tools" }
            val cx = when {
                t != null -> topX + t.x + t.w / 2f
                b != null -> botX + b.x + b.w / 2f
                else -> realW / 2f
            }
            val boxW = pl.tools.third + dp(12f)
            val bx = ScanButtons.toolsPanelX(lay.toolsStyle, d, cx.toInt(), boxW.toInt(), realW.toInt()).toFloat()
            icon(R.drawable.bg_flyout)?.let { fl ->
                fl.setBounds(bx.toInt(), 0, (bx + boxW).toInt(), (pl.tools.second + dp(12f)).toInt())
                fl.draw(c)
            }
            drawButtons(c, ScanLayout.AREA_TOOLS, pl.tools.first, bx + dp(6f), dp(6f), selected)
        }
    }

    private fun drawScanFrame(c: Canvas, r: RectF) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0xFFEE4D2D.toInt()
        val s = dp(7f)
        val l = r.left + r.width() * 0.34f
        val rr = r.right - r.width() * 0.34f
        val t = r.top + dp(6f)
        val b = r.bottom - dp(6f)
        c.drawLine(l, t, l + s, t, paint); c.drawLine(l, t, l, t + s, paint)
        c.drawLine(rr, t, rr - s, t, paint); c.drawLine(rr, t, rr, t + s, paint)
        c.drawLine(l, b, l + s, b, paint); c.drawLine(l, b, l, b - s, paint)
        c.drawLine(rr, b, rr - s, b, paint); c.drawLine(rr, b, rr, b - s, paint)
    }

    // ------------------------------------------------------------------ bảng Bàn phím
    private class KeySpec(val label: String, val weight: Float, val kind: ThemeRender.Kind, val iconRes: Int = 0, val isSpace: Boolean = false)

    private fun typeRows(): List<List<KeySpec>> {
        fun chars(s: String) = s.map { KeySpec(it.toString(), 1f, ThemeRender.Kind.NORMAL) }
        val r3 = ArrayList<KeySpec>()
        r3.add(KeySpec("", 0.5f, ThemeRender.Kind.NORMAL, -1))        // khoảng đệm đầu hàng (không vẽ)
        r3.addAll(chars("asdfghjkl"))
        r3.add(KeySpec("", 0.5f, ThemeRender.Kind.NORMAL, -1))
        val r4 = ArrayList<KeySpec>()
        r4.add(KeySpec("", 1.5f, ThemeRender.Kind.SPECIAL, R.drawable.ic_shift))
        r4.addAll(chars("zxcvbnm"))
        r4.add(KeySpec("", 1.5f, ThemeRender.Kind.SPECIAL, R.drawable.ic_backspace))
        val r5 = listOf(
            KeySpec("?123", 1.5f, ThemeRender.Kind.SPECIAL), KeySpec("VN", 1.2f, ThemeRender.Kind.SPECIAL),
            KeySpec(",", 1f, ThemeRender.Kind.NORMAL), KeySpec("", space.weight / 10f, ThemeRender.Kind.NORMAL, 0, true),
            KeySpec(".", 1f, ThemeRender.Kind.NORMAL), KeySpec("", 1.5f, ThemeRender.Kind.ACCENT, R.drawable.ic_enter)
        )
        return listOf(chars("1234567890"), chars("qwertyuiop"), r3, r4, r5)
    }

    private fun typeHeight(): Float = dp(26f) + 5f * rowPx + panelBox(kbSt).third + dp(4f)

    private fun drawType(c: Canvas) {
        val st = kbSt
        val (pw, px, _) = panelBox(st)
        val rows = typeRows()
        val top = dp(26f)
        drawHandle(c, 0f)
        drawPanelBg(c, st, px, top, px + pw, top + 5f * rowPx)
        val gap = dp(st.gapDp.toFloat())
        val pad = dp(st.gapDp * 2f / 3f)
        val inner = pw - dp(4f)                       // đệm 2dp hai bên như bàn phím thật
        val tint = st.iconColor
        rows.forEachIndexed { ri, keys ->
            val total = keys.sumOf { it.weight.toDouble() }.toFloat()
            var x = px + dp(2f)
            val kt = top + ri * rowPx + pad
            val kb = top + (ri + 1) * rowPx - pad
            for (k in keys) {
                val slot = inner * k.weight / total
                val l = x + gap
                val r = x + slot - gap
                x += slot
                if (k.iconRes == -1) continue                      // khoảng đệm
                if (k.isSpace) { drawSpace(c, st, l, kt, r, kb); continue }
                drawFace(c, st, k.kind, l, kt, r, kb)
                val cx = (l + r) / 2f
                val cy = (kt + kb) / 2f - dp(st.depthDp.toFloat()) / 2f
                if (k.iconRes != 0) drawIcon(c, k.iconRes, cx, cy, dp(24f), tint)
                else drawCentered(c, k.label, cx, cy, spPx((if (k.label.length > 1) 14f else 19f) * st.fontScale / 100f), st.textColor, st)
            }
        }
    }

    private fun drawSpace(c: Canvas, st: PanelStyle, l: Float, t: Float, r: Float, b: Float) {
        val sp = space
        val face = if (sp.custom) SpaceRender.derived(st, sp) else st
        drawFace(c, face, ThemeRender.Kind.NORMAL, l, t, r, b)
        if (!sp.custom) return
        sp.image?.let { name ->
            SpaceRender.bitmapOf(context, name)?.let { bmp ->
                val di = SpaceImageDrawable(bmp, sp, dp(sp.cornerDp.toFloat()))
                di.setBounds(l.toInt(), t.toInt(), r.toInt(), (b - dp(sp.depthDp.toFloat())).toInt())
                di.draw(c)
            }
        }
        val label = SpaceRender.labelFor(sp, true)
        if (label.isNotEmpty()) {
            val spec = st.copy(fontFamily = sp.fontFamily, fontWeight = sp.fontWeight)
            drawCentered(c, label, (l + r) / 2f, (t + b) / 2f - dp(sp.depthDp.toFloat()) / 2f, spPx(sp.labelSize * st.fontScale / 100f), sp.labelColor, spec)
        }
    }

    // ------------------------------------------------------------------ vẽ + chạm
    override fun onDraw(c: Canvas) {
        hits.clear()
        c.drawColor(ContextCompat.getColor(context, R.color.bg))
        if (width <= 0 || height <= 0) return
        val plan = if (mode == MODE_SCAN) planScan() else null
        val content = if (plan != null) scanHeight(plan) else typeHeight()
        // Tỉ lệ CỐ ĐỊNH theo từng bảng (không đổi khi chỉnh giá trị); chỉ thu thêm khi nội dung vượt hẳn khung
        var s = min(if (mode == MODE_SCAN) SCALE_SCAN else SCALE_TYPE, width / realW)
        if (content * s > height) s = height / content
        hitScale = s
        hitOffX = (width - realW * s) / 2f
        c.save()
        c.translate(hitOffX, 0f)
        c.scale(s, s)
        if (plan != null) drawScan(c, plan) else drawType(c)
        c.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val pick = onPick ?: return false
        if (mode != MODE_SCAN) return false
        val hit = hits.firstOrNull { it.second.contains(e.x, e.y) }
        return when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> hit != null
            MotionEvent.ACTION_UP -> { if (hit != null) { pick(hit.first); performClick() }; true }
            else -> hit != null
        }
    }

    override fun performClick(): Boolean = super.performClick()
}
