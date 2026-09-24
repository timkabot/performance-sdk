package io.github.timkabot.performance.runtime

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import io.github.timkabot.performance.api.PerformanceConfig
import io.github.timkabot.performance.api.PerformanceConfigProvider
import io.github.timkabot.performance.api.PerformanceEventCallback
import io.github.timkabot.performance.environment.AndroidPerformanceEnvironmentProvider
import io.github.timkabot.performance.environment.EmptyPerformanceEnvironmentProvider
import io.github.timkabot.performance.environment.PerformanceEnvironmentProvider
import io.github.timkabot.performance.logging.NoOpPerformanceLogger
import io.github.timkabot.performance.logging.PerformanceLogger
import kotlin.random.Random

object PerformanceSdk {
    @Volatile
    private var runtime: PerformanceRuntime = disabledRuntime()

    fun init(
        context: Context? = null,
        configProvider: PerformanceConfigProvider = PerformanceConfigProvider {
            PerformanceConfig()
        },
        eventCallback: PerformanceEventCallback,
        environmentProvider: PerformanceEnvironmentProvider = context?.let {
            AndroidPerformanceEnvironmentProvider(it)
        } ?: EmptyPerformanceEnvironmentProvider,
        logger: PerformanceLogger = NoOpPerformanceLogger,
        callbackScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        callbackDispatcher: CoroutineDispatcher = Dispatchers.Default,
        eventBufferCapacity: Int = 256,
    ) {
        val nextRuntime = PerformanceRuntime(
            configProvider = configProvider,
            eventCallback = eventCallback,
            environmentProvider = environmentProvider,
            logger = logger,
            callbackScope = callbackScope,
            callbackDispatcher = callbackDispatcher,
            eventBufferCapacity = eventBufferCapacity,
        )
        val previousRuntime = runtime
        runtime = nextRuntime
        previousRuntime.close()
    }

    fun runtime(): PerformanceRuntime = runtime

    internal fun resetForTests(
        runtime: PerformanceRuntime = disabledRuntime(),
    ) {
        val previousRuntime = this.runtime
        this.runtime = runtime
        if (previousRuntime !== runtime) previousRuntime.close()
    }

    internal fun createRuntimeForTests(
        configProvider: PerformanceConfigProvider,
        eventCallback: PerformanceEventCallback,
        environmentProvider: PerformanceEnvironmentProvider = EmptyPerformanceEnvironmentProvider,
        logger: PerformanceLogger = NoOpPerformanceLogger,
        callbackScope: CoroutineScope,
        callbackDispatcher: CoroutineDispatcher,
        clock: PerformanceClock,
        eventBufferCapacity: Int = 256,
        random: Random = Random(0),
    ): PerformanceRuntime = PerformanceRuntime(
        configProvider = configProvider,
        eventCallback = eventCallback,
        environmentProvider = environmentProvider,
        logger = logger,
        callbackScope = callbackScope,
        callbackDispatcher = callbackDispatcher,
        eventBufferCapacity = eventBufferCapacity,
        clock = clock,
        random = random,
    )

    private fun disabledRuntime(): PerformanceRuntime = PerformanceRuntime(
        configProvider = PerformanceConfigProvider {
            PerformanceConfig(enabled = false)
        },
        eventCallback = PerformanceEventCallback {},
        callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        callbackDispatcher = Dispatchers.Default,
    )
}
