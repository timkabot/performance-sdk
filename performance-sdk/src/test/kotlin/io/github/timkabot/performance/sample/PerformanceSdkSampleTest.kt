package io.github.timkabot.performance.sample

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.timkabot.performance.api.PerformanceAttributeKeys
import io.github.timkabot.performance.api.PerformanceConfig
import io.github.timkabot.performance.api.PerformanceConfigProvider
import io.github.timkabot.performance.api.PerformanceEvent
import io.github.timkabot.performance.api.PerformanceEventCallback
import io.github.timkabot.performance.api.PerformanceEventKind
import io.github.timkabot.performance.api.PerformanceOwner
import io.github.timkabot.performance.api.PerformanceSpace
import io.github.timkabot.performance.api.PerformanceStage
import io.github.timkabot.performance.api.PerformanceTrace
import io.github.timkabot.performance.api.PerformanceTraceMode
import io.github.timkabot.performance.api.asPerformanceOwner
import io.github.timkabot.performance.api.asPerformanceSpace
import io.github.timkabot.performance.environment.PerformanceEnvironment
import io.github.timkabot.performance.environment.PerformanceEnvironmentProvider
import io.github.timkabot.performance.logging.PerformanceLogger
import io.github.timkabot.performance.runtime.FakePerformanceClock
import io.github.timkabot.performance.runtime.PerformanceMeasure
import io.github.timkabot.performance.runtime.PerformanceSdk

@OptIn(ExperimentalCoroutinesApi::class)
class PerformanceSdkSampleTest {

    @Test
    fun `sample app can measure stages and log callback events`() {
        val events = mutableListOf<PerformanceEvent>()
        val logs = mutableListOf<String>()
        val clock = FakePerformanceClock()
        val dispatcher = UnconfinedTestDispatcher()
        val runtime = PerformanceSdk.createRuntimeForTests(
            configProvider = PerformanceConfigProvider {
                PerformanceConfig(enabled = true, sampleRate = 1.0)
            },
            eventCallback = PerformanceEventCallback { event ->
                events += event

                // Consumer-owned delivery policy. In production this can be Clickstream,
                // Firebase, Datadog, a custom backend, local logs, or a batch queue.
                logs += "SEND ${event.kind}: stage=${event.stage}, duration=${event.durationMs}, attrs=${event.attributes}"
            },
            environmentProvider = SampleEnvironmentProvider,
            logger = PerformanceLogger { message ->
                logs += "SDK $message"
            },
            callbackScope = TestScope(dispatcher),
            callbackDispatcher = dispatcher,
            clock = clock,
        )
        PerformanceSdk.resetForTests(runtime)

        val measure = PerformanceMeasure()
        val result = measure.measure(
            space = SampleScreen.Cart,
            owner = SampleTeam.Checkout,
            stage = PerformanceStage.NetworkRequestsTotalTime("loadCart"),
        ) {
            clock.advanceMs(42)
            "cart-loaded"
        }

        measure.measureTrace(
            trace = PerformanceTrace(
                key = "cart_mapper",
                attributes = mapOf("source" to "sample"),
            ),
        ) {
            clock.advanceMs(7)
        }

        measure.saveTime(
            space = "Main".asPerformanceSpace(),
            owner = "Discovery".asPerformanceOwner(),
            stage = PerformanceStage.MapToDomainTime("mapMain"),
            timeMs = 13,
        )

        logs.forEach(::println)

        assertEquals("cart-loaded", result)
        assertEquals(3, events.size)
        assertEquals(PerformanceEventKind.StageDuration, events[0].kind)
        assertEquals("Cart", events[0].space)
        assertEquals("Checkout", events[0].owner)
        assertEquals("NetworkRequestsTotalTime", events[0].stage)
        assertEquals(42L, events[0].durationMs)
        assertEquals("low", events[0].attributes["device_tier"])
        assertEquals(PerformanceEventKind.CustomTrace, events[1].kind)
        assertEquals("cart_mapper", events[1].stage)
        assertTrue(logs.any { it.contains("SEND StageDuration") })
        assertTrue(logs.any { it.contains("SDK kind=StageDuration") })
    }

