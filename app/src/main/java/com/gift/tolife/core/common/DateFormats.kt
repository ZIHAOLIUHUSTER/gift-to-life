package com.gift.tolife.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateFormats {
    private val dateTime = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }
    private val shortDateTime = ThreadLocal.withInitial {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    }

    fun formatDateTime(ts: Long): String = dateTime.get()!!.format(Date(ts))
    fun formatShortDateTime(ts: Long): String = shortDateTime.get()!!.format(Date(ts))
}
