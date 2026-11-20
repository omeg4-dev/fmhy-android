package io.github.omeg4dev.fmhy

import android.os.StrictMode
import android.util.Log

internal object DebugStrictMode {
    const val PROPERTY = "debug.fmhy.strict"

    /** StrictMode is on in every debug build unless debug.fmhy.strict is 0. */
    fun shouldEnable(isDebugBuild: Boolean, propertyValue: String?): Boolean {
        return isDebugBuild && propertyValue?.trim() != "0"
    }

    /** Reads PROPERTY via `getprop` (debug builds only; avoids the hidden SystemProperties API); null on any failure. */
    fun readProperty(): String? = runCatching {
        val process = Runtime.getRuntime().exec(arrayOf("getprop", PROPERTY))
        try {
            process.inputStream.bufferedReader().use { it.readLine() }
        } finally {
            process.destroy()
        }
    }.getOrNull()

    /** In debug builds, unless the property is 0: thread + VM policies with penaltyLog, then logs "StrictMode on". */
    fun applyIfRequested() {
        if (!BuildConfig.DEBUG) return
        if (shouldEnable(BuildConfig.DEBUG, readProperty())) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
            Log.i("FmhyStrict", "StrictMode on")
        }
    }
}
