package io.github.timkabot.performance.view

import android.os.Process
import android.view.Choreographer
import android.view.Window
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
import io.github.timkabot.performance.tracing.DataReadyCondition

private const val NS_TO_MS = 1_000_000L
private const val FROZEN_FRAME_DURATION_NS = 700L * NS_TO_MS

/**
 * View-system counterpart of `io.github.timkabot.performance.compose.UiScreenTracer`, for
 * Activities/Fragments that render XML layouts instead of composables.
 *
 * Measures the same four stages as the Compose tracer: first frame render time,
 * data-ready wait, render-after-data-ready time, and a full screen session (jank,
 * frozen frames, CPU usage, battery/memory snapshot) once the screen is torn down.
 *
 * Usage in a Fragment:
 * ```
 * private val tracer = ViewScreenTracer(
 *     space = Screen.Cart,
 *     owner = Team.Checkout,
 *     viewName = "CartFragment",
 * )
 *
 * override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
 *     super.onViewCreated(view, savedInstanceState)
 *     tracer.start(requireActivity().window)
 *     viewLifecycleOwner.lifecycleScope.launch {
 *         tracer.awaitDataReady(DataReadyCondition.FlowDataReady(viewModel.dataReady))
 *     }
 * }
 *
 * override fun onDestroyView() {
 *     tracer.stop()
 *     super.onDestroyView()
 * }
 * ```
 *
 * For a `DialogFragment` / `BottomSheetDialogFragment`, pass `dialog!!.window`
 * instead of the host Activity's window — bottom sheets render into their own
 * `Window`, which the Activity's `JankStats` tracker would not observe.
 *
 * Sampling is decided once, the first time the tracer is actually used — via
 * [start] for a screen tracer, or via the first [awaitDataReady] call for a
 * widget tracer that never calls [start] — and held for the whole session:
 * either all of first-frame/data-ready/session events are emitted, or none
 * of them are — matching the Compose tracer's per-composition sampling semantics
 * (as opposed to [io.github.timkabot.performance.runtime.PerformanceRuntime.saveTime], which
 * re-samples independently on every call). Deferring this to first use (rather
 * than the constructor) also means the elapsed-time baseline is captured when
 * the screen actually starts, not whenever the tracer instance happened to be
 * created (e.g. a Fragment field created well before its view/`onViewCreated`).
 */
