package com.fpsunlocker.tool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fpsunlocker.tool.data.EngineDetector
import com.fpsunlocker.tool.data.FileEntry
import com.fpsunlocker.tool.data.ScanHit

@Composable
fun TuneTab(s: UiState, vm: MainViewModel, listState: LazyListState) {
    if (s.packageName.isBlank()) {
        EmptyState(
            title = "ยังไม่ได้เลือกเกม",
            text = "ไปที่แท็บ \"เกม\" แล้วแตะเกมที่ต้องการปรับ FPS",
            action = "เลือกเกม",
            onAction = { vm.setTab(Tabs.GAMES) },
        )
        return
    }

    s.pendingApply?.let { DiffDialog(it, onConfirm = vm::confirmApply, onCancel = vm::cancelApply) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { GameHero(s, vm) }
        if (s.reverted) {
            item {
                NoticeCard(
                    color = Palette.Bad,
                    text = "ค่า FPS ที่ตั้งไว้ถูกเกมเขียนทับแล้ว ตอนนี้เป็น ${s.appliedNow} จากที่ตั้งไว้ ${s.applied?.value} (มักเกิดหลังเกมอัปเดตหรือเปลี่ยนกราฟิกในเกม)",
                    action = "ตั้งซ้ำ",
                    onAction = vm::reapply,
                )
            }
        }
        if (!s.hasAccess) {
            item {
                NoticeCard(
                    color = Palette.Warn,
                    text = "ยังเข้าถึงโฟลเดอร์ของเกมไม่ได้ ต้องเชื่อม Shizuku ก่อน",
                    action = "วิธีเชื่อม",
                    onAction = { vm.setTab(Tabs.SETUP) },
                )
            }
        }
        item { MeterCard(s, vm) }
        item { ScanCard(s, vm) }
        s.verdict?.let { v -> item { VerdictCard(v) } }
        if (s.scanHits.isNotEmpty()) item { HitsCard(s.scanHits, vm::useScanHit) }
        s.preview?.let { (path, text) -> item { PreviewCard(path, text, vm::closePreview) } }
        if (s.textFiles.isNotEmpty()) item { TextFilesCard(s.textFiles, s.configPath, vm::useTextFile) }
        item { ConfigCard(s, vm) }
        item { FpsCard(s, vm) }
        item { ActionButtons(s, vm) }
        item { ForceFpsCard(s, vm) }
        if (s.backups.isNotEmpty()) item { BackupsCard(s, vm) }
        item { LogCard(s.log) }
    }
}

// ---------------- เกมที่เลือก ----------------

