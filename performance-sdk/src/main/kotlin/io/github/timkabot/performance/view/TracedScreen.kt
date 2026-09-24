package io.github.timkabot.performance.view

import io.github.timkabot.performance.api.PerformanceOwner
import io.github.timkabot.performance.api.PerformanceSpace
import io.github.timkabot.performance.tracing.DataReadyCondition

/**
 * Implemented by a `Fragment`/`DialogFragment` that wants automatic screen
 * tracing wired up by [TracedScreenLifecycleCallbacks], instead of constructing
 * and driving a [ViewScreenTracer] by hand in every screen.
 *
 * This is the recommended way to roll XML tracing out across an app: it does
 * not depend on a specific `BaseFragment` shape, so it works the same way
 * whether `onViewCreated`/`onDestroyView` are `final` in your base class, not
 * overridden at all, or freely overridden by each screen.
 *
 * Always traced in [io.github.timkabot.performance.api.PerformanceTraceMode.Screen] mode —
 * a fragment is a screen, not a widget. For an independently loading section
 * *within* a screen (e.g. a recommendations block), use [ViewWidgetTracer]
 * directly at that section's call site instead of implementing this interface.
 */
interface TracedScreen {
    val performanceSpace: PerformanceSpace
    val performanceOwner: PerformanceOwner? get() = null
    val performanceViewName: String get() = this::class.java.simpleName

    /**
     * Read once, when the fragment's view is created. If your data-ready flow is
     * not known yet at that point, expose it from your ViewModel/feature class
     * and reference it here — this property itself does not need to be stable
     * across recreation, only the underlying `Flow<Boolean>` does.
     */
    val performanceDataReady: DataReadyCondition get() = DataReadyCondition.NoRequiredData
}
