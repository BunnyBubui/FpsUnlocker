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
import androidx.compose.ui.res.stringResource
import com.fpsunlocker.tool.R
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
        stringResource(R.string.meter_title),
        icon = Icons.Default.PlayArrow,
        subtitle = stringResource(R.string.meter_subtitle),
    ) {
        Text(
            stringResource(R.string.meter_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            !s.shizukuBound -> NoticeCard(Palette.Warn, stringResource(R.string.meter_needs_shizuku), stringResource(R.string.action_how_to_connect)) { vm.setTab(Tabs.SETUP) }
            !s.canDrawOverlay -> FilledTonalButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) { Text(stringResource(R.string.meter_grant_overlay)) }
            s.overlayRunning -> OutlinedButton(onClick = vm::stopOverlay, shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.meter_stop)) }
            else -> Button(onClick = vm::startOverlay, shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.meter_start)) }
        }
    }
}

// ---------------- บังคับ FPS ระดับระบบ ----------------

@Composable
fun ForceFpsCard(s: UiState, vm: MainViewModel) {
    SectionCard(
        stringResource(R.string.force_title),
        icon = Icons.Default.Lock,
        subtitle = stringResource(R.string.force_subtitle),
    ) {
        Text(
            stringResource(R.string.force_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ForceRow(
            title = stringResource(R.string.force_refresh_title, s.maxHz.toInt()),
            detail = if (s.refreshLocked) {
                stringResource(R.string.force_refresh_on, s.currentHz.toInt())
            } else {
                stringResource(R.string.force_refresh_off)
            },
            active = s.refreshLocked,
            enabled = s.shizukuBound && !s.busy,
            onClick = vm::toggleRefreshLock,
        )

        if (Build.VERSION.SDK_INT >= 33) {
            ForceRow(
                title = stringResource(R.string.force_gm_title),
                detail = if (s.gameModeActive) {
                    stringResource(R.string.force_gm_on)
                } else {
                    stringResource(R.string.force_gm_off)
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
                OutlinedButton(onClick = onClick, enabled = enabled) { Text(stringResource(R.string.action_off)) }
            } else {
                Button(onClick = onClick, enabled = enabled) { Text(stringResource(R.string.action_on)) }
            }
        }
    }
}

// ---------------- Backup ทุกเกม ----------------

@Composable
fun BackupsCard(s: UiState, vm: MainViewModel) {
    SectionCard(
        stringResource(R.string.backups_title, s.backups.size),
        icon = Icons.Default.Refresh,
        subtitle = stringResource(R.string.backups_subtitle),
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
                            Text(stringResource(R.string.backup_restore))
                        }
                        TextButton(onClick = { vm.forgetBackup(b) }) { Text(stringResource(R.string.backup_forget)) }
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
        title = { Text(stringResource(R.string.diff_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.diff_summary, p.path, p.key, p.value) + if (p.addedKey) stringResource(R.string.diff_added) else "",
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
                    stringResource(R.string.diff_backup_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.diff_write)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
    )
}
