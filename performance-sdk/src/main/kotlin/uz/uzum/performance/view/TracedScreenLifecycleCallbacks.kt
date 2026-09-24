package uz.uzum.performance.view

import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import uz.uzum.performance.runtime.PerformanceRuntime
import uz.uzum.performance.runtime.PerformanceSdk
import java.util.WeakHashMap

/**
 * Wires [ViewScreenTracer] into every fragment that implements [TracedScreen],
 * driven by the `FragmentManager` itself rather than by fragment lifecycle method
 * overrides. This is deliberately independent of any app's `BaseFragment`:
 *
 * - It fires for every `Fragment`/`DialogFragment`/`BottomSheetDialogFragment`
 *   regardless of which base class they extend.
 * - It fires whether or not the fragment's own `onViewCreated`/`onDestroyView`
 *   call `super` — a base-class override only runs for subclasses that call
 *   `super`, this does not depend on that at all.
 *
 * Register once per host, recursively, so nested `childFragmentManager`s (tab
 * containers, proxy fragments, etc.) are covered too:
 *
 * ```
 * supportFragmentManager.registerFragmentLifecycleCallbacks(
 *     TracedScreenLifecycleCallbacks(),
 *     true,
 * )
 * ```
 *
 * Fragments opt in by implementing [TracedScreen]; everything else is ignored.
 *
 * Fragments kept alive via `hide()`/`show()` (e.g. bottom-navigation tabs that
 * are `add()`-ed once and then hidden/shown instead of replaced) do **not** get
 * `onFragmentViewDestroyed` on tab switch, so a session would never end or
 * restart. Call [notifyHidden] / [notifyShown] explicitly at the exact point
 * your navigation hides/shows a tab if it uses that pattern instead of
 * replacing fragments.
 */
class TracedScreenLifecycleCallbacks(
    private val runtime: PerformanceRuntime = PerformanceSdk.runtime(),
) : FragmentManager.FragmentLifecycleCallbacks() {

    /**
     * A tracer plus the job awaiting its data-ready condition, so the job can be
     * cancelled explicitly when the screen is hidden/destroyed instead of being
     * left to keep collecting the data-ready flow after [ViewScreenTracer.stop]
     * has already closed the session (e.g. across a `hide()`/`show()` cycle,
     * where the fragment's `viewLifecycleOwner` — and thus its `lifecycleScope`
     * — stays alive).
     */
    private class TracingSession(val tracer: ViewScreenTracer, val job: Job)

    private val tracers = WeakHashMap<Fragment, TracingSession>()

    override fun onFragmentViewCreated(
        fm: FragmentManager,
        f: Fragment,
        v: View,
        savedInstanceState: Bundle?,
    ) {
        val screen = f as? TracedScreen ?: return
        startTracing(f, screen)
    }

    override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
        tracers.remove(f)?.let { session ->
            session.job.cancel()
            session.tracer.stop()
        }
    }

    /**
     * Call when a fragment kept alive via `hide()`/`show()` navigation becomes
     * visible again. No-ops if [fragment] does not implement [TracedScreen] or
     * already has an active tracer (e.g. it was just created and
     * [onFragmentViewCreated] already handled it).
     */
    fun notifyShown(fragment: Fragment) {
        val screen = fragment as? TracedScreen ?: return
        if (tracers.containsKey(fragment)) return
        startTracing(fragment, screen)
    }

    /**
     * Call when a fragment kept alive via `hide()`/`show()` navigation is about
     * to be hidden. No-ops if [fragment] has no active tracer.
     */
    fun notifyHidden(fragment: Fragment) {
        tracers.remove(fragment)?.let { session ->
            session.job.cancel()
            session.tracer.stop()
        }
    }

    private fun startTracing(fragment: Fragment, screen: TracedScreen) {
        val tracer = ViewScreenTracer(
            space = screen.performanceSpace,
            owner = screen.performanceOwner,
            viewName = screen.performanceViewName,
            runtime = runtime,
        )
        val window = (fragment as? DialogFragment)?.dialog?.window ?: fragment.requireActivity().window
        tracer.start(window)
        val job = fragment.viewLifecycleOwner.lifecycleScope.launch {
            tracer.awaitDataReady(screen.performanceDataReady)
        }
        tracers[fragment] = TracingSession(tracer, job)
    }
}
