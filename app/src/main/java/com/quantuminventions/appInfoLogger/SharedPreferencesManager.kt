package com.quantuminventions.appInfoLogger

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Wrapper of SharedPreferences that includes encryption using JetPack Security
 * Read https://medium.com/att-israel/how-to-migrate-to-encrypted-shared-preferences-cc4105c03518
 */
interface SharedPreferencesManager {
    fun <T : Any?> set(key: String, value: T)
    fun getString(key: String, defaultValue: String): String
    fun getInt(key: String, defaultValue: Int): Int
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun getLong(key: String, defaultValue: Long): Long
    fun getFloat(key: String, defaultValue: Float): Float
    fun contains(key: String): Boolean
    fun remove(key: String)
    fun clear()

    /**
     * Default implementation of SharedPreferencesManager
     *
     */
    class Impl(context: Context, name: String) : SharedPreferencesManager {
        private var prefs: SharedPreferences =
            context.getSharedPreferences(name, Context.MODE_PRIVATE)

        override fun <T : Any?> set(key: String, value: T) {
            prefs.set(key, value)
        }

        override fun getString(key: String, defaultValue: String): String {
            val value = getValue(key, defaultValue)
            return value as String
        }

        override fun getInt(key: String, defaultValue: Int): Int {
            val value = getValue(key, defaultValue)
            return value as Int
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
            val value = getValue(key, defaultValue)
            return value as Boolean
        }

        override fun getLong(key: String, defaultValue: Long): Long {
            val value = getValue(key, defaultValue)
            return value as Long
        }

        override fun getFloat(key: String, defaultValue: Float): Float {
            val value = getValue(key, defaultValue)
            return value as Float
        }

        private fun getValue(key: String, defaultValue: Any?): Any? {
            var value = prefs.all[key]
            value = value ?: defaultValue
            return value
        }

        override fun contains(key: String): Boolean {
            return prefs.contains(key)
        }

        override fun remove(key: String) {
            prefs.remove(key)
        }

        override fun clear() {
            prefs.clear()
        }
    }
}

// Extensions here
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
