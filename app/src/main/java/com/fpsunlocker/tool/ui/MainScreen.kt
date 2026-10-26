package com.fpsunlocker.tool.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fpsunlocker.tool.data.FileEntry
import com.fpsunlocker.tool.shizuku.ShizukuManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel, onRequestSafAccess: () -> Unit) {
    val s by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("FPS Unlocker", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { AccessCard(s, vm, onRequestSafAccess) }
                item { GameCard(s, vm) }
                item { ConfigCard(s, vm) }
                s.preview?.let { (path, text) -> item { PreviewCard(path, text, vm::closePreview) } }
                if (s.textFiles.isNotEmpty()) item { TextFilesCard(s.textFiles, s.configPath, vm::useTextFile) }
                item { FpsCard(s, vm) }
                item { ActionButtons(s, vm) }
                item { LogCard(s.log) }
            }
        }
    }
}

// ---------------- Cards ----------------

@Composable
private fun AccessCard(s: UiState, vm: MainViewModel, onRequestSafAccess: () -> Unit) {
    SectionCard("1. สิทธิ์เข้าถึงไฟล์") {
        val (color, icon) = when (s.shizukuStatus) {
            ShizukuManager.Status.READY -> Color(0xFF2E7D32) to Icons.Default.Done
            ShizukuManager.Status.NO_PERMISSION -> Color(0xFFEF6C00) to Icons.Default.Lock
            else -> Color(0xFFC62828) to Icons.Default.Warning
        }
        StatusRow(color, icon, s.shizukuStatus.label)
        if (s.shizukuStatus == ShizukuManager.Status.READY && !s.shizukuBound) {
            Text("กำลังเริ่ม FileService…", style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.shizukuStatus == ShizukuManager.Status.NO_PERMISSION) {
                Button(onClick = vm::requestShizukuPermission) { Text("อนุญาต Shizuku") }
            }
            OutlinedButton(onClick = vm::refreshShizuku) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("ตรวจอีกครั้ง")
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        val safSupported = Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
        Text(
            if (safSupported) "ทางเลือกสำรอง: ให้สิทธิ์โฟลเดอร์เกมผ่าน SAF (Android 11–12)"
            else "SAF ใช้กับ Android/data ไม่ได้บน Android 13 ขึ้นไป ต้องใช้ Shizuku",
            style = MaterialTheme.typography.bodySmall,
        )
        FilledTonalButton(
            onClick = onRequestSafAccess,
            enabled = safSupported && s.packageName.isNotBlank(),
        ) { Text(if (s.safGranted) "SAF: ได้สิทธิ์แล้ว (ขอใหม่)" else "ขอสิทธิ์โฟลเดอร์เกม (SAF)") }

        Text("ช่องทางที่ใช้อยู่: ${s.accessLabel}", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun GameCard(s: UiState, vm: MainViewModel) {
    SectionCard("2. เกม") {
        OutlinedTextField(
            value = s.packageName,
            onValueChange = vm::onPackageChange,
            label = { Text("Package name ของเกม") },
            placeholder = { Text("เช่น com.netease.xxxx") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = vm::findGamePackages, enabled = s.shizukuBound && !s.busy) {
            Icon(Icons.Default.Search, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("ค้นหาเกม NetEase ในเครื่อง")
        }
        s.gameCandidates.forEach { pkg ->
            Text(
                pkg,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.onPackageChange(pkg) }
                    .padding(vertical = 6.dp),
                color = if (pkg == s.packageName) MaterialTheme.colorScheme.primary else Color.Unspecified,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ConfigCard(s: UiState, vm: MainViewModel) {
    SectionCard("3. ไฟล์ config") {
        OutlinedTextField(
            value = s.configPath,
            onValueChange = vm::onPathChange,
            label = { Text("Path ของไฟล์ (นับจาก Android/data/<package>/)") },
            placeholder = { Text("files/…/settings.ini") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.fpsKey,
            onValueChange = vm::onKeyChange,
            label = { Text("ชื่อคีย์ FPS ในไฟล์") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::scan, enabled = s.packageName.isNotBlank() && !s.busy) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scan")
            }
            OutlinedButton(
                onClick = { vm.preview(s.configPath) },
                enabled = s.configPath.isNotBlank() && !s.busy,
            ) { Text("ดูเนื้อหาไฟล์") }
        }

        if (s.scanHits.isNotEmpty()) {
            Text("แตะรายการเพื่อใช้ไฟล์และคีย์นั้น", style = MaterialTheme.typography.bodySmall)
            s.scanHits.forEach { hit ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().clickable { vm.useScanHit(hit) },
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(hit.key, fontWeight = FontWeight.SemiBold)
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

        Text(
            "ค่าปัจจุบันในไฟล์: ${s.currentValue ?: "—"}   ·   Backup: ${if (s.backupExists) "มี" else "ยังไม่มี"}",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FpsCard(s: UiState, vm: MainViewModel) {
    SectionCard("4. FPS Target") {
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
        if (s.fps == FpsOption.UNLOCKED) {
            OutlinedTextField(
                value = s.unlockedValue,
                onValueChange = vm::onUnlockedValueChange,
                label = { Text("ค่าที่จะเขียนสำหรับ Unlocked") },
                supportingText = { Text("แต่ละเกมต่างกัน บางเกมใช้ 0 หรือ -1 แทน 'ไม่จำกัด'") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActionButtons(s: UiState, vm: MainViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = vm::apply,
            enabled = s.canEdit,
            modifier = Modifier.weight(1f).height(52.dp),
        ) { Text("Apply Settings") }
        OutlinedButton(
            onClick = vm::restore,
            enabled = s.canEdit && s.backupExists,
            modifier = Modifier.weight(1f).height(52.dp),
        ) { Text("Restore Backup") }
    }
}

@Composable
private fun PreviewCard(path: String, text: String, onClose: () -> Unit) {
    SectionCard("เนื้อหาไฟล์") {
        Text(path, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
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
    SectionCard("ไฟล์ที่อ่านเป็นข้อความได้ (${files.size})") {
        Text(
            "แตะเพื่อเลือกและเปิดดูเนื้อหา" + if (files.size > shown.size) " · แสดง ${shown.size} ไฟล์แรก" else "",
            style = MaterialTheme.typography.bodySmall,
        )
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

private fun formatSize(bytes: Long): String =
    if (bytes < 1024) "$bytes B" else "%.1f kB".format(bytes / 1024.0)

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

// ---------------- building blocks ----------------

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun StatusRow(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = color.copy(alpha = 0.15f)) {
            Icon(icon, null, tint = color, modifier = Modifier.padding(6.dp).size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
