@file:Suppress("LongParameterList")

package io.github.timkabot.performance.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Process
import android.view.Choreographer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.metrics.performance.JankStats
import kotlinx.coroutines.flow.first
import io.github.timkabot.performance.api.PerformanceEventKind
import io.github.timkabot.performance.api.PerformanceOwner
import io.github.timkabot.performance.api.PerformanceSpace
import io.github.timkabot.performance.api.PerformanceStage
import io.github.timkabot.performance.api.PerformanceTraceMode
import io.github.timkabot.performance.runtime.PerformanceRuntime
import io.github.timkabot.performance.runtime.PerformanceSdk
import io.github.timkabot.performance.runtime.buildUiSessionAttributes
import io.github.timkabot.performance.runtime.nanosToMillis
import io.github.timkabot.performance.tracing.DataReadyCondition

private const val NS_TO_MS = 1_000_000L
private const val FROZEN_FRAME_DURATION_NS = 700L * NS_TO_MS

@Composable
fun UiScreenTracer(
    space: PerformanceSpace,
    owner: PerformanceOwner? = null,
    viewName: String,
    dataReadyCondition: DataReadyCondition = DataReadyCondition.NoRequiredData,
    content: @Composable () -> Unit,
) {
    val runtime = PerformanceSdk.runtime()
    val state = rememberTraceState(
        runtime = runtime,
        space = space,
        owner = owner,
        viewName = viewName,
        mode = PerformanceTraceMode.Screen,
    )
    if (state == null) {
        content()
        return
    }
    ActiveScreenTracer(state, dataReadyCondition, content)
}

@Composable
fun UiWidgetTracer(
    space: PerformanceSpace,
    owner: PerformanceOwner? = null,
    viewName: String,
    dataReadyCondition: DataReadyCondition = DataReadyCondition.NoRequiredData,
    content: @Composable () -> Unit,
) {
    val runtime = PerformanceSdk.runtime()
    val state = rememberTraceState(
        runtime = runtime,
        space = space,
        owner = owner,
        viewName = viewName,
        mode = PerformanceTraceMode.Widget,
    )
    if (state == null) {
        content()
        return
    }
    ActiveWidgetTracer(state, dataReadyCondition, content)
}

@Deprecated(
    message = "Use UiScreenTracer or UiWidgetTracer explicitly.",
    replaceWith = ReplaceWith("UiWidgetTracer(space, owner, viewName, dataReadyCondition, content)"),
)
@Composable
fun UiPerformanceTracer(
    space: PerformanceSpace,
    owner: PerformanceOwner? = null,
    viewName: String,
    mode: PerformanceTraceMode = PerformanceTraceMode.Widget,
    dataReadyCondition: DataReadyCondition = DataReadyCondition.NoRequiredData,
    content: @Composable () -> Unit,
) {
    when (mode) {
        PerformanceTraceMode.Screen -> UiScreenTracer(space, owner, viewName, dataReadyCondition, content)
        PerformanceTraceMode.Widget,
        PerformanceTraceMode.Custom -> UiWidgetTracer(space, owner, viewName, dataReadyCondition, content)
    }
}

@Composable
private fun rememberTraceState(
    runtime: PerformanceRuntime,
    space: PerformanceSpace,
    owner: PerformanceOwner?,
    viewName: String,
    mode: PerformanceTraceMode,
): TraceState? {
    val sampled = remember(runtime, space.performanceName, owner?.performanceName, viewName, mode) {
        runtime.shouldSample()
    }
    if (!sampled) return null
    return remember(runtime, space.performanceName, owner?.performanceName, viewName, mode) {
        TraceState(
            runtime = runtime,
            space = space,
            owner = owner,
            viewName = viewName,
            mode = mode,
            startNs = runtime.clock.elapsedRealtimeNanos(),
            cpuStartMs = Process.getElapsedCpuTime(),
        )
    }
}

