package io.github.timkabot.performance.runtime

import android.os.SystemClock

interface PerformanceClock {
    fun elapsedRealtimeMs(): Long
    fun elapsedRealtimeNanos(): Long
}

object SystemPerformanceClock : PerformanceClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun elapsedRealtimeNanos(): Long = SystemClock.elapsedRealtimeNanos()
}

internal fun nanosToMillis(nanos: Long): Long = nanos / 1_000_000L
