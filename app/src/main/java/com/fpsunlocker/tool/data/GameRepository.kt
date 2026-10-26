package com.fpsunlocker.tool.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.fpsunlocker.tool.R
import java.io.File
import java.util.zip.ZipFile

data class GameApp(
    val packageName: String,
    val label: String,
    val publisher: String,
    val knownPublisher: Boolean,
    val engine: String?,
    /** ระบบบอกว่าเป็นเกม หรือเดาได้จาก engine / ค่ายที่ทำแต่เกม */
    val isGame: Boolean,
    val installed: Boolean,
    val hasDataFolder: Boolean,
    val versionName: String?,
)

/**
 * รวมข้อมูลเกมจาก 2 แหล่ง
 * 1) แอปที่ติดตั้ง (PackageManager) → ชื่อ ไอคอน เวอร์ชัน และ engine จาก native library ใน APK
 * 2) โฟลเดอร์ใน Android/data (ได้จาก Shizuku) → รู้ว่าเกมไหนมีไฟล์ให้สแกน
 */
class GameRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val iconCache = LruCache<String, ImageBitmap>(300)

    fun loadApps(dataFolders: Set<String>): List<GameApp> {
        val result = mutableListOf<GameApp>()
        val seen = mutableSetOf<String>()

        for (info in installedApps()) {
            val pkg = info.packageName
            if (pkg == context.packageName) continue
            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            val hasData = pkg in dataFolders
            if (isSystem && !hasData) continue

            @Suppress("DEPRECATION")
            val flaggedGame = info.category == ApplicationInfo.CATEGORY_GAME ||
                (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            val publisher = Publishers.find(pkg)
            val engine = if (isSystem) null else EngineDetector.fromApk(info)

            result += GameApp(
                packageName = pkg,
                label = info.loadLabel(pm).toString(),
                publisher = publisher?.name ?: Publishers.guess(pkg),
                knownPublisher = publisher != null,
                engine = engine,
                isGame = flaggedGame || publisher?.gamesOnly == true || engine != null,
                installed = true,
                hasDataFolder = hasData,
                versionName = versionName(pkg),
            )
            seen += pkg
        }

        // โฟลเดอร์ที่ค้างอยู่ แต่ไม่มีแอปติดตั้งแล้ว
        for (pkg in dataFolders - seen) {
            if (pkg.startsWith(".")) continue
            val publisher = Publishers.find(pkg)
            result += GameApp(
                packageName = pkg,
                label = pkg,
                publisher = publisher?.name ?: Publishers.guess(pkg),
                knownPublisher = publisher != null,
                engine = null,
                isGame = publisher?.gamesOnly == true,
                installed = false,
                hasDataFolder = true,
                versionName = null,
            )
        }

        return result.sortedWith(
            compareByDescending<GameApp> { it.isGame }
                .thenByDescending { it.installed }
                .thenBy { it.label.lowercase() }
        )
    }

    fun icon(packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        val drawable = runCatching { pm.getApplicationIcon(packageName) }.getOrNull() ?: return null
        val bitmap = runCatching { drawable.toBitmap(144, 144).asImageBitmap() }.getOrNull() ?: return null
        iconCache.put(packageName, bitmap)
        return bitmap
    }

    @Suppress("DEPRECATION")
    private fun installedApps(): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getInstalledApplications(0)
        }

    @Suppress("DEPRECATION")
    private fun versionName(pkg: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            pm.getPackageInfo(pkg, 0).versionName
        }
    }.getOrNull()
}

/** ดู engine จากชื่อ native library ที่อยู่ใน APK (base + split APKs) */
object EngineDetector {

    fun fromApk(info: ApplicationInfo): String? {
        val libs = HashSet<String>()
        val apks = buildList {
            info.sourceDir?.let { add(it) }
            info.splitSourceDirs?.let { addAll(it) }
        }
        for (path in apks) {
            runCatching {
                ZipFile(path).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val name = entries.nextElement().name
                        if (name.startsWith("lib/") && name.endsWith(".so")) libs += name.substringAfterLast('/')
                    }
                }
            }
        }
        // บางเครื่องแตกไฟล์ .so ออกมาไว้ที่ nativeLibraryDir แทน
        info.nativeLibraryDir?.let { dir -> File(dir).list()?.let { libs.addAll(it) } }
        return fromLibs(libs)
    }

    fun fromLibs(libs: Set<String>): String? = when {
        libs.any { it == "libUE4.so" || it == "libUnreal.so" || it.startsWith("libUE5") } -> "Unreal Engine"
        "libunity.so" in libs -> if ("libil2cpp.so" in libs) "Unity (IL2CPP)" else "Unity"
        libs.any { it.startsWith("libcocos") } -> "Cocos"
        "libgodot_android.so" in libs -> "Godot"
        "libgdx.so" in libs -> "libGDX"
        else -> null
    }

    /** คำแนะนำสั้นๆ ว่าเกมที่ใช้ engine นี้มักเก็บค่าตั้งค่าไว้แบบไหน (id ของข้อความ) */
    @StringRes
    fun hint(engine: String?): Int = when {
        engine == null -> R.string.engine_hint_unknown
        engine.startsWith("Unreal") -> R.string.engine_hint_unreal
        engine.startsWith("Unity") -> R.string.engine_hint_unity
        else -> R.string.engine_hint_other
    }
}
