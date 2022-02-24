package com.quantuminventions.appInfoLogger

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.jaredrummler.android.device.DeviceName
import java.util.*

class AppInfoLogger(
    private val context: Context,
    private val config: Config,
    sharedPreferencesName: String? = null
) {
    private var sharedPreferencesManager: SharedPreferencesManager? = null

    companion object {
        const val APP_ID = "appId"
        const val DEFAULT_PREF_NAME = "appInfo"
        const val FIREBASE_FUNCTION = "appInfoLogger"
        const val DATE_UPDATED = "dateUpdated"
        const val SAVE_TIME_INTERVAL = 2419200000 // 4 weeks
    }

    init {
        sharedPreferencesManager =
            SharedPreferencesManager.Impl(context, sharedPreferencesName ?: DEFAULT_PREF_NAME)
    }

    fun saveAppInfo(fcmToken: String? = null, userId: String? = null) {
        // only send the app info every SAVE_TIME_INTERVAL so the it won't abuse the Firebase function
        val lastUpdatedDate = sharedPreferencesManager?.getLong(DATE_UPDATED, 0) ?: 0
        val dateNow = Date().time
        val difference = dateNow - lastUpdatedDate
        Log.d("[saveAppInfo]", "difference=$difference, SAVE_TIME_INTERVAL=$SAVE_TIME_INTERVAL")

        if (lastUpdatedDate == 0L || difference > SAVE_TIME_INTERVAL) {
            getDeviceDetails(context) {
                saveAppInfo(it, fcmToken, userId)
            }
        } else {
            Log.d("[saveAppInfo]", "It's not time to save yet")
        }
    }

    private fun getDeviceDetails(context: Context, callback: (deviceInfo: DeviceInfo) -> Unit) {
        try {
            DeviceName.with(context).request { info, error ->
                val manufacturer = info.manufacturer ?: Build.MANUFACTURER // "Samsung"
                val name = info.marketName // "Galaxy S8+"
                val model = info.model ?: Build.MODEL // "SM-G955W"
                val codename = info.codename // "dream2qltecan"
                val deviceName = info.name // "Galaxy S8+"
                Log.d(
                    "[getDeviceDetails]",
                    "manufacturer=$manufacturer, name=$name, model=$model, codename=$codename, deviceName=$deviceName, error=$error"
                )
                callback(DeviceInfo(manufacturer, model, name))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveAppInfo(
        deviceInfo: DeviceInfo,
        fcmToken: String? = null,
        userId: String? = null
    ) {
        try {
            val date = Date()
            val data = hashMapOf(
                "config" to "${config.collection}-${config.environment}",
                "platform" to "android",
                APP_ID to getAppId(),
                "userId" to userId,
                "deviceManufacturer" to deviceInfo.deviceManufacturer,
                "deviceModel" to deviceInfo.deviceModel,
                "deviceName" to deviceInfo.deviceName,
                "fcmToken" to fcmToken,
                "sdkVersion" to Build.VERSION.SDK_INT,
                "osVersion" to Build.VERSION.RELEASE_OR_CODENAME,
                "userId" to userId,
                "dateUpdatedLong" to date.time,
                "dateUpdatedStr" to date.toStr()
            )

            Firebase.functions?.getHttpsCallable(FIREBASE_FUNCTION)
                ?.call(data)
                ?.continueWith { task ->
                    // This continuation runs on either success or failure, but if the task
                    // has failed then result will throw an Exception which will be
                    // propagated down.
                    Log.d("[saveAppInfo]", "result=${task.result?.data}")
                    // val result = task.result?.data as String
                    // Log.d("[rootCheck]", "result=$result")
                    // result
                    ""
                }
                ?.addOnCompleteListener { task ->
                    Log.d("[saveAppInfo]", "task=$task")
                    if (!task.isSuccessful) {
                        val e = task.exception
                        e?.printStackTrace()
                        if (e is FirebaseFunctionsException) {
                            val code = e.code
                            val details = e.details
                            Log.e("[saveAppInfo]", "code=$code, details=$details")
                        }
                    } else {
                        Log.d("[saveAppInfo]", "SUCCESS task=${task.result}")
                        sharedPreferencesManager?.set(DATE_UPDATED, date.time)
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getAppId(): String {
        val appId = sharedPreferencesManager?.getString(APP_ID, "")
        return if (!appId.isNullOrBlank()) {
            appId
        } else {
            val random = UUID.randomUUID().toString()
            sharedPreferencesManager?.set(APP_ID, random)
            random
        }
    }

    data class DeviceInfo(
        val deviceManufacturer: String?,
        val deviceModel: String?,
        val deviceName: String?
    )

    data class Config(
        val collection: String, // table name
        val environment: String, // dev, staging, prod
    )
}
