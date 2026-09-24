package io.github.timkabot.performance.environment

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.WindowManager
import io.github.timkabot.performance.api.PerformanceAttributeKeys
import kotlin.math.roundToInt

data class PerformanceEnvironment(
    val deviceTier: String = "unknown",
    val networkQuality: String = "unknown",
    val refreshRateHz: Float = 60f,
)

interface PerformanceEnvironmentProvider {
    fun get(): PerformanceEnvironment

    fun getRuntimeSnapshot(): Map<String, Any?> = emptyMap()
}

object EmptyPerformanceEnvironmentProvider : PerformanceEnvironmentProvider {
    override fun get(): PerformanceEnvironment = PerformanceEnvironment()
}

class AndroidPerformanceEnvironmentProvider(
    context: Context,
    private val networkQualityProvider: () -> String = { "unknown" },
) : PerformanceEnvironmentProvider {

    private val appContext = context.applicationContext
    private val refreshRate by lazy { appContext.refreshRateHz().coerceAtLeast(1f) }
    private val deviceTier by lazy {
        computeDeviceTier(
            totalRamGb = appContext.totalRamGb(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            refreshRate = refreshRate,
        )
    }

    override fun get(): PerformanceEnvironment = PerformanceEnvironment(
        deviceTier = deviceTier,
        networkQuality = networkQualityProvider(),
        refreshRateHz = refreshRate,
    )

    override fun getRuntimeSnapshot(): Map<String, Any?> = mapOf(
        PerformanceAttributeKeys.BatteryLevel to appContext.batteryLevel(),
        PerformanceAttributeKeys.MemoryUsage to appContext.processPssMb(),
    )

    private fun computeDeviceTier(
        totalRamGb: Double,
        cpuCores: Int,
        refreshRate: Float,
    ): String {
        val base = when {
            totalRamGb < LOW_RAM_GB -> DEVICE_TIER_LOW
            totalRamGb < MID_RAM_GB -> DEVICE_TIER_MID
            else -> DEVICE_TIER_HIGH
        }
        val canMoveUp = cpuCores >= MID_CPU_CORES && refreshRate >= MID_REFRESH_RATE
        val shouldMoveDown = cpuCores <= LOW_CPU_CORES && refreshRate <= LOW_REFRESH_RATE
        return when {
            base == DEVICE_TIER_MID && canMoveUp -> DEVICE_TIER_HIGH
            base == DEVICE_TIER_HIGH && shouldMoveDown -> DEVICE_TIER_MID
            base == DEVICE_TIER_MID && shouldMoveDown -> DEVICE_TIER_LOW
            else -> base
        }
    }

    private companion object {
        private const val LOW_RAM_GB = 3.0
        private const val MID_RAM_GB = 6.0
        private const val LOW_CPU_CORES = 4
        private const val MID_CPU_CORES = 8
        private const val LOW_REFRESH_RATE = 60f
        private const val MID_REFRESH_RATE = 90f
        private const val DEVICE_TIER_LOW = "low"
        private const val DEVICE_TIER_MID = "mid"
        private const val DEVICE_TIER_HIGH = "high"
    }
}

private fun Context.refreshRateHz(): Float {
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    @Suppress("DEPRECATION")
    return windowManager?.defaultDisplay?.refreshRate ?: 60f
}

private fun Context.totalRamGb(): Double {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return 0.0
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    return memoryInfo.totalMem.toDouble() / BYTES_IN_GB
}

private fun Context.processPssMb(): Int? {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return null
    val pid = android.os.Process.myPid()
    val memoryInfo = activityManager.getProcessMemoryInfo(intArrayOf(pid)).firstOrNull()
    return memoryInfo?.totalPss?.let { it / KB_IN_MB }
}

private fun Context.batteryLevel(): Int? {
    val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return null
    val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    return ((level.toFloat() / scale) * 100f).roundToInt()
}

private const val BYTES_IN_GB = 1024.0 * 1024.0 * 1024.0
private const val KB_IN_MB = 1024
