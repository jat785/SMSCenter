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
        // 用户从最近任务划掉 → 重新启动保活
        Log.d(TAG, "onTaskRemoved，重新调度保活")
        SmsKeepAliveReceiver.schedule(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 服务被系统杀掉 → 重新调度保活（下一轮 Alarm 触发时会检查并通知用户）
        Log.d(TAG, "onDestroy，重新调度保活")
        try {
            SmsKeepAliveReceiver.schedule(this)
        } catch (_: Exception) {}
    }

    /** 已知短信类应用包名（常见国产手机/Android 原生） */
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

        // 只处理短信类应用的通知
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
            SmsRepository.add(
                SmsMessage(sender = sender, body = body, timestamp = sbn.postTime)
            )
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