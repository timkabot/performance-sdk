package uz.uzum.performance.sample

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uz.uzum.performance.api.PerformanceConfig
import uz.uzum.performance.api.PerformanceEvent
import uz.uzum.performance.logging.PerformanceLogger
import uz.uzum.performance.runtime.PerformanceSdk

class PerformanceSampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PerformanceSdk.init(
            context = this,
            configProvider = {
                PerformanceConfig(
                    enabled = true,
                    sampleRate = 1.0,
                )
            },
            eventCallback = { event ->
                SampleLogStore.add("SEND ${event.shortLine()}")
            },
            logger = PerformanceLogger { message ->
                SampleLogStore.add("SDK $message")
            },
            eventBufferCapacity = 512,
        )
    }
}

object SampleLogStore {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    fun add(message: String) {
        _logs.update { current ->
            (listOf(message) + current).take(MAX_LOG_LINES)
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }

    private const val MAX_LOG_LINES = 80
}

private fun PerformanceEvent.shortLine(): String = buildString {
    append(kind)
    append(" space=")
    append(space)
    append(" stage=")
    append(stage)
    append(" duration=")
    append(durationMs)
    append(" attrs=")
    append(attributes)
}
