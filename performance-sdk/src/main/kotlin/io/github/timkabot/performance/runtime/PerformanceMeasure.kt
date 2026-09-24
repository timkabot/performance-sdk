package io.github.timkabot.performance.runtime

import io.github.timkabot.performance.api.PerformanceOwner
import io.github.timkabot.performance.api.PerformanceSpace
import io.github.timkabot.performance.api.PerformanceStage
import io.github.timkabot.performance.api.PerformanceTrace
import io.github.timkabot.performance.api.PerformanceTraceMode

class PerformanceMeasure(
    private val runtimeProvider: () -> PerformanceRuntime = PerformanceSdk::runtime,
) {
    fun saveTime(
        space: PerformanceSpace,
        owner: PerformanceOwner? = null,
        stage: PerformanceStage,
        timeMs: Long,
        mode: PerformanceTraceMode? = null,
    ) {
        runtimeProvider().saveTime(
            space = space,
            owner = owner,
            stage = stage,
            timeMs = timeMs,
            mode = mode,
        )
    }

    fun <T> measure(
        space: PerformanceSpace,
        owner: PerformanceOwner? = null,
        stage: PerformanceStage,
        block: () -> T,
    ): T = runtimeProvider().measure(
        space = space,
        owner = owner,
        stage = stage,
        block = block,
    )

    suspend fun <T> measureSuspend(
        space: PerformanceSpace,
        owner: PerformanceOwner? = null,
        stage: PerformanceStage,
        block: suspend () -> T,
    ): T = runtimeProvider().measureSuspend(
        space = space,
        owner = owner,
        stage = stage,
        block = block,
    )

    fun <T> measureTrace(
        trace: PerformanceTrace,
        block: () -> T,
    ): T = runtimeProvider().measureTrace(
        trace = trace,
        block = block,
    )

    suspend fun <T> measureTraceSuspend(
        trace: PerformanceTrace,
        block: suspend () -> T,
    ): T = runtimeProvider().measureTraceSuspend(
        trace = trace,
        block = block,
    )
}
