package com.fpsunlocker.tool

import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.fpsunlocker.tool.ui.FpsTheme
import com.fpsunlocker.tool.ui.MainScreen
import com.fpsunlocker.tool.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    // ตัวเลือกโฟลเดอร์ของระบบ (SAF) — ใช้ได้กับ Android/data/<package> เฉพาะ Android 11–12
    private val pickGameFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.onTreeGranted(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FpsTheme {
                MainScreen(vm = vm, onRequestSafAccess = ::requestSafAccess)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshShizuku() // ผู้ใช้อาจเพิ่งไปเปิด Shizuku มา
    }

    private fun requestSafAccess() {
        val pkg = vm.ui.value.packageName
        // เปิดตัวเลือกไปที่ Android/data/<package> ตรงๆ ผู้ใช้แค่กด "ใช้โฟลเดอร์นี้"
        val initial = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Android/data/$pkg",
        )
        pickGameFolder.launch(initial)
    }
}
