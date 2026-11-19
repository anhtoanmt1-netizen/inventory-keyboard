package com.inventory.scannerkeyboard

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Mã dựng bàn phím DÙNG CHUNG cho bàn phím thật ([ScannerKeyboardService]) và các khung xem trước trong màn hình
 * tùy chỉnh. Cả hai cùng gọi những hàm này nên kích thước, nền, bo góc, khoảng cách, phím, phím Space, hiệu ứng
 * đều giống nhau tuyệt đối: giá trị đang xem trước chính là giá trị sẽ có trên bàn phím sau khi áp dụng.
 */
object KbMetrics {
    const val KEY_ROW_MIN = 36
    const val KEY_ROW_MAX = 72

    fun autoRowScale(ctx: Context, st: PanelStyle): Float {
        if (!st.layoutAuto) return 1f
        val m = ctx.resources.displayMetrics
        return (minOf(m.widthPixels, m.heightPixels) / m.density / 360f).coerceIn(0.9f, 1.15f)
    }

    /** Chiều cao một hàng phím (px) theo cài đặt "chiều cao hàng phím" và giao diện. */
    fun rowHeightPx(ctx: Context, prefs: Prefs, st: PanelStyle): Int {
        val d = ctx.resources.displayMetrics.density
        return (((prefs.keyRowDp.coerceIn(KEY_ROW_MIN, KEY_ROW_MAX) * autoRowScale(ctx, st)).toInt()) * d).toInt()
    }
}

/** Gắn thao tác thật cho từng loại phím. Bản xem trước không truyền binder nên các phím chỉ để hiển thị. */
interface KeyBinder {
    fun onChar(v: View, c: Char, letter: Boolean)
    fun onLayer(v: View, target: Int)
    fun onShift(v: View)
    fun onVn(v: View)
    fun onSymbol(v: View, s: String)
    fun onEnter(v: View)
    fun onDelete(v: View)
    fun onSpace(v: View)
}

