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
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Màn hình cài đặt Keyboard Scan - SPX. */
class MainActivity : Activity() {

    private var prefs: Prefs? = null
    private var ready = false

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
        et(R.id.etDebounce).setText(p.debounceMs.toString())
        et(R.id.etScanDelay).setText(p.scanDelayMs.toString())
        et(R.id.etVibrateMs).setText(p.vibrateMs.toString())
        et(R.id.etEnterDelay).setText(p.enterDelayMs.toString())
        et(R.id.etDimPercent).setText(p.dimPercent.toString())
        et(R.id.etCamHeight).setText(p.cameraHeightDp.toString())
        sw(R.id.swEnter).isChecked = p.sendEnter
        sw(R.id.swAutoStart).isChecked = p.autoStart
        sw(R.id.swBeep).isChecked = p.beep
        sw(R.id.swVibrate).isChecked = p.vibrate
        sw(R.id.swKeepOn).isChecked = p.keepScreenOn
        sw(R.id.swDim).isChecked = p.dimScreen
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
