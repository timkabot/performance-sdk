package uz.uzum.performance.tracing

import kotlinx.coroutines.flow.Flow

sealed interface DataReadyCondition {
    data object NoRequiredData : DataReadyCondition

    data class FlowDataReady(
        val flow: Flow<Boolean>,
    ) : DataReadyCondition
}