    @Test
    fun `runtime snapshot is attached only when requested`() {
        val events = mutableListOf<PerformanceEvent>()
        val dispatcher = UnconfinedTestDispatcher()
        val runtime = PerformanceSdk.createRuntimeForTests(
            configProvider = PerformanceConfigProvider {
                PerformanceConfig(enabled = true, sampleRate = 1.0)
            },
            eventCallback = PerformanceEventCallback { event ->
                events += event
            },
            environmentProvider = SampleEnvironmentProvider,
            callbackScope = TestScope(dispatcher),
            callbackDispatcher = dispatcher,
            clock = FakePerformanceClock(),
        )

        runtime.saveTime(
            space = SampleScreen.Main,
            owner = SampleTeam.Discovery,
            stage = PerformanceStage.MapToDomainTime("mapMain"),
            timeMs = 10,
        )
        runtime.emit(
            kind = PerformanceEventKind.UiSession,
            space = SampleScreen.Main,
            owner = SampleTeam.Discovery,
            stage = PerformanceStage.OnScreenDisposed,
            durationMs = 100,
            mode = PerformanceTraceMode.Screen,
            includeRuntimeSnapshot = true,
        )

        assertEquals(2, events.size)
        assertEquals(null, events[0].attributes["sample_device"])
        assertEquals("unit-test", events[1].attributes["sample_device"])
    }

    @Test
    fun `burst events are buffered and dropped without blocking caller`() {
        val events = mutableListOf<PerformanceEvent>()
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val runtime = PerformanceSdk.createRuntimeForTests(
            configProvider = PerformanceConfigProvider {
                PerformanceConfig(enabled = true, sampleRate = 1.0)
            },
            eventCallback = PerformanceEventCallback { event ->
                events += event
            },
            environmentProvider = SampleEnvironmentProvider,
            callbackScope = scope,
            callbackDispatcher = dispatcher,
            clock = FakePerformanceClock(),
            eventBufferCapacity = 2,
        )

        repeat(5) { index ->
            runtime.saveTime(
                space = SampleScreen.Main,
                owner = SampleTeam.Discovery,
                stage = PerformanceStage.MapToDomainTime("mapMain"),
                timeMs = (index + 1).toLong(),
                attributes = mapOf("index" to index),
            )
        }

        assertEquals(0, events.size)

        scope.runCurrent()

        assertEquals(2, events.size)
        assertEquals(listOf(3, 4), events.map { it.attributes["index"] })
    }

    @Test
    fun `compose navigation sample logs multiple screen routes`() {
        val events = mutableListOf<PerformanceEvent>()
        val logs = mutableListOf<String>()
        val clock = FakePerformanceClock()
        val dispatcher = UnconfinedTestDispatcher()
        val runtime = PerformanceSdk.createRuntimeForTests(
            configProvider = PerformanceConfigProvider {
                PerformanceConfig(enabled = true, sampleRate = 1.0)
            },
            eventCallback = PerformanceEventCallback { event ->
                events += event
                logs += "SEND ${event.kind}: space=${event.space}, stage=${event.stage}, duration=${event.durationMs}"
            },
            environmentProvider = SampleEnvironmentProvider,
            logger = PerformanceLogger { message ->
                logs += "SDK $message"
            },
            callbackScope = TestScope(dispatcher),
            callbackDispatcher = dispatcher,
            clock = clock,
        )

        logs += "NAV open ${SampleComposeRoute.Main.route}"
        runtime.logComposeScreenRoute(
            route = SampleComposeRoute.Main,
            clock = clock,
            firstFrameMs = 16,
            dataReadyMs = 120,
            renderAfterDataReadyMs = 24,
            sessionMs = 700,
        )
        logs += "NAV ${SampleComposeRoute.Main.route} -> ${SampleComposeRoute.Product.route}"
        runtime.logComposeScreenRoute(
            route = SampleComposeRoute.Product,
            clock = clock,
            firstFrameMs = 18,
            dataReadyMs = 180,
            renderAfterDataReadyMs = 32,
            sessionMs = 900,
        )
        logs += "NAV ${SampleComposeRoute.Product.route} -> ${SampleComposeRoute.Checkout.route}"
        runtime.logComposeScreenRoute(
            route = SampleComposeRoute.Checkout,
            clock = clock,
            firstFrameMs = 14,
            dataReadyMs = 95,
            renderAfterDataReadyMs = 20,
            sessionMs = 500,
        )

        logs.forEach(::println)

        assertEquals(15, events.size)
        assertEquals(setOf("Main", "Product", "Checkout"), events.mapNotNull { it.space }.toSet())
        assertEquals(3, events.count { it.kind == PerformanceEventKind.UiFirstFrame })
        assertEquals(3, events.count { it.stage == PerformanceStage.ScreenLoadTime.id })
        assertEquals(3, events.count { it.kind == PerformanceEventKind.UiSession })
        assertTrue(logs.any { it == "NAV main -> product" })
        assertTrue(logs.any { it.contains("SEND UiSession: space=Checkout") })
    }

