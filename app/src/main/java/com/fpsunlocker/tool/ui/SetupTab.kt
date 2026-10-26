package com.fpsunlocker.tool.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fpsunlocker.tool.shizuku.ShizukuManager

private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

/** กลุ่มของ ROM ที่มีวิธีแก้ปัญหาต่างกัน */
enum class Rom(val label: String) {
    COLOROS("OPPO / OnePlus / realme (ColorOS)"),
    XIAOMI("Xiaomi / Redmi / POCO (HyperOS / MIUI)"),
    SAMSUNG("Samsung (One UI)"),
    MEIZU("Meizu (Flyme)"),
    OTHER("ยี่ห้ออื่น"),
}

fun detectRom(): Rom {
    val names = listOf(Build.MANUFACTURER, Build.BRAND).map { it.lowercase() }
    return when {
        names.any { it in listOf("oppo", "oneplus", "realme") } -> Rom.COLOROS
        names.any { it in listOf("xiaomi", "redmi", "poco") } -> Rom.XIAOMI
        names.any { it == "samsung" } -> Rom.SAMSUNG
        names.any { it == "meizu" } -> Rom.MEIZU
        else -> Rom.OTHER
    }
}

private data class Trouble(val title: String, val rom: Rom?, val steps: List<String>)

private val TROUBLES = listOf(
    Trouble(
        "Shizuku ขึ้นว่า \"การอนุญาตของ adb นั้นจำกัด\"",
        Rom.COLOROS,
        listOf(
            "ColorOS 16 ขึ้นไป: เข้าตัวเลือกสำหรับนักพัฒนา แล้วเปิดสวิตช์ \"Disable system optimization\" (ปิดการเพิ่มประสิทธิภาพระบบ) กดยืนยันเมื่อระบบเตือน",
            "ถ้าหาไม่เจอ ให้เปลี่ยนภาษาเครื่องเป็น English ชั่วคราวที่ ตั้งค่า > ระบบ > ภาษา เพราะบางภาษาซ่อนตัวเลือกนี้ไว้ เปิดสวิตช์เสร็จแล้วค่อยเปลี่ยนภาษากลับ",
            "ColorOS รุ่นเก่ากว่า 16: ใช้ตัวเลือก \"Disable permission monitoring\" แทน",
            "เสร็จแล้วเข้า Shizuku กด \"หยุด\" แล้วกด \"เริ่ม\" ใหม่ ถ้ายังไม่ได้ ให้รีสตาร์ทเครื่องหนึ่งครั้งแล้วเริ่ม Shizuku อีกครั้ง",
        ),
    ),
    Trouble(
        "Shizuku ขึ้นว่า \"การอนุญาตของ adb นั้นจำกัด\"",
        Rom.XIAOMI,
        listOf(
            "เข้าตัวเลือกสำหรับนักพัฒนา แล้วเปิด \"USB debugging (Security settings)\" หรือ \"การแก้ไขข้อบกพร่อง USB (การตั้งค่าความปลอดภัย)\" ซึ่งเป็นคนละตัวกับสวิตช์ USB debugging ปกติ",
            "Xiaomi อาจบังคับให้ใส่ซิมการ์ดและล็อกอิน Mi Account ก่อนจึงจะเปิดสวิตช์นี้ได้",
            "เสร็จแล้วเข้า Shizuku กด \"หยุด\" แล้วกด \"เริ่ม\" ใหม่",
        ),
    ),
    Trouble(
        "Shizuku ขึ้นว่า \"การอนุญาตของ adb นั้นจำกัด\"",
        Rom.MEIZU,
        listOf(
            "เข้าตัวเลือกสำหรับนักพัฒนา แล้วปิด \"Flyme payment protection\"",
            "เสร็จแล้วเข้า Shizuku กด \"หยุด\" แล้วกด \"เริ่ม\" ใหม่",
        ),
    ),
    Trouble(
        "เริ่ม Shizuku ไม่ได้ หรือเชื่อมต่อ adb ไม่ได้",
        Rom.SAMSUNG,
        listOf(
            "ถ้าเปิด \"ตัวบล็อกอัตโนมัติ\" (Auto Blocker) ไว้ ตัวนี้อาจบล็อกการใช้ adb ให้ปิดชั่วคราวที่ ตั้งค่า > ความปลอดภัยและความเป็นส่วนตัว > ตัวบล็อกอัตโนมัติ",
            "จากนั้นจับคู่และเริ่ม Shizuku ใหม่ตามขั้นตอนด้านบน",
        ),
    ),
    Trouble(
        "ช่องป้อนรหัสจับคู่ในการแจ้งเตือนไม่ขึ้น",
        null,
        listOf(
            "อนุญาตการแจ้งเตือนของ Shizuku ที่ ตั้งค่า > แอป > Shizuku > การแจ้งเตือน",
            "ถ้ายังไม่ขึ้น ใช้โหมดแยกหน้าจอ: เปิด Shizuku ครึ่งจอ และหน้า \"การแก้ไขข้อบกพร่องแบบไร้สาย\" อีกครึ่งจอ",
            "รหัสจับคู่หมดอายุเร็ว ถ้าพิมพ์แล้วไม่สำเร็จ ให้ปิดหน้ารหัสแล้วขอรหัสใหม่",
        ),
    ),
    Trouble(
        "Shizuku หยุดทำงานหลังรีสตาร์ทเครื่องหรือเปลี่ยน Wi-Fi",
        null,
        listOf(
            "เป็นเรื่องปกติของการเริ่มผ่าน Wireless debugging เพราะระบบปิดการดีบักแบบไร้สายเองเมื่อรีสตาร์ทหรือ Wi-Fi หลุด",
            "เชื่อม Wi-Fi แล้วเปิด \"การแก้ไขข้อบกพร่องแบบไร้สาย\" ใหม่ จากนั้นเข้า Shizuku กด \"เริ่ม\" ไม่ต้องจับคู่ใหม่",
        ),
    ),
    Trouble(
        "แอปนี้ขึ้นว่า Shizuku ปิดอยู่ ทั้งที่เปิดแล้ว",
        null,
        listOf(
            "ดูหน้าแรกของ Shizuku ว่าขึ้นว่า \"กำลังทำงาน\" จริงหรือไม่",
            "กด \"ตรวจอีกครั้ง\" ในแอปนี้ หรือปัดปิดแอปนี้แล้วเปิดใหม่",
            "ถ้าเคยกด \"ปฏิเสธ\" ไว้ ให้เข้า Shizuku > แอปที่ได้รับอนุญาต แล้วเปิดสวิตช์ของ FPS Unlocker",
        ),
    ),
)

