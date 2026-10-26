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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fpsunlocker.tool.R
import com.fpsunlocker.tool.data.EngineDetector
import com.fpsunlocker.tool.data.FileEntry
import com.fpsunlocker.tool.data.ScanHit

@Composable
fun TuneTab(s: UiState, vm: MainViewModel, listState: LazyListState) {
    if (s.packageName.isBlank()) {
        EmptyState(
            title = stringResource(R.string.tune_empty_title),
            text = stringResource(R.string.tune_empty_text),
            action = stringResource(R.string.tune_empty_action),
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
                    text = stringResource(R.string.tune_reverted, s.appliedNow.orEmpty(), s.applied?.value.orEmpty()),
                    action = stringResource(R.string.action_reapply),
                    onAction = vm::reapply,
                )
            }
        }
        if (!s.hasAccess) {
            item {
                NoticeCard(
                    color = Palette.Warn,
                    text = stringResource(R.string.tune_no_access),
                    action = stringResource(R.string.action_how_to_connect),
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
                            game?.publisher ?: stringResource(R.string.hero_unknown_publisher),
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
                    Pill(game?.engine ?: stringResource(R.string.hero_engine_unknown), MaterialTheme.colorScheme.primary)
                    s.dataSize?.let { Pill(stringResource(R.string.hero_data_size, it), Palette.Amber) }
                    game?.versionName?.let { Pill("v$it", MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (s.isRoot) Pill("root", Palette.Good)
                }
                Text(
                    stringResource(EngineDetector.hint(game?.engine)),
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
        stringResource(R.string.scan_title),
        icon = Icons.Default.Search,
        subtitle = stringResource(R.string.scan_subtitle),
    ) {
        Text(
            stringResource(R.string.scan_desc, s.packageName),
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
            Text(if (s.busy) stringResource(R.string.scan_working) else stringResource(R.string.scan_button))
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
    SectionCard(stringResource(R.string.hits_title, hits.size), icon = Icons.Default.Star, subtitle = stringResource(R.string.hits_subtitle)) {
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
    SectionCard(stringResource(R.string.preview_title), subtitle = path) {
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
        TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) }
    }
}

@Composable
private fun TextFilesCard(files: List<FileEntry>, selected: String, onPick: (FileEntry) -> Unit) {
    val shown = files.take(80)
    SectionCard(
        stringResource(R.string.textfiles_title, files.size),
        subtitle = if (files.size > shown.size) stringResource(R.string.textfiles_subtitle_more, shown.size) else stringResource(R.string.textfiles_subtitle),
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
    SectionCard(stringResource(R.string.config_title), icon = Icons.Default.Edit, subtitle = stringResource(R.string.config_subtitle)) {
        OutlinedTextField(
            value = s.configPath,
            onValueChange = vm::onPathChange,
            label = { Text(stringResource(R.string.config_path_label)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.fpsKey,
            onValueChange = vm::onKeyChange,
            label = { Text(stringResource(R.string.config_key_label)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow(
            title = stringResource(R.string.switch_autostop_title),
            detail = stringResource(R.string.switch_autostop_detail),
            checked = s.autoStopGame,
            onChange = vm::onAutoStopChange,
        )
        SwitchRow(
            title = stringResource(R.string.switch_addkey_title),
            detail = stringResource(R.string.switch_addkey_detail),
            checked = s.addKeyIfMissing,
            onChange = vm::onAddKeyChange,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.config_current, s.currentValue ?: "—"), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(if (s.backupExists) R.string.config_backup_yes else R.string.config_backup_no),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = { vm.preview(s.configPath) },
                enabled = s.configPath.isNotBlank() && s.hasAccess && !s.busy,
            ) { Text(stringResource(R.string.action_view_file)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FpsCard(s: UiState, vm: MainViewModel) {
    SectionCard(stringResource(R.string.fps_title)) {
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
                label = { Text(stringResource(R.string.fps_max_label)) },
                supportingText = { Text(stringResource(R.string.fps_max_help)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val maxHz = s.maxHz.toInt()
        Text(
            stringResource(R.string.fps_display_info, maxHz, s.currentHz.toInt()),
            style = MaterialTheme.typography.labelLarge,
        )
        val target = s.targetValue?.toIntOrNull()
        if (target != null && target > maxHz) {
            NoticeCard(
                color = Palette.Warn,
                text = stringResource(R.string.fps_over_display, target, maxHz),
            )
        } else {
            Text(
                stringResource(R.string.fps_note),
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
        ) { Text(stringResource(R.string.action_apply), fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = vm::restore,
            enabled = s.canEdit && s.backupExists,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f).height(54.dp),
        ) { Text(stringResource(R.string.action_restore_backup)) }
    }
}

@Composable
private fun LogCard(log: List<String>) {
    SectionCard(stringResource(R.string.log_title)) {
        if (log.isEmpty()) {
            Text(stringResource(R.string.log_empty), style = MaterialTheme.typography.bodySmall)
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
