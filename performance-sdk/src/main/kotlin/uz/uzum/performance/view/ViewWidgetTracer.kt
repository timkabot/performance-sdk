package uz.uzum.performance.view

import android.view.Choreographer
import uz.uzum.performance.api.PerformanceOwner
import uz.uzum.performance.api.PerformanceSpace
import uz.uzum.performance.api.PerformanceTraceMode
import uz.uzum.performance.runtime.PerformanceRuntime
import uz.uzum.performance.runtime.PerformanceSdk
import uz.uzum.performance.tracing.DataReadyCondition

/**
 * View-system counterpart of `uz.uzum.performance.compose.UiWidgetTracer`, for a
 * small number of independently loading sections of an XML screen (e.g. a
 * recommendations block loaded separately from the rest of the screen). Unlike
 * [ViewScreenTracer], it does not track jank or emit a `UiSession` event — only
 * data-ready timing. Do not wrap every list item with widget tracing.
 *
 * Usage:
 * ```
 * private val tracer = ViewWidgetTracer(
 *     space = Screen.Catalog,
 *     owner = Team.Discovery,
 *     viewName = "RecommendationsWidget",
 * )
 *
 * fun onViewCreated() {
 *     viewLifecycleOwner.lifecycleScope.launch {
 *         tracer.awaitDataReady(DataReadyCondition.FlowDataReady(feature.dataReady))
 *     }
 * }
 * ```
 */
class ViewWidgetTracer(
    space: PerformanceSpace,
    owner: PerformanceOwner? = null,
    viewName: String,
    runtime: PerformanceRuntime = PerformanceSdk.runtime(),
) {
    private val delegate = ViewScreenTracer(
        space = space,
        owner = owner,
        viewName = viewName,
        mode = PerformanceTraceMode.Widget,
        runtime = runtime,
    )

    /** Suspends until [condition] reports the widget's data is ready. */
    suspend fun awaitDataReady(
        condition: DataReadyCondition,
        choreographer: Choreographer = Choreographer.getInstance(),
    ) = delegate.awaitDataReady(condition, choreographer)
}
