package com.fpsunlocker.tool

import android.app.Application
import com.fpsunlocker.tool.shizuku.ShizukuManager

/**
 * ถือ ShizukuManager ตัวเดียวทั้งแอป เพื่อให้ทั้งหน้าจอหลักและตัววัด FPS ลอย (service)
 * ใช้การเชื่อมต่อ Shizuku ชุดเดียวกัน
 */
class App : Application() {

    val shizuku: ShizukuManager by lazy { ShizukuManager() }

    override fun onCreate() {
        super.onCreate()
        shizuku.start()
    }
}
