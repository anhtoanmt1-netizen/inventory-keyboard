package com.inventory.scannerkeyboard

import android.app.Application
import android.content.Context
import android.util.Log

/** Ghi lại nội dung lỗi khi ứng dụng bị sập, để lần mở sau hiển thị ra màn hình. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val text = Log.getStackTraceString(error)
                getSharedPreferences("crash", Context.MODE_PRIVATE)
                    .edit().putString("trace", "Thread: ${thread.name}\n$text").commit()
            } catch (_: Throwable) { }
            previous?.uncaughtException(thread, error)
        }
    }
}
