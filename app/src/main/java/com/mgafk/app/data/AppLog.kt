package com.mgafk.app.data

import android.util.Log
import com.mgafk.app.BuildConfig

/**
 * Centralized logging wrapper.
 * Debug logs are suppressed in release builds.
 */
object AppLog {

    private val isDebug = BuildConfig.DEBUG

    fun d(tag: String, message: String) {
        if (isDebug) Log.d(tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
