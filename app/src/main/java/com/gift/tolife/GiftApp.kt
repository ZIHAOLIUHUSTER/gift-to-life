package com.gift.tolife

import android.app.Application
import com.gift.tolife.core.ai.SummaryWorkScheduler
import com.gift.tolife.core.common.ImageStore
import com.gift.tolife.core.database.dao.EntryDao
import com.gift.tolife.core.notify.SummaryNotifier
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class GiftApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val entryPoint = EntryPointAccessors.fromApplication(this, CleanupEntryPoint::class.java)
        entryPoint.imageStore().cleanupTempFiles()

        // 2026-09-24：回顾通知渠道 + 首次排期（周志周一 09:00 / 月志每月 1 号 09:00）
        SummaryNotifier.ensureChannel(this)
        entryPoint.scheduler().scheduleAll()

        applicationScope.launch {
            val referencedPaths = entryPoint.entryDao().getAllImagePaths().toSet()
            entryPoint.imageStore().removeOrphans(referencedPaths)
        }

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("GiftApp", "Uncaught exception on $thread", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CleanupEntryPoint {
        fun imageStore(): ImageStore
        fun entryDao(): EntryDao
        fun scheduler(): SummaryWorkScheduler
    }
}
