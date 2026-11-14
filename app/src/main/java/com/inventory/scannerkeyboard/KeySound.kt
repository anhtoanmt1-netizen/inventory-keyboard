package com.inventory.scannerkeyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Âm thanh gõ phím: 8 kiểu, tạo bằng cách tổng hợp sóng âm (không cần file âm thanh đi kèm).
 * Mỗi kiểu được ghi thành file WAV nhỏ trong cache rồi nạp vào SoundPool để phát với độ trễ thấp.
 * Âm lượng chỉnh riêng (0..100) trong ứng dụng, tính trên âm lượng đa phương tiện của máy.
 */
class KeySound(context: Context) {
    companion object {
        val NAMES = arrayOf(
            "Click nhẹ", "Cơ xanh (Blue)", "Bong bóng", "Gõ gỗ",
            "Máy chữ", "Bấm bút", "Chuông nhỏ", "Mềm êm"
        )
        private const val RATE = 22050
        private const val VERSION = 1
    }

    private val ctx = context.applicationContext
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
    private val ids = IntArray(NAMES.size) { 0 }
    private val ready = BooleanArray(NAMES.size)

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) for (i in ids.indices) if (ids[i] == sampleId) ready[i] = true
        }
        for (i in NAMES.indices) {
            try {
                val f = File(ctx.cacheDir, "keysnd_v${VERSION}_$i.wav")
                if (!f.exists() || f.length() < 100) writeWav(f, synth(i))
                ids[i] = pool.load(f.absolutePath, 1)
            } catch (_: Throwable) { }
        }
    }

    /** Phát âm thanh kiểu [type] với âm lượng [volumePercent] (0..100). */
    fun play(type: Int, volumePercent: Int) {
        val i = type.coerceIn(0, NAMES.size - 1)
        if (!ready[i]) return
        val v = (volumePercent.coerceIn(0, 100) / 100f)
        try { pool.play(ids[i], v, v, 1, 0, 1f) } catch (_: Throwable) { }
    }

    fun release() { try { pool.release() } catch (_: Throwable) { } }

    // ---------------------------------------------------------------- tổng hợp sóng
    private fun synth(type: Int): ShortArray {
        val rnd = Random(1234 + type)
        val ms = when (type) { 1 -> 90; 4 -> 80; 6 -> 110; 7 -> 70; else -> 55 }
        val n = RATE * ms / 1000
        val out = FloatArray(n)
        fun t(i: Int) = i.toFloat() / RATE
        fun noise() = rnd.nextFloat() * 2f - 1f
        fun tone(f: Float, i: Int) = sin(2.0 * PI * f * t(i)).toFloat()
        fun env(i: Int, tau: Float) = exp(-t(i) / tau)
        for (i in 0 until n) {
            val s = when (type) {
                0 -> noise() * env(i, 0.003f) * 0.7f + tone(2600f, i) * env(i, 0.006f) * 0.5f
                1 -> {
                    val a = (noise() * 0.6f + tone(3200f, i) * 0.5f) * env(i, 0.004f)
                    val j = i - RATE * 28 / 1000
                    val b = if (j >= 0) (noise() * 0.4f + tone(1500f, j) * 0.6f) * env(j, 0.012f) else 0f
                    a + b
                }
                2 -> {
                    // quét tần số 650Hz -> 200Hz trong 50ms rồi giữ 200Hz
                    val tt = t(i).toDouble()
                    val sweep = 0.05
                    val ph = if (tt < sweep) 2.0 * PI * (650.0 * tt - 450.0 * tt * tt / (2 * sweep))
                    else 2.0 * PI * (650.0 * sweep - 450.0 * sweep / 2 + 200.0 * (tt - sweep))
                    sin(ph).toFloat() * env(i, 0.018f)
                }
                3 -> (tone(900f, i) * 0.7f + tone(1800f, i) * 0.25f + noise() * 0.05f) * env(i, 0.012f)
                4 -> {
                    val a = noise() * env(i, 0.002f) * 0.6f + tone(3200f, i) * env(i, 0.01f) * 0.35f
                    val b = tone(180f, i) * env(i, 0.02f) * 0.7f
                    a + b
                }
                5 -> (if (tone(1200f, i) >= 0) 0.5f else -0.5f) * env(i, 0.006f) + noise() * 0.08f * env(i, 0.003f)
                6 -> (tone(1760f, i) * 0.6f + tone(2637f, i) * 0.35f) * env(i, 0.035f)
                else -> {
                    val att = (t(i) / 0.004f).coerceAtMost(1f)
                    tone(320f, i) * att * env(i, 0.03f) * 0.9f + noise() * 0.03f * env(i, 0.01f)
                }
            }
            out[i] = s
        }
        // chuẩn hóa về ~0.9 và làm mượt phần cuối để không bị "tách"
        var peak = 0.0001f
        for (x in out) if (kotlin.math.abs(x) > peak) peak = kotlin.math.abs(x)
        val fade = n / 8
        return ShortArray(n) { i ->
            var x = out[i] / peak * 0.9f
            if (i > n - fade) x *= (n - i).toFloat() / fade
            (x * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun writeWav(f: File, pcm: ShortArray) {
        val dataLen = pcm.size * 2
        val bb = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + dataLen); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(1); bb.putShort(1)
        bb.putInt(RATE); bb.putInt(RATE * 2); bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(dataLen)
        for (s in pcm) bb.putShort(s)
        FileOutputStream(f).use { it.write(bb.array()) }
    }
}
