package com.example.smscenter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class SmsNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "SmsNotif"
        private const val CHANNEL_ID = "sms_foreground"
        private const val NOTIF_ID = 1001

        /** 供 ViewModel 查询服务是否真正在运行（权限开了不代表服务已绑定） */
        @Volatile
        var isConnected: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildForegroundNotification())
        SmsKeepAliveReceiver.schedule(this)
        Log.d(TAG, "前台服务已启动，保活定时器已调度")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved，重新调度保活")
        SmsKeepAliveReceiver.schedule(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        Log.d(TAG, "onDestroy，重新调度保活")
        try {
            SmsKeepAliveReceiver.schedule(this)
        } catch (_: Exception) {}
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        Log.d(TAG, "✅ onListenerConnected — 通知监听已绑定")
        startForeground(NOTIF_ID, buildForegroundNotification())
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        Log.w(TAG, "⚠️ onListenerDisconnected — 通知监听已断开")
    }

    /** 已知短信类应用包名 */
    private val smsPackages = setOf(
        "com.android.mms",
        "com.google.android.apps.messaging",
        "com.huawei.message",
        "com.xiaomi.smsextra",
        "com.android.smsextra",
        "com.oppo.mms",
        "com.vivo.mms",
        "com.samsung.android.messaging",
        "com.oneplus.mms",
        "com.coloros.mms",
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: ""
        val isSms = pkg in smsPackages || pkg.contains("mms", ignoreCase = true)
        if (!isSms) {
            Log.d(TAG, "跳过非短信应用: $pkg")
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        if (title.isBlank() && text.isBlank()) return

        Log.d(TAG, "短信通知: pkg=$pkg title=$title text=$text")

        val sender = title
        val body = if (text.length > 2 && text != sender) text else ""

        if (sender.isNotBlank()) {
            SmsRepository.add(SmsMessage(sender = sender, body = body, timestamp = sbn.postTime))
            Log.d(TAG, "已接收: $sender — $body")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "短信转发服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持 SMSCenter 在后台运行"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("SMSCenter")
                .setContentText("正在监听短信通知")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setContentIntent(pi)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("SMSCenter")
                .setContentText("正在监听短信通知")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(Notification.PRIORITY_MIN)
                .build()
        }
    }
}