package com.fpsunlocker.tool.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fpsunlocker.tool.R
import com.fpsunlocker.tool.shizuku.ShizukuManager

@Composable
fun MainScreen(vm: MainViewModel, onRequestSafAccess: () -> Unit) {
    val s by vm.ui.collectAsStateWithLifecycle()

    // เก็บตำแหน่งการเลื่อนของแต่ละแท็บไว้ที่นี่ สลับแท็บไปมาแล้วจะกลับมาที่ตำแหน่งเดิม
    val gamesListState = rememberLazyListState()
    val tuneListState = rememberLazyListState()
    val setupListState = rememberLazyListState()
    // เปลี่ยนเกมแล้วให้หน้าปรับ FPS เริ่มจากด้านบน
    LaunchedEffect(s.packageName) { runCatching { tuneListState.scrollToItem(0) } }

    Scaffold(
        topBar = { TopBar(s.shizukuStatus, s.shizukuBound) { vm.setTab(Tabs.SETUP) } },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                NavigationBarItem(
                    selected = s.tab == Tabs.GAMES,
                    onClick = { vm.setTab(Tabs.GAMES) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    label = { Text(stringResource(R.string.tab_games)) },
                )
                NavigationBarItem(
                    selected = s.tab == Tabs.TUNE,
                    onClick = { vm.setTab(Tabs.TUNE) },
                    icon = { Icon(Icons.Default.Build, null) },
                    label = { Text(stringResource(R.string.tab_tune)) },
                )
                NavigationBarItem(
                    selected = s.tab == Tabs.SETUP,
                    onClick = { vm.setTab(Tabs.SETUP) },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text(stringResource(R.string.tab_setup)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (s.busy || s.gamesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (s.tab) {
                Tabs.GAMES -> GamesTab(s, vm, gamesListState)
                Tabs.TUNE -> TuneTab(s, vm, tuneListState)
                else -> SetupTab(s, vm, onRequestSafAccess, setupListState)
            }
        }
    }
}

@Composable
private fun TopBar(status: ShizukuManager.Status, bound: Boolean, onStatusClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // โลโก้แบบไล่สี
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Palette.Cyan, Palette.Violet))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "FPS",
                    color = Color(0xFF0B0F1C),
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val (color, text) = when {
                bound -> Palette.Good to stringResource(R.string.status_ready)
                status == ShizukuManager.Status.READY -> Palette.Warn to stringResource(R.string.status_connecting)
                status == ShizukuManager.Status.NO_PERMISSION -> Palette.Warn to stringResource(R.string.status_no_permission)
                else -> Palette.Bad to stringResource(R.string.status_off)
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onStatusClick),
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(color)
                    Spacer(Modifier.width(6.dp))
                    Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
