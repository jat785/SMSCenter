package com.example.smscenter

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SmsMessage(
    val sender: String,
    val body: String,
    val timestamp: Long
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}