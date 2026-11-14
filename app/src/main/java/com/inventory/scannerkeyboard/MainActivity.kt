package com.inventory.scannerkeyboard

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Màn hình cài đặt Keyboard Scan - SPX. */
class MainActivity : Activity() {

    private var prefs: Prefs? = null
    private var ready = false
    private var keySound: KeySound? = null
    private val soundRows = ArrayList<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nếu lần chạy trước bị sập, hiện nội dung lỗi để gửi cho người hỗ trợ.
        val crash = getSharedPreferences("crash", Context.MODE_PRIVATE).getString("trace", null)
        if (crash != null) {
            showText("ỨNG DỤNG ĐÃ BỊ LỖI LẦN TRƯỚC", crash, clearable = true)
            return
        }
        try {
            setContentView(R.layout.activity_main)
            setup()
            Fonts.apply(window.decorView)
            ready = true
        } catch (e: Throwable) {
            showText("LỖI KHI MỞ MÀN HÌNH", Log.getStackTraceString(e), clearable = false)
        }
    }

    private fun setup() {
        val p = Prefs(this)
        prefs = p
        findViewById<TextView>(R.id.btnCamPerm).setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        }
        findViewById<TextView>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<TextView>(R.id.btnPick).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        findViewById<TextView>(R.id.btnButtonLayout).setOnClickListener {
            startActivity(Intent(this, ButtonLayoutActivity::class.java))
        }
        et(R.id.etDebounce).setText(p.debounceMs.toString())
        et(R.id.etScanDelay).setText(p.scanDelayMs.toString())
        et(R.id.etVibrateMs).setText(p.vibrateMs.toString())
        et(R.id.etEnterDelay).setText(p.enterDelayMs.toString())
        et(R.id.etDimPercent).setText(p.dimPercent.toString())
        et(R.id.etCamHeight).setText(p.cameraHeightDp.toString())
        et(R.id.etIdleOff).setText(p.idleOffSec.toString())
        sw(R.id.swEnter).isChecked = p.sendEnter
        sw(R.id.swAutoStart).isChecked = p.autoStart
        sw(R.id.swBeep).isChecked = p.beep
        sw(R.id.swVibrate).isChecked = p.vibrate
        sw(R.id.swKeepOn).isChecked = p.keepScreenOn
        sw(R.id.swDim).isChecked = p.dimScreen
        setupSound(p)
        setupHaptic(p)
    }

    /** Mục "Rung khi chạm / gõ phím": công tắc + cường độ. Lưu ngay, kéo xong thì rung thử. */
    private fun setupHaptic(p: Prefs) {
        sw(R.id.swKeyHaptic).isChecked = p.keyHaptic
        sw(R.id.swKeyHaptic).setOnCheckedChangeListener { _, on ->
            p.keyHaptic = on
            if (on) Haptics.tick(this, p.keyHapticStrength)
        }
        val sb = findViewById<SeekBar>(R.id.sbHaptic)
        val tv = findViewById<TextView>(R.id.tvHaptic)
        sb.progress = p.keyHapticStrength
        tv.text = "Cường độ rung: ${p.keyHapticStrength}%"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                val x = v.coerceAtLeast(1)
                p.keyHapticStrength = x
                tv.text = "Cường độ rung: $x%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { Haptics.tick(this@MainActivity, p.keyHapticStrength) }
        })
    }

    /** Mục "Âm thanh gõ phím": bật/tắt, 8 kiểu có nghe thử, thanh âm lượng. Lưu ngay khi thay đổi. */
    private fun setupSound(p: Prefs) {
        sw(R.id.swKeySound).isChecked = p.keySound
        sw(R.id.swKeySound).setOnCheckedChangeListener { _, on -> p.keySound = on }
        val list = findViewById<LinearLayout>(R.id.soundList)
        val d = resources.displayMetrics.density
        soundRows.clear()
        KeySound.NAMES.forEachIndexed { i, name ->
            val tv = TextView(this).apply {
                text = "▶  ${i + 1}. $name"
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text))
                setBackgroundResource(R.drawable.bg_toggle)
                isClickable = true
                setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (46 * d).toInt())
                    .apply { topMargin = (6 * d).toInt() }
                setOnClickListener {
                    p.keySoundType = i
                    markSound(i)
                    ensureSound()?.play(i, p.keySoundVolume)
                }
            }
            soundRows.add(tv)
            list.addView(tv)
        }
        markSound(p.keySoundType)
        ensureSound() // nạp sẵn để lần nghe thử đầu tiên không bị lỡ
        val sb = findViewById<SeekBar>(R.id.sbVolume)
        val tvVol = findViewById<TextView>(R.id.tvVolume)
        sb.progress = p.keySoundVolume
        tvVol.text = "Âm lượng: ${p.keySoundVolume}%"
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                p.keySoundVolume = v
                tvVol.text = "Âm lượng: $v%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { ensureSound()?.play(p.keySoundType, p.keySoundVolume) }
        })
    }

    private fun markSound(sel: Int) {
        soundRows.forEachIndexed { i, tv -> tv.isSelected = i == sel }
    }

    private fun ensureSound(): KeySound? {
        if (keySound == null) keySound = try { KeySound(this) } catch (_: Throwable) { null }
        return keySound
    }

    override fun onDestroy() {
        keySound?.release()
        super.onDestroy()
    }

    private fun et(id: Int) = findViewById<EditText>(id)
    private fun sw(id: Int) = findViewById<CompoundButton>(id)
    private fun num(id: Int, def: Int, lo: Int, hi: Int) =
        (et(id).text.toString().toIntOrNull() ?: def).coerceIn(lo, hi)

    override fun onResume() {
        super.onResume()
        if (ready) {
            try { refreshState() } catch (e: Throwable) {
                showText("LỖI KHI CẬP NHẬT TRẠNG THÁI", Log.getStackTraceString(e), clearable = false)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (ready) try { saveSettings() } catch (_: Throwable) { }
    }

    private fun saveSettings() {
        val p = prefs ?: return
        p.debounceMs = num(R.id.etDebounce, 1200, 300, 10000)
        p.scanDelayMs = num(R.id.etScanDelay, 400, 0, 5000)
        p.vibrateMs = num(R.id.etVibrateMs, 150, 0, 2000)
        p.enterDelayMs = num(R.id.etEnterDelay, 60, 0, 1000)
        p.dimPercent = num(R.id.etDimPercent, 50, 5, 100)
        p.cameraHeightDp = num(R.id.etCamHeight, 228, 120, 420)
        p.idleOffSec = num(R.id.etIdleOff, 0, 0, 3600)
        p.sendEnter = sw(R.id.swEnter).isChecked
        p.autoStart = sw(R.id.swAutoStart).isChecked
        p.beep = sw(R.id.swBeep).isChecked
        p.vibrate = sw(R.id.swVibrate).isChecked
        p.keepScreenOn = sw(R.id.swKeepOn).isChecked
        p.dimScreen = sw(R.id.swDim).isChecked
    }

    private fun refreshState() {
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        // Android 14 cấm đọc Settings.Secure.ENABLED_INPUT_METHODS nên hỏi trực tiếp InputMethodManager.
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val selected: Boolean? = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.contains(packageName) == true
        } catch (_: Throwable) { null }
        fun mark(ok: Boolean?) = when (ok) { true -> "✔"; false -> "✘"; null -> "?" }
        findViewById<TextView>(R.id.tvState).text =
            "${mark(cam)} Quyền camera\n${mark(enabled)} Bàn phím đã bật\n${mark(selected)} Đang dùng bàn phím này"
    }

    /** Màn hình lỗi dựng bằng code (không dùng file layout) để luôn hiển thị được. */
    private fun showText(title: String, body: String, clearable: Boolean) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        box.addView(TextView(this).apply { text = title; textSize = 18f; setTypeface(null, Typeface.BOLD) })
        box.addView(TextView(this).apply {
            text = "SAO CHÉP NỘI DUNG LỖI"; textSize = 15f; setPadding(0, 32, 0, 32)
            setOnClickListener {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("loi", body))
            }
        })
        if (clearable) box.addView(TextView(this).apply {
            text = "XÓA LỖI VÀ MỞ LẠI ỨNG DỤNG"; textSize = 15f; setPadding(0, 16, 0, 32)
            setOnClickListener {
                getSharedPreferences("crash", Context.MODE_PRIVATE).edit().clear().commit()
                recreate()
            }
        })
        box.addView(TextView(this).apply { text = body; textSize = 11f; setTextIsSelectable(true) })
        setContentView(ScrollView(this).apply { addView(box) })
    }
}
