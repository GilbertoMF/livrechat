package com.livrechat.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {
    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    private val dayMonthFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM", Locale.getDefault())
    }

    private val fullDateFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    }

    private val dateTimeWithWordFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
    }

    fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return timeFormat.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatDayMonth(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return dayMonthFormat.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatFullDate(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return fullDateFormat.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatDateTimeWithWord(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        return dateTimeWithWordFormat.get()?.format(Date(timestamp)) ?: ""
    }

    fun formatConversationTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { timeInMillis = timestamp }

        val isSameDay = now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
        if (isSameDay) {
            return formatTime(timestamp)
        }

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = yesterday.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) {
            return "Ontem"
        }

        return formatDayMonth(timestamp)
    }
}
