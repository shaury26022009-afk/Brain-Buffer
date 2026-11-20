package com.example

import com.example.domain.FocusTimerEngine
import com.example.model.FocusMode
import com.example.model.PersistedTimerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTimerEngineTest {

    @Test
    fun `running timer tracks wall clock elapsed time across backgrounding`() {
        val initial = PersistedTimerSnapshot(
            mode = FocusMode.FOCUS,
            totalSeconds = 25 * 60
        )
        val t0 = 1_000_000L
        val running = FocusTimerEngine.startOrResume(initial, nowEpochMs = t0)

        assertTrue(running.hasStarted)
        assertEquals(t0, running.runningSinceEpochMs)

        // Simulate app going to background / screen turning off for 10 minutes
        val tAfter10Min = t0 + 10 * 60 * 1000L
        assertEquals(15 * 60, FocusTimerEngine.remainingSeconds(running, tAfter10Min))
        assertEquals(10 * 60, FocusTimerEngine.actualFocusedSeconds(running, tAfter10Min))
        assertEquals(0.4f, FocusTimerEngine.progress(running, tAfter10Min), 0.001f)
        assertFalse(FocusTimerEngine.isCompleted(running, tAfter10Min))
    }

    @Test
    fun `paused intervals are never counted as focused time`() {
        val initial = PersistedTimerSnapshot(
            mode = FocusMode.FOCUS,
            totalSeconds = 20 * 60
        )
        val t0 = 5_000_000L
        // Run for 5 minutes
        val started = FocusTimerEngine.startOrResume(initial, nowEpochMs = t0)
        val tPause = t0 + 5 * 60 * 1000L
        val paused = FocusTimerEngine.pause(started, nowEpochMs = tPause)

        assertNull(paused.runningSinceEpochMs)
        assertEquals(5 * 60 * 1000L, paused.accumulatedFocusedMs)

        // Stay paused for 45 minutes (e.g., long interruption)
        val tAfterLongPause = tPause + 45 * 60 * 1000L
        assertEquals(15 * 60, FocusTimerEngine.remainingSeconds(paused, tAfterLongPause))
        assertEquals(5 * 60, FocusTimerEngine.actualFocusedSeconds(paused, tAfterLongPause))
        assertFalse(FocusTimerEngine.isCompleted(paused, tAfterLongPause))

        // Resume and run for 3 more minutes -> total focused should be exactly 8 minutes, NOT 53
        val resumed = FocusTimerEngine.startOrResume(paused, nowEpochMs = tAfterLongPause)
        val tEnd = tAfterLongPause + 3 * 60 * 1000L
        assertEquals(8 * 60, FocusTimerEngine.actualFocusedSeconds(resumed, tEnd))
        assertEquals(12 * 60, FocusTimerEngine.remainingSeconds(resumed, tEnd))
    }

    @Test
    fun `timer marks completed when wall clock reaches or passes planned duration`() {
        val initial = PersistedTimerSnapshot(
            mode = FocusMode.FOCUS,
            totalSeconds = 15 * 60
        )
        val t0 = 10_000_000L
        val running = FocusTimerEngine.startOrResume(initial, nowEpochMs = t0)

        // App was closed for 30 minutes on a 15-minute session
        val tWayLater = t0 + 30 * 60 * 1000L
        assertEquals(0, FocusTimerEngine.remainingSeconds(running, tWayLater))
        assertTrue(FocusTimerEngine.isCompleted(running, tWayLater))
        assertEquals(15 * 60, FocusTimerEngine.actualFocusedSeconds(running, tWayLater))
        assertEquals(1f, FocusTimerEngine.progress(running, tWayLater), 0.001f)
    }

    @Test
    fun `reset and configureMode clear accumulated focus time cleanly`() {
        val started = FocusTimerEngine.startOrResume(
            PersistedTimerSnapshot(mode = FocusMode.FOCUS, totalSeconds = 25 * 60),
            nowEpochMs = 1_000L
        )
        val paused = FocusTimerEngine.pause(started, nowEpochMs = 1_000L + 120_000L)
        assertEquals(120, FocusTimerEngine.actualFocusedSeconds(paused, 500_000L))

        val reset = FocusTimerEngine.reset(paused)
        assertFalse(reset.hasStarted)
        assertNull(reset.runningSinceEpochMs)
        assertEquals(0L, reset.accumulatedFocusedMs)
        assertEquals(25 * 60, FocusTimerEngine.remainingSeconds(reset, 500_000L))

        val shortBreak = FocusTimerEngine.configureMode(reset, FocusMode.SHORT_BREAK, 5)
        assertEquals(FocusMode.SHORT_BREAK, shortBreak.mode)
        assertEquals(5 * 60, shortBreak.totalSeconds)
        assertEquals(5 * 60, FocusTimerEngine.remainingSeconds(shortBreak, 500_000L))
    }
}
