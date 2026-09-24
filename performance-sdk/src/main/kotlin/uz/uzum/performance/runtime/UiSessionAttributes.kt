package uz.uzum.performance.runtime

import android.annotation.SuppressLint
import uz.uzum.performance.api.PerformanceAttributeKeys
import kotlin.math.roundToInt

private const val NS_TO_MS = 1_000_000L
private const val HITCH_MIN_FRAMES = 5

@SuppressLint("DefaultLocale")
internal fun buildUiSessionAttributes(
    viewName: String,
    frames: Long,
    jankFrames: Long,
    frozenFrames: Long,
    activeRenderingTimeNanos: Long,
    cpuStartMs: Long,
    cpuEndMs: Long,
    sessionMs: Long,
): Map<String, Any?> {
    val cpuDeltaMs = (cpuEndMs - cpuStartMs).coerceAtLeast(0L)
    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val cpuUsagePct = if (sessionMs > 0) {
        ((cpuDeltaMs.toDouble() / (sessionMs * cores)) * 100.0).coerceIn(0.0, 100.0)
    } else {
        null
    }
    return buildMap {
        put("view_name", viewName)
        put(PerformanceAttributeKeys.SessionDuration, sessionMs)
        put(PerformanceAttributeKeys.TotalFrames, frames.takeIf { it > 0 })
        put(
            PerformanceAttributeKeys.ActiveTime,
            activeRenderingTimeNanos.takeIf { it > 0 }?.let { it / NS_TO_MS },
        )
        put(PerformanceAttributeKeys.FrozenFrames, frozenFrames)
        if (frames >= HITCH_MIN_FRAMES) {
            put(
                PerformanceAttributeKeys.HitchRate,
                ((jankFrames * 100f) / frames).roundToInt(),
            )
        }
        put(PerformanceAttributeKeys.CpuUsage, cpuUsagePct?.let { String.format("%.1f", it) })
    }.filterValues { it != null }
}
