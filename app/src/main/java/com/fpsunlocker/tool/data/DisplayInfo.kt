package com.fpsunlocker.tool.data

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/** อ่าน refresh rate ของจอ (Hz) */
object DisplayInfo {

    private fun display(context: Context): Display? =
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)

    /** Hz สูงสุดที่จอรองรับ */
    fun maxRefresh(context: Context): Float =
        display(context)?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 60f

    /** Hz ที่จอใช้อยู่ตอนนี้ (ระบบอาจลดลงเองเมื่อจอนิ่งหรือแบตต่ำ) */
    fun currentRefresh(context: Context): Float =
        display(context)?.refreshRate ?: 60f
}
