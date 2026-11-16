package com.inventory.scannerkeyboard

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Rung nhẹ khi chạm / gõ phím (Haptic Feedback). Cường độ 1..100 quy đổi ra thời lượng và biên độ rung:
 * 1 = rất nhẹ (khoảng 6 ms), 100 = mạnh (khoảng 30 ms). Máy không chỉnh được biên độ thì chỉ đổi thời lượng.
 */
object Haptics {
    private var vib: Vibrator? = null

    private fun vibrator(ctx: Context): Vibrator? {
        if (vib == null) vib = try { ctx.applicationContext.getSystemService(Vibrator::class.java) } catch (_: Throwable) { null }
        return vib
    }

    /** Rung một nhịp dài [ms] mili giây với biên độ mặc định của máy (dùng khi quét mã thành công). */
    fun pulse(ctx: Context, ms: Long) {
        val v = vibrator(ctx) ?: return
        try { v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Throwable) { }
    }

    /** Rung nhẹ theo công tắc và cường độ "rung khi chạm / gõ phím" trong cài đặt. */
    fun tickIfEnabled(ctx: Context) {
        val p = Prefs(ctx)
        if (p.keyHaptic) tick(ctx, p.keyHapticStrength)
    }

    fun tick(ctx: Context, strength: Int) {
        val v = vibrator(ctx) ?: return
        if (!v.hasVibrator()) return
        val s = strength.coerceIn(1, 100)
        val ms = (6 + s * 0.24f).toLong()
        val amp = (30 + s * 2.25f).toInt().coerceIn(1, 255)
        try {
            val effect = if (v.hasAmplitudeControl()) VibrationEffect.createOneShot(ms, amp)
            else VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)
            v.vibrate(effect)
        } catch (_: Throwable) { }
    }
}
