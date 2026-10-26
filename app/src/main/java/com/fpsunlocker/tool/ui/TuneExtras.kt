package com.fpsunlocker.tool.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------- ตัววัด FPS ----------------

@Composable
fun MeterCard(s: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    SectionCard(
        "ตัววัด FPS ขณะเล่น",
        icon = Icons.Default.PlayArrow,
        subtitle = "ตัวเลขลอยบนจอ ใช้ดูว่า FPS เปลี่ยนจริงหรือไม่ก่อนและหลังแก้",
    ) {
        Text(
            "อ่านเวลาที่แต่ละเฟรมขึ้นจอจากระบบผ่าน Shizuku ถ้าหาภาพของเกมเจอจะวัดเฉพาะเกม (เฉพาะเกม) ถ้าไม่เจอจะวัดทั้งจอแทน (ทั้งจอ) " +
                "ลากตัวเลขเพื่อย้าย และกดค้างที่ตัวเลขเพื่อปิด",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            !s.shizukuBound -> NoticeCard(Palette.Warn, "ตัววัดต้องใช้ Shizuku", "วิธีเชื่อม") { vm.setTab(Tabs.SETUP) }
            !s.canDrawOverlay -> FilledTonalButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) { Text("อนุญาตให้แสดงทับแอปอื่น") }
            s.overlayRunning -> OutlinedButton(onClick = vm::stopOverlay, shape = RoundedCornerShape(12.dp)) { Text("ปิดตัววัด FPS") }
            else -> Button(onClick = vm::startOverlay, shape = RoundedCornerShape(12.dp)) { Text("เปิดตัววัด FPS") }
        }
    }
}

// ---------------- บังคับ FPS ระดับระบบ ----------------

@Composable
fun ForceFpsCard(s: UiState, vm: MainViewModel) {
    SectionCard(
        "บังคับ FPS ระดับระบบ",
        icon = Icons.Default.Lock,
        subtitle = "ใช้ได้กับทุกเกม รวมถึงเกมที่แก้ไฟล์ไม่ได้",
    ) {
        Text(
            "ข้อจำกัดที่ต้องรู้: Android ไม่มีช่องทางให้ดัน FPS ของเกมสูงกว่าเพดานที่เกมกำหนดเอง " +
                "สิ่งที่ระบบทำได้มี 2 อย่าง คือไม่ให้จอลด Hz ลงเองระหว่างเล่น และล็อก FPS ของเกมให้นิ่ง (ลดลงได้ เพิ่มไม่ได้)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ForceRow(
            title = "ล็อกจอที่ ${s.maxHz.toInt()}Hz",
            detail = if (s.refreshLocked) {
                "เปิดอยู่ · ตอนนี้จอ ${s.currentHz.toInt()}Hz (ถ้าไม่ขึ้น แปลว่า ROM ไม่ทำตามค่านี้)"
            } else {
                "กันระบบลด Hz เองระหว่างเล่น เปลืองแบตขึ้น ปิดเมื่อไม่ใช้"
            },
            active = s.refreshLocked,
            enabled = s.shizukuBound && !s.busy,
            onClick = vm::toggleRefreshLock,
        )

        if (Build.VERSION.SDK_INT >= 33) {
            ForceRow(
                title = "Game Mode: Performance + ล็อก FPS",
                detail = if (s.gameModeActive) {
                    "เปิดอยู่กับเกมนี้ · เปิดเกมใหม่เพื่อให้มีผล"
                } else {
                    "ตั้งโหมด Performance และจำกัด FPS ไม่เกินค่าที่เลือกด้านบน (30/40/45/60/90/120) ช่วยให้เฟรมนิ่ง ลดความร้อน บางยี่ห้ออาจไม่รองรับ"
                },
                active = s.gameModeActive,
                enabled = s.shizukuBound && s.packageName.isNotBlank() && !s.busy,
                onClick = vm::toggleGameMode,
            )
        }
    }
}

@Composable
private fun ForceRow(title: String, detail: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) Palette.Good.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (active) Palette.Good else MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (active) {
                OutlinedButton(onClick = onClick, enabled = enabled) { Text("ปิด") }
            } else {
                Button(onClick = onClick, enabled = enabled) { Text("เปิด") }
            }
        }
    }
}

// ---------------- Backup ทุกเกม ----------------

@Composable
fun BackupsCard(s: UiState, vm: MainViewModel) {
    SectionCard(
        "Backup ทั้งหมด (${s.backups.size})",
        icon = Icons.Default.Refresh,
        subtitle = "ไฟล์ต้นฉบับที่แอปสำรองไว้ก่อนแก้ครั้งแรก กดคืนค่าได้ทุกเมื่อ",
    ) {
        s.backups.forEach { b ->
            val label = s.games.firstOrNull { it.packageName == b.packageName }?.label ?: b.packageName
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        b.path,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { vm.restoreBackup(b) }, enabled = s.shizukuBound && !s.busy) {
                            Text("คืนค่าไฟล์เดิม")
                        }
                        TextButton(onClick = { vm.forgetBackup(b) }) { Text("ลบออกจากรายการ") }
                    }
                }
            }
        }
    }
}

// ---------------- หน้าต่างเปรียบเทียบก่อนเขียนไฟล์ ----------------

@Composable
fun DiffDialog(p: PendingApply, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("ตรวจก่อนเขียนไฟล์") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "ไฟล์ ${p.path}\nตั้ง ${p.key} = ${p.value}" + if (p.addedKey) " (เพิ่มคีย์ใหม่)" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
                p.changes.forEach { (old, new) ->
                    Surface(shape = RoundedCornerShape(8.dp), color = Palette.Bad.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                        Text("− $old", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp))
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = Palette.Good.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                        Text("+ $new", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp))
                    }
                }
                Text(
                    "แอปจะสำรองไฟล์ต้นฉบับไว้ก่อน (ครั้งแรกที่แก้) กดคืนค่าได้ภายหลัง",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("เขียนไฟล์") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("ยกเลิก") } },
    )
}
