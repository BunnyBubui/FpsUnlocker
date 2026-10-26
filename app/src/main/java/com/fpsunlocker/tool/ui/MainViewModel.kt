package com.fpsunlocker.tool.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fpsunlocker.tool.data.ConfigEditor
import com.fpsunlocker.tool.data.FileAccess
import com.fpsunlocker.tool.data.SafFileAccess
import com.fpsunlocker.tool.data.ScanHit
import com.fpsunlocker.tool.data.ShizukuFileAccess
import com.fpsunlocker.tool.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FpsOption(val label: String, val value: String?) {
    FPS_60("60", "60"),
    FPS_90("90", "90"),
    FPS_120("120", "120"),
    UNLOCKED("Unlocked", null), // ใช้ค่าที่ผู้ใช้กรอกเอง เพราะแต่ละเกมใช้ค่าต่างกัน (0, -1, 999 ...)
}

data class UiState(
    val shizukuStatus: ShizukuManager.Status = ShizukuManager.Status.NOT_RUNNING,
    val shizukuBound: Boolean = false,
    val safGranted: Boolean = false,
    val packageName: String = "",
    val gameCandidates: List<String> = emptyList(),
    val configPath: String = "",
    val fpsKey: String = "",
    val fps: FpsOption = FpsOption.FPS_60,
    val unlockedValue: String = "999",
    val currentValue: String? = null,
    val backupExists: Boolean = false,
    val scanHits: List<ScanHit> = emptyList(),
    val busy: Boolean = false,
    val log: List<String> = emptyList(),
) {
    val accessLabel: String
        get() = when {
            shizukuBound -> "Shizuku"
            safGranted -> "SAF"
            else -> "ยังไม่มีสิทธิ์"
        }
    val canEdit: Boolean
        get() = (shizukuBound || safGranted) && packageName.isNotBlank() &&
            configPath.isNotBlank() && fpsKey.isNotBlank() && !busy
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val shizuku = ShizukuManager()

    private val _ui = MutableStateFlow(
        UiState(
            packageName = prefs.getString(KEY_PKG, "").orEmpty(),
            configPath = prefs.getString(KEY_PATH, "").orEmpty(),
            fpsKey = prefs.getString(KEY_FPS_KEY, "").orEmpty(),
            unlockedValue = prefs.getString(KEY_UNLOCKED, "999").orEmpty(),
        )
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        shizuku.start()
        updateSafState()
        viewModelScope.launch {
            combine(shizuku.status, shizuku.service) { status, service -> status to (service != null) }
                .collect { (status, bound) ->
                    _ui.update { it.copy(shizukuStatus = status, shizukuBound = bound) }
                    refreshFileInfo()
                }
        }
    }

    override fun onCleared() {
        shizuku.stop()
    }

    // ---------- Shizuku / SAF ----------

    fun refreshShizuku() = shizuku.refresh()
    fun requestShizukuPermission() = shizuku.requestPermission()

    fun onTreeGranted(uri: Uri) {
        prefs.edit().putString(KEY_TREE, uri.toString()).apply()
        updateSafState()
        log("ได้สิทธิ์โฟลเดอร์ผ่าน SAF แล้ว")
        refreshFileInfo()
    }

    private fun updateSafState() {
        _ui.update { it.copy(safGranted = safAccess() != null) }
    }

    private fun safAccess(): SafFileAccess? {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) return null
        val uri = prefs.getString(KEY_TREE, null) ?: return null
        val pkg = _ui.value.packageName.trim()
        return runCatching { SafFileAccess(getApplication(), Uri.parse(uri)) }
            .getOrNull()
            ?.takeIf { it.rootName == pkg } // สิทธิ์ต้องตรงกับโฟลเดอร์ของเกมที่เลือก
    }

    /** เลือกช่องทาง: Shizuku ก่อน ถ้าไม่มีค่อยใช้ SAF */
    private fun fileAccess(): FileAccess? {
        val pkg = _ui.value.packageName.trim()
        if (pkg.isEmpty()) return null
        shizuku.service.value?.let { return ShizukuFileAccess(it, "/storage/emulated/0/Android/data/$pkg") }
        return safAccess()
    }

    // ---------- input ----------

    fun onPackageChange(v: String) {
        _ui.update { it.copy(packageName = v.trim(), scanHits = emptyList()) }
        prefs.edit().putString(KEY_PKG, v.trim()).apply()
        updateSafState()
        refreshFileInfo()
    }

    fun onPathChange(v: String) {
        _ui.update { it.copy(configPath = v.trim()) }
        prefs.edit().putString(KEY_PATH, v.trim()).apply()
        refreshFileInfo()
    }

    fun onKeyChange(v: String) {
        _ui.update { it.copy(fpsKey = v.trim()) }
        prefs.edit().putString(KEY_FPS_KEY, v.trim()).apply()
        refreshFileInfo()
    }

    fun onFpsSelected(option: FpsOption) = _ui.update { it.copy(fps = option) }

    fun onUnlockedValueChange(v: String) {
        val clean = v.filter { it.isDigit() || it == '-' }.take(5)
        _ui.update { it.copy(unlockedValue = clean) }
        prefs.edit().putString(KEY_UNLOCKED, clean).apply()
    }

    fun useScanHit(hit: ScanHit) {
        onPathChange(hit.path)
        onKeyChange(hit.key)
        log("เลือก ${hit.key} ใน ${hit.path}")
    }

    // ---------- actions ----------

    /** หาแพ็กเกจที่น่าจะเป็นเกม (ต้องใช้ Shizuku) */
    fun findGamePackages() {
        val access = fileAccess() as? ShizukuFileAccess
            ?: shizuku.service.value?.let { ShizukuFileAccess(it, "/") }
        if (access == null) {
            log("✗ ต้องเชื่อม Shizuku ก่อนจึงจะค้นหาแพ็กเกจได้ (หรือพิมพ์ชื่อแพ็กเกจเอง)")
            return
        }
        launchIo("ค้นหาเกม") {
            val packages = access.exec("pm list packages")
                .lineSequence()
                .map { it.removePrefix("package:").trim() }
                .filter { p -> GAME_HINTS.any { p.contains(it, ignoreCase = true) } }
                .sorted()
                .toList()
            _ui.update { it.copy(gameCandidates = packages) }
            if (packages.isEmpty()) "ไม่พบแพ็กเกจของ NetEase ในเครื่อง" else "พบ ${packages.size} แพ็กเกจ แตะเพื่อเลือก"
        }
    }

    /** ไล่หาไฟล์ข้อความที่มีคีย์เกี่ยวกับ FPS ในโฟลเดอร์ของเกม */
    fun scan() = withAccess("Scan") { fa ->
        val files = fa.listFiles(maxDepth = 6, limit = 5000).filter { path ->
            val ext = path.substringAfterLast('.', "").lowercase()
            ext in ConfigEditor.TEXT_EXTENSIONS
        }
        val hits = mutableListOf<ScanHit>()
        var binary = 0
        for (path in files) {
            if (hits.size >= 100) break
            val size = fa.size(path)
            if (size <= 0 || size > 512_000) continue
            val content = runCatching { fa.readText(path) }.getOrNull() ?: continue
            if (ConfigEditor.isProbablyBinary(content)) { binary++; continue }
            hits += ConfigEditor.findFpsCandidates(path, content)
        }
        _ui.update { it.copy(scanHits = hits) }
        buildString {
            append("ตรวจ ${files.size} ไฟล์ข้อความ พบคีย์ที่น่าจะเกี่ยวกับ FPS ${hits.size} รายการ")
            if (binary > 0) append(" (ข้าม $binary ไฟล์ที่เป็น binary/เข้ารหัส)")
        }
    }

    fun apply() = withAccess("Apply") { fa ->
        val s = _ui.value
        val value = s.fps.value ?: s.unlockedValue.ifBlank { throw IllegalArgumentException("กรอกค่าสำหรับ Unlocked ก่อน") }
        val path = s.configPath
        val backup = "$path.bak"

        val content = fa.readText(path)
        if (ConfigEditor.isProbablyBinary(content)) {
            throw IllegalStateException("ไฟล์นี้เป็น binary หรือถูกเข้ารหัส แก้ด้วยแอปนี้ไม่ได้")
        }
        val format = ConfigEditor.detectFormat(path, content)
        val (newContent, count) = ConfigEditor.applyValue(content, format, s.fpsKey, value)

        // สำรองเฉพาะครั้งแรก เพื่อให้ .bak เป็นไฟล์ต้นฉบับจริงเสมอ ไม่ถูกทับด้วยไฟล์ที่แก้แล้ว
        val backupNote = if (!fa.exists(backup)) {
            fa.copy(path, backup)
            "สร้าง $backup แล้ว"
        } else {
            "มี .bak เดิมอยู่แล้ว (เก็บต้นฉบับไว้ ไม่ทับ)"
        }

        fa.writeText(path, newContent)

        val check = ConfigEditor.readValue(fa.readText(path), format, s.fpsKey)
        if (check != value) throw IllegalStateException("เขียนแล้วอ่านกลับได้ $check ไม่ตรงกับ $value")

        "ตั้ง ${s.fpsKey} = $value แล้ว ($count ตำแหน่ง, $format) · $backupNote"
    }

    fun restore() = withAccess("Restore") { fa ->
        val path = _ui.value.configPath
        val backup = "$path.bak"
        if (!fa.exists(backup)) throw IllegalStateException("ไม่พบไฟล์ $backup")
        fa.copy(backup, path)
        "คืนค่าไฟล์จาก $backup แล้ว"
    }

    private fun refreshFileInfo() {
        val s = _ui.value
        val fa = fileAccess()
        if (fa == null || s.configPath.isBlank()) {
            _ui.update { it.copy(currentValue = null, backupExists = false) }
            return
        }
        viewModelScope.launch {
            val (current, hasBackup) = withContext(Dispatchers.IO) {
                runCatching {
                    val content = fa.readText(s.configPath)
                    val value = if (s.fpsKey.isBlank() || ConfigEditor.isProbablyBinary(content)) null
                    else ConfigEditor.readValue(content, ConfigEditor.detectFormat(s.configPath, content), s.fpsKey)
                    value to fa.exists("${s.configPath}.bak")
                }.getOrDefault(null to false)
            }
            _ui.update { it.copy(currentValue = current, backupExists = hasBackup) }
        }
    }

    // ---------- helpers ----------

    private fun withAccess(name: String, block: suspend (FileAccess) -> String) {
        val fa = fileAccess()
        if (fa == null) {
            log("✗ $name: ยังเข้าถึงโฟลเดอร์เกมไม่ได้ (เชื่อม Shizuku หรือให้สิทธิ์ SAF และกรอกชื่อแพ็กเกจ)")
            return
        }
        launchIo(name) { block(fa) + " [${fa.label}]" }
    }

    private fun launchIo(name: String, block: suspend () -> String) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                val message = withContext(Dispatchers.IO) { block() }
                log("✓ $message")
            } catch (e: Exception) {
                log("✗ $name: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _ui.update { it.copy(busy = false) }
                refreshFileInfo()
            }
        }
    }

    private fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _ui.update { it.copy(log = (listOf("[$time] $message") + it.log).take(40)) }
    }

    private companion object {
        const val KEY_PKG = "package"
        const val KEY_PATH = "config_path"
        const val KEY_FPS_KEY = "fps_key"
        const val KEY_UNLOCKED = "unlocked_value"
        const val KEY_TREE = "tree_uri"
        val GAME_HINTS = listOf("netease", "yysls", "wherewinds", "wwm")
    }
}