    enum class SampleTeam(
        override val performanceName: String,
    ) : PerformanceOwner {
        Checkout("Checkout"),
        Discovery("Discovery"),
    }

    enum class SampleScreen(
        override val performanceName: String,
    ) : PerformanceSpace {
        Main("Main"),
        Cart("Cart"),
    }

    private object SampleEnvironmentProvider : PerformanceEnvironmentProvider {
        override fun get(): PerformanceEnvironment = PerformanceEnvironment(
            deviceTier = "low",
            networkQuality = "good",
            refreshRateHz = 60f,
        )

        override fun getRuntimeSnapshot(): Map<String, Any?> = mapOf(
            "sample_device" to "unit-test",
        )
    }

    private fun io.github.timkabot.performance.runtime.PerformanceRuntime.logComposeScreenRoute(
        route: SampleComposeRoute,
        clock: FakePerformanceClock,
        firstFrameMs: Long,
        dataReadyMs: Long,
        renderAfterDataReadyMs: Long,
        sessionMs: Long,
    ) {
        clock.advanceMs(firstFrameMs)
        emit(
            kind = PerformanceEventKind.UiFirstFrame,
            space = route,
            owner = SampleComposeOwner,
            stage = PerformanceStage.FirstFrameRenderTime,
            durationMs = firstFrameMs,
            mode = PerformanceTraceMode.Screen,
            attributes = mapOf("view_name" to route.viewName),
        )
        clock.advanceMs((dataReadyMs - firstFrameMs).coerceAtLeast(0L))
        emit(
            kind = PerformanceEventKind.UiDataReady,
            space = route,
            owner = SampleComposeOwner,
            stage = PerformanceStage.BeforeDataBecameReadyTime,
            durationMs = dataReadyMs,
            mode = PerformanceTraceMode.Screen,
            attributes = mapOf("view_name" to route.viewName),
        )
        clock.advanceMs(renderAfterDataReadyMs)
        emit(
            kind = PerformanceEventKind.UiDataReady,
            space = route,
            owner = SampleComposeOwner,
            stage = PerformanceStage.DataRenderTime,
            durationMs = renderAfterDataReadyMs,
            mode = PerformanceTraceMode.Screen,
            attributes = mapOf("view_name" to route.viewName),
        )
        emit(
            kind = PerformanceEventKind.UiDataReady,
            space = route,
            owner = SampleComposeOwner,
            stage = PerformanceStage.ScreenLoadTime,
            durationMs = dataReadyMs + renderAfterDataReadyMs,
            mode = PerformanceTraceMode.Screen,
            attributes = mapOf("view_name" to route.viewName),
        )
        clock.advanceMs((sessionMs - dataReadyMs - renderAfterDataReadyMs).coerceAtLeast(0L))
        emit(
            kind = PerformanceEventKind.UiSession,
            space = route,
            owner = SampleComposeOwner,
            stage = PerformanceStage.OnScreenDisposed,
            durationMs = sessionMs,
            mode = PerformanceTraceMode.Screen,
            attributes = mapOf(
                "view_name" to route.viewName,
                PerformanceAttributeKeys.SessionDuration to sessionMs,
            ),
            includeRuntimeSnapshot = true,
        )
    }
}
