package com.inventory.scannerkeyboard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Màn hình cài đặt: cấp quyền camera, bật bàn phím, chỉnh tùy chọn, ô thử nghiệm. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        findViewById<Button>(R.id.btnCamPerm).setOnClickListener {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        }
        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btnPick).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        findViewById<EditText>(R.id.etDebounce).setText(prefs.debounceMs.toString())
        findViewById<EditText>(R.id.etEnterDelay).setText(prefs.enterDelayMs.toString())
        findViewById<CheckBox>(R.id.cbEnter).isChecked = prefs.sendEnter
        findViewById<CheckBox>(R.id.cbBeep).isChecked = prefs.beep
        findViewById<CheckBox>(R.id.cbVibrate).isChecked = prefs.vibrate
        findViewById<CheckBox>(R.id.cbAutoStart).isChecked = prefs.autoStart
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }

    private fun saveSettings() {
        prefs.debounceMs = (findViewById<EditText>(R.id.etDebounce).text.toString().toIntOrNull() ?: 1200)
            .coerceIn(300, 10000)
        prefs.enterDelayMs = (findViewById<EditText>(R.id.etEnterDelay).text.toString().toIntOrNull() ?: 60)
            .coerceIn(0, 1000)
        prefs.sendEnter = findViewById<CheckBox>(R.id.cbEnter).isChecked
        prefs.beep = findViewById<CheckBox>(R.id.cbBeep).isChecked
        prefs.vibrate = findViewById<CheckBox>(R.id.cbVibrate).isChecked
        prefs.autoStart = findViewById<CheckBox>(R.id.cbAutoStart).isChecked
    }

    /** Hiển thị trạng thái 3 bước cài đặt. */
    private fun refreshState() {
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS)
            ?.contains(packageName) == true
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.contains(packageName) == true
        fun mark(ok: Boolean) = if (ok) "✔" else "✘"
        findViewById<TextView>(R.id.tvState).text =
            "${mark(cam)} Quyền camera\n${mark(enabled)} Bàn phím đã bật\n${mark(selected)} Đang dùng bàn phím này"
    }
}
