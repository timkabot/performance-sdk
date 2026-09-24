package uz.uzum.performance.api

sealed class PerformanceStage(
    open val id: String,
    open val attributes: Map<String, Any?> = emptyMap(),
) {
    data object AppStartupTime : PerformanceStage("AppStartupTime")

    data class InitViewModelTime(
        val viewModelName: String,
    ) : PerformanceStage(
        id = "InitViewModelTime",
        attributes = mapOf(PerformanceAttributeKeys.ViewModelName to viewModelName),
    )

    data class MapToDomainTime(
        val requestName: String,
    ) : PerformanceStage(
        id = "MapToDomainTime",
        attributes = mapOf(PerformanceAttributeKeys.RequestName to requestName),
    )

    data class NetworkRequestsTotalTime(
        val requestName: String,
    ) : PerformanceStage(
        id = "NetworkRequestsTotalTime",
        attributes = mapOf(PerformanceAttributeKeys.RequestName to requestName),
    )

    data object BeforeDataBecameReadyTime : PerformanceStage("BeforeDataBecameReadyTime")
    data object FirstFrameRenderTime : PerformanceStage("FirstFrameRenderTime")
    data object DataRenderTime : PerformanceStage("DataRenderTime")
    data object ScreenLoadTime : PerformanceStage("ScreenLoadTime")
    data object OnScreenDisposed : PerformanceStage("OnScreenDisposed")

    data class Custom(
        override val id: String,
        override val attributes: Map<String, Any?> = emptyMap(),
    ) : PerformanceStage(id, attributes)
}

object PerformanceAttributeKeys {
    const val Time = "time_ms"
    const val RequestName = "request_name"
    const val ViewModelName = "view_model_name"
    const val TraceKey = "trace_key"
    const val StartupType = "startup_type"
    const val FirstScreen = "first_screen"
    const val DeviceTier = "device_tier"
    const val NetworkQuality = "network_quality"
    const val RefreshRate = "refresh_rate_hz"
    const val SessionDuration = "session_duration_ms"
    const val TotalFrames = "total_frames"
    const val HitchRate = "hitch_rate"
    const val FrozenFrames = "frozen_frames"
    const val ActiveTime = "active_time_ms"
    const val BatteryLevel = "battery_level"
    const val MemoryUsage = "memory_usage_mb"
    const val CpuUsage = "cpu_usage_pct"
}
