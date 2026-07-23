package com.example.smscenter

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smscenter.ui.theme.SMSCenterTheme

class MainActivity : ComponentActivity() {

    private val vm: SmsViewModel by viewModels()

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.refresh()
        if (vm.listenerEnabled.value) {
            SmsKeepAliveReceiver.schedule(this)
        }

        setContent {
            SMSCenterTheme {
                SmsApp(
                    vm = vm,
                    onEnableListener = {
                        openNotificationSettings()
                        vm.refresh()
                        if (vm.listenerEnabled.value) {
                            SmsKeepAliveReceiver.schedule(this@MainActivity)
                        }
                    },
                    onRequestNotificationPerm = { requestNotificationPerm() },
                    onBatteryOptimization = { openBatterySettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        if (vm.listenerEnabled.value) {
            SmsKeepAliveReceiver.schedule(this)
        }
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 22) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } else {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }
        Toast.makeText(this, "请开启 SMSCenter 的通知使用权", Toast.LENGTH_LONG).show()
    }

    private fun requestNotificationPerm() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openBatterySettings() {
        if (Build.VERSION.SDK_INT >= 23) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            Toast.makeText(this, "请选择「允许」以保持后台运行", Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsApp(
    vm: SmsViewModel,
    onEnableListener: () -> Unit,
    onRequestNotificationPerm: () -> Unit,
    onBatteryOptimization: () -> Unit
) {
    val msgs by vm.messages.collectAsState()
    val listenerEnabled by vm.listenerEnabled.collectAsState()
    val notificationsGranted by vm.notificationsGranted.collectAsState()
    val batteryOptimized by vm.batteryOptimized.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SMSCenter", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { pad ->
        when {
            // 第一步：通知使用权
            !listenerEnabled -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(pad).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("需要通知使用权", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "SMSCenter 通过监听系统短信通知来实时接收短信内容。\n\n" +
                        "请开启「通知使用权」权限。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onEnableListener, modifier = Modifier.fillMaxWidth()) {
                        Text("前往开启通知使用权", fontWeight = FontWeight.Bold)
                    }
                }
            }
            // 第二步：通知显示权限（Android 13+）
            !notificationsGranted -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(pad).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("需要通知权限", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "SMSCenter 需要「通知」权限才能显示前台服务通知，确保持续运行。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onRequestNotificationPerm, modifier = Modifier.fillMaxWidth()) {
                        Text("授予通知权限", fontWeight = FontWeight.Bold)
                    }
                }
            }
            // 第三步：电池优化
            !batteryOptimized -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(pad).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("⚠️ 关闭电池优化", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "为确保持续接收短信、不被系统清理，请关闭 SMSCenter 的电池优化。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onBatteryOptimization, modifier = Modifier.fillMaxWidth()) {
                        Text("前往设置", fontWeight = FontWeight.Bold)
                    }
                }
            }
            // 第四步：等待短信
            msgs.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(pad),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("等待接收短信…", fontSize = 16.sp)
                }
            }
            // 第五步：显示短信列表
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(msgs.size, key = { it }) { index ->
                        val msg = msgs[index]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("发送者：${msg.sender}", fontWeight = FontWeight.Bold)
                                    Text(msg.formattedTime(), style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("内容：${msg.body}")
                            }
                        }
                    }
                }
            }
        }
    }
}