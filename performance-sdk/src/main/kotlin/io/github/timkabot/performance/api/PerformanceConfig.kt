package io.github.timkabot.performance.api

data class PerformanceConfig(
    val enabled: Boolean = true,
    val sampleRate: Double = 1.0,
) {
    init {
        require(sampleRate in 0.0..1.0) {
            "sampleRate must be between 0.0 and 1.0"
        }
    }
}

fun interface PerformanceConfigProvider {
    fun get(): PerformanceConfig
}