@Composable
fun SetupTab(s: UiState, vm: MainViewModel, onRequestSafAccess: () -> Unit) {
    val context = LocalContext.current
    val rom = remember { detectRom() }
    val shizukuInstalled = remember(s.shizukuStatus) {
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG) != null
    }
    val troubles = remember(rom) { TROUBLES.filter { it.rom == null || it.rom == rom } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusCard(s, vm) }
        item { DeviceCard(rom) }
        item { GuideCard(s, rom, shizukuInstalled, vm) }
        item {
            Text(
                "แก้ปัญหาที่พบบ่อย",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
        items(troubles) { t -> TroubleCard(t, highlight = t.rom == rom) }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            item { SafCard(s, onRequestSafAccess) }
        }
        item { SafetyCard() }
    }
}

// ---------------- สถานะ ----------------

@Composable
private fun StatusCard(s: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    val (color, title, detail) = when {
        s.shizukuBound -> Triple(Palette.Good, "พร้อมใช้งาน", "เชื่อม Shizuku และได้สิทธิ์แล้ว แอปอ่านและแก้ไฟล์ใน Android/data ได้")
        s.shizukuStatus == ShizukuManager.Status.READY -> Triple(Palette.Warn, "กำลังเริ่มตัวช่วยอ่านไฟล์…", "รอสักครู่ ถ้าค้างนาน ให้กดตรวจอีกครั้ง")
        s.shizukuStatus == ShizukuManager.Status.NO_PERMISSION -> Triple(Palette.Warn, "Shizuku ทำงานอยู่ แต่ยังไม่อนุญาตแอปนี้", "กดปุ่ม \"อนุญาต Shizuku\" แล้วเลือกอนุญาต")
        s.shizukuStatus == ShizukuManager.Status.UNSUPPORTED -> Triple(Palette.Bad, "Shizuku เวอร์ชันเก่าเกินไป", "อัปเดต Shizuku เป็นเวอร์ชันล่าสุดจาก Play Store")
        else -> Triple(Palette.Bad, "Shizuku ยังไม่ทำงาน", "ทำตามคู่มือด้านล่างทีละขั้น ใช้เวลาประมาณ 5 นาที และทำครั้งแรกครั้งเดียว")
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (s.shizukuBound) Icons.Default.Done else Icons.Default.Lock, null, tint = color)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(detail, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.shizukuStatus == ShizukuManager.Status.NO_PERMISSION) {
                    Button(onClick = vm::requestShizukuPermission, shape = RoundedCornerShape(12.dp)) { Text("อนุญาต Shizuku") }
                } else if (!s.shizukuBound) {
                    Button(onClick = { openShizuku(context) }, shape = RoundedCornerShape(12.dp)) { Text("เปิด Shizuku") }
                }
                OutlinedButton(onClick = vm::refreshShizuku, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ตรวจอีกครั้ง")
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(rom: Rom) {
    SectionCard("เครื่องของคุณ", icon = Icons.Default.Phone) {
        Text(
            "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            style = MaterialTheme.typography.bodyMedium,
        )
        Pill(rom.label, MaterialTheme.colorScheme.secondary)
        if (rom != Rom.OTHER) {
            Text(
                "ขั้นตอนและวิธีแก้ปัญหาด้านล่างปรับให้ตรงกับยี่ห้อนี้แล้ว",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------- คู่มือทีละขั้น ----------------

private fun devOptionsPath(rom: Rom): String = when (rom) {
    Rom.COLOROS -> "ตั้งค่า > เกี่ยวกับอุปกรณ์ > เวอร์ชัน > แตะ \"หมายเลขเวอร์ชัน\" (Build number) 7 ครั้งติดกัน แล้วใส่รหัสปลดล็อกหน้าจอ เมนูนักพัฒนาจะอยู่ที่ ตั้งค่า > ระบบ (หรือ ตั้งค่าเพิ่มเติม) > ตัวเลือกสำหรับนักพัฒนา"
    Rom.XIAOMI -> "ตั้งค่า > เกี่ยวกับโทรศัพท์ > แตะ \"เวอร์ชัน OS\" (HyperOS/MIUI) 7 ครั้งติดกัน แล้วใส่รหัสปลดล็อกหน้าจอ เมนูนักพัฒนาจะอยู่ที่ ตั้งค่า > การตั้งค่าเพิ่มเติม > ตัวเลือกสำหรับนักพัฒนา"
    Rom.SAMSUNG -> "ตั้งค่า > เกี่ยวกับโทรศัพท์ > ข้อมูลซอฟต์แวร์ > แตะ \"หมายเลขรุ่นสร้าง\" (Build number) 7 ครั้งติดกัน แล้วใส่รหัสปลดล็อกหน้าจอ เมนูนักพัฒนาจะอยู่ล่างสุดของหน้าการตั้งค่า"
    else -> "ตั้งค่า > เกี่ยวกับโทรศัพท์ > แตะ \"หมายเลขบิลด์\" (Build number) 7 ครั้งติดกัน แล้วใส่รหัสปลดล็อกหน้าจอ จนขึ้นว่า \"คุณเป็นนักพัฒนาแล้ว\" เมนูนักพัฒนามักอยู่ใน ตั้งค่า > ระบบ"
}

@Composable
private fun GuideCard(s: UiState, rom: Rom, shizukuInstalled: Boolean, vm: MainViewModel) {
    val context = LocalContext.current
    val running = s.shizukuStatus == ShizukuManager.Status.READY || s.shizukuStatus == ShizukuManager.Status.NO_PERMISSION
    SectionCard("วิธีเชื่อม Shizuku (ทำครั้งแรกครั้งเดียว)", subtitle = "Shizuku ให้แอปนี้ใช้สิทธิ์ระดับ adb เพื่อเข้าโฟลเดอร์ Android/data ของเกม โดยไม่ต้อง root") {
        GuideStep(1, "ติดตั้งแอป Shizuku", done = shizukuInstalled,
            text = "ดาวน์โหลดแอป Shizuku (ผู้พัฒนา Xingchen & Rikka) จาก Play Store ฟรี",
        ) {
            if (!shizukuInstalled) FilledTonalButton(onClick = { openStore(context) }) { Text("เปิด Play Store") }
        }
        GuideStep(2, "เปิดตัวเลือกสำหรับนักพัฒนา", text = devOptionsPath(rom)) {
            FilledTonalButton(onClick = { openDevOptions(context) }) { Text("เปิดตัวเลือกนักพัฒนา") }
        }
        GuideStep(3, "เปิดการแก้ไขข้อบกพร่องแบบไร้สาย",
            text = "เชื่อมต่อ Wi-Fi ก่อน (ไม่ต้องมีอินเทอร์เน็ตก็ได้ แต่ต้องเชื่อม Wi-Fi) จากนั้นในตัวเลือกสำหรับนักพัฒนา เปิดสวิตช์ \"การแก้ไขข้อบกพร่องแบบไร้สาย\" (Wireless debugging) แล้วกดอนุญาตเมื่อระบบถามถึงเครือข่ายนี้\n\nแนะนำ: เปิด \"ปิดการหมดเวลาการให้สิทธิ์ adb\" (Disable adb authorization timeout) ด้วย ถ้ามีตัวเลือกนี้ จะได้ไม่ต้องจับคู่ใหม่บ่อย",
        )
        GuideStep(4, "จับคู่ Shizuku กับระบบ",
            text = "1) เปิด Shizuku ในหัวข้อ \"เริ่มต้นผ่านการแก้ไขข้อบกพร่องแบบไร้สาย\" กด \"จับคู่\" Shizuku จะขึ้นการแจ้งเตือนค้างไว้ (ถ้าถามสิทธิ์แจ้งเตือน ให้กดอนุญาต)\n" +
                "2) กลับไปหน้า \"การแก้ไขข้อบกพร่องแบบไร้สาย\" แตะ \"จับคู่อุปกรณ์ด้วยรหัสการจับคู่\" จะเห็นรหัส 6 หลัก\n" +
                "3) ดึงแถบแจ้งเตือนลงมาโดยไม่ปิดหน้ารหัส กด \"ป้อนรหัสการจับคู่\" ในการแจ้งเตือนของ Shizuku แล้วพิมพ์รหัส 6 หลัก\n" +
                "4) รอจนการแจ้งเตือนบอกว่าจับคู่สำเร็จ",
        ) {
            FilledTonalButton(onClick = { openShizuku(context) }) { Text("เปิด Shizuku") }
        }
        GuideStep(5, "เริ่ม Shizuku", done = running,
            text = "ในแอป Shizuku กด \"เริ่ม\" ในหัวข้อเดิม รอจนหน้าแรกขึ้นว่า Shizuku กำลังทำงาน\n\nทุกครั้งที่รีสตาร์ทเครื่อง ต้องเปิด Wireless debugging แล้วกด \"เริ่ม\" ใหม่ แต่ไม่ต้องจับคู่ใหม่",
        )
        GuideStep(6, "อนุญาตแอปนี้", done = s.shizukuBound,
            text = "กลับมาที่ FPS Unlocker กดปุ่ม \"อนุญาต Shizuku\" ในการ์ดด้านบน แล้วเลือก \"อนุญาตตลอด\" สถานะมุมขวาบนจะเปลี่ยนเป็นสีเขียว",
        ) {
            if (s.shizukuStatus == ShizukuManager.Status.NO_PERMISSION) {
                Button(onClick = vm::requestShizukuPermission) { Text("อนุญาต Shizuku") }
            }
        }
        if (rom == Rom.COLOROS || rom == Rom.XIAOMI || rom == Rom.MEIZU) {
            NoticeCard(
                color = Palette.Warn,
                text = "ยี่ห้อนี้มักจำกัดสิทธิ์ของ adb ถ้าขั้นที่ 6 ขึ้นว่า \"การอนุญาตของ adb นั้นจำกัด\" ให้ดูวิธีแก้ในหัวข้อแก้ปัญหาที่ไฮไลต์ไว้ด้านล่าง",
            )
        }
    }
}

@Composable
private fun GuideStep(
    n: Int,
    title: String,
    text: String,
    done: Boolean = false,
    actions: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        StepNumber(n, done)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions()
        }
    }
}

@Composable
private fun TroubleCard(t: Trouble, highlight: Boolean) {
    var expanded by rememberSaveable(t.title, t.rom) { mutableStateOf(highlight) }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (highlight) BorderStroke(1.dp, Palette.Warn.copy(alpha = 0.6f)) else null,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(14.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = if (highlight) Palette.Warn else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    t.rom?.let {
                        Text(it.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
            }
            if (expanded) {
                t.steps.forEachIndexed { i, step ->
                    Row {
                        Text("${i + 1}.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                        Text(step, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SafCard(s: UiState, onRequestSafAccess: () -> Unit) {
    SectionCard("ทางเลือกสำรอง: SAF (Android 11–12 เท่านั้น)", subtitle = "ให้สิทธิ์เฉพาะโฟลเดอร์ของเกมที่เลือก โดยไม่ต้องใช้ Shizuku") {
        Text(
            if (s.packageName.isBlank()) "เลือกเกมในแท็บ \"เกม\" ก่อน" else "เกมที่เลือก: ${s.packageName}",
            style = MaterialTheme.typography.bodySmall,
        )
        FilledTonalButton(onClick = onRequestSafAccess, enabled = s.packageName.isNotBlank()) {
            Text(if (s.safGranted) "ได้สิทธิ์แล้ว (ขอใหม่)" else "ขอสิทธิ์โฟลเดอร์เกม")
        }
        Text(
            "ในหน้าที่เปิดขึ้น ให้กด \"ใช้โฟลเดอร์นี้\" แล้วกด \"อนุญาต\"",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SafetyCard() {
    SectionCard("ก่อนใช้งาน ควรรู้", icon = Icons.Default.Warning) {
        Text(
            "การแก้ไฟล์ของเกมออนไลน์อาจผิดข้อตกลงการใช้งานของเกม และระบบป้องกันการโกงอาจตรวจพบได้ ควรทดลองกับบัญชีสำรองก่อน " +
                "แอปนี้แก้ได้เฉพาะไฟล์ที่เป็นข้อความธรรมดา ถ้าเกมเข้ารหัสไฟล์ตั้งค่า หรือกำหนด FPS จากเซิร์ฟเวอร์ แอปจะแก้ไม่ได้ และจะไม่พยายามเจาะการเข้ารหัส",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------- เปิดหน้าอื่น ----------------

private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) {
    false
}

private fun openDevOptions(context: Context) {
    context.tryStart(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) ||
        context.tryStart(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) ||
        context.tryStart(Intent(Settings.ACTION_SETTINGS))
}

private fun openStore(context: Context) {
    context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PKG"))) ||
        context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PKG")))
}

private fun openShizuku(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG)
    if (launch == null || !context.tryStart(launch)) openStore(context)
}
