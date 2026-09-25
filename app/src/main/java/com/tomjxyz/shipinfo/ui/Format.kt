package com.tomjxyz.shipinfo.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(ms: Long): String {
    val totalS = (ms / 1000).coerceAtLeast(0)
    val h = totalS / 3600
    val m = (totalS % 3600) / 60
    val s = totalS % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatDateTime(ms: Long): String = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))

fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

fun formatShortDateTime(ms: Long): String = SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(ms))

fun fmt(v: Double?, decimals: Int = 1, suffix: String = ""): String =
    if (v == null || v.isNaN()) "–" else String.format(Locale.US, "%.${decimals}f", v) + suffix
