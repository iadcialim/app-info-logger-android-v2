package com.quantuminventions.appInfoLogger.library

import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

fun Date.toStr(format: String = "dd MMM yyyy, hh:mm a"): String {
    return SimpleDateFormat(format, Locale.US).format(this)
}

inline fun SharedPreferences.edit(operation: (SharedPreferences.Editor) -> Unit) {
    val editor = this.edit()
    operation(editor)
    editor.apply()
}

fun SharedPreferences.set(key: String, value: Any?) {
    when (value) {
        is String? -> edit { it.putString(key, value) }
        is Int -> edit { it.putInt(key, value.toInt()) }
        is Boolean -> edit { it.putBoolean(key, value) }
        is Float -> edit { it.putFloat(key, value.toFloat()) }
        is Long -> edit { it.putLong(key, value.toLong()) }
        else -> {
            Log.e("TAG", "Unsupported Type: $value")
        }
    }
}

fun SharedPreferences.clear() {
    edit { it.clear() }
}

fun SharedPreferences.remove(key: String) {
    edit { it.remove(key) }
}
