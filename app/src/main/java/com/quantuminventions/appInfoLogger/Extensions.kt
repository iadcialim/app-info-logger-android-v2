package com.quantuminventions.appInfoLogger

import java.text.SimpleDateFormat
import java.util.*

fun Date.toStr(format: String = "dd MMM yyyy, hh:mm a"): String {
    return SimpleDateFormat(format, Locale.US).format(this)
}