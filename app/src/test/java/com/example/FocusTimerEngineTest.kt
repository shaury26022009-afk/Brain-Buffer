package com.example

import com.example.domain.FocusTimerEngine
import com.example.domain.TimerClock
import com.example.model.FocusMode
import com.example.model.FocusSessionState
import com.example.model.PersistedTimerSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FocusTimerEngineTest {

    /**
     * Controllable deterministic clock backed by [TestCoroutineScheduler.currentTime] with
     * independent monotonic and wall-clock offsets so tests can simulate normal passage of time,
     * wall-clock/NTP jumps, screen-lock/Doze sleep, and device reboots deterministically.
     */
    private class FakeTimerClock(
        private val scheduler: TestCoroutineScheduler,
        initialElapsedMs: Long = 100_000L,
        initialEpochMs: Long = 1_700_000_000_000L
    ) : TimerClock {
        var elapsedOffsetMs: Long = initialElapsedMs
        var wallOffsetMs: Long = initialEpochMs

        override fun elapsedRealtimeMs(): Long = (elapsedOffsetMs + scheduler.currentTime).coerceAtLeast(0L)
        override fun currentTimeMillis(): Long = wallOffsetMs + scheduler.currentTime

        fun simulateSleepOrBackground(durationMs: Long) {
            elapsedOffsetMs += durationMs
            wallOffsetMs += durationMs
        }

        fun shiftWallClockOnly(deltaMs: Long) {
            wallOffsetMs += deltaMs
        }

        fun simulateDeviceReboot(newBootElapsedMs: Long, wallTimePassedMs: Long) {
            elapsedOffsetMs = newBootElapsedMs - scheduler.currentTime
            wallOffsetMs += wallTimePassedMs
        }
    }

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
        val started = FocusTimerEngine.startOrResume(initial, nowEpochMs = t0)
        val tPause = t0 + 5 * 60 * 1000L
        val paused = FocusTimerEngine.pause(started, nowEpochMs = tPause)

        assertNull(paused.runningSinceEpochMs)
        assertEquals(5 * 60 * 1000L, paused.accumulatedFocusedMs)

        val tAfterLongPause = tPause + 45 * 60 * 1000L
        assertEquals(15 * 60, FocusTimerEngine.remainingSeconds(paused, tAfterLongPause))
        assertEquals(5 * 60, FocusTimerEngine.actualFocusedSeconds(paused, tAfterLongPause))
        assertFalse(FocusTimerEngine.isCompleted(paused, tAfterLongPause))

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

    @Test
    fun `engine manages focus session states and ticks visibly every second without freezing`() = runTest {
        val clock = FakeTimerClock(testScheduler)
        val engine = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            initialSnapshot = PersistedTimerSnapshot(mode = FocusMode.FOCUS, totalSeconds = 25 * 60),
            minTickDelayMs = 1L
        )

        assertEquals(FocusSessionState.IDLE, engine.sessionState)

        engine.startOrResume()
        runCurrent()

        assertTrue(engine.isRunning)
        assertEquals(FocusSessionState.FOCUSING, engine.sessionState)
        assertEquals(1500, engine.tickState.value.remainingSeconds)
        assertEquals(0, engine.tickState.value.elapsedSeconds)

        // Verify every single second ticks accurately including 24:54/00:06 and 24:00 -> 23:59 rollover
        for (sec in 1..65) {
            advanceTimeBy(1_000L)
            runCurrent()
            val tick = engine.tickState.value
            assertEquals("Elapsed mismatch at second $sec", sec, tick.elapsedSeconds)
            assertEquals("Remaining mismatch at second $sec", 1500 - sec, tick.remainingSeconds)
            assertEquals(1500, tick.elapsedSeconds + tick.remainingSeconds)
            assertEquals(FocusSessionState.FOCUSING, tick.sessionState)
        }

        engine.pause()
        runCurrent()
        assertEquals(FocusSessionState.PAUSED, engine.sessionState)

        engine.close()
    }

    @Test
    fun `monotonic elapsedRealtime clock ignores wall clock jumps during active focus`() = runTest {
        val clock = FakeTimerClock(testScheduler)
        val engine = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            initialSnapshot = PersistedTimerSnapshot(mode = FocusMode.FOCUS, totalSeconds = 25 * 60),
            minTickDelayMs = 1L
        )

        engine.startOrResume()
        advanceTimeBy(6_000L)
        runCurrent()
        assertEquals(1494, engine.tickState.value.remainingSeconds)
        assertEquals(6, engine.tickState.value.elapsedSeconds)

        // Simulate user/NTP changing device wall clock backward by 2 hours, then forward by 5 hours
        clock.shiftWallClockOnly(-2 * 60 * 60 * 1000L)
        advanceTimeBy(4_000L)
        runCurrent()
        assertEquals(1490, engine.tickState.value.remainingSeconds)
        assertEquals(10, engine.tickState.value.elapsedSeconds)

        clock.shiftWallClockOnly(5 * 60 * 60 * 1000L)
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(1485, engine.tickState.value.remainingSeconds)
        assertEquals(15, engine.tickState.value.elapsedSeconds)

        engine.close()
    }

    @Test
    fun `pause freezes exact remaining time and mid-second resume aligns cleanly without drift`() = runTest {
        val clock = FakeTimerClock(testScheduler)
        val engine = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            initialSnapshot = PersistedTimerSnapshot(mode = FocusMode.FOCUS, totalSeconds = 25 * 60),
            minTickDelayMs = 1L
        )

        engine.startOrResume()
        advanceTimeBy(6_400L)
        runCurrent()
        engine.pause()
        runCurrent()

        assertFalse(engine.isRunning)
        assertEquals(FocusSessionState.PAUSED, engine.sessionState)
        assertEquals(1494, engine.tickState.value.remainingSeconds)
        assertEquals(6, engine.tickState.value.elapsedSeconds)
        assertEquals(6_400L, engine.snapshot.accumulatedFocusedMs)

        // Stay paused for 15 minutes — remaining time must stay completely frozen
        advanceTimeBy(15 * 60 * 1000L)
        runCurrent()
        assertEquals(1494, engine.tickState.value.remainingSeconds)
        assertEquals(6, engine.tickState.value.elapsedSeconds)

        // Resume: remaining 600ms of the 7th second should roll over cleanly at +600ms
        engine.startOrResume()
        runCurrent()
        assertTrue(engine.isRunning)
        assertEquals(FocusSessionState.FOCUSING, engine.sessionState)
        advanceTimeBy(599L)
        runCurrent()
        assertEquals(6, engine.tickState.value.elapsedSeconds)
        assertEquals(1494, engine.tickState.value.remainingSeconds)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(7, engine.tickState.value.elapsedSeconds)
        assertEquals(1493, engine.tickState.value.remainingSeconds)

        engine.close()
    }

    @Test
    fun `rapid pause resume toggling is atomic and race safe`() = runTest {
        val clock = FakeTimerClock(testScheduler)
        val engine = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            initialSnapshot = PersistedTimerSnapshot(mode = FocusMode.FOCUS, totalSeconds = 25 * 60),
            minTickDelayMs = 1L
        )

        engine.startOrResume()
        advanceTimeBy(2_000L)
        runCurrent()

        // Rapidly tap Pause/Resume 6 times -> ends running with no lost time or duplicate loops
        repeat(6) {
            engine.togglePauseResume()
        }
        runCurrent()
        assertTrue(engine.isRunning)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(5, engine.tickState.value.elapsedSeconds)
        assertEquals(1495, engine.tickState.value.remainingSeconds)

        // One more toggle pauses cleanly
        engine.togglePauseResume()
        runCurrent()
        assertFalse(engine.isRunning)
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(5, engine.tickState.value.elapsedSeconds)
        assertEquals(1495, engine.tickState.value.remainingSeconds)

        engine.close()
    }

    @Test
    fun `reset restores initial planned duration and returns state to IDLE`() = runTest {
        val clock = FakeTimerClock(testScheduler)
        val engine = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            initialSnapshot = PersistedTimerSnapshot(mode = FocusMode.FOCUS, totalSeconds = 25 * 60),
            minTickDelayMs = 1L
        )

        engine.startOrResume()
        advanceTimeBy(40_000L)
        runCurrent()
        assertEquals(1460, engine.tickState.value.remainingSeconds)

        engine.reset()
        runCurrent()

        val afterReset = engine.tickState.value
        assertFalse(afterReset.snapshot.hasStarted)
        assertFalse(afterReset.isRunning)
        assertFalse(afterReset.isCompleted)
        assertEquals(FocusSessionState.IDLE, afterReset.sessionState)
        assertEquals(1500, afterReset.remainingSeconds)
        assertEquals(0, afterReset.elapsedSeconds)
        assertEquals(0f, afterReset.progress, 0.0001f)

        advanceTimeBy(5 * 60 * 1000L)
        runCurrent()
        assertEquals(1500, engine.tickState.value.remainingSeconds)
        assertEquals(0, engine.tickState.value.elapsedSeconds)

        engine.close()
    }

    @Test
    fun `natural completion fires exactly once transitions to COMPLETED and never goes negative`() = runTest {
        val clock = FakeTimerClock(testScheduler)
        var completionCalls = 0
        var completedActualSeconds = -1
        val engine = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            initialSnapshot = PersistedTimerSnapshot(mode = FocusMode.FOCUS, totalSeconds = 60),
            minTickDelayMs = 1L,
            onNaturalCompletion = { _, decision ->
                completionCalls++
                completedActualSeconds = decision.actualSeconds
            }
        )

        engine.startOrResume()
        advanceTimeBy(59_000L)
        runCurrent()
        assertEquals(1, engine.tickState.value.remainingSeconds)
        assertEquals(0, completionCalls)

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(0, engine.tickState.value.remainingSeconds)
        assertEquals(60, engine.tickState.value.elapsedSeconds)
        assertTrue(engine.tickState.value.isCompleted)
        assertFalse(engine.isRunning)
        assertEquals(FocusSessionState.COMPLETED, engine.sessionState)
        assertEquals(1, completionCalls)
        assertEquals(60, completedActualSeconds)

        advanceTimeBy(30_000L)
        runCurrent()
        engine.syncWithClock()
        engine.syncWithClock()
        assertEquals(0, engine.tickState.value.remainingSeconds)
        assertEquals(60, engine.tickState.value.elapsedSeconds)
        assertEquals(1, completionCalls)

        engine.close()
    }

    @Test
    fun `screen lock app switch and settings return syncs immediately and keeps ticking`() = runTest {
        val clock = FakeTimerClock(testScheduler)
        val engine = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            initialSnapshot = PersistedTimerSnapshot(mode = FocusMode.FOCUS, totalSeconds = 10 * 60),
            minTickDelayMs = 1L
        )

        engine.startOrResume()
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(590, engine.tickState.value.remainingSeconds)

        clock.simulateSleepOrBackground(4 * 60 * 1000L)
        val synced = engine.syncWithClock()
        assertEquals(350, synced.remainingSeconds)
        assertEquals(250, synced.elapsedSeconds)
        assertTrue(synced.isRunning)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(347, engine.tickState.value.remainingSeconds)
        assertEquals(253, engine.tickState.value.elapsedSeconds)

        engine.close()
    }

    @Test
    fun `process recreation restores running paused expired and already completed snapshots via restoreFromPersisted`() = runTest {
        val clock = FakeTimerClock(testScheduler)
        val original = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            minTickDelayMs = 1L
        )
        original.start(durationMinutes = 5, mode = FocusMode.FOCUS)
        val runningPersisted = original.exportPersistableSnapshot()
        original.close()

        // 1. Process recreated 2 minutes later while session is still running
        clock.simulateSleepOrBackground(2 * 60 * 1000L)
        val restoredRunning = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            minTickDelayMs = 1L
        )
        val runningOutcome = restoredRunning.restoreFromPersisted(runningPersisted)
        runCurrent()
        assertTrue(runningOutcome is FocusTimerEngine.ProcessRestorationResult.Running)
        assertTrue(restoredRunning.isRunning)
        assertEquals(FocusSessionState.FOCUSING, restoredRunning.sessionState)
        assertEquals(180, restoredRunning.tickState.value.remainingSeconds)
        assertEquals(120, restoredRunning.tickState.value.elapsedSeconds)

        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(178, restoredRunning.tickState.value.remainingSeconds)
        val pausedPersisted = restoredRunning.pause()
        restoredRunning.close()

        // 2. Process recreated while paused -> remains paused at exact 178s
        clock.simulateSleepOrBackground(60_000L)
        val restoredPaused = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            minTickDelayMs = 1L
        )
        val pausedOutcome = restoredPaused.restoreFromPersisted(pausedPersisted)
        runCurrent()
        assertTrue(pausedOutcome is FocusTimerEngine.ProcessRestorationResult.Paused)
        assertFalse(restoredPaused.isRunning)
        assertEquals(FocusSessionState.PAUSED, restoredPaused.sessionState)
        assertEquals(178, restoredPaused.tickState.value.remainingSeconds)
        restoredPaused.close()

        // 3. Process recreated AFTER running timer expired -> completes exactly once
        clock.simulateSleepOrBackground(10 * 60 * 1000L)
        var restoredCompletionCount = 0
        val restoredExpired = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            minTickDelayMs = 1L,
            onNaturalCompletion = { _, _ -> restoredCompletionCount++ }
        )
        val expiredOutcome = restoredExpired.restoreFromPersisted(runningPersisted)
        runCurrent()
        assertTrue(expiredOutcome is FocusTimerEngine.ProcessRestorationResult.NaturalCompletionPending)
        assertEquals(1, restoredCompletionCount)
        assertEquals(0, restoredExpired.tickState.value.remainingSeconds)
        restoredExpired.close()

        // 4. Already-completed sessionId must NEVER complete again on recreation
        var duplicateCompletionCount = 0
        val alreadyCompletedSnap = runningPersisted.copy(lastCompletedSessionId = runningPersisted.sessionId)
        val restoredDone = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            minTickDelayMs = 1L,
            onNaturalCompletion = { _, _ -> duplicateCompletionCount++ }
        )
        val doneOutcome = restoredDone.restoreFromPersisted(alreadyCompletedSnap)
        runCurrent()
        assertTrue(doneOutcome is FocusTimerEngine.ProcessRestorationResult.AlreadyCompleted)
        assertEquals(0, duplicateCompletionCount)
        assertEquals(FocusSessionState.IDLE, restoredDone.sessionState)
        restoredDone.close()
    }

    @Test
    fun `device reboot fallback recovers safely when elapsedRealtime resets`() = runTest {
        val clock = FakeTimerClock(testScheduler, initialElapsedMs = 500_000L)
        val engine = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            initialSnapshot = PersistedTimerSnapshot(mode = FocusMode.FOCUS, totalSeconds = 10 * 60),
            minTickDelayMs = 1L
        )

        val persisted = engine.startOrResume()
        engine.close()

        // Simulate device reboot: elapsedRealtime resets to 12_000ms (< 500_000ms), wall clock +3 min
        clock.simulateDeviceReboot(newBootElapsedMs = 12_000L, wallTimePassedMs = 3 * 60 * 1000L)
        val afterReboot = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            minTickDelayMs = 1L
        )
        afterReboot.restoreFromPersisted(persisted)
        runCurrent()

        assertEquals(7 * 60, afterReboot.tickState.value.remainingSeconds)
        assertEquals(3 * 60, afterReboot.tickState.value.elapsedSeconds)
        assertTrue(afterReboot.isRunning)

        afterReboot.close()
    }

    @Test
    fun `external notification shade pause and resume synchronize cleanly`() = runTest {
        val clock = FakeTimerClock(testScheduler)
        val engine = FocusTimerEngine(
            scope = backgroundScope,
            clock = clock,
            initialSnapshot = PersistedTimerSnapshot(mode = FocusMode.FOCUS, totalSeconds = 25 * 60),
            minTickDelayMs = 1L
        )

        val running = engine.startOrResume()
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(1490, engine.tickState.value.remainingSeconds)

        val externallyPaused = FocusTimerEngine.pause(
            running,
            nowEpochMs = clock.currentTimeMillis(),
            nowElapsedRealtimeMs = clock.elapsedRealtimeMs()
        )
        engine.reconcileExternalSnapshot(externallyPaused)
        runCurrent()
        assertFalse(engine.isRunning)
        assertEquals(FocusSessionState.PAUSED, engine.sessionState)

        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(1490, engine.tickState.value.remainingSeconds)

        val externallyResumed = FocusTimerEngine.startOrResume(
            externallyPaused,
            nowEpochMs = clock.currentTimeMillis(),
            nowElapsedRealtimeMs = clock.elapsedRealtimeMs()
        )
        engine.reconcileExternalSnapshot(externallyResumed)
        runCurrent()
        assertTrue(engine.isRunning)
        assertEquals(FocusSessionState.FOCUSING, engine.sessionState)

        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(1485, engine.tickState.value.remainingSeconds)
        assertEquals(15, engine.tickState.value.elapsedSeconds)

        engine.close()
    }

    @Test
    fun `formatTimerDuration formats seconds minutes hours and clamps negative values`() {
        assertEquals("00:00", com.example.ui.components.formatTimerDuration(-42))
        assertEquals("00:00", com.example.ui.components.formatTimerDuration(0))
        assertEquals("00:06", com.example.ui.components.formatTimerDuration(6))
        assertEquals("00:59", com.example.ui.components.formatTimerDuration(59))
        assertEquals("01:00", com.example.ui.components.formatTimerDuration(60))
        assertEquals("24:54", com.example.ui.components.formatTimerDuration(24 * 60 + 54))
        assertEquals("1:01:05", com.example.ui.components.formatTimerDuration(3665))
    }
}
