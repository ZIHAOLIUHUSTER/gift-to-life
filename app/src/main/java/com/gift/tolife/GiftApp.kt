package com.gift.tolife

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GiftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("GiftApp", "Uncaught exception on $thread", throwable)
        }
    }
}