@Composable
private fun ActiveScreenTracer(
    state: TraceState,
    dataReady: DataReadyCondition,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val window = remember(context) { context.activityOrNull()?.window }
    val choreographer = remember { Choreographer.getInstance() }
    val frameStats = remember { FrameStatsCollector() }

    DisposableEffect(window) {
        if (window == null) return@DisposableEffect onDispose {}
        val jankStats = JankStats.createAndTrack(window) { frameData ->
            frameStats.frames++
            frameStats.activeRenderingTimeNanos += frameData.frameDurationUiNanos
            if (frameData.isJank) frameStats.jankFrames++
            if (frameData.frameDurationUiNanos >= FROZEN_FRAME_DURATION_NS) frameStats.frozenFrames++
        }
        onDispose { jankStats.isTrackingEnabled = false }
    }

    DisposableEffect(Unit) {
        val callback = Choreographer.FrameCallback {
            state.runtime.emit(
                kind = PerformanceEventKind.UiFirstFrame,
                space = state.space,
                owner = state.owner,
                stage = PerformanceStage.FirstFrameRenderTime,
                durationMs = nanosToMillis(state.runtime.clock.elapsedRealtimeNanos() - state.startNs),
                mode = state.mode,
                attributes = mapOf("view_name" to state.viewName),
            )
        }
        choreographer.postFrameCallback(callback)
        onDispose { choreographer.removeFrameCallback(callback) }
    }

    LaunchedEffect(dataReady) {
        awaitDataReadyAndLog(dataReady, state, choreographer)
    }

    DisposableEffect(Unit) {
        onDispose {
            val cpuEndMs = Process.getElapsedCpuTime()
            val frameSnapshot = frameStats.snapshot()
            val sessionMs = nanosToMillis(state.runtime.clock.elapsedRealtimeNanos() - state.startNs)
            state.runtime.emit(
                kind = PerformanceEventKind.UiSession,
                space = state.space,
                owner = state.owner,
                stage = PerformanceStage.OnScreenDisposed,
                durationMs = sessionMs,
                mode = state.mode,
                attributes = state.sessionAttributes(frameSnapshot, cpuEndMs, sessionMs),
                includeRuntimeSnapshot = true,
            )
        }
    }

    content()
}

@Composable
private fun ActiveWidgetTracer(
    state: TraceState,
    dataReady: DataReadyCondition,
    content: @Composable () -> Unit,
) {
    val choreographer = remember { Choreographer.getInstance() }
    LaunchedEffect(dataReady) {
        awaitDataReadyAndLog(dataReady, state, choreographer)
    }
    content()
}

private suspend fun awaitDataReadyAndLog(
    dataReady: DataReadyCondition,
    state: TraceState,
    choreographer: Choreographer,
) {
    if (dataReady !is DataReadyCondition.FlowDataReady) return
    dataReady.flow.first { it }
    val dataReadyNs = state.runtime.clock.elapsedRealtimeNanos()
    val waitDataMs = nanosToMillis(dataReadyNs - state.startNs)
    state.runtime.emit(
        kind = PerformanceEventKind.UiDataReady,
        space = state.space,
        owner = state.owner,
        stage = PerformanceStage.BeforeDataBecameReadyTime,
        durationMs = waitDataMs,
        mode = state.mode,
        attributes = mapOf("view_name" to state.viewName),
    )
    choreographer.postFrameCallback {
        val renderAfterDataReadyMs = nanosToMillis(state.runtime.clock.elapsedRealtimeNanos() - dataReadyNs)
        val totalMs = waitDataMs + renderAfterDataReadyMs
        state.runtime.emit(
            kind = PerformanceEventKind.UiDataReady,
            space = state.space,
            owner = state.owner,
            stage = PerformanceStage.DataRenderTime,
            durationMs = renderAfterDataReadyMs,
            mode = state.mode,
            attributes = mapOf("view_name" to state.viewName),
        )
        state.runtime.emit(
            kind = PerformanceEventKind.UiDataReady,
            space = state.space,
            owner = state.owner,
            stage = PerformanceStage.ScreenLoadTime,
            durationMs = totalMs,
            mode = state.mode,
            attributes = mapOf("view_name" to state.viewName),
        )
    }
}

private fun TraceState.sessionAttributes(
    frameSnapshot: FrameStatsSnapshot,
    cpuEndMs: Long,
    sessionMs: Long,
): Map<String, Any?> = buildUiSessionAttributes(
    viewName = viewName,
    frames = frameSnapshot.frames,
    jankFrames = frameSnapshot.jankFrames,
    frozenFrames = frameSnapshot.frozenFrames,
    activeRenderingTimeNanos = frameSnapshot.activeRenderingTimeNanos,
    cpuStartMs = cpuStartMs,
    cpuEndMs = cpuEndMs,
    sessionMs = sessionMs,
)

private data class TraceState(
    val runtime: PerformanceRuntime,
    val space: PerformanceSpace,
    val owner: PerformanceOwner?,
    val viewName: String,
    val mode: PerformanceTraceMode,
    val startNs: Long,
    val cpuStartMs: Long,
)

private class FrameStatsCollector(
    var frames: Long = 0,
    var jankFrames: Long = 0,
    var activeRenderingTimeNanos: Long = 0,
    var frozenFrames: Long = 0,
) {
    fun snapshot(): FrameStatsSnapshot = FrameStatsSnapshot(
        frames = frames,
        jankFrames = jankFrames,
        activeRenderingTimeNanos = activeRenderingTimeNanos,
        frozenFrames = frozenFrames,
    )
}

private data class FrameStatsSnapshot(
    val frames: Long,
    val jankFrames: Long,
    val activeRenderingTimeNanos: Long,
    val frozenFrames: Long,
)

private fun Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}
