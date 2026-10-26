package com.fpsunlocker.tool.data

import androidx.annotation.StringRes

/**
 * ข้อผิดพลาดที่แสดงให้ผู้ใช้เห็น เก็บเป็น id ของข้อความ แล้วแปลตามภาษาเครื่องตอนแสดงผล
 * (ชั้น data ไม่ต้องรู้จัก Context)
 */
open class LocalizedException(@StringRes val res: Int, vararg val args: Any) : Exception()
