package com.fpsunlocker.tool.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fpsunlocker.tool.data.ConfigEditor
import com.fpsunlocker.tool.data.FileAccess
import com.fpsunlocker.tool.data.FileEntry
import com.fpsunlocker.tool.data.GameApp
import com.fpsunlocker.tool.data.GameRepository
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
    MAX("Max", null), // ใช้ค่าที่ผู้ใช้กรอกเอง เพราะแต่ละเกมใช้ค่าต่างกัน (0, -1, 999 ...)
}

enum class VerdictLevel { GOOD, MAYBE, BAD }

data class Verdict(val level: VerdictLevel, val text: String)

object Tabs {
    const val GAMES = 0
    const val TUNE = 1
    const val SETUP = 2
}

data class UiState(
    val tab: Int = Tabs.GAMES,
    // สิทธิ์
    val shizukuStatus: ShizukuManager.Status = ShizukuManager.Status.NOT_RUNNING,
    val shizukuBound: Boolean = false,
    val safGranted: Boolean = false,
    // รายชื่อเกม
    val games: List<GameApp> = emptyList(),
    val gamesLoading: Boolean = false,
    val dataFoldersScanned: Boolean = false,
    val showAllApps: Boolean = false,
    val query: String = "",
    // เกมที่เลือก
    val packageName: String = "",
    val dataSize: String? = null,
    // ไฟล์ config
    val configPath: String = "",
    val fpsKey: String = "",
    val fps: FpsOption = FpsOption.FPS_60,
    val unlockedValue: String = "999",
    val currentValue: String? = null,
    val backupExists: Boolean = false,
    val scanHits: List<ScanHit> = emptyList(),
    val textFiles: List<FileEntry> = emptyList(),
    val preview: Pair<String, String>? = null, // (path, เนื้อหาที่ตัดมาแสดง)
    val verdict: Verdict? = null,
    // อื่นๆ
    val busy: Boolean = false,
    val log: List<String> = emptyList(),
) {
    val selectedGame: GameApp? get() = games.firstOrNull { it.packageName == packageName }

    val accessLabel: String
        get() = when {
            shizukuBound -> "Shizuku"
            safGranted -> "SAF"
            else -> "ยังไม่มีสิทธิ์"
        }

    val hasAccess: Boolean get() = shizukuBound || safGranted

    val canEdit: Boolean
        get() = hasAccess && packageName.isNotBlank() &&
            configPath.isNotBlank() && fpsKey.isNotBlank() && !busy

    val visibleGames: List<GameApp>
        get() {
            val q = query.trim()
            return games.filter { g ->
                (showAllApps || g.isGame) && (
                    q.isEmpty() ||
                        g.label.contains(q, ignoreCase = true) ||
                        g.packageName.contains(q, ignoreCase = true) ||
                        g.publisher.contains(q, ignoreCase = true)
                    )
            }
        }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val shizuku = ShizukuManager()
    private val repo = GameRepository(app)

    private val _ui = MutableStateFlow(
        UiState(
            packageName = prefs.getString(KEY_PKG, "").orEmpty(),
            unlockedValue = prefs.getString(KEY_UNLOCKED, "999").orEmpty(),
        ).let { s ->
            s.copy(
                configPath = prefs.getString(pathKey(s.packageName), "").orEmpty(),
                fpsKey = prefs.getString(fpsKeyKey(s.packageName), "").orEmpty(),
            )
        }
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var lastBound = false

    init {
        shizuku.start()
        updateSafState()
        loadGames()
        viewModelScope.launch {
            combine(shizuku.status, shizuku.service) { status, service -> status to (service != null) }
                .collect { (status, bound) ->
                    _ui.update { it.copy(shizukuStatus = status, shizukuBound = bound) }
                    if (bound && !lastBound) {
                        // เพิ่งเชื่อม Shizuku ได้ → สแกนโฟลเดอร์ Android/data ใหม่ และหาขนาดข้อมูลของเกมที่เลือก
                        loadGames()
                        loadDataSize()
                    }
                    lastBound = bound
                    refreshFileInfo()
                }
        }
    }

    override fun onCleared() {
        shizuku.stop()
    }

    // ---------- navigation ----------

    fun setTab(tab: Int) = _ui.update { it.copy(tab = tab) }

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
        shizuku.service.value?.let { return ShizukuFileAccess(it, "$DATA_ROOT/$pkg") }
        return safAccess()
    }

    // ---------- รายชื่อเกม ----------

    fun loadGames() {
        viewModelScope.launch {
            _ui.update { it.copy(gamesLoading = true) }
            try {
                val service = shizuku.service.value
                val folders: Set<String> = withContext(Dispatchers.IO) {
                    service?.let { runCatching { ShizukuFileAccess(it, DATA_ROOT).listDirs("") }.getOrNull() }
                        ?.toSet()
                        ?: emptySet()
                }
                val games = withContext(Dispatchers.IO) { repo.loadApps(folders) }
                _ui.update { it.copy(games = games, dataFoldersScanned = folders.isNotEmpty()) }
                val gameCount = games.count { it.isGame }
                log(
                    "✓ พบเกม $gameCount รายการ จากแอปทั้งหมด ${games.size}" +
                        if (folders.isNotEmpty()) " · โฟลเดอร์ใน Android/data ${folders.size}" else " · ยังไม่ได้สแกน Android/data (ต้องใช้ Shizuku)"
                )
            } catch (e: Exception) {
                log("✗ โหลดรายชื่อเกม: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _ui.update { it.copy(gamesLoading = false) }
            }
        }
    }

    suspend fun icon(packageName: String): ImageBitmap? =
        withContext(Dispatchers.IO) { repo.icon(packageName) }

    fun onQueryChange(q: String) = _ui.update { it.copy(query = q) }
    fun onShowAllApps(show: Boolean) = _ui.update { it.copy(showAllApps = show) }

    fun selectGame(game: GameApp) {
        val pkg = game.packageName
        prefs.edit().putString(KEY_PKG, pkg).apply()
        _ui.update {
            it.copy(
                packageName = pkg,
                configPath = prefs.getString(pathKey(pkg), "").orEmpty(),
                fpsKey = prefs.getString(fpsKeyKey(pkg), "").orEmpty(),
                scanHits = emptyList(),
                textFiles = emptyList(),
                preview = null,
                verdict = null,
                dataSize = null,
                currentValue = null,
                backupExists = false,
                tab = Tabs.TUNE,
            )
        }
        updateSafState()
        refreshFileInfo()
        loadDataSize()
    }

    private fun loadDataSize() {
        val service = shizuku.service.value ?: return
        val pkg = _ui.value.packageName
        if (pkg.isBlank()) return
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                runCatching {
                    ShizukuFileAccess(service, DATA_ROOT)
                        .exec("du -sh \"$DATA_ROOT/$pkg\" 2>/dev/null | cut -f1")
                        .trim()
                }.getOrNull()
            }
            if (_ui.value.packageName == pkg) {
                _ui.update { it.copy(dataSize = size?.takeIf { s -> s.isNotEmpty() } ?: "ไม่มีโฟลเดอร์") }
            }
        }
    }

    // ---------- input ----------

    fun onPathChange(v: String) {
        val path = v.trim()
        _ui.update { it.copy(configPath = path) }
        prefs.edit().putString(pathKey(_ui.value.packageName), path).apply()
        refreshFileInfo()
    }

    fun onKeyChange(v: String) {
        val key = v.trim()
        _ui.update { it.copy(fpsKey = key) }
        prefs.edit().putString(fpsKeyKey(_ui.value.packageName), key).apply()
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
        preview(hit.path)
    }

    // ---------- actions ----------

    /**
     * ไล่หาไฟล์ config ในโฟลเดอร์ของเกม (ใช้ได้กับทุกเกม)
     * - ไล่ทีละโฟลเดอร์ย่อย เพื่อไม่ให้โฟลเดอร์ resource ใหญ่ๆ กินโควตารายชื่อไฟล์จนโฟลเดอร์อื่นตกหล่น
     * - อ่านเฉพาะไฟล์เล็ก (ไฟล์ตั้งค่ามักเล็ก) ไม่กรองนามสกุล เพราะหลายเกมใช้ไฟล์ไม่มีนามสกุล
     * - ไฟล์ที่ path มีคำว่า config / setting / saved ถูกอ่านก่อน
     */
    fun scan() = withAccess("Scan") { fa ->
        val found = LinkedHashMap<String, FileEntry>()
        var truncated = false

        fun collect(dir: String, depth: Int) {
            val entries = runCatching { fa.listFiles(dir, depth, LIST_LIMIT, MAX_READ_SIZE) }.getOrDefault(emptyList())
            if (entries.size >= LIST_LIMIT) truncated = true
            entries.forEach { found.putIfAbsent(it.path, it) }
        }

        collect("", 1)
        for (top in runCatching { fa.listDirs("") }.getOrDefault(emptyList())) {
            collect(top, 1)
            for (sub in runCatching { fa.listDirs(top) }.getOrDefault(emptyList())) {
                collect("$top/$sub", 12)
            }
        }

        val candidates = found.values.sortedByDescending { priority(it.path) }.take(MAX_READS)
        val hits = mutableListOf<ScanHit>()
        val textFiles = mutableListOf<FileEntry>()
        var binary = 0
        for (entry in candidates) {
            val content = runCatching { fa.readText(entry.path) }.getOrNull() ?: continue
            if (ConfigEditor.isProbablyBinary(content)) {
                binary++
                continue
            }
            textFiles += entry
            if (hits.size < 100) hits += ConfigEditor.findFpsCandidates(entry.path, content)
        }

        val verdict = when {
            hits.isNotEmpty() -> Verdict(
                VerdictLevel.GOOD,
                "พบคีย์ที่น่าจะเป็นค่า FPS ${hits.size} รายการ แตะรายการเพื่อเลือก และเปิดดูเนื้อหาก่อนกด Apply",
            )
            textFiles.isNotEmpty() -> Verdict(
                VerdictLevel.MAYBE,
                "ไม่พบคีย์ชื่อเกี่ยวกับ FPS แต่มีไฟล์ข้อความ ${textFiles.size} ไฟล์ ลองเปิดดูไฟล์ที่ชื่อมีคำว่า config หรือ setting",
            )
            found.isNotEmpty() -> Verdict(
                VerdictLevel.BAD,
                "ไฟล์ทั้งหมดเป็น binary หรือถูกเข้ารหัส เกมนี้น่าจะแก้ FPS ผ่านไฟล์ไม่ได้",
            )
            else -> Verdict(
                VerdictLevel.BAD,
                "ไม่พบไฟล์ในโฟลเดอร์ของเกม เกมอาจเก็บค่าไว้ใน /data/data ซึ่งเข้าไม่ได้หากไม่ root",
            )
        }

        _ui.update { it.copy(scanHits = hits, textFiles = textFiles.take(300), verdict = verdict) }
        buildString {
            append("ไฟล์เล็ก ${found.size}")
            if (truncated || found.size > MAX_READS) append(" (ถึงขีดจำกัด อาจตรวจไม่ครบ)")
            append(" · ข้อความ ${textFiles.size} · binary/เข้ารหัส $binary · คีย์ FPS ${hits.size}")
        }
    }

    /** เปิดดูเนื้อหาไฟล์ เพื่อให้ผู้ใช้หาค่าตั้งค่าเองได้ถ้า Scan ไม่เจอคีย์ */
    fun preview(path: String) = withAccess("ดูไฟล์") { fa ->
        if (path.isBlank()) throw IllegalArgumentException("ยังไม่ได้เลือกไฟล์")
        val size = fa.size(path)
        if (size > MAX_READ_SIZE) throw IllegalStateException("ไฟล์ใหญ่เกินไป ($size bytes)")
        val content = fa.readText(path)
        val text = if (ConfigEditor.isProbablyBinary(content)) {
            "[binary หรือเข้ารหัส · $size bytes]\n" +
                content.take(600).map { c -> if (c.code in 32..126) c else '.' }.joinToString("")
        } else {
            content.take(6000)
        }
        _ui.update { it.copy(preview = path to text) }
        "เปิดดู $path ($size bytes)"
    }

    fun useTextFile(entry: FileEntry) {
        onPathChange(entry.path)
        preview(entry.path)
    }

    fun closePreview() = _ui.update { it.copy(preview = null) }

    fun apply() = withAccess("Apply") { fa ->
        val s = _ui.value
        val value = s.fps.value ?: s.unlockedValue.ifBlank { throw IllegalArgumentException("กรอกค่าสำหรับ Max ก่อน") }
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
                    val value = if (s.fpsKey.isBlank() || ConfigEditor.isProbablyBinary(content)) {
                        null
                    } else {
                        ConfigEditor.readValue(content, ConfigEditor.detectFormat(s.configPath, content), s.fpsKey)
                    }
                    value to fa.exists("${s.configPath}.bak")
                }.getOrDefault(null to false)
            }
            _ui.update { it.copy(currentValue = current, backupExists = hasBackup) }
        }
    }

    // ---------- helpers ----------

    private fun priority(path: String): Int {
        val p = path.lowercase()
        var score = CONFIG_HINTS.count { p.contains(it) } * 2
        if (ConfigEditor.TEXT_EXTENSIONS.any { p.endsWith(".$it") }) score += 1
        return score
    }

    private fun withAccess(name: String, block: suspend (FileAccess) -> String) {
        val fa = fileAccess()
        if (fa == null) {
            log("✗ $name: ยังเข้าถึงโฟลเดอร์เกมไม่ได้ (เลือกเกม และเชื่อม Shizuku ก่อน)")
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
        const val DATA_ROOT = "/storage/emulated/0/Android/data"
        const val KEY_PKG = "package"
        const val KEY_UNLOCKED = "unlocked_value"
        const val KEY_TREE = "tree_uri"

        fun pathKey(pkg: String) = "path:$pkg"
        fun fpsKeyKey(pkg: String) = "key:$pkg"

        val CONFIG_HINTS = listOf("config", "setting", "saved", "option", "pref", "user", "graphic", "quality", "localdata")
        const val LIST_LIMIT = 2500          // ต่อหนึ่งโฟลเดอร์ (กันเกินขนาด binder)
        const val MAX_READ_SIZE = 256_000L   // ไฟล์ config จริงมักเล็กกว่านี้มาก
        const val MAX_READS = 3000
    }
}
