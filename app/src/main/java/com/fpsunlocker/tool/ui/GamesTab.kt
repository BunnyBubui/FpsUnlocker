package com.fpsunlocker.tool.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fpsunlocker.tool.data.GameApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesTab(s: UiState, vm: MainViewModel) {
    val list = s.visibleGames

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = s.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    IconButton(onClick = vm::loadGames, enabled = !s.gamesLoading) {
                        Icon(Icons.Default.Refresh, "สแกนใหม่")
                    }
                },
                placeholder = { Text("ค้นหาชื่อเกม ค่าย หรือ package") },
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = !s.showAllApps,
                    onClick = { vm.onShowAllApps(false) },
                    label = { Text("เฉพาะเกม") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = s.showAllApps,
                    onClick = { vm.onShowAllApps(true) },
                    label = { Text("ทุกแอป") },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (s.gamesLoading) "กำลังสแกน…" else "${list.size} รายการ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!s.dataFoldersScanned && !s.gamesLoading) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Palette.Warn.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = Palette.Warn)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "ตอนนี้เห็นเฉพาะเกมที่ติดตั้ง ยังไม่ได้สแกนโฟลเดอร์ Android/data เพราะต้องเชื่อม Shizuku ก่อน",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.setTab(Tabs.SETUP) }) { Text("วิธีเชื่อม") }
                    }
                }
            }
        }

        if (!s.gamesLoading && list.isEmpty()) {
            item {
                Text(
                    if (s.query.isNotBlank()) "ไม่พบรายการที่ตรงกับ \"${s.query}\"" else "ไม่พบเกมในเครื่อง ลองกด \"ทุกแอป\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }

        items(list, key = { it.packageName }) { game ->
            GameRow(
                game = game,
                selected = game.packageName == s.packageName,
                loader = { vm.icon(it) },
                onClick = { vm.selectGame(game) },
            )
        }
    }
}

@Composable
private fun GameRow(
    game: GameApp,
    selected: Boolean,
    loader: suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GameIcon(game.packageName, game.label, 52.dp, loader)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    game.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    game.publisher,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    game.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    game.engine?.let { Pill(it, MaterialTheme.colorScheme.primary) }
                    if (game.hasDataFolder) Pill("มีโฟลเดอร์ data", Palette.Good)
                    if (!game.installed) Pill("ถอนแอปแล้ว", Palette.Bad)
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
