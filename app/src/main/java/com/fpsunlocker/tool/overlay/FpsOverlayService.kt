package com.fpsunlocker.tool.overlay

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fpsunlocker.tool.App
import com.fpsunlocker.tool.R
import com.fpsunlocker.tool.data.DisplayInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ตัวเลข FPS ลอยบนจอขณะเล่นเกม
 * - ลากเพื่อย้ายตำแหน่ง · กดค้างเพื่อปิด · หรือกด "ปิด" ในการแจ้งเตือน
 * - เป็น foreground service เพื่อไม่ให้ระบบปิดตัววัดทิ้งตอนอยู่ในเกม
 */
class FpsOverlayService : Service() {

    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val ACTION_STOP = "com.fpsunlocker.tool.STOP_OVERLAY"
        private const val CHANNEL_ID = "fps_overlay"
        private const val NOTIFICATION_ID = 42

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun start(context: Context, packageName: String?) {
            val intent = Intent(context, FpsOverlayService::class.java).putExtra(EXTRA_PKG, packageName)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FpsOverlayService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loop: Job? = null
    private var view: TextView? = null
    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (view == null) addOverlay()
        startLoop(intent?.getStringExtra(EXTRA_PKG))
        _running.value = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        _running.value = false
        scope.cancel()
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        super.onDestroy()
    }

    // ---------------- การแจ้งเตือน (จำเป็นสำหรับ foreground service) ----------------

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ตัววัด FPS", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, FpsOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fps)
            .setContentTitle("ตัววัด FPS กำลังทำงาน")
            .setContentText("ลากตัวเลขเพื่อย้าย · กดค้างที่ตัวเลขเพื่อปิด")
            .setOngoing(true)
            .addAction(0, "ปิด", stopIntent)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    // ---------------- หน้าต่างลอย ----------------

    @SuppressLint("ClickableViewAccessibility")
    private fun addOverlay() {
        val dp = { v: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics) }
        val text = TextView(this).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            val padH = dp(10f).roundToInt()
            val padV = dp(6f).roundToInt()
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                cornerRadius = dp(12f)
                setColor(Color.argb(200, 11, 15, 28))
                setStroke(dp(1f).roundToInt(), Color.argb(160, 79, 227, 232))
            }
            this.text = "FPS …"
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16f).roundToInt()
            y = dp(80f).roundToInt()
        }

        // ลากเพื่อย้าย, กดค้าง 0.7 วินาทีโดยไม่ขยับเพื่อปิด
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var downAt = 0L
        var moved = false
        text.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    downAt = System.currentTimeMillis()
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    params.x = startX + dx.roundToInt()
                    params.y = startY + dy.roundToInt()
                    runCatching { windowManager.updateViewLayout(text, params) }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && System.currentTimeMillis() - downAt > 700) stopSelf()
                }
            }
            true
        }

        windowManager.addView(text, params)
        view = text
    }

    // ---------------- วัด FPS ทุก 1 วินาที ----------------

    private fun startLoop(packageName: String?) {
        loop?.cancel()
        loop = scope.launch {
            val maxHz = DisplayInfo.maxRefresh(this@FpsOverlayService).roundToInt()
            var meter: FpsMeter? = null
            var sum = 0f
            var count = 0
            var min = Float.MAX_VALUE
            while (isActive) {
                val service = (application as App).shizuku.service.value
                if (service == null) {
                    meter = null
                    view?.text = "FPS —\nShizuku ไม่ได้เชื่อม"
                } else {
                    val m = meter ?: FpsMeter(service, packageName).also { meter = it }
                    val fps = withContext(Dispatchers.IO) { m.sample() }
                    val line1 = if (fps == null) "FPS …" else "FPS ${fps.roundToInt()}"
                    if (fps != null && fps >= 1f) {
                        sum += fps
                        count++
                        if (fps < min) min = fps
                    }
                    val line2 = if (count > 0) {
                        "เฉลี่ย ${(sum / count).roundToInt()} · ต่ำสุด ${min.roundToInt()}"
                    } else {
                        "จอ ${maxHz}Hz"
                    }
                    view?.text = "$line1  (${m.method})\n$line2 · จอ ${maxHz}Hz"
                    view?.setTextColor(
                        when {
                            fps == null -> Color.WHITE
                            fps >= maxHz * 0.9f -> Color.rgb(52, 211, 153)
                            fps >= 45f -> Color.rgb(251, 191, 36)
                            else -> Color.rgb(248, 113, 113)
                        }
                    )
                }
                delay(1000)
            }
        }
    }
}
