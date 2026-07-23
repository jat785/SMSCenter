package com.example.smscenter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 检查通知监听已授权 → 启动保活定时器
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (flat?.contains(context.packageName) == true) {
                SmsKeepAliveReceiver.schedule(context)
            }
        }
    }
}