class ViewScreenTracer(
    private val space: PerformanceSpace,
    private val owner: PerformanceOwner? = null,
    private val viewName: String,
    private val mode: PerformanceTraceMode = PerformanceTraceMode.Screen,
    private val runtime: PerformanceRuntime = PerformanceSdk.runtime(),
) {
    private var sampled = false
    private var startNs = 0L
    private var cpuStartMs = 0L
    private var baselineSet = false

    private var jankStats: JankStats? = null
    private var started = false
    private var firstFrameSent = false
    private var dataReadyAwaited = false
    private var stopped = false
    private var frames = 0L
    private var jankFrames = 0L
    private var frozenFrames = 0L
    private var activeRenderingTimeNanos = 0L

    /**
     * Captures sampling decision and the elapsed-time/CPU-time baseline the first
     * time the tracer is actually used (from [start], [awaitDataReady], or [stop],
     * whichever runs first). Idempotent — later calls are no-ops.
     */
    private fun ensureBaseline() {
        if (baselineSet) return
        baselineSet = true
        sampled = runtime.shouldSample()
        startNs = runtime.clock.elapsedRealtimeNanos()
        cpuStartMs = Process.getElapsedCpuTime()
    }

    /**
     * Call as early as possible once the screen's window is available, e.g. from
     * `onViewCreated`. No-ops if this tracer instance was not sampled. Safe to call
     * more than once — only the first call attaches jank tracking, to avoid leaking
     * a duplicate [JankStats] listener on the window.
     */
    fun start(
        window: Window,
        choreographer: Choreographer = Choreographer.getInstance(),
    ) {
        ensureBaseline()
        if (!sampled || started) return
        started = true
        jankStats = JankStats.createAndTrack(window) { frameData ->
            frames++
            activeRenderingTimeNanos += frameData.frameDurationUiNanos
            if (frameData.isJank) jankFrames++
            if (frameData.frameDurationUiNanos >= FROZEN_FRAME_DURATION_NS) frozenFrames++
        }
        choreographer.postFrameCallback {
            if (firstFrameSent || stopped) return@postFrameCallback
            firstFrameSent = true
            runtime.emit(
                kind = PerformanceEventKind.UiFirstFrame,
                space = space,
                owner = owner,
                stage = PerformanceStage.FirstFrameRenderTime,
                durationMs = elapsedMs(),
                mode = mode,
                attributes = mapOf("view_name" to viewName),
            )
        }
    }

    /**
     * Suspends until [condition] reports the screen's data is ready, then logs
     * data-ready wait time, render-after-data-ready time, and total screen load
     * time. Call from a coroutine scoped to the view's lifecycle (e.g.
     * `viewLifecycleOwner.lifecycleScope`). No-ops for [DataReadyCondition.NoRequiredData],
     * if this tracer instance was not sampled, or if this method already ran once
     * (guards against double emission if a caller awaits it more than once).
     *
     * Also no-ops if [stop] already ran by the time the flow reports readiness, or
     * by the time the following frame is drawn — e.g. a fragment kept alive via
     * `hide()`/`show()` whose data becomes ready after it was hidden (and its
     * session already closed by [stop]). Callers should still cancel the coroutine
     * running this method when tearing the screen down; these checks are a
     * backstop against the race between that cancellation and the flow emitting.
     */
    suspend fun awaitDataReady(
        condition: DataReadyCondition,
        choreographer: Choreographer = Choreographer.getInstance(),
    ) {
        ensureBaseline()
        if (!sampled || dataReadyAwaited || condition !is DataReadyCondition.FlowDataReady) return
        dataReadyAwaited = true
        condition.flow.first { it }
        if (stopped) return
        val dataReadyNs = runtime.clock.elapsedRealtimeNanos()
        val waitDataMs = (dataReadyNs - startNs) / NS_TO_MS
        runtime.emit(
            kind = PerformanceEventKind.UiDataReady,
            space = space,
            owner = owner,
            stage = PerformanceStage.BeforeDataBecameReadyTime,
            durationMs = waitDataMs,
            mode = mode,
            attributes = mapOf("view_name" to viewName),
        )
        choreographer.postFrameCallback {
            if (stopped) return@postFrameCallback
            val renderAfterDataReadyMs = (runtime.clock.elapsedRealtimeNanos() - dataReadyNs) / NS_TO_MS
            val totalMs = waitDataMs + renderAfterDataReadyMs
            runtime.emit(
                kind = PerformanceEventKind.UiDataReady,
                space = space,
                owner = owner,
                stage = PerformanceStage.DataRenderTime,
                durationMs = renderAfterDataReadyMs,
                mode = mode,
                attributes = mapOf("view_name" to viewName),
            )
            runtime.emit(
                kind = PerformanceEventKind.UiDataReady,
                space = space,
                owner = owner,
                stage = PerformanceStage.ScreenLoadTime,
                durationMs = totalMs,
                mode = mode,
                attributes = mapOf("view_name" to viewName),
            )
        }
    }

    /**
     * Call once the screen's view is torn down, e.g. from `onDestroyView`. Stops
     * jank tracking (if [start] was called) and emits the `UiSession` event. Safe
     * to call more than once — only the first call has an effect. No-ops if this
     * tracer instance was not sampled.
     */
    fun stop() {
        ensureBaseline()
        if (!sampled || stopped) return
        stopped = true
        jankStats?.isTrackingEnabled = false
        jankStats = null
        val sessionMs = elapsedMs()
        val cpuEndMs = Process.getElapsedCpuTime()
        runtime.emit(
            kind = PerformanceEventKind.UiSession,
            space = space,
            owner = owner,
            stage = PerformanceStage.OnScreenDisposed,
            durationMs = sessionMs,
            mode = mode,
            attributes = buildUiSessionAttributes(
                viewName = viewName,
                frames = frames,
                jankFrames = jankFrames,
                frozenFrames = frozenFrames,
                activeRenderingTimeNanos = activeRenderingTimeNanos,
                cpuStartMs = cpuStartMs,
                cpuEndMs = cpuEndMs,
                sessionMs = sessionMs,
            ),
            includeRuntimeSnapshot = true,
        )
    }

    private fun elapsedMs(): Long = (runtime.clock.elapsedRealtimeNanos() - startNs) / NS_TO_MS
}
