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
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Màn hình cài đặt: cấp quyền camera, bật bàn phím, chỉnh tùy chọn, ô thử nghiệm. */
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
            ready = true
        } catch (e: Throwable) {
            showText("LỖI KHI MỞ MÀN HÌNH", Log.getStackTraceString(e), clearable = false)
        }
    }

    private fun setup() {
        val p = Prefs(this)
        prefs = p
        findViewById<Button>(R.id.btnCamPerm).setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        }
        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btnPick).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        findViewById<EditText>(R.id.etDebounce).setText(p.debounceMs.toString())
        findViewById<EditText>(R.id.etEnterDelay).setText(p.enterDelayMs.toString())
        findViewById<CheckBox>(R.id.cbEnter).isChecked = p.sendEnter
        findViewById<CheckBox>(R.id.cbBeep).isChecked = p.beep
        findViewById<CheckBox>(R.id.cbVibrate).isChecked = p.vibrate
        findViewById<CheckBox>(R.id.cbAutoStart).isChecked = p.autoStart
    }

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
        p.debounceMs = (findViewById<EditText>(R.id.etDebounce).text.toString().toIntOrNull() ?: 1200).coerceIn(300, 10000)
        p.enterDelayMs = (findViewById<EditText>(R.id.etEnterDelay).text.toString().toIntOrNull() ?: 60).coerceIn(0, 1000)
        p.sendEnter = findViewById<CheckBox>(R.id.cbEnter).isChecked
        p.beep = findViewById<CheckBox>(R.id.cbBeep).isChecked
        p.vibrate = findViewById<CheckBox>(R.id.cbVibrate).isChecked
        p.autoStart = findViewById<CheckBox>(R.id.cbAutoStart).isChecked
    }

    private fun refreshState() {
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        // Android 14 cấm đọc Settings.Secure.ENABLED_INPUT_METHODS nên hỏi trực tiếp InputMethodManager.
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        // DEFAULT_INPUT_METHOD vẫn có thể bị chặn trên một số máy, nên bọc try/catch.
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
        box.addView(Button(this).apply {
            text = "SAO CHÉP NỘI DUNG LỖI"
            setOnClickListener {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("loi", body))
            }
        })
        if (clearable) box.addView(Button(this).apply {
            text = "XÓA LỖI VÀ MỞ LẠI ỨNG DỤNG"
            setOnClickListener {
                getSharedPreferences("crash", Context.MODE_PRIVATE).edit().clear().commit()
                recreate()
            }
        })
        box.addView(TextView(this).apply { text = body; textSize = 11f; setTextIsSelectable(true) })
        setContentView(ScrollView(this).apply { addView(box) })
    }
}
