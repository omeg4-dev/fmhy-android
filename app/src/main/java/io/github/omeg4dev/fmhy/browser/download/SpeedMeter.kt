package io.github.omeg4dev.fmhy.browser.download

import kotlin.math.ceil
import kotlin.math.roundToLong

/** Smoothed download speed. Not thread-safe; one per running download. */
class SpeedMeter(private val alpha: Double = 0.3) {

    private var lastSampleBytes: Long? = null
    private var lastSampleMs: Long? = null
    private var smoothedRate: Double? = null

    /** Feed the total bytes so far at time [nowMs]. Samples closer than 250 ms to the previous one are ignored for speed. */
    fun sample(totalBytes: Long, nowMs: Long) {
        val prevMs = lastSampleMs
        val prevBytes = lastSampleBytes
        if (prevMs == null || prevBytes == null) {
            lastSampleMs = nowMs
            lastSampleBytes = totalBytes
            return
        }

        val dt = nowMs - prevMs
        if (dt < 250L) {
            return
        }

        val deltaBytes = (totalBytes - prevBytes).coerceAtLeast(0L)
        val instantRate = (deltaBytes.toDouble() * 1000.0) / dt.toDouble()

        val currentSmoothed = smoothedRate
        smoothedRate = if (currentSmoothed == null) {
            instantRate
        } else {
            alpha * instantRate + (1.0 - alpha) * currentSmoothed
        }

        lastSampleMs = nowMs
        lastSampleBytes = totalBytes
    }

    /** Bytes/second (exponential moving average of per-sample rates), null until two samples ≥250 ms apart. */
    val bytesPerSecond: Long?
        get() = smoothedRate?.roundToLong()

    /** Seconds left for [remainingBytes], null if speed unknown/0 or remaining < 0. Rounded up. */
    fun etaSeconds(remainingBytes: Long): Long? {
        val speed = bytesPerSecond ?: return null
        if (speed <= 0L || remainingBytes < 0L) return null
        if (remainingBytes == 0L) return 0L
        return ceil(remainingBytes.toDouble() / speed.toDouble()).toLong()
    }

    fun reset() {
        lastSampleBytes = null
        lastSampleMs = null
        smoothedRate = null
    }
}
