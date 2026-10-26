package com.fpsunlocker.tool.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fpsunlocker.tool.App
import com.fpsunlocker.tool.data.ConfigEditor
import com.fpsunlocker.tool.data.DisplayInfo
import com.fpsunlocker.tool.data.FileAccess
import com.fpsunlocker.tool.data.FileEntry
import com.fpsunlocker.tool.data.GameApp
import com.fpsunlocker.tool.data.GameRepository
import com.fpsunlocker.tool.data.KeyNotFoundException
import com.fpsunlocker.tool.data.SafFileAccess
import com.fpsunlocker.tool.data.ScanHit
import com.fpsunlocker.tool.data.ShizukuFileAccess
import com.fpsunlocker.tool.overlay.FpsOverlayService
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
import kotlin.math.roundToInt

enum class FpsOption(val label: String, val value: String?) {
    FPS_60("60", "60"),
    FPS_90("90", "90"),
    FPS_120("120", "120"),
    MAX("Max", null), // ใช้ค่าที่ผู้ใช้กรอกเอง เพราะแต่ละเกมใช้ค่าต่างกัน (0, -1, 999 ...)
}

enum class VerdictLevel { GOOD, MAYBE, BAD }

data class Verdict(val level: VerdictLevel, val text: String)

/** ค่าที่ผู้ใช้เคยตั้งไว้กับเกม ใช้ตรวจว่าเกมเขียนทับหรือยัง */
data class Applied(val path: String, val key: String, val value: String)

data class BackupRecord(val packageName: String, val path: String)

