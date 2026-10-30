package com.ken1dot.spartanlock

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitClockTest {
    @Test
    fun secondHandAdvancesOnEvery120HzFrame() {
        var previous = -1f
        for (frame in 0..120) {
            val angle = orbitAngle(frame * (1000.0 / 120.0), 60_000.0)
            assertTrue("Frame $frame must advance", angle > previous)
            assertEquals(frame * 0.05f, angle, 0.0001f)
            previous = angle
        }
    }

    @Test
    fun ringsWrapAndKeepFractionalTime() {
        assertEquals(90f, orbitAngle(15_000.0, 60_000.0), 0f)
        assertEquals(270f, orbitAngle(-15_000.0, 60_000.0), 0f)
        assertEquals(0f, orbitAngle(60_000.0, 60_000.0), 0f)
        assertEquals(0.1f, orbitAngle(1_000.0, 3_600_000.0), 0.00001f)
        assertEquals(0.008333333f, orbitAngle(1_000.0, 43_200_000.0), 0.000001f)
    }

    @Test
    fun weekdayHighlightFollowsTheLocalesWeekOrder() {
        assertEquals(Calendar.FRIDAY, orbitWeekday(Calendar.SUNDAY, 5))
        assertEquals(Calendar.FRIDAY, orbitWeekday(Calendar.MONDAY, 4))
        assertEquals(Calendar.SUNDAY, orbitWeekday(Calendar.MONDAY, 6))
        for (firstDay in Calendar.SUNDAY..Calendar.SATURDAY) {
            assertEquals((Calendar.SUNDAY..Calendar.SATURDAY).toSet(),
                (0..6).map { orbitWeekday(firstDay, it) }.toSet())
        }
    }
}
