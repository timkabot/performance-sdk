package uz.uzum.performance.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import uz.uzum.performance.api.PerformanceAttributeKeys

class UiSessionAttributesTest {

    @Test
    fun `includes view name and session duration`() {
        val attributes = buildUiSessionAttributes(
            viewName = "CartFragment",
            frames = 0,
            jankFrames = 0,
            frozenFrames = 0,
            activeRenderingTimeNanos = 0,
            cpuStartMs = 0,
            cpuEndMs = 0,
            sessionMs = 1_200,
        )

        assertEquals("CartFragment", attributes["view_name"])
        assertEquals(1_200L, attributes[PerformanceAttributeKeys.SessionDuration])
    }

    @Test
    fun `omits frame-derived attributes when no frames were observed`() {
        val attributes = buildUiSessionAttributes(
            viewName = "CartFragment",
            frames = 0,
            jankFrames = 0,
            frozenFrames = 0,
            activeRenderingTimeNanos = 0,
            cpuStartMs = 0,
            cpuEndMs = 0,
            sessionMs = 500,
        )

        assertNull(attributes[PerformanceAttributeKeys.TotalFrames])
        assertNull(attributes[PerformanceAttributeKeys.ActiveTime])
        assertFalse(attributes.containsKey(PerformanceAttributeKeys.HitchRate))
    }

    @Test
    fun `computes hitch rate only once minimum frame count is reached`() {
        val belowThreshold = buildUiSessionAttributes(
            viewName = "CartFragment",
            frames = 4,
            jankFrames = 4,
            frozenFrames = 0,
            activeRenderingTimeNanos = 4_000_000,
            cpuStartMs = 0,
            cpuEndMs = 0,
            sessionMs = 500,
        )
        assertFalse(belowThreshold.containsKey(PerformanceAttributeKeys.HitchRate))

        val atThreshold = buildUiSessionAttributes(
            viewName = "CartFragment",
            frames = 5,
            jankFrames = 1,
            frozenFrames = 0,
            activeRenderingTimeNanos = 5_000_000,
            cpuStartMs = 0,
            cpuEndMs = 0,
            sessionMs = 500,
        )
        assertEquals(20, atThreshold[PerformanceAttributeKeys.HitchRate])
    }

    @Test
    fun `computes cpu usage percentage from elapsed cpu and session time`() {
        val attributes = buildUiSessionAttributes(
            viewName = "CartFragment",
            frames = 10,
            jankFrames = 0,
            frozenFrames = 0,
            activeRenderingTimeNanos = 10_000_000,
            cpuStartMs = 0,
            cpuEndMs = 100,
            sessionMs = 1_000,
        )

        val cpuUsage = attributes[PerformanceAttributeKeys.CpuUsage] as String
        assertEquals(true, cpuUsage.toDouble() in 0.0..100.0)
    }

    @Test
    fun `frozen frames is always reported, even when zero`() {
        val attributes = buildUiSessionAttributes(
            viewName = "CartFragment",
            frames = 10,
            jankFrames = 0,
            frozenFrames = 0,
            activeRenderingTimeNanos = 10_000_000,
            cpuStartMs = 0,
            cpuEndMs = 0,
            sessionMs = 1_000,
        )

        assertEquals(0L, attributes[PerformanceAttributeKeys.FrozenFrames])
    }
}