/** Dựng các hàng phím của bảng Gõ phím. */
class TypePanelBuilder(
    private val ctx: Context,
    private val st: PanelStyle,
    private val space: SpaceStyle,
    private val rowPx: Int,
    private val vn: Boolean,
    private val layer: Int,
    private val shift: Int,
    private val binder: KeyBinder?
) {
    val keyViews = ArrayList<View>()
    val letterKeys = ArrayList<Pair<TextView, Char>>()
    var shiftKey: ImageView? = null
    var spaceView: TextView? = null

    private enum class KS { NORMAL, SPECIAL, ACCENT }

    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    fun build(panel: LinearLayout) {
        panel.removeAllViews()
        keyViews.clear()
        letterKeys.clear()
        shiftKey = null
        spaceView = null

        when (layer) {
            0 -> {
                panel.addView(charRow("1234567890", false, false))
                panel.addView(charRow("qwertyuiop", true, false))
                panel.addView(charRow("asdfghjkl", true, true))
                val r = newRow()
                shiftKey = addIconKey(r, R.drawable.ic_shift, 1.5f, KS.SPECIAL) { binder?.onShift(it) }
                "zxcvbnm".forEach { addChar(r, it, true) }
                addIconKey(r, R.drawable.ic_backspace, 1.5f, KS.SPECIAL) { binder?.onDelete(it) }
                panel.addView(r)
            }
            1 -> {
                panel.addView(charRow("1234567890", false, false))
                panel.addView(charRow("@#$%&-+()/", false, false))
                val r = newRow()
                addKey(r, "=\\<", 1.5f, KS.SPECIAL) { binder?.onLayer(it, 2) }
                "*\"':;!?".forEach { addChar(r, it, false) }
                addIconKey(r, R.drawable.ic_backspace, 1.5f, KS.SPECIAL) { binder?.onDelete(it) }
                panel.addView(r)
            }
            else -> {
                panel.addView(charRow("[]{}<>^~|\\", false, false))
                panel.addView(charRow("_=+-*/&%$€", false, false))
                val r = newRow()
                addKey(r, "?123", 1.5f, KS.SPECIAL) { binder?.onLayer(it, 1) }
                ".,?!'\":".forEach { addChar(r, it, false) }
                addIconKey(r, R.drawable.ic_backspace, 1.5f, KS.SPECIAL) { binder?.onDelete(it) }
                panel.addView(r)
            }
        }

        val b = newRow()
        addKey(b, if (layer == 0) "?123" else "ABC", 1.5f, KS.SPECIAL) { binder?.onLayer(it, if (layer == 0) 1 else 0) }
        addKey(b, if (vn) "VN" else "EN", 1.2f, KS.SPECIAL) { binder?.onVn(it) }
        addKey(b, ",", 1f, KS.NORMAL) { binder?.onSymbol(it, ",") }
        addSpaceKey(b)
        addKey(b, ".", 1f, KS.NORMAL) { binder?.onSymbol(it, ".") }
        addIconKey(b, R.drawable.ic_enter, 1.5f, KS.ACCENT) { binder?.onEnter(it) }
        panel.addView(b)

        Fonts.apply(panel)
        applyText()
    }

    /** Màu / font chữ của phím theo giao diện; phím Space có kiểu chữ riêng. */
    private fun applyText() {
        for (k in keyViews) if (k is TextView) {
            if (k === spaceView) SpaceRender.styleLabel(k, st, space, vn) else ThemeRender.styleText(k, st)
        }
    }

    private fun newRow(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowPx)
        setPadding(0, dp(st.gapDp * 2 / 3), 0, dp(st.gapDp * 2 / 3))
        clipChildren = false
        clipToPadding = false
    }

    private fun charRow(chars: String, letters: Boolean, pad: Boolean): LinearLayout {
        val r = newRow()
        if (pad) r.addView(Space(ctx), LinearLayout.LayoutParams(0, 1, 0.5f))
        chars.forEach { addChar(r, it, letters) }
        if (pad) r.addView(Space(ctx), LinearLayout.LayoutParams(0, 1, 0.5f))
        return r
    }

    private fun kindOf(style: KS) = when (style) {
        KS.NORMAL -> ThemeRender.Kind.NORMAL
        KS.SPECIAL -> ThemeRender.Kind.SPECIAL
        KS.ACCENT -> ThemeRender.Kind.ACCENT
    }

    private fun place(row: LinearLayout, v: View, weight: Float) {
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
        lp.setMargins(dp(st.gapDp), 0, dp(st.gapDp), 0)
        row.addView(v, lp)
    }

    private fun addKey(row: LinearLayout, label: String, weight: Float, style: KS, bind: (View) -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setTextColor(ContextCompat.getColor(ctx, R.color.text))
        tv.textSize = (if (label.length > 1) 14f else 19f) * st.fontScale / 100f
        tv.isClickable = true
        tv.background = ThemeRender.keyBg(ctx, st, kindOf(style))
        ThemeRender.elevate(tv, st)
        keyViews.add(tv)
        place(row, tv, weight)
        bind(tv)
        return tv
    }

    /** Phím dùng biểu tượng (kích thước icon luôn 24dp, giống hàng nút ở màn hình quét). */
    private fun addIconKey(row: LinearLayout, iconRes: Int, weight: Float, style: KS, bind: (View) -> Unit): ImageView {
        val iv = ImageView(ctx)
        iv.setImageResource(iconRes)
        ThemeRender.styleIcon(iv, st)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.setPadding(dp(13), dp(13), dp(13), dp(13))
        iv.isClickable = true
        iv.background = ThemeRender.keyBg(ctx, st, kindOf(style))
        ThemeRender.elevate(iv, st)
        keyViews.add(iv)
        place(row, iv, weight)
        bind(iv)
        return iv
    }

    private fun addChar(row: LinearLayout, c: Char, letter: Boolean) {
        val label = if (letter && shift > 0) c.uppercaseChar().toString() else c.toString()
        val key = addKey(row, label, 1f, KS.NORMAL) { binder?.onChar(it, c, letter) }
        if (letter) letterKeys.add(key to c)
    }

    private fun addSpaceKey(row: LinearLayout) {
        val sp = space
        val tv = TextView(ctx)
        tv.gravity = Gravity.CENTER
        tv.setSingleLine()
        tv.setTextColor(ContextCompat.getColor(ctx, R.color.text))
        tv.textSize = 14f * st.fontScale / 100f
        tv.isClickable = true
        tv.background = SpaceRender.background(ctx, st, sp)
        ThemeRender.elevate(tv, SpaceRender.shapeStyle(st, sp))
        spaceView = tv
        keyViews.add(tv)
        place(row, tv, sp.weight / 10f)
        binder?.onSpace(tv)
    }
}

