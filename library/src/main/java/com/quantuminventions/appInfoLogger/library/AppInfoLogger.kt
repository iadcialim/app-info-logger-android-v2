package com.quantuminventions.appInfoLogger.library

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.NonNull
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.jaredrummler.android.device.DeviceName
import java.util.*

/**
 * Class that gets the device model, app id, and other info and send it Firebase Functions
 * and to be saved in Firebase Firestore
 * This saves and retrieves data in the SharedPreferences file
 */
class AppInfoLogger private constructor(builder: Builder) {

    private var context: Context = builder.context
    private var maxSavesPerInterval: Int = MAX_SAVES_PER_INTERVAL
    private var saveTimeInterval: Long = SAVE_TIME_INTERVAL
    private var sharedPreferencesName: String? = null

    // the name of the [SharedPreferences] to use.
    // If null, it will create a new SharedPreference file of its own
    private var sharedPreferencesManager: SharedPreferencesManager? = null

    init {
        this.maxSavesPerInterval = builder.maxSavesPerInterval
        this.saveTimeInterval = builder.saveTimeInterval
        sharedPreferencesManager =
            SharedPreferencesManager.Impl(context, sharedPreferencesName ?: DEFAULT_PREF_NAME)
    }

    companion object {
        const val APP_ID = "appId"
        const val DEFAULT_PREF_NAME = "appInfo"
        const val FIREBASE_FUNCTION = "appInfoLogger"
        const val DATE_UPDATED = "dateUpdated"
        const val CALL_COUNTER = "callCtr"
        const val APP_VERSION = "appVersion"
        const val OS_VERSION = "osVersion"

        // default values
        const val SAVE_TIME_INTERVAL = 2419200000 // 4 weeks = 2419200000, 1 min = 60000
        const val MAX_SAVES_PER_INTERVAL = 2
    }

    /**
     * Builder class to provide immutability to the parent class [AppInfoLogger]
     * Followed https://howtodoinjava.com/design-patterns/creational/builder-pattern-in-java/
     * @param context The context
     */
    class Builder(@NonNull val context: Context) {

        /**
         * Calling the Firebase function [saveToFirebase] is restricted to a number of calls [maxSavesPerInterval]
         * in a period of time [saveTimeInterval].
         * Example: If []maxCallsPerInterval] is 2419200000 (4 weeks) and [saveTimeInterval] is 2, it means it can
         * only call twice in a period of  4 weeks
         * This way the Firebase function is not abused and won't incur bill as long as its below some threshold
         */
        internal var maxSavesPerInterval: Int = MAX_SAVES_PER_INTERVAL
        internal var saveTimeInterval: Long = SAVE_TIME_INTERVAL

        /**
         * Sets time interval when the library can save in Firebase DB
         * @param saveTimeInterval the time interval between saving app info
         */
        fun setTimeIntervalToSave(saveTimeInterval: Long): Builder {
            this.saveTimeInterval = saveTimeInterval
            return this
        }

        /**
         * Sets the max number of saves per [saveTimeInterval]
         * @param maxSaves
         */
        fun setMaxSavesPerInterval(maxSaves: Int): Builder {
            this.maxSavesPerInterval = maxSaves
            return this
        }

        /**
         * This provides an instance of [AppInfoLogger] with param as [Builder] object
         * @return instance of [AppInfoLogger]
         */
        fun build(): AppInfoLogger {
            return AppInfoLogger(this)
        }
    }

    /**
     * Saves the app fcmToken and userId in the app
     * @param environment this will determine on which environment (part of collection name) in Firestore
     * @param appVersion The version name of the app
     * @param fcmToken Firebase Cloud Messaging token used for push notifications
     * @param userId Important to know which user has this app information
     */
    fun saveAppInfo(
        environment: String,
        appVersion: String,
        userId: String,
        fcmToken: String? = null
    ) {
        // only send the app info every SAVE_TIME_INTERVAL so the it won't abuse the Firebase function
        val lastUpdatedDate = sharedPreferencesManager?.getLong(DATE_UPDATED, 0) ?: 0

        val dateNow = Date().time
        val difference = dateNow - lastUpdatedDate
        val gotChange = hasItChanged(appVersion)
        Log.d(
            "[saveAppInfo]",
            "dateNow=$dateNow, lastUpdatedDate=$lastUpdatedDate, difference=$difference, " +
                    "saveTimeInterval=$saveTimeInterval, gotChange=$gotChange"
        )

        if (difference > saveTimeInterval || gotChange) {
            val callCtr = sharedPreferencesManager?.getInt(CALL_COUNTER, 0) ?: 0
            Log.d(
                "[saveAppInfo]",
                "callCtr=$callCtr, maxCallsPerInterval=$maxSavesPerInterval"
            )

            if (callCtr < maxSavesPerInterval) {
                getDeviceDetails(context) {
                    saveToFirebase(environment, appVersion, it, fcmToken, userId)
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
     * @param appVersion The version name of the app
     * @param fcmToken Firebase Cloud Messaging token used for push notifications
     * @param userId Important to know which user has this app information
     */
    private fun saveToFirebase(
        environment: String,
        appVersion: String,
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
                APP_VERSION to appVersion,
                "userId" to userId,
                "deviceManufacturer" to deviceInfo.deviceManufacturer,
                "deviceModel" to deviceInfo.deviceModel,
                "deviceName" to deviceInfo.deviceName,
                "fcmToken" to fcmToken,
                "sdkVersion" to Build.VERSION.SDK_INT,
                OS_VERSION to Build.VERSION.RELEASE,
                "userId" to userId,
                "dateUpdatedLong" to date.time,
                "dateUpdatedStr" to date.toStr()
            )

            // save them
            sharedPreferencesManager?.set(APP_VERSION, appVersion)
            sharedPreferencesManager?.set(OS_VERSION, Build.VERSION.RELEASE)

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
                            "newCtr=$newCtr, maxSavesPerInterval=$maxSavesPerInterval"
                        )
                        if (newCtr >= maxSavesPerInterval) {
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

    /**
     * Check if there are changes in the app version or OS version
     * @param appVersion The version name of the app
     *
     */
    private fun hasItChanged(appVersion: String): Boolean {
        return sharedPreferencesManager?.getString(APP_VERSION, "") != appVersion ||
                sharedPreferencesManager?.getString(OS_VERSION, "") != Build.VERSION.RELEASE
    }

    data class DeviceInfo(
        val deviceManufacturer: String?,
        val deviceModel: String?,
        val deviceName: String?
    )
}
