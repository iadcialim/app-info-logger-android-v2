package com.quantuminventions.appInfoLogger

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.jaredrummler.android.device.DeviceName
import java.util.*

/**
 * Class that gets the device model, app id, and other info and send it Firebase Functions
 * and to be saved in Firebase Firestore
 * This saves and retrieves data in the SharedPreferreces file
 * @param context
 * @param sharedPreferencesName the name of the [SharedPreferences] to use
 *        If null, it will create a new SharedPreference file of its own
 */
class AppInfoLogger(
    private val context: Context,
    sharedPreferencesName: String? = null
) {
    private var sharedPreferencesManager: SharedPreferencesManager? = null

    companion object {
        const val APP_ID = "appId"
        const val DEFAULT_PREF_NAME = "appInfo"
        const val FIREBASE_FUNCTION = "appInfoLogger"
        const val DATE_UPDATED = "dateUpdated"
        const val SAVE_TIME_INTERVAL = 2419200000 // 4 weeks = 2419200000, 1 min = 60000
        const val MAX_CALLS_PER_INTERVAL = 2 // max calls per SAVE_TIME_INTERVAL
        const val CALL_COUNTER = "callCtr"
    }

    init {
        sharedPreferencesManager =
            SharedPreferencesManager.Impl(context, sharedPreferencesName ?: DEFAULT_PREF_NAME)
    }

    /**
     * Saves the app fcmToken and userId in the app
     * @param environment this will determine on which environment (part of collection name) in Firestore
     * @param fcmToken Firebase Cloud Messaging token used for push notifications
     * @param userId Important to know which user has this app information
     */
    fun saveAppInfo(environment: String, userId: String, fcmToken: String? = null) {
        // only send the app info every SAVE_TIME_INTERVAL so the it won't abuse the Firebase function
        val lastUpdatedDate = sharedPreferencesManager?.getLong(DATE_UPDATED, 0) ?: 0

        val dateNow = Date().time
        val difference = dateNow - lastUpdatedDate
        Log.d(
            "[saveAppInfo]",
            "difference=$difference, SAVE_TIME_INTERVAL=$SAVE_TIME_INTERVAL"
        )

        if (difference > SAVE_TIME_INTERVAL) {
            val callCtr = sharedPreferencesManager?.getInt(CALL_COUNTER, 0) ?: 0
            Log.d(
                "[saveAppInfo]",
                "callCtr=$callCtr, MAX_CALLS_PER_INTERVAL=$MAX_CALLS_PER_INTERVAL"
            )

            if (callCtr < MAX_CALLS_PER_INTERVAL) {
                getDeviceDetails(context) {
                    saveToFirebase(environment, it, fcmToken, userId)
                }
            } else {
                sharedPreferencesManager?.set(CALL_COUNTER, 0)
            }
        } else {
            sharedPreferencesManager?.set(CALL_COUNTER, 0)
            Log.d("[saveAppInfo]", "It's not time to save yet")
        }
    }

    /**
     * Gets the device details
     */
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

    /**
     * Actual call of the Firebase Function and sending of the data
     * @param environment this will determine on which environment (part of collection name) in Firestore
     * @param fcmToken Firebase Cloud Messaging token used for push notifications
     * @param userId Important to know which user has this app information
     */
    private fun saveToFirebase(
        environment: String,
        deviceInfo: DeviceInfo,
        fcmToken: String? = null,
        userId: String? = null
    ) {
        try {
            val date = Date()
            val data = hashMapOf(
                "environment" to environment,
                "platform" to "android",
                APP_ID to getAppId(),
                "userId" to userId,
                "deviceManufacturer" to deviceInfo.deviceManufacturer,
                "deviceModel" to deviceInfo.deviceModel,
                "deviceName" to deviceInfo.deviceName,
                "fcmToken" to fcmToken,
                "sdkVersion" to Build.VERSION.SDK_INT,
                "osVersion" to Build.VERSION.RELEASE,
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

                        val callCtr = sharedPreferencesManager?.getInt(CALL_COUNTER, 0) ?: 0
                        val newCtr = callCtr + 1
                        sharedPreferencesManager?.set(CALL_COUNTER, newCtr)
                        Log.d(
                            "[saveAppInfo]",
                            "newCtr=$newCtr, MAX_CALLS_PER_INTERVAL=$MAX_CALLS_PER_INTERVAL"
                        )
                        if (newCtr >= MAX_CALLS_PER_INTERVAL) {
                            sharedPreferencesManager?.set(DATE_UPDATED, date.time)
                        }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Gets a unique id of the app and saves it in the SharedPreference file
     */
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
}