/** Áp giao diện cho các khung của bàn phím: bố cục (rộng, căn lề, nâng đáy...), nền, chữ trạng thái. */
object KbLook {
    fun layoutPanel(panel: View, st: PanelStyle, fullPx: Int, d: Float) {
        val lp = panel.layoutParams as? LinearLayout.LayoutParams ?: return
        ThemeRender.applyPanelLayout(lp, st, fullPx, d)
        panel.layoutParams = lp
    }

    fun styleTypePanel(ctx: Context, panelType: LinearLayout, st: PanelStyle, fullPx: Int) {
        layoutPanel(panelType, st, fullPx, ctx.resources.displayMetrics.density)
        panelType.clipChildren = false
        panelType.background = ThemeRender.panelBackground(ctx, st)
    }

    fun styleScanPanel(
        ctx: Context, panelScan: View, tvStatus: TextView, tvHint: TextView,
        st: PanelStyle, fullPx: Int, statusColor0: Int, hintColor0: Int
    ) {
        layoutPanel(panelScan, st, fullPx, ctx.resources.displayMetrics.density)
        panelScan.background = ThemeRender.panelBackground(ctx, st)
        val custom = st.textColor != PanelStyle().textColor
        for ((t, c0) in listOf(tvStatus to statusColor0, tvHint to hintColor0)) {
            Fonts.apply(t)                       // trả về font gốc trước khi áp font theo giao diện
            t.setTextColor(c0)
            t.setShadowLayer(0f, 0f, 0f, 0)
            ThemeRender.styleText(t, st, custom)
        }
    }
}

/**
 * Khung chứa hiển thị nội dung ở bề rộng THẬT của bàn phím ([contentW] px) rồi thu nhỏ đều (cả chiều ngang và dọc)
 * cho vừa khung xem trước. Nhờ vậy mọi tỉ lệ, khoảng cách, số nút trên hàng đều đúng như bàn phím thật.
 */
