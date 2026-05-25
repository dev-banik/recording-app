package com.callrecorder.app.util

import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

fun Long.formatDuration(): String {
    val h  = TimeUnit.MILLISECONDS.toHours(this)
    val m  = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    val s  = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}

fun Long.formatDate(pattern: String = "dd MMM yyyy, HH:mm"): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(this))

fun Context.toast(msg: String, long: Boolean = false) {
    Toast.makeText(this, msg, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
