package cc.kennydev.silic2.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import cc.kennydev.silic2.app.R
import cc.kennydev.silic2.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RecordingForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "silic2_recording_channel_v2"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "cc.kennydev.silic2.action.START_RECORDING_SERVICE"
        const val ACTION_STOP = "cc.kennydev.silic2.action.STOP_RECORDING_SERVICE"
        const val ACTION_STOP_FROM_NOTIFICATION = "cc.kennydev.silic2.action.STOP_FROM_NOTIFICATION"

        const val EXTRA_MAX_MINUTES = "extra_max_minutes"
        const val EXTRA_AUTO_STOP_LOW_BATTERY = "extra_auto_stop_low_battery"

        // 廣播給 ViewModel / Activity 的通知
        const val BROADCAST_AUTO_STOPPED = "cc.kennydev.silic2.broadcast.AUTO_STOPPED"
        const val EXTRA_STOP_REASON = "extra_stop_reason"

        fun start(context: Context, maxDurationMinutes: Int = 60, autoStopLowBattery: Boolean = true) {
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MAX_MINUTES, maxDurationMinutes)
                putExtra(EXTRA_AUTO_STOP_LOW_BATTERY, autoStopLowBattery)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private var maxDurationMinutes: Int = 60
    private var autoStopLowBattery: Boolean = true
    private var elapsedSeconds: Long = 0L

    private var batteryReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP, ACTION_STOP_FROM_NOTIFICATION -> {
                if (intent.action == ACTION_STOP_FROM_NOTIFICATION) {
                    sendStopBroadcast("用戶從通知列手動停止")
                }
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                maxDurationMinutes = intent.getIntExtra(EXTRA_MAX_MINUTES, 60)
                autoStopLowBattery = intent.getBooleanExtra(EXTRA_AUTO_STOP_LOW_BATTERY, true)

                startAsForeground()
                startSafetyTimer()
                if (autoStopLowBattery) {
                    registerBatteryMonitor()
                }
            }
        }
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildNotification("正在背景持續監聽生態聲景，鎖定螢幕仍正常收音")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecordingForegroundService::class.java).apply {
            action = ACTION_STOP_FROM_NOTIFICATION
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎙️ 正在錄音中 (SILIC 2)")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setTicker("🎙️ SILIC 2 正在錄音中")
            .addAction(
                android.R.drawable.ic_media_pause,
                "停止並存檔",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotificationText(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun startSafetyTimer() {
        timerJob?.cancel()
        elapsedSeconds = 0L
        timerJob = scope.launch {
            val maxSeconds = maxDurationMinutes * 60L
            while (isActive) {
                delay(1000L)
                elapsedSeconds++

                // 每 30 秒更新一次通知上的已錄時間與上限提示
                if (elapsedSeconds % 30 == 0L) {
                    val m = elapsedSeconds / 60
                    val s = elapsedSeconds % 60
                    updateNotificationText("已錄音 ${m}分${s}秒 (上限 ${maxDurationMinutes}分，防過熱保護中)")
                }

                // 檢查是否超過保護時間上限
                if (elapsedSeconds >= maxSeconds) {
                    sendStopBroadcast("已達到連續錄音保護上限 (${maxDurationMinutes} 分鐘)，自動安全存檔")
                    stopForegroundAndSelf()
                    break
                }
            }
        }
    }

    private fun registerBatteryMonitor() {
        if (batteryReceiver != null) return
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    if (level >= 0 && scale > 0) {
                        val batteryPct = (level * 100) / scale
                        // 若電量低於 15% 且未接電源，自動停止存檔防關機
                        if (batteryPct <= 15 && !isCharging) {
                            sendStopBroadcast("手機電量僅剩 $batteryPct%，自動安全存檔以防斷電")
                            stopForegroundAndSelf()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun unregisterBatteryMonitor() {
        batteryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) { }
            batteryReceiver = null
        }
    }

    private fun sendStopBroadcast(reason: String) {
        val intent = Intent(BROADCAST_AUTO_STOPPED).apply {
            putExtra(EXTRA_STOP_REASON, reason)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "silic2:recording_wakelock")?.apply {
                acquire()
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SILIC 2 錄音通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "SILIC 2 錄音與野生動物聲音監聽服務狀態通知"
                setShowBadge(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundAndSelf() {
        timerJob?.cancel()
        timerJob = null
        unregisterBatteryMonitor()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopForegroundAndSelf()
        super.onDestroy()
    }
}
