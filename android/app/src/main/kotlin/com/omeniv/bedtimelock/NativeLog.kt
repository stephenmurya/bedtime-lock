package com.omeniv.bedtimelock

import android.util.Log

object NativeLog {
    private const val TAG = "BedtimeLock"

    fun d(message: String) = Log.d(TAG, message)
}
