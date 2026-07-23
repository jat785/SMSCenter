package com.example.smscenter

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object SmsRepository {

    private val _messages = MutableStateFlow<List<SmsMessage>>(emptyList())
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()

    /** 新消息事件流（供 SmsSyncService 订阅转发） */
    private val _newMessageFlow = MutableSharedFlow<SmsMessage>(extraBufferCapacity = 64)
    val newMessageFlow: SharedFlow<SmsMessage> = _newMessageFlow.asSharedFlow()

    private val list = mutableListOf<SmsMessage>()

    fun add(msg: SmsMessage) {
        val isNew = synchronized(this) {
            val contentKey = "${msg.sender}|${msg.body}"

            // 同内容已存在 → 跳过（防重复）
            if (list.any { "${it.sender}|${it.body}" == contentKey }) return@synchronized false

            // 同发送者、旧版本 body 被新版本完全包含 → 替换为更完整版本
            val oldIdx = list.indexOfFirst {
                it.sender == msg.sender && msg.body.contains(it.body) && msg.body.length > it.body.length
            }
            if (oldIdx >= 0) {
                list[oldIdx] = msg
                list.sortByDescending { it.timestamp }
                _messages.value = list.toList()
                Log.d("SmsRepo", "更新消息: ${msg.sender}")
                return@synchronized true
            }

            list.add(0, msg)
            _messages.value = list.toList()
            Log.d("SmsRepo", "新增: ${msg.sender}")
            true
        }
        // 发射到转发流
        if (isNew) {
            _newMessageFlow.tryEmit(msg)
        }
    }
}