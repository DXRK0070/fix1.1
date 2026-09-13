package com.anil.igbizlogger

import android.app.Application
import android.os.Build

/**
 * Installs before the launcher Activity so startup exceptions are still recorded
 * when MainActivity never reaches its first rendered frame.
 */
class BizLoggerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashDiagnostics.install(this)
        CrashDiagnostics.checkpoint(
            this,
            "Application.onCreate completed; Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        )
    }
}