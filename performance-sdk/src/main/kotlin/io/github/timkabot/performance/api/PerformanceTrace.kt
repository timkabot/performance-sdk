package io.github.timkabot.performance.api

data class PerformanceTrace(
    val key: String,
    val attributes: Map<String, String> = emptyMap(),
)
