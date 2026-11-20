package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BrainBufferRepository
import com.example.data.local.BrainBufferDatabase
import com.example.data.local.UserPreferencesStore
import com.example.model.FocusMode
import com.example.model.FocusSessionRecord
import com.example.model.GardenDecoration
import com.example.model.MindNoteType
import com.example.model.PlantStage
import com.example.model.PreferredStudyTime
import com.example.model.StudentAvatar
import com.example.model.StudyLevel
import com.example.model.StudyTask
import com.example.model.Subject
import com.example.model.SubjectIcon
import com.example.model.TaskPriority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BrainBufferPersistenceTest {

    private lateinit var database: BrainBufferDatabase
    private lateinit var preferencesStore: UserPreferencesStore
    private lateinit var repository: BrainBufferRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BrainBufferDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferencesStore = UserPreferencesStore(context)
        repository = BrainBufferRepository(database, preferencesStore)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `onboarding persists profile and subjects with zero fake focus sessions`() = runTest {
        val math = Subject(
            name = "Mathematics",
            colorHex = 0xFF7B5CFA,
            iconKey = SubjectIcon.CALCULATE.key,
            targetHoursPerWeek = 5f
        )
        repository.completeOnboarding(
            studentName = "Shaury",
            avatarKey = StudentAvatar.SPARK.key,
            studyLevel = StudyLevel.COLLEGE,
            courseOrExamName = "B.S. Computer Science",
            initialSubjects = listOf(math),
            studyGoals = setOf("Prepare for exams", "Build a daily study habit"),
            customGoal = "Finish calculus review",
            preferredFocusMinutes = 45,
            shortBreakMinutes = 10,
            preferredStudyTime = PreferredStudyTime.EVENING,
            weeklyStudyDaysTarget = 6,
            biggestDistractions = setOf("Notifications"),
            otherDistraction = "",
            dailyTargetMinutes = 120
        )

        val prefs = repository.preferences.first()
        val subjects = repository.subjects.first()
        val sessions = repository.focusSessions.first()
        val plants = repository.gardenPlants.first()

        assertTrue(prefs.onboardingCompleted)
        assertEquals("Shaury", prefs.displayName)
        assertEquals(StudyLevel.COLLEGE, prefs.studyLevel)
        assertEquals(45, prefs.preferredFocusMinutes)
        assertEquals(120, prefs.dailyTargetMinutes)
        assertEquals(1, subjects.size)
        assertEquals("Mathematics", subjects.first().name)
        assertTrue("Fresh onboarding must have zero fake focus sessions", sessions.isEmpty())
        assertEquals(1, plants.size)
        assertEquals(0, plants.first().sessionsLogged)
        assertEquals(PlantStage.SEED, plants.first().stage)
    }

    @Test
    fun `tasks sessions garden growth and mind notes persist and clear cleanly`() = runTest {
        val today = LocalDate.now().toEpochDay()
        val subjectId = repository.upsertSubject(
            Subject(name = "Physics", colorHex = 0xFF3B82F6, iconKey = SubjectIcon.SCIENCE.key)
        )

        val taskId = repository.upsertTask(
            StudyTask(
                title = "Solve kinematics problem set",
                subjectId = subjectId,
                dueDateEpochDay = today,
                priority = TaskPriority.HIGH
            )
        )
        val savedTasks = repository.tasks.first()
        assertEquals(1, savedTasks.size)
        assertEquals(taskId, savedTasks.first().id)

        // Record a real 30-minute focus session
        val session = FocusSessionRecord(
            subjectId = subjectId,
            subjectName = "Physics",
            subjectColorHex = 0xFF3B82F6,
            durationSeconds = 30 * 60,
            plannedSeconds = 30 * 60,
            sessionGoal = "Chapter 4 exercises",
            mode = FocusMode.FOCUS,
            completedAtMs = System.currentTimeMillis(),
            dateEpochDay = today
        )
        val (grownPlant, unlockedDeco) = repository.recordFocusSessionAndGrowGarden(
            session = session,
            dailyTargetMinutes = 60,
            allExistingSessions = emptyList(),
            existingDecorations = emptySet()
        )
        assertEquals(1, grownPlant.sessionsLogged)
        assertEquals(PlantStage.SPROUT, grownPlant.stage)
        assertEquals(GardenDecoration.STEPPING_STONES, unlockedDeco)

        val sessions = repository.focusSessions.first()
        assertEquals(1, sessions.size)
        assertEquals(1, BrainBufferRepository.computeCurrentStreakDays(sessions, LocalDate.now()))

        // Persist distraction dump & reflection notes
        repository.addMindNote(MindNoteType.DISTRACTION_DUMP, "Reply to lab partner after study block")
        repository.addMindNote(MindNoteType.SESSION_REFLECTION, "Felt steady after turning phone face down", "Calm")
        val notes = repository.mindNotes.first()
        assertEquals(2, notes.size)

        // Clear all study data
        repository.clearAllStudyData()
        assertTrue(repository.tasks.first().isEmpty())
        assertTrue(repository.focusSessions.first().isEmpty())
        assertTrue(repository.mindNotes.first().isEmpty())
    }
}
