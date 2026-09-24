package uz.uzum.performance.api

data class PerformanceTrace(
    val key: String,
    val attributes: Map<String, String> = emptyMap(),
)
