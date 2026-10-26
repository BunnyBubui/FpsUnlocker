package com.fpsunlocker.tool.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.fpsunlocker.tool.BuildConfig
import com.fpsunlocker.tool.IFileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * จัดการ 3 เรื่องของ Shizuku:
 * 1) สถานะ service (รันอยู่ไหม, เวอร์ชันรองรับไหม, ได้สิทธิ์หรือยัง)
 * 2) การขอสิทธิ์
 * 3) การ bind FileService (UserService) เพื่อใช้อ่าน/เขียนไฟล์ด้วยสิทธิ์ shell
 */
class ShizukuManager {

    enum class Status(val label: String) {
        NOT_RUNNING("Shizuku ไม่ได้รัน"),
        UNSUPPORTED("Shizuku เวอร์ชันเก่าเกินไป (ต้อง v11 ขึ้นไป)"),
        NO_PERMISSION("Shizuku รันอยู่ แต่ยังไม่ได้อนุญาตแอปนี้"),
        READY("เชื่อมต่อ Shizuku แล้ว"),
    }

    private val _status = MutableStateFlow(Status.NOT_RUNNING)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _service = MutableStateFlow<IFileService?>(null)
    val service: StateFlow<IFileService?> = _service.asStateFlow()

    private val _isRoot = MutableStateFlow(false)
    /** Shizuku ที่เริ่มด้วย root (uid 0) จะเข้า /data/data ของเกมได้ */
    val isRoot: StateFlow<Boolean> = _isRoot.asStateFlow()

    private var binding = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, FileService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("file_service")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            if (binder != null && binder.pingBinder()) {
                _service.value = IFileService.Stub.asInterface(binder)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binding = false
            _service.value = null
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        _service.value = null
        binding = false
        refresh()
    }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun stop() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
        if (_service.value != null) {
            runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
            _service.value = null
        }
    }

    fun refresh() {
        val newStatus = when {
            !Shizuku.pingBinder() -> Status.NOT_RUNNING
            Shizuku.isPreV11() -> Status.UNSUPPORTED
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Status.NO_PERMISSION
            else -> Status.READY
        }
        _status.value = newStatus
        _isRoot.value = newStatus == Status.READY && runCatching { Shizuku.getUid() == 0 }.getOrDefault(false)
        if (newStatus == Status.READY && _service.value == null) bindService()
    }

    fun requestPermission() {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    private fun bindService() {
        if (binding) return
        binding = true
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (e: Throwable) {
            binding = false
            Log.e(TAG, "bindUserService failed", e)
        }
    }

    private companion object {
        const val TAG = "ShizukuManager"
        const val REQUEST_CODE = 1001
    }
}