/** ผลการเตรียมแก้ไฟล์ รอผู้ใช้ยืนยันในหน้าต่างเปรียบเทียบ */
data class PendingApply(
    val path: String,
    val key: String,
    val value: String,
    val changes: List<Pair<String, String>>,
    val newContent: String,
    val originalHash: Int,
    val addedKey: Boolean,
)

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
    val isRoot: Boolean = false,
    val safGranted: Boolean = false,
    // รายชื่อเกม
    val games: List<GameApp> = emptyList(),
    val gamesLoading: Boolean = false,
    val dataFoldersScanned: Boolean = false,
    val showAllApps: Boolean = false,
    val query: String = "",
    val appliedValues: Map<String, String> = emptyMap(), // package → ค่า FPS ที่ตั้งไว้
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
    val addKeyIfMissing: Boolean = false,
    val autoStopGame: Boolean = true,
    val scanHits: List<ScanHit> = emptyList(),
    val textFiles: List<FileEntry> = emptyList(),
    val preview: Pair<String, String>? = null, // (path, เนื้อหาที่ตัดมาแสดง)
    val verdict: Verdict? = null,
    val pendingApply: PendingApply? = null,
    val applied: Applied? = null,
    val appliedNow: String? = null, // ค่าปัจจุบันในไฟล์ของ applied
    val backups: List<BackupRecord> = emptyList(),
    // จอและการบังคับ FPS
    val maxHz: Float = 60f,
    val currentHz: Float = 60f,
    val refreshLocked: Boolean = false,
    val gameModeActive: Boolean = false,
    val overlayRunning: Boolean = false,
    val canDrawOverlay: Boolean = false,
    // อื่นๆ
    val busy: Boolean = false,
    val log: List<String> = emptyList(),
) {
    val selectedGame: GameApp? get() = games.firstOrNull { it.packageName == packageName }

    val hasAccess: Boolean get() = shizukuBound || safGranted

    val canEdit: Boolean
        get() = hasAccess && packageName.isNotBlank() &&
            configPath.isNotBlank() && fpsKey.isNotBlank() && !busy

    /** ค่าที่ตั้งไว้ถูกเกมเขียนทับแล้ว */
    val reverted: Boolean
        get() = applied != null && appliedNow != null && appliedNow != applied.value

    val targetValue: String? get() = fps.value ?: unlockedValue.ifBlank { null }

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
    private val shizuku: ShizukuManager = (app as App).shizuku
    private val repo = GameRepository(app)

    private val _ui = MutableStateFlow(initialState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var lastBound = false

    init {
        updateSafState()
        loadGames()
        viewModelScope.launch {
            combine(shizuku.status, shizuku.service, shizuku.isRoot) { status, service, root ->
                Triple(status, service != null, root)
            }.collect { (status, bound, root) ->
                _ui.update { it.copy(shizukuStatus = status, shizukuBound = bound, isRoot = root) }
                if (bound && !lastBound) {
                    loadGames()
                    loadDataSize()
                }
                lastBound = bound
                refreshFileInfo()
            }
        }
        viewModelScope.launch {
            FpsOverlayService.running.collect { r -> _ui.update { it.copy(overlayRunning = r) } }
        }
    }

    private fun initialState(): UiState {
        val pkg = prefs.getString(KEY_PKG, "").orEmpty()
        val ctx = getApplication<Application>()
        return UiState(
            packageName = pkg,
            unlockedValue = prefs.getString(KEY_UNLOCKED, "999").orEmpty(),
            configPath = prefs.getString(pathKey(pkg), "").orEmpty(),
            fpsKey = prefs.getString(fpsKeyKey(pkg), "").orEmpty(),
            addKeyIfMissing = prefs.getBoolean(KEY_ADD_KEY, false),
            autoStopGame = prefs.getBoolean(KEY_AUTO_STOP, true),
            applied = loadApplied(pkg),
            appliedValues = loadAppliedValues(),
            backups = loadBackups(),
            maxHz = DisplayInfo.maxRefresh(ctx),
            currentHz = DisplayInfo.currentRefresh(ctx),
            refreshLocked = prefs.getBoolean(KEY_REFRESH_LOCKED, false),
            gameModeActive = prefs.contains(gameModeKey(pkg)),
            canDrawOverlay = FpsOverlayService.canDraw(ctx),
        )
    }

    /** เรียกตอนกลับเข้าแอป: สิทธิ์และ Hz อาจเปลี่ยน และเกมอาจเขียนไฟล์ทับไปแล้ว */
    fun onResume() {
        shizuku.refresh()
        val ctx = getApplication<Application>()
        _ui.update {
            it.copy(
                canDrawOverlay = FpsOverlayService.canDraw(ctx),
                currentHz = DisplayInfo.currentRefresh(ctx),
                maxHz = DisplayInfo.maxRefresh(ctx),
            )
        }
        refreshFileInfo()
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

    private fun safAccess(pkg: String = _ui.value.packageName.trim()): SafFileAccess? {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) return null
        val uri = prefs.getString(KEY_TREE, null) ?: return null
        return runCatching { SafFileAccess(getApplication(), Uri.parse(uri)) }
            .getOrNull()
            ?.takeIf { it.rootName == pkg } // สิทธิ์ต้องตรงกับโฟลเดอร์ของเกมที่เลือก
    }

    /** ช่องทางเข้าถึงไฟล์ของเกม [pkg]: Shizuku ก่อน (ถ้าเป็น root จะเข้า /data/data ได้ด้วย) ไม่มีค่อยใช้ SAF */
    private fun accessFor(pkg: String): FileAccess? {
        if (pkg.isBlank()) return null
        shizuku.service.value?.let { service ->
            val internal = if (shizuku.isRoot.value) "/data/data/$pkg" else null
            return ShizukuFileAccess(service, "$DATA_ROOT/$pkg", internal)
        }
        return safAccess(pkg)
    }

    private fun fileAccess(): FileAccess? = accessFor(_ui.value.packageName.trim())

    private fun shellAccess(): ShizukuFileAccess? = shizuku.service.value?.let { ShizukuFileAccess(it, "/") }

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
                log(
                    "✓ พบเกม ${games.count { it.isGame }} รายการ จากแอปทั้งหมด ${games.size}" +
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
                applied = loadApplied(pkg),
                appliedNow = null,
                gameModeActive = prefs.contains(gameModeKey(pkg)),
                tab = Tabs.TUNE,
            )
        }
        updateSafState()
        refreshFileInfo()
        loadDataSize()
    }

    private fun loadDataSize() {
        val shell = shellAccess() ?: return
        val pkg = _ui.value.packageName
        if (pkg.isBlank()) return
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                runCatching { shell.exec("du -sh \"$DATA_ROOT/$pkg\" 2>/dev/null | cut -f1").trim() }.getOrNull()
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

    fun onAddKeyChange(v: Boolean) {
        _ui.update { it.copy(addKeyIfMissing = v) }
        prefs.edit().putBoolean(KEY_ADD_KEY, v).apply()
    }

    fun onAutoStopChange(v: Boolean) {
        _ui.update { it.copy(autoStopGame = v) }
        prefs.edit().putBoolean(KEY_AUTO_STOP, v).apply()
    }

    fun useScanHit(hit: ScanHit) {
        onPathChange(hit.path)
        onKeyChange(hit.key)
        preview(hit.path)
    }

    // ---------- Scan ----------

    /**
     * ไล่หาไฟล์ config ในโฟลเดอร์ของเกม (ใช้ได้กับทุกเกม)
     * - ไล่ทีละโฟลเดอร์ย่อย เพื่อไม่ให้โฟลเดอร์ resource ใหญ่ๆ กินโควตาจนโฟลเดอร์อื่นตกหล่น
     * - ถ้า Shizuku เป็น root จะสแกน /data/data/<package> ด้วย (โฟลเดอร์ "internal")
     *   ซึ่งเป็นที่เก็บค่าตั้งค่าของเกมส่วนใหญ่ เช่น Unity PlayerPrefs ใน shared_prefs
     * - ไฟล์ที่ path มีคำว่า config / setting / prefs ถูกอ่านก่อน
     */
    fun scan() = withAccess("Scan") { fa ->
        val found = LinkedHashMap<String, FileEntry>()
        var truncated = false

        fun collect(dir: String, depth: Int) {
            val entries = runCatching { fa.listFiles(dir, depth, LIST_LIMIT, MAX_READ_SIZE) }.getOrDefault(emptyList())
            if (entries.size >= LIST_LIMIT) truncated = true
            entries.forEach { found.putIfAbsent(it.path, it) }
        }

        val topDirs = runCatching { fa.listDirs("") }.getOrDefault(emptyList())
        collect("", 1)
        for (top in topDirs) {
            collect(top, 1)
            for (sub in runCatching { fa.listDirs(top) }.getOrDefault(emptyList())) {
                collect("$top/$sub", 30)
            }
        }

        val candidates = found.values.sortedByDescending { priority(it.path) }.take(MAX_READS)
        val hits = mutableListOf<ScanHit>()
        val textFiles = mutableListOf<FileEntry>()
        val binaryHits = mutableListOf<String>()
        var binary = 0
        for (entry in candidates) {
            val content = runCatching { fa.readText(entry.path) }.getOrNull() ?: continue
            if (ConfigEditor.isProbablyBinary(content)) {
                binary++
                val words = ConfigEditor.fpsWordsInBinary(content)
                if (words.isNotEmpty() && binaryHits.size < 5) binaryHits += "${entry.path} (${words.joinToString()})"
                continue
            }
            textFiles += entry
            if (hits.size < 150) hits += ConfigEditor.findFpsCandidates(entry.path, content)
        }

        val internalNote = when {
            ShizukuFileAccess.INTERNAL in topDirs -> ""
            else -> " · ยังไม่ได้สแกน /data/data ของเกม (ต้องใช้ Shizuku แบบ root)"
        }
        val verdict = when {
            hits.isNotEmpty() -> Verdict(
                VerdictLevel.GOOD,
                "พบคีย์ที่น่าจะเกี่ยวกับ FPS ${hits.size} รายการ แตะรายการเพื่อเลือก แล้วดูเนื้อหาไฟล์ก่อนกด Apply",
            )
            textFiles.isNotEmpty() -> Verdict(
                VerdictLevel.MAYBE,
                "ไม่พบคีย์ชื่อเกี่ยวกับ FPS แต่มีไฟล์ข้อความ ${textFiles.size} ไฟล์ ลองเปิดดูไฟล์ที่ชื่อมีคำว่า config, setting หรือ prefs$internalNote",
            )
            found.isNotEmpty() -> Verdict(
                VerdictLevel.BAD,
                "ไฟล์ทั้งหมดเป็น binary หรือถูกเข้ารหัส" +
                    (if (binaryHits.isNotEmpty()) " (มีคำเกี่ยวกับ FPS อยู่ใน: ${binaryHits.joinToString("; ")})" else "") +
                    " แอปแก้ไฟล์แบบนี้ไม่ได้ ลองใช้ส่วน \"บังคับ FPS\" แทน$internalNote",
            )
            else -> Verdict(
                VerdictLevel.BAD,
                "ไม่พบไฟล์ในโฟลเดอร์ของเกม เกมอาจเก็บค่าไว้ใน /data/data$internalNote",
            )
        }

        _ui.update { it.copy(scanHits = hits, textFiles = textFiles.take(300), verdict = verdict) }
        buildString {
            append("ไฟล์เล็ก ${found.size}")
            if (truncated || found.size > MAX_READS) append(" (ถึงขีดจำกัด อาจตรวจไม่ครบ)")
            append(" · ข้อความ ${textFiles.size} · binary/เข้ารหัส $binary · คีย์ FPS ${hits.size}")
        }
    }

    /** เปิดดูเนื้อหาไฟล์ */
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

    // ---------- Apply (2 ขั้น: เตรียม → ยืนยัน) ----------

    /** ขั้นที่ 1: คำนวณไฟล์ใหม่และบรรทัดที่เปลี่ยน แล้วให้ผู้ใช้ดูก่อน ยังไม่เขียนอะไรลงไฟล์ */
    fun prepareApply() = withAccess("Apply") { fa ->
        val s = _ui.value
        val value = s.targetValue ?: throw IllegalArgumentException("กรอกค่าสำหรับ Max ก่อน")
        val path = s.configPath
        if (s.autoStopGame) stopGame()

        val content = fa.readText(path)
        if (ConfigEditor.isProbablyBinary(content)) {
            throw IllegalStateException("ไฟล์นี้เป็น binary หรือถูกเข้ารหัส แก้ด้วยแอปนี้ไม่ได้")
        }
        val format = ConfigEditor.detectFormat(path, content)
        var added = false
        val newContent = try {
            ConfigEditor.applyValue(content, format, s.fpsKey, value).first
        } catch (e: KeyNotFoundException) {
            if (!s.addKeyIfMissing) throw e
            added = true
            ConfigEditor.addKey(content, format, s.fpsKey, value)
        }
        val changes = diffLines(content, newContent)
        if (changes.isEmpty()) return@withAccess "ค่าในไฟล์เป็น $value อยู่แล้ว ไม่ต้องแก้"

        _ui.update {
            it.copy(
                pendingApply = PendingApply(path, s.fpsKey, value, changes, newContent, content.hashCode(), added),
            )
        }
        "เตรียมแก้ ${changes.size} บรรทัด รอยืนยัน"
    }

    fun cancelApply() = _ui.update { it.copy(pendingApply = null) }

    /** ขั้นที่ 2: สำรองไฟล์ (ครั้งแรก) แล้วเขียนไฟล์ใหม่ และอ่านกลับมาตรวจ */
    fun confirmApply() {
        val p = _ui.value.pendingApply ?: return
        _ui.update { it.copy(pendingApply = null) }
        withAccess("Apply") { fa ->
            val pkg = _ui.value.packageName
            if (_ui.value.autoStopGame) stopGame()
            val current = fa.readText(p.path)
            if (current.hashCode() != p.originalHash) {
                throw IllegalStateException("ไฟล์ถูกเปลี่ยนระหว่างรอยืนยัน (เกมอาจกำลังเปิดอยู่) ปิดเกมแล้วกด Apply ใหม่")
            }
            val backup = "${p.path}.bak"
            val backupNote = if (!fa.exists(backup)) {
                fa.copy(p.path, backup)
                addBackupRecord(BackupRecord(pkg, p.path))
                "สร้าง backup แล้ว"
            } else {
                "มี backup ต้นฉบับอยู่แล้ว"
            }

            fa.writeText(p.path, p.newContent)

            val format = ConfigEditor.detectFormat(p.path, p.newContent)
            val check = ConfigEditor.readValue(fa.readText(p.path), format, p.key)
            if (check != p.value) throw IllegalStateException("เขียนแล้วอ่านกลับได้ $check ไม่ตรงกับ ${p.value}")

            saveApplied(pkg, Applied(p.path, p.key, p.value))
            "ตั้ง ${p.key} = ${p.value} แล้ว" + (if (p.addedKey) " (เพิ่มคีย์ใหม่)" else "") +
                " · $backupNote · เปิดเกมเพื่อดูผล แล้วใช้ตัววัด FPS ตรวจ"
        }
    }

    /** ตั้งค่าที่เคยตั้งไว้ซ้ำ หลังเกมเขียนทับ */
    fun reapply() {
        val a = _ui.value.applied ?: return
        val option = FpsOption.entries.firstOrNull { it.value == a.value }
        _ui.update {
            it.copy(
                configPath = a.path,
                fpsKey = a.key,
                fps = option ?: FpsOption.MAX,
                unlockedValue = if (option == null) a.value else it.unlockedValue,
            )
        }
        prepareApply()
    }

    fun restore() = withAccess("Restore") { fa ->
        val path = _ui.value.configPath
        val backup = "$path.bak"
        if (!fa.exists(backup)) throw IllegalStateException("ไม่พบไฟล์ $backup")
        if (_ui.value.autoStopGame) stopGame()
        fa.copy(backup, path)
        clearApplied(_ui.value.packageName)
        "คืนค่าไฟล์จาก backup แล้ว"
    }

    // ---------- Backup ทุกเกม ----------

    fun restoreBackup(record: BackupRecord) {
        val fa = accessFor(record.packageName)
        if (fa == null) {
            log("✗ Restore: ต้องเชื่อม Shizuku ก่อน")
            return
        }
        launchIo("Restore") {
            val backup = "${record.path}.bak"
            if (!fa.exists(backup)) throw IllegalStateException("ไม่พบไฟล์ backup แล้ว (อาจถูกลบไปพร้อมข้อมูลเกม)")
            shellAccess()?.exec("am force-stop ${record.packageName}")
            fa.copy(backup, record.path)
            clearApplied(record.packageName)
            "คืนค่า ${record.path} ของ ${record.packageName} แล้ว"
        }
    }

    fun forgetBackup(record: BackupRecord) {
        val set = prefs.getStringSet(KEY_BACKUPS, emptySet()).orEmpty().toMutableSet()
        set.remove(encode(record))
        prefs.edit().putStringSet(KEY_BACKUPS, set).apply()
        _ui.update { it.copy(backups = loadBackups()) }
    }

    // ---------- บังคับ FPS ระดับระบบ ----------

    /**
     * ล็อกจอไว้ที่ Hz สูงสุด (settings peak/min_refresh_rate)
     * ช่วยกรณีระบบลด Hz เองระหว่างเล่น แต่ไม่ได้ปลดเพดาน FPS ที่เกมกำหนดเอง
     */
    fun toggleRefreshLock() = shellTask("ล็อก Hz") { sh ->
        if (!_ui.value.refreshLocked) {
            if (!prefs.contains(KEY_ORIG_PEAK)) {
                prefs.edit()
                    .putString(KEY_ORIG_PEAK, sh.exec("settings get system peak_refresh_rate").trim())
                    .putString(KEY_ORIG_MIN, sh.exec("settings get system min_refresh_rate").trim())
                    .apply()
            }
            val hz = String.format(Locale.US, "%.1f", _ui.value.maxHz)
            checkShell(sh.exec("settings put system peak_refresh_rate $hz && settings put system min_refresh_rate $hz 2>&1"))
            prefs.edit().putBoolean(KEY_REFRESH_LOCKED, true).apply()
            _ui.update { it.copy(refreshLocked = true) }
            "ล็อกจอที่ ${hz}Hz แล้ว (บางยี่ห้ออาจไม่ทำตาม ดูค่า Hz ปัจจุบันในการ์ด)"
        } else {
            restoreSetting(sh, "peak_refresh_rate", prefs.getString(KEY_ORIG_PEAK, "null"))
            restoreSetting(sh, "min_refresh_rate", prefs.getString(KEY_ORIG_MIN, "null"))
            prefs.edit().remove(KEY_ORIG_PEAK).remove(KEY_ORIG_MIN).putBoolean(KEY_REFRESH_LOCKED, false).apply()
            _ui.update { it.copy(refreshLocked = false) }
            "คืนค่า refresh rate เดิมแล้ว"
        }
    }

    /**
     * Game Mode ของ Android 13+: ตั้งโหมด Performance และล็อก FPS ของเกมไม่ให้เกินค่าที่เลือก
     * ทำได้แค่ "จำกัด" FPS ให้นิ่ง ดันให้สูงกว่าที่เกมตั้งเองไม่ได้
     */
    fun toggleGameMode() = shellTask("Game Mode") { sh ->
        if (Build.VERSION.SDK_INT < 33) throw IllegalStateException("ต้องใช้ Android 13 ขึ้นไป")
        val pkg = _ui.value.packageName
        if (pkg.isBlank()) throw IllegalStateException("ยังไม่ได้เลือกเกม")
        val key = gameModeKey(pkg)
        if (!_ui.value.gameModeActive) {
            val previous = sh.exec("device_config get game_overlay $pkg").trim()
            prefs.edit().putString(key, previous).apply()
            val cap = gameModeCap()
            checkShell(sh.exec("device_config put game_overlay $pkg mode=2,fps=$cap:mode=3,fps=30 2>&1"))
            val modeOut = sh.exec("cmd game mode performance $pkg 2>&1").trim()
            sh.exec("am force-stop $pkg")
            _ui.update { it.copy(gameModeActive = true) }
            "ตั้ง Game Mode = Performance และจำกัด FPS ไม่เกิน $cap แล้ว เปิดเกมใหม่เพื่อให้มีผล" +
                if (modeOut.isNotEmpty()) " (ระบบตอบ: ${modeOut.take(120)})" else ""
        } else {
            val previous = prefs.getString(key, "null").orEmpty()
            if (previous.isBlank() || previous == "null") {
                sh.exec("device_config delete game_overlay $pkg")
            } else {
                sh.exec("device_config put game_overlay $pkg '$previous'")
            }
            sh.exec("cmd game mode standard $pkg")
            prefs.edit().remove(key).apply()
            _ui.update { it.copy(gameModeActive = false) }
            "ยกเลิก Game Mode ของเกมนี้แล้ว"
        }
    }

    private fun gameModeCap(): Int {
        val maxHz = _ui.value.maxHz.roundToInt()
        val target = _ui.value.targetValue?.toIntOrNull()?.takeIf { it > 0 } ?: maxHz
        return GAME_MODE_FPS.filter { it <= maxHz && it <= target }.maxOrNull() ?: 60
    }

    private fun restoreSetting(sh: ShizukuFileAccess, name: String, original: String?) {
        if (original.isNullOrBlank() || original == "null") {
            sh.exec("settings delete system $name")
        } else {
            sh.exec("settings put system $name $original")
        }
    }

    private fun checkShell(output: String) {
        val o = output.trim()
        if (o.contains("Exception") || o.contains("Error", ignoreCase = true) || o.contains("denied", ignoreCase = true)) {
            throw IllegalStateException(o.take(200))
        }
    }

    private fun stopGame() {
        val pkg = _ui.value.packageName
        if (pkg.isNotBlank()) runCatching { shellAccess()?.exec("am force-stop $pkg") }
    }

    // ---------- ตัววัด FPS ----------

    fun startOverlay() {
        val ctx = getApplication<Application>()
        if (!FpsOverlayService.canDraw(ctx)) {
            log("✗ ยังไม่ได้อนุญาตให้แสดงทับแอปอื่น")
            return
        }
        FpsOverlayService.start(ctx, _ui.value.packageName.ifBlank { null })
        log("✓ เปิดตัววัด FPS แล้ว เปิดเกมได้เลย (กดค้างที่ตัวเลขเพื่อปิด)")
    }

    fun stopOverlay() = FpsOverlayService.stop(getApplication())

    // ---------- ข้อมูลไฟล์ปัจจุบัน ----------

    private fun refreshFileInfo() {
        val s = _ui.value
        val fa = fileAccess()
        if (fa == null) {
            _ui.update { it.copy(currentValue = null, backupExists = false, appliedNow = null) }
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val current = if (s.configPath.isBlank()) null else runCatching { readKey(fa, s.configPath, s.fpsKey) }.getOrNull()
                val hasBackup = s.configPath.isNotBlank() && runCatching { fa.exists("${s.configPath}.bak") }.getOrDefault(false)
                val appliedNow = s.applied?.let { a -> runCatching { readKey(fa, a.path, a.key) }.getOrNull() }
                Triple(current, hasBackup, appliedNow)
            }
            if (_ui.value.packageName == s.packageName) {
                _ui.update { it.copy(currentValue = result.first, backupExists = result.second, appliedNow = result.third) }
            }
        }
    }

    private fun readKey(fa: FileAccess, path: String, key: String): String? {
        if (key.isBlank()) return null
        val content = fa.readText(path)
        if (ConfigEditor.isProbablyBinary(content)) return null
        return ConfigEditor.readValue(content, ConfigEditor.detectFormat(path, content), key)
    }

    // ---------- บันทึกสถานะ ----------

    private fun loadApplied(pkg: String): Applied? {
        val raw = prefs.getString(appliedKey(pkg), null) ?: return null
        val parts = raw.split('\t')
        return if (parts.size == 3) Applied(parts[0], parts[1], parts[2]) else null
    }

    private fun loadAppliedValues(): Map<String, String> =
        prefs.all.filterKeys { it.startsWith("applied:") }
            .mapNotNull { (k, v) -> (v as? String)?.split('\t')?.getOrNull(2)?.let { k.removePrefix("applied:") to it } }
            .toMap()

    private fun saveApplied(pkg: String, a: Applied) {
        prefs.edit().putString(appliedKey(pkg), "${a.path}\t${a.key}\t${a.value}").apply()
        _ui.update { it.copy(applied = if (it.packageName == pkg) a else it.applied, appliedValues = loadAppliedValues()) }
    }

    private fun clearApplied(pkg: String) {
        prefs.edit().remove(appliedKey(pkg)).apply()
        _ui.update { it.copy(applied = if (it.packageName == pkg) null else it.applied, appliedValues = loadAppliedValues()) }
    }

    private fun encode(r: BackupRecord) = "${r.packageName}\t${r.path}"

    private fun loadBackups(): List<BackupRecord> =
        prefs.getStringSet(KEY_BACKUPS, emptySet()).orEmpty()
            .mapNotNull { s -> s.split('\t').takeIf { it.size == 2 }?.let { BackupRecord(it[0], it[1]) } }
            .sortedWith(compareBy({ it.packageName }, { it.path }))

    private fun addBackupRecord(r: BackupRecord) {
        val set = prefs.getStringSet(KEY_BACKUPS, emptySet()).orEmpty().toMutableSet()
        set.add(encode(r))
        prefs.edit().putStringSet(KEY_BACKUPS, set).apply()
        _ui.update { it.copy(backups = loadBackups()) }
    }

    // ---------- helpers ----------

    private fun diffLines(old: String, new: String): List<Pair<String, String>> {
        val a = old.lines()
        val b = new.lines()
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrNull(i)
            val y = b.getOrNull(i)
            if (x != y) out += (x ?: "(ไม่มี)").take(200) to (y ?: "(ไม่มี)").take(200)
            if (out.size >= 20) break
        }
        return out
    }

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

    private fun shellTask(name: String, block: suspend (ShizukuFileAccess) -> String) {
        val sh = shellAccess()
        if (sh == null) {
            log("✗ $name: ต้องเชื่อม Shizuku ก่อน")
            return
        }
        launchIo(name) { block(sh) }
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
        const val KEY_ADD_KEY = "add_key_if_missing"
        const val KEY_AUTO_STOP = "auto_stop_game"
        const val KEY_BACKUPS = "backups"
        const val KEY_REFRESH_LOCKED = "refresh_locked"
        const val KEY_ORIG_PEAK = "orig_peak_refresh"
        const val KEY_ORIG_MIN = "orig_min_refresh"

        fun pathKey(pkg: String) = "path:$pkg"
        fun fpsKeyKey(pkg: String) = "key:$pkg"
        fun appliedKey(pkg: String) = "applied:$pkg"
        fun gameModeKey(pkg: String) = "gm:$pkg"

        val GAME_MODE_FPS = listOf(30, 40, 45, 60, 90, 120)
        val CONFIG_HINTS = listOf("config", "setting", "saved", "option", "pref", "user", "graphic", "quality", "localdata")
        const val LIST_LIMIT = 3000          // ต่อหนึ่งโฟลเดอร์ (กันเกินขนาด binder)
        const val MAX_READ_SIZE = 300_000L   // ใหญ่กว่านี้ส่งผ่าน binder ไม่ได้ และไฟล์ config จริงมักเล็กกว่ามาก
        const val MAX_READS = 4000
    }
}
