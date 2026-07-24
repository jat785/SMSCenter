package com.example.smscenter

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SmsViewModel(app: Application) : AndroidViewModel(app) {

    val messages: StateFlow<List<SmsMessage>> = SmsRepository.messages

    private val _listenerEnabled = MutableStateFlow(false)
    val listenerEnabled: StateFlow<Boolean> = _listenerEnabled.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _notificationsGranted = MutableStateFlow(true)
    val notificationsGranted: StateFlow<Boolean> = _notificationsGranted.asStateFlow()

    private val _batteryOptimized = MutableStateFlow(false)
    val batteryOptimized: StateFlow<Boolean> = _batteryOptimized.asStateFlow()

    init {
        refresh()
        SmsSyncService.start()
    }

    fun refresh() {
        val ctx = getApplication<Application>()
        // Android 13+ 通知权限
        _notificationsGranted.value = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        // 通知使用权（系统设置中是否开启）
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        _listenerEnabled.value = flat?.contains(ctx.packageName) == true
        // 服务是否实际已绑定
        _serviceConnected.value = SmsNotificationListener.isConnected
        // 电池优化
        _batteryOptimized.value = if (Build.VERSION.SDK_INT >= 23) {
            val pm = ctx.getSystemService(PowerManager::class.java)
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
        } else true
    }
}