package com.gift.tolife

import android.app.Application
import com.gift.tolife.core.common.ImageStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class GiftApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 清理崩溃残留的导入临时目录
        val entryPoint = EntryPointAccessors.fromApplication(this, CleanupEntryPoint::class.java)
        entryPoint.imageStore().cleanupStagingDirs()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("GiftApp", "Uncaught exception on $thread", throwable)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CleanupEntryPoint {
        fun imageStore(): ImageStore
    }
}