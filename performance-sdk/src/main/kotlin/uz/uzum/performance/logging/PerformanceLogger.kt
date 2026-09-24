package uz.uzum.performance.logging

import uz.uzum.performance.api.PerformanceEvent
import uz.uzum.performance.api.PerformanceEventCallback

fun interface PerformanceLogger {
    fun log(message: String)
}

object NoOpPerformanceLogger : PerformanceLogger {
    override fun log(message: String) = Unit
}

class StdoutPerformanceLogger(
    private val tag: String = "PerformanceSdk",
) : PerformanceLogger {
    override fun log(message: String) {
        println("$tag: $message")
    }
}

class LoggingPerformanceEventCallback(
    private val logger: PerformanceLogger = StdoutPerformanceLogger(),
) : PerformanceEventCallback {
    override fun onEvent(event: PerformanceEvent) {
        logger.log(event.toLogLine())
    }
}

fun PerformanceEvent.toLogLine(): String {
    val duration = durationMs?.let { " duration=${it}ms" }.orEmpty()
    val spacePart = space?.let { " space=$it" }.orEmpty()
    val ownerPart = owner?.let { " owner=$it" }.orEmpty()
    val modePart = mode?.let { " mode=$it" }.orEmpty()
    val attrs = if (attributes.isEmpty()) "" else " attributes=$attributes"
    return "kind=$kind stage=$stage$duration$spacePart$ownerPart$modePart$attrs"
}
