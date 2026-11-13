package com.inventory.scannerkeyboard

import android.content.Context

/** Cài đặt dùng chung giữa màn hình cài đặt (MainActivity) và bàn phím (Service). */
class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)

    private fun getI(k: String, d: Int) = sp.getInt(k, d)
    private fun putI(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    private fun getB(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun putB(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    /** Chống quét trùng: cùng mã chỉ nhận lại sau khi vắng mặt lâu hơn giá trị này (ms). */
    var debounceMs: Int get() = getI("debounce", 1200); set(v) = putI("debounce", v)

    /** Độ nhạy: thời gian chờ SAU khi rung xong rồi mới quét mã tiếp (ms). */
    var scanDelayMs: Int get() = getI("scanDelay", 400); set(v) = putI("scanDelay", v)

    /** Độ dài nhịp rung khi quét thành công (ms). */
    var vibrateMs: Int get() = getI("vibrateMs", 150); set(v) = putI("vibrateMs", v)

    /** Độ trễ giữa lúc chèn mã và lúc gửi Enter (ms). */
    var enterDelayMs: Int get() = getI("enterDelay", 60); set(v) = putI("enterDelay", v)

    var sendEnter: Boolean get() = getB("sendEnter", true); set(v) = putB("sendEnter", v)
    var beep: Boolean get() = getB("beep", true); set(v) = putB("beep", v)
    var vibrate: Boolean get() = getB("vibrate", true); set(v) = putB("vibrate", v)
    var autoStart: Boolean get() = getB("autoStart", true); set(v) = putB("autoStart", v)

    var keepScreenOn: Boolean get() = getB("keepOn", true); set(v) = putB("keepOn", v)
    var dimScreen: Boolean get() = getB("dim", false); set(v) = putB("dim", v)
    var dimPercent: Int get() = getI("dimPercent", 50); set(v) = putI("dimPercent", v)

    /** Chiều cao khung camera (dp). Mặc định 228 = 190 x 1.2. */
    var cameraHeightDp: Int get() = getI("camHeight", 228); set(v) = putI("camHeight", v)
}
