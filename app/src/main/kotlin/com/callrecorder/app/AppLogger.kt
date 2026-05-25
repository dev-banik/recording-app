package com.callrecorder.app

import android.util.Log

object AppLogger {

    private const val TAG = "CallRecorder"
    private var debugEnabled = false

    fun init(debug: Boolean) {
        debugEnabled = debug
    }

    fun d(tag: String, msg: String) {
        if (debugEnabled) Log.d("$TAG/$tag", msg)
    }

    fun i(tag: String, msg: String) {
        Log.i("$TAG/$tag", msg)
    }

    fun w(tag: String, msg: String, throwable: Throwable? = null) {
        Log.w("$TAG/$tag", msg, throwable)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        Log.e("$TAG/$tag", msg, throwable)
    }
}
