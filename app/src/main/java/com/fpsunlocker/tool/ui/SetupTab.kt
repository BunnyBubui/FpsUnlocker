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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.fpsunlocker.tool.R
import com.fpsunlocker.tool.shizuku.ShizukuManager

private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

/** กลุ่มของ ROM ที่มีวิธีแก้ปัญหาต่างกัน (ชื่อยี่ห้อไม่ต้องแปล ยกเว้น "ยี่ห้ออื่น") */
enum class Rom(val label: String?) {
    COLOROS("OPPO / OnePlus / realme (ColorOS)"),
    XIAOMI("Xiaomi / Redmi / POCO (HyperOS / MIUI)"),
    SAMSUNG("Samsung (One UI)"),
    MEIZU("Meizu (Flyme)"),
    OTHER(null),
}

@Composable
private fun Rom.displayName(): String = label ?: stringResource(R.string.rom_other)

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

private data class Trouble(@StringRes val title: Int, val rom: Rom?, val steps: List<Int>)

private val TROUBLES = listOf(
    Trouble(
        R.string.trouble_adb_title, Rom.COLOROS,
        listOf(R.string.trouble_coloros_1, R.string.trouble_coloros_2, R.string.trouble_coloros_3, R.string.trouble_restart_shizuku),
    ),
    Trouble(
        R.string.trouble_adb_title, Rom.XIAOMI,
        listOf(R.string.trouble_xiaomi_1, R.string.trouble_xiaomi_2, R.string.trouble_restart_shizuku),
    ),
    Trouble(R.string.trouble_adb_title, Rom.MEIZU, listOf(R.string.trouble_meizu_1, R.string.trouble_restart_shizuku)),
    Trouble(R.string.trouble_samsung_title, Rom.SAMSUNG, listOf(R.string.trouble_samsung_1, R.string.trouble_samsung_2)),
    Trouble(R.string.trouble_pair_title, null, listOf(R.string.trouble_pair_1, R.string.trouble_pair_2, R.string.trouble_pair_3)),
    Trouble(R.string.trouble_reboot_title, null, listOf(R.string.trouble_reboot_1, R.string.trouble_reboot_2)),
    Trouble(R.string.trouble_scan_title, null, listOf(R.string.trouble_scan_1, R.string.trouble_scan_2, R.string.trouble_scan_3)),
    Trouble(R.string.trouble_off_title, null, listOf(R.string.trouble_off_1, R.string.trouble_off_2, R.string.trouble_off_3)),
)