@Composable
private fun GameHero(s: UiState, vm: MainViewModel) {
    val game = s.selectedGame
    val label = game?.label ?: s.packageName
    Surface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                    )
                )
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameIcon(s.packageName, label, 68.dp) { vm.icon(it) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            game?.publisher ?: "ไม่ทราบค่าย",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            s.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(game?.engine ?: "Engine: ไม่ทราบ", MaterialTheme.colorScheme.primary)
                    s.dataSize?.let { Pill("ข้อมูล $it", Palette.Amber) }
                    game?.versionName?.let { Pill("v$it", MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (s.isRoot) Pill("root", Palette.Good)
                }
                Text(
                    EngineDetector.hint(game?.engine),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------- Scan ----------------

@Composable
private fun ScanCard(s: UiState, vm: MainViewModel) {
    SectionCard(
        "สแกนไฟล์ตั้งค่า",
        icon = Icons.Default.Search,
        subtitle = "ปิดเกมก่อนสแกนและก่อนแก้ไฟล์ทุกครั้ง",
    ) {
        Text(
            "แอปจะไล่ทุกโฟลเดอร์ใน Android/data/${s.packageName} อ่านไฟล์ขนาดเล็ก แล้วหาคีย์ที่ชื่อเกี่ยวกับ FPS เช่น fps, FrameRate, FrameRateLimit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = vm::scan,
            enabled = s.hasAccess && !s.busy,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Default.Search, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (s.busy) "กำลังทำงาน…" else "Scan หาไฟล์และคีย์ FPS")
        }
    }
}

@Composable
private fun VerdictCard(v: Verdict) {
    val (color, icon) = when (v.level) {
        VerdictLevel.GOOD -> Palette.Good to Icons.Default.CheckCircle
        VerdictLevel.MAYBE -> Palette.Warn to Icons.Default.Info
        VerdictLevel.BAD -> Palette.Bad to Icons.Default.Warning
    }
    Surface(shape = RoundedCornerShape(18.dp), color = color.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.width(12.dp))
            Text(v.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HitsCard(hits: List<ScanHit>, onPick: (ScanHit) -> Unit) {
    SectionCard("คีย์ที่น่าจะเป็น FPS (${hits.size})", icon = Icons.Default.Star, subtitle = "แตะเพื่อเลือกไฟล์และคีย์นั้น") {
        hits.forEach { hit ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(hit) },
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(hit.key, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text(hit.path, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        hit.line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(path: String, text: String, onClose: () -> Unit) {
    SectionCard("เนื้อหาไฟล์", subtitle = path) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
        TextButton(onClick = onClose) { Text("ปิด") }
    }
}

@Composable
private fun TextFilesCard(files: List<FileEntry>, selected: String, onPick: (FileEntry) -> Unit) {
    val shown = files.take(80)
    SectionCard(
        "ไฟล์ที่อ่านเป็นข้อความได้ (${files.size})",
        subtitle = "แตะเพื่อเลือกและเปิดดู" + if (files.size > shown.size) " · แสดง ${shown.size} ไฟล์แรก" else "",
    ) {
        shown.forEach { entry ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(entry) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.path,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (entry.path == selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(formatSize(entry.size), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ---------------- ตั้งค่า + Apply ----------------

@Composable
private fun ConfigCard(s: UiState, vm: MainViewModel) {
    SectionCard("ไฟล์และคีย์ที่จะแก้", icon = Icons.Default.Edit, subtitle = "เลือกจากผล Scan หรือพิมพ์เอง") {
        OutlinedTextField(
            value = s.configPath,
            onValueChange = vm::onPathChange,
            label = { Text("Path ของไฟล์ (นับจาก Android/data/<package>/)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.fpsKey,
            onValueChange = vm::onKeyChange,
            label = { Text("ชื่อคีย์ FPS ในไฟล์") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow(
            title = "ปิดเกมอัตโนมัติก่อนแก้ไฟล์",
            detail = "เกมที่เปิดอยู่จะเขียนค่าเดิมทับตอนปิด แนะนำให้เปิดไว้",
            checked = s.autoStopGame,
            onChange = vm::onAutoStopChange,
        )
        SwitchRow(
            title = "เพิ่มคีย์ถ้าไม่มีในไฟล์",
            detail = "ใช้เมื่อรู้ชื่อคีย์ที่เกมอ่านจริงเท่านั้น ถ้าชื่อผิด เกมจะไม่สนใจคีย์นี้",
            checked = s.addKeyIfMissing,
            onChange = vm::onAddKeyChange,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ค่าปัจจุบัน: ${s.currentValue ?: "—"}", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Backup: ${if (s.backupExists) "มีแล้ว" else "ยังไม่มี"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = { vm.preview(s.configPath) },
                enabled = s.configPath.isNotBlank() && s.hasAccess && !s.busy,
            ) { Text("ดูเนื้อหาไฟล์") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FpsCard(s: UiState, vm: MainViewModel) {
    SectionCard("เป้าหมาย FPS") {
        val options = FpsOption.entries
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = s.fps == option,
                    onClick = { vm.onFpsSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(option.label, maxLines = 1) }
            }
        }
        if (s.fps == FpsOption.MAX) {
            OutlinedTextField(
                value = s.unlockedValue,
                onValueChange = vm::onUnlockedValueChange,
                label = { Text("ค่าที่จะเขียนเมื่อเลือก Max") },
                supportingText = { Text("แต่ละเกมต่างกัน บางเกมใช้ 0 หรือ -1 แทน \"ไม่จำกัด\"") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val maxHz = s.maxHz.toInt()
        Text(
            "จอรองรับสูงสุด ${maxHz}Hz · ตอนนี้ ${s.currentHz.toInt()}Hz",
            style = MaterialTheme.typography.labelLarge,
        )
        val target = s.targetValue?.toIntOrNull()
        if (target != null && target > maxHz) {
            NoticeCard(
                color = Palette.Warn,
                text = "ค่าที่เลือก ($target) สูงกว่าที่จอแสดงได้ (${maxHz}Hz) ภาพจะไม่ลื่นขึ้น แต่เครื่องจะร้อนและแบตหมดเร็วขึ้น",
            )
        } else {
            Text(
                "ตั้งค่าสูงกว่า refresh rate ของจอไม่ทำให้ภาพลื่นขึ้น แต่ทำให้เครื่องร้อนและแบตหมดเร็วขึ้น",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionButtons(s: UiState, vm: MainViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = vm::prepareApply,
            enabled = s.canEdit,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f).height(54.dp),
        ) { Text("Apply", fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = vm::restore,
            enabled = s.canEdit && s.backupExists,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f).height(54.dp),
        ) { Text("Restore Backup") }
    }
}

@Composable
private fun LogCard(log: List<String>) {
    SectionCard("Log") {
        if (log.isEmpty()) {
            Text("ยังไม่มีรายการ", style = MaterialTheme.typography.bodySmall)
        }
        log.forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

// ---------------- ใช้ร่วมกัน ----------------

@Composable
fun NoticeCard(color: Color, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = color)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun EmptyState(title: String, text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Palette.Cyan, Palette.Violet))),
            contentAlignment = Alignment.Center,
        ) {
            Text("FPS", color = Color(0xFF0B0F1C), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAction, shape = RoundedCornerShape(14.dp)) { Text(action) }
    }
}

fun formatSize(bytes: Long): String =
    if (bytes < 1024) "$bytes B" else "%.1f kB".format(bytes / 1024.0)
