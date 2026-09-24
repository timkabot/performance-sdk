package io.github.timkabot.performance.startup

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import io.github.timkabot.performance.api.PerformanceAttributeKeys
import io.github.timkabot.performance.api.PerformanceEventKind
import io.github.timkabot.performance.api.PerformanceSpace
import io.github.timkabot.performance.api.PerformanceSpaceName
import io.github.timkabot.performance.api.PerformanceStage
import io.github.timkabot.performance.runtime.PerformanceRuntime
import io.github.timkabot.performance.runtime.PerformanceSdk

class StartupPerformanceTracker(
    private val runtime: PerformanceRuntime = PerformanceSdk.runtime(),
    private val autoTrackActivityResumed: Boolean = true,
) : Application.ActivityLifecycleCallbacks {

    enum class StartType {
        Cold,
        Warm,
    }

    private var processStartUptimeMs: Long = processStartUptimeMs()
    private var coldBaselineUptimeMs: Long? = processStartUptimeMs
    private var warmBaselineUptimeMs: Long? = null
    private var firstInteractiveLogged = false
    private var pendingTypeForNextInteractive: StartType? = null

    val hasLoggedFirstInteractive: Boolean
        get() = firstInteractiveLogged

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    fun markInteractive(
        screenName: String,
        space: PerformanceSpace = PerformanceSpaceName(screenName),
    ) {
        val type = pendingTypeForNextInteractive ?: return
        val baseline = when (type) {
            StartType.Cold -> coldBaselineUptimeMs ?: processStartUptimeMs
            StartType.Warm -> warmBaselineUptimeMs ?: SystemClock.uptimeMillis()
        }
        val startupMs = (SystemClock.uptimeMillis() - baseline).coerceAtLeast(0L)
        runtime.saveTime(
            space = space,
            owner = null,
            stage = PerformanceStage.AppStartupTime,
            timeMs = startupMs,
            kind = PerformanceEventKind.Startup,
            attributes = mapOf(
                PerformanceAttributeKeys.StartupType to type.name,
                PerformanceAttributeKeys.FirstScreen to screenName,
            ),
        )
        if (type == StartType.Cold && !firstInteractiveLogged) {
            firstInteractiveLogged = true
        }
        pendingTypeForNextInteractive = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (isFromLauncher(activity)) {
            if (coldBaselineUptimeMs == null) {
                coldBaselineUptimeMs = processStartUptimeMs.takeIf { it > 0L } ?: SystemClock.uptimeMillis()
            }
            pendingTypeForNextInteractive = if (!firstInteractiveLogged) StartType.Cold else StartType.Warm
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (!autoTrackActivityResumed) return
        markInteractive(
            screenName = activity::class.java.simpleName,
            space = PerformanceSpaceName(activity::class.java.simpleName),
        )
    }

    override fun onActivityStarted(activity: Activity) {
        if (firstInteractiveLogged) {
            warmBaselineUptimeMs = SystemClock.uptimeMillis()
            pendingTypeForNextInteractive = StartType.Warm
        }
    }

    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun isFromLauncher(activity: Activity): Boolean {
        val intent = activity.intent ?: return false
        return intent.action == Intent.ACTION_MAIN &&
            intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true &&
            activity.isTaskRoot
    }

    private fun processStartUptimeMs(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.os.Process.getStartUptimeMillis()
        } else {
            SystemClock.uptimeMillis()
        }
    }
}
