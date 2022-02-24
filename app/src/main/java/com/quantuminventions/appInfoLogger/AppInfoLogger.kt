package com.quantuminventions.appInfoLogger

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.jaredrummler.android.device.DeviceName
import java.util.*

class AppInfoLogger(private val context: Context, sharedPreferencesName: String? = null) {
    private var sharedPreferencesManager: SharedPreferencesManager? = null

    init {
        sharedPreferencesManager =
            SharedPreferencesManager.Impl(context, sharedPreferencesName ?: "appInfo")
    }

    fun saveAppInfo(fcmToken: String? = null, userId: String? = null) {
        getDeviceDetails(context) {
            saveAppInfo(it, fcmToken, userId)
        }
    }

    private fun getDeviceDetails(context: Context, callbacks: (deviceInfo: DeviceInfo) -> Unit) {
        // DeviceName.init(context)
        DeviceName.with(context).request { info, error ->
            val manufacturer = info.manufacturer // "Samsung"
            val name = info.marketName // "Galaxy S8+"
            val model = info.model // "SM-G955W"
            val codename = info.codename // "dream2qltecan"
            val deviceName = info.name // "Galaxy S8+"
            Log.d(
                "[getDeviceDetails]",
                "manufacturer=$manufacturer, name=$name, model=$model, codename=$codename, deviceName=$deviceName, error=$error"
            )
            callbacks(DeviceInfo(manufacturer, model, name))
        }
    }

    private fun saveAppInfo(
        deviceInfo: DeviceInfo,
        fcmToken: String? = null,
        userId: String? = null
    ) {
        val date = Date()
        val data = hashMapOf(
            "platform" to "android",
            "appId" to getAppId(),
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

        Firebase.functions?.getHttpsCallable("appInfoLogger")
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
                }
            }
    }

    private fun getAppId(): String {
        val appId = sharedPreferencesManager?.getString("appId", "")
        return if (!appId.isNullOrBlank()) {
            appId
        } else {
            val random = UUID.randomUUID().toString()
            sharedPreferencesManager?.set("appId", random)
            random
        }
    }

    data class DeviceInfo(
        val deviceManufacturer: String,
        val deviceModel: String,
        val deviceName: String
    )
}
