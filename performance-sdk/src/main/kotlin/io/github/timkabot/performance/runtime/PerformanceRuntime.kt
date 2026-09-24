package io.github.timkabot.performance.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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
import io.github.timkabot.performance.environment.EmptyPerformanceEnvironmentProvider
import io.github.timkabot.performance.environment.PerformanceEnvironmentProvider
import io.github.timkabot.performance.logging.NoOpPerformanceLogger
import io.github.timkabot.performance.logging.PerformanceLogger
import io.github.timkabot.performance.logging.toLogLine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

class PerformanceRuntime internal constructor(
    private val configProvider: PerformanceConfigProvider,
    private val eventCallback: PerformanceEventCallback,
    private val environmentProvider: PerformanceEnvironmentProvider = EmptyPerformanceEnvironmentProvider,
    private val logger: PerformanceLogger = NoOpPerformanceLogger,
    private val callbackScope: CoroutineScope,
    private val callbackDispatcher: CoroutineDispatcher,
    private val eventBufferCapacity: Int = DEFAULT_EVENT_BUFFER_CAPACITY,
    internal val clock: PerformanceClock = SystemPerformanceClock,
    private val random: Random = Random.Default,
) {

    private val eventChannel = Channel<PendingEvent>(
        capacity = eventBufferCapacity.also { require(it > 0) },
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val eventWorker: Job = callbackScope.launch(callbackDispatcher) {
        for (event in eventChannel) {
            dispatch(buildEvent(event))
        }
    }

    fun currentConfig(): PerformanceConfig = configProvider.get()

    fun shouldSample(): Boolean {
        val config = currentConfig()
        return config.enabled && random.nextDouble() < config.sampleRate
    }

    fun saveTime(
        space: PerformanceSpace,
        owner: PerformanceOwner? = null,
        stage: PerformanceStage,
        timeMs: Long,
        mode: PerformanceTraceMode? = null,
        kind: PerformanceEventKind = PerformanceEventKind.StageDuration,
        attributes: Map<String, Any?> = emptyMap(),
    ) {
        if (timeMs <= 0L || !shouldSample()) return
        emit(
            kind = kind,
            space = space,
            owner = owner,
            stage = stage,
            durationMs = timeMs,
            mode = mode,
            attributes = attributes,
        )
    }

    fun <T> measure(
        space: PerformanceSpace,
        owner: PerformanceOwner? = null,
        stage: PerformanceStage,
        block: () -> T,
    ): T {
        val startNs = clock.elapsedRealtimeNanos()
        return try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            saveTime(
                space = space,
                owner = owner,
                stage = stage,
                timeMs = nanosToMillis(clock.elapsedRealtimeNanos() - startNs),
            )
        }
    }

    suspend fun <T> measureSuspend(
        space: PerformanceSpace,
        owner: PerformanceOwner? = null,
        stage: PerformanceStage,
        block: suspend () -> T,
    ): T {
        val startNs = clock.elapsedRealtimeNanos()
        return try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            saveTime(
                space = space,
                owner = owner,
                stage = stage,
                timeMs = nanosToMillis(clock.elapsedRealtimeNanos() - startNs),
            )
        }
    }

    fun <T> measureTrace(
        trace: PerformanceTrace,
        block: () -> T,
    ): T {
        val sampled = shouldSample()
        val startNs = clock.elapsedRealtimeNanos()
        return try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            if (sampled) {
                emitCustomTrace(
                    trace = trace,
                    durationMs = nanosToMillis(clock.elapsedRealtimeNanos() - startNs),
                )
            }
        }
    }

    suspend fun <T> measureTraceSuspend(
        trace: PerformanceTrace,
        block: suspend () -> T,
    ): T {
        val sampled = shouldSample()
        val startNs = clock.elapsedRealtimeNanos()
        return try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            if (sampled) {
                emitCustomTrace(
                    trace = trace,
                    durationMs = nanosToMillis(clock.elapsedRealtimeNanos() - startNs),
                )
            }
        }
    }

    internal fun emit(
        kind: PerformanceEventKind,
        space: PerformanceSpace?,
        owner: PerformanceOwner?,
        stage: PerformanceStage,
        durationMs: Long?,
        mode: PerformanceTraceMode?,
        attributes: Map<String, Any?> = emptyMap(),
        includeEnvironment: Boolean = true,
        includeRuntimeSnapshot: Boolean = false,
    ) {
        val spaceName = space?.performanceName
        val ownerName = owner?.performanceName
        val stageId = stage.id
        val stageAttributes = stage.attributes.toMap()
        val eventAttributes = attributes.toMap()
        val elapsedRealtimeMs = clock.elapsedRealtimeMs()
        eventChannel.trySend(
            PendingEvent(
                kind = kind,
                space = spaceName,
                owner = ownerName,
                stage = stageId,
                durationMs = durationMs,
                mode = mode,
                stageAttributes = stageAttributes,
                attributes = eventAttributes,
                elapsedRealtimeMs = elapsedRealtimeMs,
                includeEnvironment = includeEnvironment,
                includeRuntimeSnapshot = includeRuntimeSnapshot,
            ),
        )
    }

    internal fun close() {
        eventChannel.close()
        eventWorker.cancel()
    }

    private fun emitCustomTrace(
        trace: PerformanceTrace,
        durationMs: Long,
    ) {
        if (durationMs <= 0L) return
        emit(
            kind = PerformanceEventKind.CustomTrace,
            space = null,
            owner = null,
            stage = PerformanceStage.Custom(trace.key),
            durationMs = durationMs,
            mode = PerformanceTraceMode.Custom,
            attributes = mapOf(PerformanceAttributeKeys.TraceKey to trace.key) + trace.attributes,
        )
    }

    private fun dispatch(event: PerformanceEvent) {
        logger.log(event.toLogLine())
        eventCallback.onEvent(event)
    }

    private fun buildEvent(event: PendingEvent): PerformanceEvent = buildEvent(
        kind = event.kind,
        space = event.space,
        owner = event.owner,
        stage = event.stage,
        durationMs = event.durationMs,
        mode = event.mode,
        stageAttributes = event.stageAttributes,
        attributes = event.attributes,
        elapsedRealtimeMs = event.elapsedRealtimeMs,
        includeEnvironment = event.includeEnvironment,
        includeRuntimeSnapshot = event.includeRuntimeSnapshot,
    )

    private fun buildEvent(
        kind: PerformanceEventKind,
        space: String?,
        owner: String?,
        stage: String,
        durationMs: Long?,
        mode: PerformanceTraceMode?,
        stageAttributes: Map<String, Any?>,
        attributes: Map<String, Any?>,
        elapsedRealtimeMs: Long,
        includeEnvironment: Boolean,
        includeRuntimeSnapshot: Boolean,
    ): PerformanceEvent {
        val environment = if (includeEnvironment) environmentProvider.get() else null
        val runtimeSnapshot = if (includeRuntimeSnapshot) {
            environmentProvider.getRuntimeSnapshot()
        } else {
            emptyMap()
        }
        return PerformanceEvent(
            kind = kind,
            space = space,
            owner = owner,
            stage = stage,
            durationMs = durationMs,
            mode = mode,
            attributes = buildMap {
                putAll(stageAttributes)
                putAll(attributes)
                if (durationMs != null) put(PerformanceAttributeKeys.Time, durationMs)
                if (environment != null) {
                    put(PerformanceAttributeKeys.DeviceTier, environment.deviceTier)
                    put(PerformanceAttributeKeys.NetworkQuality, environment.networkQuality)
                    put(PerformanceAttributeKeys.RefreshRate, environment.refreshRateHz)
                }
                putAll(runtimeSnapshot)
            },
            elapsedRealtimeMs = elapsedRealtimeMs,
        )
    }

    private data class PendingEvent(
        val kind: PerformanceEventKind,
        val space: String?,
        val owner: String?,
        val stage: String,
        val durationMs: Long?,
        val mode: PerformanceTraceMode?,
        val stageAttributes: Map<String, Any?>,
        val attributes: Map<String, Any?>,
        val elapsedRealtimeMs: Long,
        val includeEnvironment: Boolean,
        val includeRuntimeSnapshot: Boolean,
    )

    private companion object {
        private const val DEFAULT_EVENT_BUFFER_CAPACITY = 256
    }
}
