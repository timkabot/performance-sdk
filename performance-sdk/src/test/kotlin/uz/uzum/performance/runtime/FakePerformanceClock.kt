package uz.uzum.performance.runtime

internal class FakePerformanceClock(
    private var nowMs: Long = 1_000L,
) : PerformanceClock {
    override fun elapsedRealtimeMs(): Long = nowMs

    override fun elapsedRealtimeNanos(): Long = nowMs * 1_000_000L

    fun advanceMs(deltaMs: Long) {
        nowMs += deltaMs
    }
}
