package com.inventory.scannerkeyboard

import android.content.Context

/** Cài đặt dùng chung giữa màn hình cài đặt (MainActivity) và bàn phím (Service). */
class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("scanner_prefs", Context.MODE_PRIVATE)

    /** Thời gian chống quét trùng (ms). */
    var debounceMs: Int
        get() = sp.getInt("debounce", 1200)
        set(v) = sp.edit().putInt("debounce", v).apply()

    /** Độ trễ giữa lúc chèn mã và lúc gửi Enter (ms). */
    var enterDelayMs: Int
        get() = sp.getInt("enterDelay", 60)
        set(v) = sp.edit().putInt("enterDelay", v).apply()

    var sendEnter: Boolean
        get() = sp.getBoolean("sendEnter", true)
        set(v) = sp.edit().putBoolean("sendEnter", v).apply()

    var beep: Boolean
        get() = sp.getBoolean("beep", true)
        set(v) = sp.edit().putBoolean("beep", v).apply()

    var vibrate: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(v) = sp.edit().putBoolean("vibrate", v).apply()

    var autoStart: Boolean
        get() = sp.getBoolean("autoStart", true)
        set(v) = sp.edit().putBoolean("autoStart", v).apply()
}
