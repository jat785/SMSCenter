package com.example.smscenter

import android.app.Application
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SmsViewModel(app: Application) : AndroidViewModel(app) {

    val messages: StateFlow<List<SmsMessage>> = SmsRepository.messages

    private val _listenerEnabled = MutableStateFlow(false)
    val listenerEnabled: StateFlow<Boolean> = _listenerEnabled.asStateFlow()

    private val _batteryOptimized = MutableStateFlow(false)
    val batteryOptimized: StateFlow<Boolean> = _batteryOptimized.asStateFlow()

    init {
        refresh()
        SmsSyncService.start()
    }

    fun refresh() {
        val ctx = getApplication<Application>()
        // 通知使用权
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
        _listenerEnabled.value = flat?.contains(ctx.packageName) == true
        // 电池优化
        _batteryOptimized.value = if (Build.VERSION.SDK_INT >= 23) {
            val pm = ctx.getSystemService(PowerManager::class.java)
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
        } else true
    }
}