class ScaledBox(
    ctx: Context,
    private val child: View,
    private val contentW: Int,
    private val maxH: Int,
    private val frameH: Int = 0,          // > 0: chiều cao khung CỐ ĐỊNH (không đổi khi nội dung đổi)
    private val fixedScale: Float = 0f,   // > 0: giữ nguyên tỉ lệ thu nhỏ, chỉ thu nhỏ thêm nếu nội dung vượt khung
    private val onScale: ((Float) -> Unit)? = null
) : FrameLayout(ctx) {
    private var scale = 1f
    private var childH = 0
    private val cleanups = ArrayList<() -> Unit>()

    /** Việc cần dọn khi khung xem trước bị gỡ (ví dụ dừng hiệu ứng ánh sáng đang chạy). */
    fun onRelease(r: () -> Unit) { cleanups.add(r) }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        for (c in cleanups) try { c() } catch (_: Throwable) { }
        cleanups.clear()
    }

    init {
        clipChildren = false
        clipToPadding = false
        addView(child, LayoutParams(contentW, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val avail = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        child.measure(
            MeasureSpec.makeMeasureSpec(contentW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        childH = child.measuredHeight
        val widthScale = minOf(1f, avail.toFloat() / contentW)
        val limit = if (frameH > 0) frameH else maxH
        var sc = if (fixedScale > 0f) minOf(fixedScale, widthScale) else widthScale
        // Lần dựng đầu: chừa thêm ~12% chiều cao để các thay đổi nhỏ (khoảng cách, số hàng) không làm khung phải thu nhỏ lại
        if (fixedScale <= 0f && limit > 0) sc = minOf(sc, limit / (childH * 1.12f))
        else if (limit > 0 && childH * sc > limit) sc = limit.toFloat() / childH
        scale = sc
        onScale?.invoke(sc)
        setMeasuredDimension(avail, if (frameH > 0) frameH else (childH * sc).toInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        child.layout(0, 0, contentW, childH)
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = scale
        child.scaleY = scale
        child.translationX = ((r - l) - contentW * scale) / 2f
    }
}

/** Dựng bản xem trước bàn phím bằng chính layout, bộ dựng nút / phím và hàm áp giao diện của bàn phím thật. */
object KbPreview {
    class Opts(
        val scan: Boolean,                         // hiện bảng Quét (khung camera + hàng dưới)
        val type: Boolean,                         // hiện bảng Gõ phím
        val toolsOpen: Boolean = false,            // hiện bảng Công cụ nổi phía trên bàn phím
        val onPick: ((String) -> Unit)? = null,    // chạm một nút để chọn (màn hình Tùy chỉnh nút Scan)
        val maxHeightPx: Int = 0,                  // chiều cao tối đa của khung xem trước (0 = không giới hạn)
        val frameHeightPx: Int = 0,                // > 0: khung xem trước có chiều cao CỐ ĐỊNH (không nhảy khi chỉnh)
        val fixedScale: Float = 0f,                // > 0: giữ nguyên tỉ lệ thu nhỏ giữa các lần dựng
        val onScale: ((Float) -> Unit)? = null     // nhận tỉ lệ thu nhỏ đang dùng để giữ ổn định cho lần dựng sau
    )

    fun build(ctx: Context, opts: Opts, layIn: ScanLayout, theme: KbTheme?, availPx: Int): View {
        val prefs = Prefs(ctx)
        val lay = ScanLayout.fromJson(layIn.toJson())          // chuẩn hóa như khi bàn phím đọc từ cài đặt
        val styleScan = theme?.scan ?: PanelStyle()
        val styleKb = theme?.kb ?: PanelStyle()
        val space = theme?.space ?: SpaceStyle()
        val dm = ctx.resources.displayMetrics
        val d = dm.density
        val fullW = dm.widthPixels
        fun dp(v: Int) = (v * d).toInt()

        val kb = LayoutInflater.from(ctx).inflate(R.layout.keyboard_view, null) as LinearLayout
        val topArea = kb.findViewById<LinearLayout>(R.id.topArea)
        val btnArea = kb.findViewById<LinearLayout>(R.id.btnArea)
        val panelScan = kb.findViewById<View>(R.id.panelScan)
        val panelType = kb.findViewById<LinearLayout>(R.id.panelType)
        val panelClip = kb.findViewById<View>(R.id.panelClip)
        val cameraBox = kb.findViewById<View>(R.id.cameraBox)
        val tvStatus = kb.findViewById<TextView>(R.id.tvStatus)
        val tvHint = kb.findViewById<TextView>(R.id.tvHint)
        val gapBelowCamera = kb.findViewById<View>(R.id.gapBelowCamera)
        val creditView = kb.findViewById<CreditView>(R.id.creditView)

        cameraBox.layoutParams.height = dp(prefs.cameraHeightDp.coerceIn(120, 420))

        // Hàng trên / hàng dưới / nhóm Công cụ: đúng bộ dựng của bàn phím thật
        val asm = ScanButtons.assemble(ctx, lay, styleScan)
        topArea.addView(asm.top)
        btnArea.addView(asm.bottom)
        ScanButtons.applyAreaPads(ctx, lay, topArea, btnArea, gapBelowCamera)
        val topSt = ScanButtons.btnStyle(styleScan, lay, ScanLayout.AREA_TOP)
        val botSt = ScanButtons.btnStyle(styleScan, lay, ScanLayout.AREA_BOTTOM)
        val toolSt = ScanButtons.btnStyle(styleScan, lay, ScanLayout.AREA_TOOLS)
        if (opts.toolsOpen) asm.buttons["tools"]?.isSelected = true
        opts.onPick?.let { pick -> for ((k, b) in asm.buttons) b.setOnClickListener { pick(k) } }

        // Bảng Gõ phím: đúng bộ dựng phím của bàn phím thật
        val builder = TypePanelBuilder(ctx, styleKb, space, KbMetrics.rowHeightPx(ctx, prefs, styleKb), true, 0, 0, null)
        if (opts.type) builder.build(panelType)

        Fonts.apply(kb)
        val statusColor0 = tvStatus.currentTextColor
        val hintColor0 = tvHint.currentTextColor

        panelScan.visibility = if (opts.scan) View.VISIBLE else View.GONE
        panelType.visibility = if (opts.type) View.VISIBLE else View.GONE
        panelClip.visibility = View.GONE

        // Hiệu ứng chạm / ánh sáng giống bàn phím thật
        val fxList = ArrayList<FxDrawable>()
        fxList.add(FxDrawable(panelType) { builder.keyViews }.also { it.style = styleKb; it.attach() })
        fxList.add(FxDrawable(btnArea) { asm.bottomViews }.also { it.style = botSt; it.attach() })
        fxList.add(FxDrawable(topArea) { asm.topViews }.also { it.style = topSt; it.attach() })

        KbLook.styleTypePanel(ctx, panelType, styleKb, fullW)
        KbLook.styleScanPanel(ctx, panelScan, tvStatus, tvHint, styleScan, fullW, statusColor0, hintColor0)
        creditView.setStyle(CreditStyle.fromJson(prefs.creditJson))

        // Bảng Công cụ nổi phía trên bàn phím (khi đang mở)
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        if (opts.toolsOpen) {
            val (box, row) = ScanButtons.newToolsBox(ctx)
            ScanButtons.fillTools(ctx, row, lay, asm.buttons)
            if (lay.tools.isEmpty()) row.addView(TextView(ctx).apply {
                text = "Nhóm Công cụ đang trống"; textSize = 12f
                setTextColor(ContextCompat.getColor(ctx, R.color.muted)); setPadding(dp(8), dp(10), dp(8), dp(10))
            })
            fxList.add(FxDrawable(box) { asm.toolViews }.also { it.style = toolSt; it.attach() })
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(lay.toolsStyle.padBottomDp)
            lp.marginStart = dp(8)
            content.addView(box, lp)
            // Canh bảng nổi theo vị trí nút Công cụ, giống bàn phím thật
            val tb = asm.buttons["tools"]
            kb.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (box.width == 0 || tb == null) return
                    kb.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    var x = 0
                    var v: View? = tb
                    while (v != null && v !== kb) { x += v.left; v = v.parent as? View }
                    val w = box.width
                    // Cùng hàm vị trí với bàn phím thật (theo bộ thông số riêng của nhóm Công cụ)
                    val want = ScanButtons.toolsPanelX(lay.toolsStyle, d, x + tb.width / 2, w, fullW)
                    (box.layoutParams as LinearLayout.LayoutParams).marginStart = want
                    box.requestLayout()
                }
            })
        }
        content.addView(kb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val sb = ScaledBox(ctx, content, fullW, opts.maxHeightPx, opts.frameHeightPx, opts.fixedScale, opts.onScale)
        sb.onRelease { for (f in fxList) f.detach() }
        sb.layoutParams = ViewGroup.LayoutParams(availPx.coerceAtLeast(1), ViewGroup.LayoutParams.WRAP_CONTENT)
        return sb
    }
}
