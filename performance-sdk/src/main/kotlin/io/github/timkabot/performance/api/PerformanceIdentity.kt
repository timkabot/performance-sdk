package io.github.timkabot.performance.api

interface PerformanceSpace {
    val performanceName: String
}

interface PerformanceOwner {
    val performanceName: String
}

data class PerformanceSpaceName(
    override val performanceName: String,
) : PerformanceSpace

data class PerformanceOwnerName(
    override val performanceName: String,
) : PerformanceOwner

fun String.asPerformanceSpace(): PerformanceSpace = PerformanceSpaceName(this)

fun String.asPerformanceOwner(): PerformanceOwner = PerformanceOwnerName(this)
