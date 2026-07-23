package com.example.smscenter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

class SmsKeepAliveReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsKeepAlive"
        private const val REQUEST_CODE = 5001
        private const val INTERVAL_MS = 15 * 60 * 1000L // 15 分钟

        fun schedule(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SmsKeepAliveReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            )

            try {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + INTERVAL_MS, pending)
                Log.d(TAG, "AlarmManager 已调度: ${INTERVAL_MS}ms")
            } catch (e: Exception) {
                Log.e(TAG, "AlarmManager 调度失败: ${e.message}")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm 触发，检查通知监听状态")

        // 检查通知监听是否已启用
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val myPkg = context.packageName
        val enabled = flat?.contains(myPkg) == true

        if (!enabled) {
            Log.w(TAG, "通知监听未启用，跳过保活")
            // 仍然继续调度，等用户重新开启
            schedule(context)
            return
        }

        Log.d(TAG, "通知监听已启用，保活成功")

        // 重新调度下一次
        schedule(context)
    }
}