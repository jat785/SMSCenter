package com.example.smscenter

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object SmsSyncService {

    private const val TAG = "SmsSync"
    private const val URL = "https://game.ahsfnuckgf.cn/api/sms"
    private const val MAX_RETRIES = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1))
        .addInterceptor(HttpLoggingInterceptor { msg -> Log.d(TAG, "OkHttp: $msg") }
            .setLevel(HttpLoggingInterceptor.Level.BASIC))
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun start() {
        Log.i(TAG, "══════════════════════════════════════")
        Log.i(TAG, "📡 转发服务已启动")
        Log.i(TAG, "目标: $URL")
        Log.i(TAG, "重试: 最多 $MAX_RETRIES 次 (1s/3s/5s)")
        Log.i(TAG, "══════════════════════════════════════")
        scope.launch {
            SmsRepository.newMessageFlow.collect { msg ->
                Log.i(TAG, "📤 开始转发: sender=${msg.sender} body=${msg.body.take(30)}")
                postMessage(msg)
            }
        }
    }

    private suspend fun postMessage(msg: SmsMessage) {
        val json = buildJson(msg)
        val body = json.toRequestBody(jsonMediaType)

        val delays = longArrayOf(1000L, 3000L, 5000L) // 1s, 3s, 5s

        for (attempt in 1..MAX_RETRIES) {
            try {
                val request = Request.Builder()
                    .url(URL)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ 转发成功 (尝试 $attempt): ${msg.sender} — ${msg.body.take(20)}")
                    response.close()
                    return
                } else {
                    Log.w(TAG, "❌ HTTP ${response.code} / ${response.message} (尝试 $attempt)")
                    response.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 转发异常 (尝试 $attempt): ${e.javaClass.name} — ${e.message}", e)
            }

            if (attempt < MAX_RETRIES) {
                val waitMs = delays[attempt - 1]
                Log.d(TAG, "等待 ${waitMs}ms 后重试...")
                delay(waitMs)
            }
        }

        Log.e(TAG, "转发最终失败（已重试 $MAX_RETRIES 次）: ${msg.sender} — ${msg.body.take(20)}")
    }

    private fun buildJson(msg: SmsMessage): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return JSONObject().apply {
            put("sender", msg.sender)
            put("body", msg.body)
            put("timestamp", msg.timestamp)
            put("time", sdf.format(Date(msg.timestamp)))
        }.toString()
    }
}