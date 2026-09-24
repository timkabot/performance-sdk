package io.github.timkabot.performance.api

data class PerformanceEvent(
    val kind: PerformanceEventKind,
    val space: String?,
    val owner: String?,
    val stage: String,
    val durationMs: Long?,
    val mode: PerformanceTraceMode?,
    val attributes: Map<String, Any?>,
    val elapsedRealtimeMs: Long,
)

enum class PerformanceEventKind {
    StageDuration,
    CustomTrace,
    UiFirstFrame,
    UiDataReady,
    UiSession,
    Startup,
}

enum class PerformanceTraceMode {
    Screen,
    Widget,
    Custom,
}

fun interface PerformanceEventCallback {
    fun onEvent(event: PerformanceEvent)
}