@Composable
fun SetupTab(s: UiState, vm: MainViewModel, onRequestSafAccess: () -> Unit, listState: LazyListState) {
    val context = LocalContext.current
    val rom = remember { detectRom() }
    val shizukuInstalled = remember(s.shizukuStatus) {
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PKG) != null
    }
    val troubles = remember(rom) { TROUBLES.filter { it.rom == null || it.rom == rom } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatusCard(s, vm) }
        item { DeviceCard(rom) }
        item { GuideCard(s, rom, shizukuInstalled, vm) }
        item {
            Text(
                stringResource(R.string.troubles_header),
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
        s.shizukuBound -> Triple(Palette.Good, R.string.setup_ready_title, R.string.setup_ready_detail)
        s.shizukuStatus == ShizukuManager.Status.READY -> Triple(Palette.Warn, R.string.setup_starting_title, R.string.setup_starting_detail)
        s.shizukuStatus == ShizukuManager.Status.NO_PERMISSION -> Triple(Palette.Warn, R.string.setup_noperm_title, R.string.setup_noperm_detail)
        s.shizukuStatus == ShizukuManager.Status.UNSUPPORTED -> Triple(Palette.Bad, R.string.setup_old_title, R.string.setup_old_detail)
        else -> Triple(Palette.Bad, R.string.setup_off_title, R.string.setup_off_detail)
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
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(detail), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.shizukuStatus == ShizukuManager.Status.NO_PERMISSION) {
                    Button(onClick = vm::requestShizukuPermission, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.action_allow_shizuku))
                    }
                } else if (!s.shizukuBound) {
                    Button(onClick = { openShizuku(context) }, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.action_open_shizuku))
                    }
                }
                OutlinedButton(onClick = vm::refreshShizuku, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_check_again))
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(rom: Rom) {
    SectionCard(stringResource(R.string.device_title), icon = Icons.Default.Phone) {
        Text(
            stringResource(
                R.string.device_line,
                Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                Build.MODEL,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Pill(rom.displayName(), MaterialTheme.colorScheme.secondary)
        if (rom != Rom.OTHER) {
            Text(
                stringResource(R.string.device_tailored),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------- คู่มือทีละขั้น ----------------

@StringRes
private fun devOptionsPath(rom: Rom): Int = when (rom) {
    Rom.COLOROS -> R.string.devpath_coloros
    Rom.XIAOMI -> R.string.devpath_xiaomi
    Rom.SAMSUNG -> R.string.devpath_samsung
    else -> R.string.devpath_other
}

@Composable
private fun GuideCard(s: UiState, rom: Rom, shizukuInstalled: Boolean, vm: MainViewModel) {
    val context = LocalContext.current
    val running = s.shizukuStatus == ShizukuManager.Status.READY || s.shizukuStatus == ShizukuManager.Status.NO_PERMISSION
    SectionCard(stringResource(R.string.guide_title), subtitle = stringResource(R.string.guide_subtitle)) {
        GuideStep(1, stringResource(R.string.step1_title), stringResource(R.string.step1_text), done = shizukuInstalled) {
            if (!shizukuInstalled) {
                FilledTonalButton(onClick = { openStore(context) }) { Text(stringResource(R.string.action_open_store)) }
            }
        }
        GuideStep(2, stringResource(R.string.step2_title), stringResource(devOptionsPath(rom))) {
            FilledTonalButton(onClick = { openDevOptions(context) }) { Text(stringResource(R.string.action_open_dev)) }
        }
        GuideStep(3, stringResource(R.string.step3_title), stringResource(R.string.step3_text))
        GuideStep(4, stringResource(R.string.step4_title), stringResource(R.string.step4_text)) {
            FilledTonalButton(onClick = { openShizuku(context) }) { Text(stringResource(R.string.action_open_shizuku)) }
        }
        GuideStep(5, stringResource(R.string.step5_title), stringResource(R.string.step5_text), done = running)
        GuideStep(6, stringResource(R.string.step6_title), stringResource(R.string.step6_text), done = s.shizukuBound) {
            if (s.shizukuStatus == ShizukuManager.Status.NO_PERMISSION) {
                Button(onClick = vm::requestShizukuPermission) { Text(stringResource(R.string.action_allow_shizuku)) }
            }
        }
        if (rom == Rom.COLOROS || rom == Rom.XIAOMI || rom == Rom.MEIZU) {
            NoticeCard(color = Palette.Warn, text = stringResource(R.string.guide_brand_warning))
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
                Icon(
                    Icons.Default.Warning, null,
                    tint = if (highlight) Palette.Warn else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(t.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    t.rom?.let {
                        Text(it.displayName(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
            }
            if (expanded) {
                t.steps.forEachIndexed { i, step ->
                    Row {
                        Text("${i + 1}.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                        Text(stringResource(step), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SafCard(s: UiState, onRequestSafAccess: () -> Unit) {
    SectionCard(stringResource(R.string.saf_title), subtitle = stringResource(R.string.saf_subtitle)) {
        Text(
            if (s.packageName.isBlank()) stringResource(R.string.saf_pick_game) else stringResource(R.string.saf_selected, s.packageName),
            style = MaterialTheme.typography.bodySmall,
        )
        FilledTonalButton(onClick = onRequestSafAccess, enabled = s.packageName.isNotBlank()) {
            Text(stringResource(if (s.safGranted) R.string.saf_granted else R.string.saf_request))
        }
        Text(
            stringResource(R.string.saf_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SafetyCard() {
    SectionCard(stringResource(R.string.safety_title), icon = Icons.Default.Warning) {
        Text(
            stringResource(R.string.safety_text),
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
