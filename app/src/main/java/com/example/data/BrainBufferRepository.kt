package com.example.data

import android.content.Context
import androidx.room.withTransaction
import com.example.cloud.CloudSyncScheduler
import com.example.cloud.SupabaseSyncManager
import com.example.data.local.ActivityLogEntity
import com.example.data.local.AppCategoryMappingEntity
import com.example.data.local.BlockedAppEntity
import com.example.data.local.BrainBufferDatabase
import com.example.data.local.CustomActivityEntity
import com.example.data.local.FocusSessionEntity
import com.example.data.local.GardenPlantEntity
import com.example.data.local.InterventionEventEntity
import com.example.data.local.MindNoteEntity
import com.example.data.local.PurposeSessionEntity
import com.example.data.local.StrictFocusPresetEntity
import com.example.data.local.StudyResourceEntity
import com.example.data.local.StudySessionEntity
import com.example.data.local.StudyTaskEntity
import com.example.data.local.SubjectEntity
import com.example.data.local.UserPreferencesStore
import com.example.model.ActivityCategory
import com.example.model.ActivityLogRecord
import com.example.model.AppCategoryMapping
import com.example.model.BlockedAppCategory
import com.example.model.BlockedAppRule
import com.example.model.BreakPlanMode
import com.example.model.BreakPolicy
import com.example.model.InterventionEventRecord
import com.example.model.InterventionEventType
import com.example.model.PurposeSessionRecord
import com.example.model.SessionPurposeCategory
import com.example.model.TemporaryAppAccessSession
import com.example.model.CustomActivityOption
import com.example.model.DailyActivityStat
import com.example.model.FamilyControlSettings
import com.example.model.FocusGuardSettings
import com.example.model.FocusMode
import com.example.model.FocusSessionRecord
import com.example.model.GardenDecoration
import com.example.model.GardenPlant
import com.example.model.ManualBlockingSession
import com.example.model.MindNote
import com.example.model.MindNoteType
import com.example.model.NotificationPreferences
import com.example.model.PersistedTimerSnapshot
import com.example.model.PlantSpecies
import com.example.model.PlantStage
import com.example.model.PreferredStudyTime
import com.example.model.StrictFocusPreset
import com.example.model.StrictnessLevel
import com.example.model.StudyActivityType
import com.example.model.StudyLevel
import com.example.model.StudyResource
import com.example.model.StudyResourceType
import com.example.model.StudyTask
import com.example.model.Subject
import com.example.model.SubjectIcon
import com.example.model.SupabaseAuthSession
import com.example.model.TaskPriority
import com.example.model.TemporaryAppAllowance
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import com.example.model.UserProfileRole
import com.example.performance.PerformanceProfileOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class BrainBufferRepository(
    private val database: BrainBufferDatabase,
    private val preferencesStore: UserPreferencesStore,
    private val appContext: Context? = null
) {
    private val subjectDao = database.subjectDao()
    private val taskDao = database.studyTaskDao()
    private val sessionDao = database.focusSessionDao()
    private val plantDao = database.gardenPlantDao()
    private val mindNoteDao = database.mindNoteDao()
    private val activityLogDao = database.activityLogDao()
    private val strictFocusPresetDao = database.strictFocusPresetDao()
    private val customActivityDao = database.customActivityDao()
    private val appCategoryMappingDao = database.appCategoryMappingDao()
    private val studySessionDao = database.studySessionDao()
    private val blockedAppDao = database.blockedAppDao()
    private val studyResourceDao = database.studyResourceDao()
    private val syncOutboxDao = database.syncOutboxDao()
    private val purposeSessionDao = database.purposeSessionDao()
    private val interventionEventDao = database.interventionEventDao()

    val syncManager: SupabaseSyncManager = SupabaseSyncManager(database, preferencesStore)

    val preferences: Flow<UserPreferences> = preferencesStore.preferencesFlow
    val timerSnapshot: Flow<PersistedTimerSnapshot> = preferencesStore.timerSnapshotFlow
    val activeTempAccess: Flow<TemporaryAppAccessSession?> = preferencesStore.activeTempAccessFlow
    val manualBlockingSession: Flow<ManualBlockingSession> = preferencesStore.manualBlockingSessionFlow
    val temporaryAllowances: Flow<List<TemporaryAppAllowance>> = preferencesStore.temporaryAllowancesFlow
    val authSession: Flow<SupabaseAuthSession> = preferencesStore.authSessionFlow
    val lastSyncedAtMs: Flow<Long> = preferencesStore.lastSyncedAtMsFlow
    val pendingOutboxCount: Flow<Int> = syncOutboxDao.observePendingCount()

    val subjects: Flow<List<Subject>> = subjectDao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    val tasks: Flow<List<StudyTask>> = taskDao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    val focusSessions: Flow<List<FocusSessionRecord>> = sessionDao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    val gardenPlants: Flow<List<GardenPlant>> = plantDao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    val mindNotes: Flow<List<MindNote>> = mindNoteDao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    val activityLogs: Flow<List<ActivityLogRecord>> = activityLogDao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    val strictFocusPresets: Flow<List<StrictFocusPreset>> = strictFocusPresetDao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    val customActivities: Flow<List<CustomActivityOption>> = customActivityDao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    val appCategoryMappings: Flow<List<AppCategoryMapping>> = appCategoryMappingDao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    val blockedApps: Flow<List<BlockedAppRule>> = blockedAppDao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    val studyResources: Flow<List<StudyResource>> = studyResourceDao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    val purposeSessions: Flow<List<PurposeSessionRecord>> = purposeSessionDao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    val interventionEvents: Flow<List<InterventionEventRecord>> = interventionEventDao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    suspend fun getActiveTempAccessOnce(): TemporaryAppAccessSession? = preferencesStore.getActiveTempAccessOnce()

    suspend fun saveActiveTempAccess(session: TemporaryAppAccessSession?) {
        preferencesStore.saveActiveTempAccess(session)
    }

    suspend fun hasPurposeSession(sessionKey: String): Boolean {
        if (sessionKey.isBlank()) return false
        return purposeSessionDao.countBySessionKey(sessionKey) > 0
    }

    suspend fun savePurposeSession(record: PurposeSessionRecord): Long {
        if (record.sessionKey.isNotBlank() && purposeSessionDao.countBySessionKey(record.sessionKey) > 0) {
            return purposeSessionDao.upsert(record.toEntity())
        }
        return purposeSessionDao.upsert(record.toEntity())
    }

    suspend fun logInterventionEvent(event: InterventionEventRecord): Long {
        return interventionEventDao.insert(event.toEntity())
    }

    // region Onboarding & Preferences

    suspend fun completeOnboarding(
        studentName: String,
        avatarKey: String,
        studyLevel: StudyLevel,
        courseOrExamName: String,
        initialSubjects: List<Subject>,
        studyGoals: Set<String>,
        customGoal: String,
        preferredFocusMinutes: Int,
        shortBreakMinutes: Int,
        preferredStudyTime: PreferredStudyTime,
        weeklyStudyDaysTarget: Int,
        biggestDistractions: Set<String>,
        otherDistraction: String,
        dailyTargetMinutes: Int
    ) {
        preferencesStore.saveOnboardingPreferences(
            studentName = studentName,
            avatarKey = avatarKey,
            studyLevel = studyLevel,
            courseOrExamName = courseOrExamName,
            studyGoals = studyGoals,
            customGoal = customGoal,
            preferredFocusMinutes = preferredFocusMinutes,
            shortBreakMinutes = shortBreakMinutes,
            preferredStudyTime = preferredStudyTime,
            weeklyStudyDaysTarget = weeklyStudyDaysTarget,
            biggestDistractions = biggestDistractions,
            otherDistraction = otherDistraction,
            dailyTargetMinutes = dailyTargetMinutes
        )

        // Sync subjects and starter dormant seed atomically (preserve existing data)
        database.withTransaction {
            val existingSubjects = subjectDao.getAllOnce()
            val existingNames = existingSubjects.map { it.name.lowercase() }.toSet()
            initialSubjects.forEach { subject ->
                if (subject.name.isNotBlank() && subject.name.lowercase() !in existingNames) {
                    subjectDao.insert(subject.toEntity())
                }
            }

            // Plant a single starter dormant seed (0 sessions, 0 fake minutes) if garden is empty
            if (plantDao.getAllOnce().isEmpty()) {
                plantDao.insert(
                    GardenPlantEntity(
                        species = PlantSpecies.LAVENDER_SPIRE.name,
                        nickname = "First Sprout",
                        sessionsLogged = 0,
                        totalMinutes = 0,
                        plantedDateEpochDay = LocalDate.now().toEpochDay(),
                        isActive = true
                    )
                )
            }
        }
    }

    suspend fun saveOnboardingCurrentStep(step: Int) {
        preferencesStore.saveOnboardingCurrentStep(step)
    }

    suspend fun completeFullOnboarding(
        userRole: UserProfileRole,
        studentName: String,
        avatarKey: String,
        studyLevel: StudyLevel,
        courseOrExamName: String,
        initialSubjects: List<Subject>,
        studyGoals: Set<String>,
        customGoal: String,
        preferredFocusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        preferredStudyTime: PreferredStudyTime,
        weeklyStudyDaysTarget: Int,
        biggestDistractions: Set<String>,
        otherDistraction: String,
        dailyTargetMinutes: Int,
        focusGuardSettings: FocusGuardSettings,
        notificationPrefs: NotificationPreferences,
        selectedBlockedApps: List<BlockedAppRule>,
        appUsageAwarenessEnabled: Boolean
    ) {
        preferencesStore.saveFullOnboardingPreferences(
            userRole = userRole,
            studentName = studentName,
            avatarKey = avatarKey,
            studyLevel = studyLevel,
            courseOrExamName = courseOrExamName,
            studyGoals = studyGoals,
            customGoal = customGoal,
            preferredFocusMinutes = preferredFocusMinutes,
            shortBreakMinutes = shortBreakMinutes,
            longBreakMinutes = longBreakMinutes,
            preferredStudyTime = preferredStudyTime,
            weeklyStudyDaysTarget = weeklyStudyDaysTarget,
            biggestDistractions = biggestDistractions,
            otherDistraction = otherDistraction,
            dailyTargetMinutes = dailyTargetMinutes,
            focusGuardSettings = focusGuardSettings,
            notificationPrefs = notificationPrefs,
            appUsageAwarenessEnabled = appUsageAwarenessEnabled
        )

        database.withTransaction {
            val existingSubjects = subjectDao.getAllOnce()
            val existingNames = existingSubjects.map { it.name.lowercase() }.toSet()
            initialSubjects.forEach { subject ->
                if (subject.name.isNotBlank() && subject.name.lowercase() !in existingNames) {
                    subjectDao.insert(subject.toEntity())
                }
            }

            selectedBlockedApps.forEach { rule ->
                if (rule.packageName.isNotBlank()) {
                    blockedAppDao.upsert(rule.toEntity())
                }
            }

            if (plantDao.getAllOnce().isEmpty()) {
                plantDao.insert(
                    GardenPlantEntity(
                        species = PlantSpecies.LAVENDER_SPIRE.name,
                        nickname = "First Sprout",
                        sessionsLogged = 0,
                        totalMinutes = 0,
                        plantedDateEpochDay = LocalDate.now().toEpochDay(),
                        isActive = true
                    )
                )
            }
        }
        scheduleCloudSyncIfSignedIn()
    }

    suspend fun updateProfile(
        studentName: String,
        avatarKey: String,
        studyLevel: StudyLevel,
        courseOrExamName: String,
        studyGoals: Set<String>,
        customGoal: String,
        preferredStudyTime: PreferredStudyTime,
        weeklyStudyDaysTarget: Int,
        biggestDistractions: Set<String>,
        otherDistraction: String
    ) {
        preferencesStore.updateProfile(
            studentName = studentName,
            avatarKey = avatarKey,
            studyLevel = studyLevel,
            courseOrExamName = courseOrExamName,
            studyGoals = studyGoals,
            customGoal = customGoal,
            preferredStudyTime = preferredStudyTime,
            weeklyStudyDaysTarget = weeklyStudyDaysTarget,
            biggestDistractions = biggestDistractions,
            otherDistraction = otherDistraction
        )
        scheduleCloudSyncIfSignedIn()
    }

    suspend fun updateTimerAndTargetPrefs(
        preferredFocusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        dailyTargetMinutes: Int
    ) {
        preferencesStore.updateTimerAndTargetPrefs(
            preferredFocusMinutes = preferredFocusMinutes,
            shortBreakMinutes = shortBreakMinutes,
            longBreakMinutes = longBreakMinutes,
            dailyTargetMinutes = dailyTargetMinutes
        )
        scheduleCloudSyncIfSignedIn()
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        preferencesStore.updateThemeMode(mode)
        scheduleCloudSyncIfSignedIn()
    }

    suspend fun updatePerformanceOverride(override: PerformanceProfileOverride) {
        preferencesStore.updatePerformanceOverride(override)
        scheduleCloudSyncIfSignedIn()
    }

    suspend fun updateNotificationPreferences(notificationPrefs: NotificationPreferences) {
        preferencesStore.updateNotificationPreferences(notificationPrefs)
        scheduleCloudSyncIfSignedIn()
    }

    suspend fun updateAppUsageAwareness(enabled: Boolean) =
        preferencesStore.updateAppUsageAwareness(enabled)

    suspend fun updateAodFocusDisplay(enabled: Boolean) =
        preferencesStore.updateAodFocusDisplay(enabled)

    suspend fun updateSavedDndInterruptionFilter(filter: Int) =
        preferencesStore.updateSavedDndInterruptionFilter(filter)

    suspend fun updateFocusGuardSettings(settings: FocusGuardSettings) {
        preferencesStore.updateFocusGuardSettings(settings)
        scheduleCloudSyncIfSignedIn()
    }

    suspend fun recordDistractionWarning(todayEpochDay: Long = LocalDate.now().toEpochDay()) =
        preferencesStore.recordDistractionWarning(todayEpochDay)

    suspend fun getPreferencesOnce(): UserPreferences = preferencesStore.getPreferencesOnce()

    suspend fun getTimerSnapshotOnce(): PersistedTimerSnapshot = preferencesStore.getTimerSnapshotOnce()

    suspend fun getSubjectsOnce(): List<Subject> = subjectDao.getAllOnce().map { it.toDomain() }

    suspend fun getAppCategoryMappingsOnce(): List<AppCategoryMapping> =
        appCategoryMappingDao.getAllOnce().map { it.toDomain() }

    suspend fun getBlockedAppsOnce(): List<BlockedAppRule> =
        blockedAppDao.getAllOnce().map { it.toDomain() }

    suspend fun updateCloudUsageSummarySyncEnabled(enabled: Boolean) {
        preferencesStore.updateCloudUsageSummarySyncEnabled(enabled)
        scheduleCloudSyncIfSignedIn()
    }

    suspend fun updateFamilyControlSettings(settings: FamilyControlSettings) {
        preferencesStore.updateFamilyControlSettings(settings)
        scheduleCloudSyncIfSignedIn()
    }

    suspend fun saveAuthSession(session: SupabaseAuthSession) =
        preferencesStore.saveAuthSession(session)

    suspend fun clearAuthSession() = preferencesStore.clearAuthSession()

    suspend fun getAuthSessionOnce(): SupabaseAuthSession = preferencesStore.getAuthSessionOnce()

    suspend fun updateActiveAccountOwnerId(ownerId: String) =
        preferencesStore.updateActiveAccountOwnerId(ownerId)

    suspend fun updateLastSyncedAtMs(ms: Long) = preferencesStore.updateLastSyncedAtMs(ms)

    suspend fun updateAppSettings(
        notificationsEnabled: Boolean,
        breakRemindersEnabled: Boolean,
        soundEnabled: Boolean,
        hapticsEnabled: Boolean,
        reducedMotionOverride: Boolean
    ) {
        preferencesStore.updateAppSettings(
            notificationsEnabled = notificationsEnabled,
            breakRemindersEnabled = breakRemindersEnabled,
            soundEnabled = soundEnabled,
            hapticsEnabled = hapticsEnabled,
            reducedMotionOverride = reducedMotionOverride
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        preferencesStore.setOnboardingCompleted(completed)
    }

    suspend fun saveTimerSnapshot(snapshot: PersistedTimerSnapshot) {
        preferencesStore.saveTimerSnapshot(snapshot)
    }

    suspend fun getManualBlockingSessionOnce(): ManualBlockingSession =
        preferencesStore.getManualBlockingSessionOnce()

    suspend fun saveManualBlockingSession(session: ManualBlockingSession) {
        preferencesStore.saveManualBlockingSession(session)
    }

    suspend fun getTemporaryAllowancesOnce(): List<TemporaryAppAllowance> =
        preferencesStore.getTemporaryAllowancesOnce()

    suspend fun saveTemporaryAllowances(allowances: List<TemporaryAppAllowance>) {
        preferencesStore.saveTemporaryAllowances(allowances)
    }

    // endregion

    // region Subjects CRUD

    suspend fun upsertSubject(subject: Subject): Long {
        val savedId = if (subject.id == 0L) {
            subjectDao.insert(subject.toEntity())
        } else {
            subjectDao.update(subject.toEntity())
            subject.id
        }
        enqueueIfSignedIn(
            entityType = "subjects",
            localKey = savedId.toString(),
            payload = org.json.JSONObject().apply {
                put("local_id", savedId)
                put("name", subject.name)
                put("color_hex", subject.colorHex)
                put("icon_key", subject.iconKey)
                put("target_hours_per_week", subject.targetHoursPerWeek.toDouble())
                if (subject.examDateEpochDay != null) put("exam_date_epoch_day", subject.examDateEpochDay) else put("exam_date_epoch_day", org.json.JSONObject.NULL)
            }
        )
        return savedId
    }

    suspend fun deleteSubject(subjectId: Long) {
        database.withTransaction {
            taskDao.clearSubjectReference(subjectId)
            subjectDao.deleteById(subjectId)
        }
        enqueueDeleteIfSignedIn("subjects", subjectId.toString())
    }

    // endregion

    // region Study Tasks CRUD

    suspend fun upsertTask(task: StudyTask): Long {
        val savedId = if (task.id == 0L) {
            taskDao.insert(task.toEntity())
        } else {
            taskDao.update(task.toEntity())
            task.id
        }
        enqueueIfSignedIn(
            entityType = "planner_tasks",
            localKey = savedId.toString(),
            payload = org.json.JSONObject().apply {
                put("local_id", savedId)
                if (task.subjectId != null) put("subject_local_id", task.subjectId) else put("subject_local_id", org.json.JSONObject.NULL)
                put("title", task.title)
                put("due_date_epoch_day", task.dueDateEpochDay)
                put("due_time_label", task.dueTimeLabel)
                put("estimated_minutes", task.estimatedMinutes)
                put("priority", task.priority.name)
                put("is_completed", task.isCompleted)
                if (task.completedAtMs != null) put("completed_at_ms", task.completedAtMs) else put("completed_at_ms", org.json.JSONObject.NULL)
                put("notes", task.notes)
            }
        )
        return savedId
    }

    suspend fun deleteTask(taskId: Long) {
        taskDao.deleteById(taskId)
        enqueueDeleteIfSignedIn("planner_tasks", taskId.toString())
    }

    // endregion

    // region Focus Guard Blocklists & Study-Only YouTube Resources

    suspend fun upsertBlockedApp(rule: BlockedAppRule) {
        blockedAppDao.upsert(rule.toEntity())
        enqueueIfSignedIn(
            entityType = "blocked_apps",
            localKey = rule.packageName,
            payload = org.json.JSONObject().apply {
                put("package_name", rule.packageName)
                put("app_label", rule.appLabel)
                put("category", rule.category.name)
                put("block_in_normal", rule.blockInNormal)
                put("block_in_high", rule.blockInHigh)
                put("block_in_custom", rule.blockInCustom)
                put("is_blocked", rule.isBlocked)
            }
        )
    }

    suspend fun deleteBlockedApp(packageName: String) {
        blockedAppDao.deleteByPackage(packageName)
        enqueueDeleteIfSignedIn("blocked_apps", packageName)
    }

    suspend fun addStudyResource(resource: StudyResource): Long {
        val savedId = studyResourceDao.insert(resource.toEntity())
        enqueueIfSignedIn(
            entityType = "study_resources",
            localKey = savedId.toString(),
            payload = org.json.JSONObject().apply {
                put("local_id", savedId)
                if (resource.subjectId != null) put("subject_local_id", resource.subjectId) else put("subject_local_id", org.json.JSONObject.NULL)
                put("title", resource.title)
                put("url", resource.url)
                put("resource_type", resource.resourceType.name)
                put("channel_name", resource.channelName)
                put("last_opened_at_ms", resource.lastOpenedAtMs)
            }
        )
        return savedId
    }

    suspend fun markStudyResourceOpened(id: Long) {
        studyResourceDao.markOpened(id, System.currentTimeMillis())
    }

    suspend fun deleteStudyResource(id: Long) {
        studyResourceDao.deleteById(id)
        enqueueDeleteIfSignedIn("study_resources", id.toString())
    }

    private suspend fun enqueueIfSignedIn(entityType: String, localKey: String, payload: org.json.JSONObject) {
        val session = preferencesStore.getAuthSessionOnce()
        if (session.isSignedIn) {
            syncManager.enqueueUpsert(entityType, localKey, payload)
            appContext?.let { CloudSyncScheduler.scheduleSync(it) }
        }
    }

    private suspend fun enqueueDeleteIfSignedIn(entityType: String, localKey: String) {
        val session = preferencesStore.getAuthSessionOnce()
        if (session.isSignedIn) {
            syncManager.enqueueDelete(entityType, localKey)
            appContext?.let { CloudSyncScheduler.scheduleSync(it) }
        }
    }

    private suspend fun scheduleCloudSyncIfSignedIn() {
        val ctx = appContext ?: return
        if (preferencesStore.getAuthSessionOnce().isSignedIn) {
            CloudSyncScheduler.scheduleSync(ctx)
        }
    }

    // endregion

    // region Focus Sessions & Study Garden Growth

    suspend fun recordFocusSessionAndGrowGarden(
        session: FocusSessionRecord,
        dailyTargetMinutes: Int,
        allExistingSessions: List<FocusSessionRecord>,
        existingDecorations: Set<String>,
        sessionKey: String = ""
    ): Pair<GardenPlant, GardenDecoration?> {
        val effectiveSessionKey = sessionKey.ifBlank {
            "local_${session.completedAtMs}_${session.durationSeconds}"
        }
        val (updatedActive, wasDuplicate) = database.withTransaction {
            if (sessionKey.isNotBlank() && sessionDao.countBySessionKey(sessionKey) > 0) {
                val plants = plantDao.getAllOnce()
                val active = plants.firstOrNull { it.isActive } ?: plants.firstOrNull()
                return@withTransaction (active ?: GardenPlantEntity(
                    species = PlantSpecies.LAVENDER_SPIRE.name,
                    nickname = "First Sprout",
                    sessionsLogged = 0,
                    totalMinutes = 0,
                    plantedDateEpochDay = LocalDate.now().toEpochDay(),
                    isActive = true
                )) to true
            }

            sessionDao.insert(session.toEntity(sessionKey = effectiveSessionKey))

            var plants = plantDao.getAllOnce()
            if (plants.isEmpty()) {
                val id = plantDao.insert(
                    GardenPlantEntity(
                        species = PlantSpecies.LAVENDER_SPIRE.name,
                        nickname = "First Sprout",
                        sessionsLogged = 0,
                        totalMinutes = 0,
                        plantedDateEpochDay = LocalDate.now().toEpochDay(),
                        isActive = true
                    )
                )
                plants = plantDao.getAllOnce().ifEmpty {
                    listOf(
                        GardenPlantEntity(
                            id = id,
                            species = PlantSpecies.LAVENDER_SPIRE.name,
                            nickname = "First Sprout",
                            sessionsLogged = 0,
                            totalMinutes = 0,
                            plantedDateEpochDay = LocalDate.now().toEpochDay(),
                            isActive = true
                        )
                    )
                }
            }

            val activeEntity = plants.firstOrNull { it.isActive } ?: plants.first()
            val grown = activeEntity.copy(
                sessionsLogged = activeEntity.sessionsLogged + 1,
                totalMinutes = activeEntity.totalMinutes + session.durationMinutes
            )
            plantDao.update(grown)
            grown to false
        }

        if (wasDuplicate) {
            return updatedActive.toDomain() to null
        }

        enqueueIfSignedIn(
            entityType = "focus_sessions",
            localKey = effectiveSessionKey,
            payload = org.json.JSONObject().apply {
                put("session_key", effectiveSessionKey)
                if (session.subjectId != null) put("subject_local_id", session.subjectId) else put("subject_local_id", org.json.JSONObject.NULL)
                put("subject_name", session.subjectName)
                put("subject_color_hex", session.subjectColorHex)
                put("duration_seconds", session.durationSeconds)
                put("planned_seconds", session.plannedSeconds)
                put("session_goal", session.sessionGoal)
                put("mode", session.mode.name)
                put("completed_at_ms", session.completedAtMs)
                put("date_epoch_day", session.dateEpochDay)
            }
        )

        val updatedSessions = listOf(session) + allExistingSessions
        val todayMinutes = updatedSessions
            .filter { it.mode == FocusMode.FOCUS && it.dateEpochDay == session.dateEpochDay }
            .sumOf { it.durationMinutes }
        val distinctDays = updatedSessions
            .filter { it.mode == FocusMode.FOCUS }
            .map { it.dateEpochDay }
            .toSet()
            .size

        val newlyUnlocked = mutableSetOf<GardenDecoration>()
        if (GardenDecoration.STEPPING_STONES.key !in existingDecorations) {
            newlyUnlocked += GardenDecoration.STEPPING_STONES
        }
        if (todayMinutes >= dailyTargetMinutes && GardenDecoration.STONE_LANTERN.key !in existingDecorations) {
            newlyUnlocked += GardenDecoration.STONE_LANTERN
        }
        if (distinctDays >= 3 && GardenDecoration.WOODEN_BENCH.key !in existingDecorations) {
            newlyUnlocked += GardenDecoration.WOODEN_BENCH
        }
        if (PlantStage.fromSessions(updatedActive.sessionsLogged).ordinal >= PlantStage.BLOOMING.ordinal &&
            GardenDecoration.STARRY_ARCH.key !in existingDecorations
        ) {
            newlyUnlocked += GardenDecoration.STARRY_ARCH
        }

        preferencesStore.updateGardenRewards(
            seedsDelta = 1,
            newDecorations = newlyUnlocked.map { it.key }.toSet()
        )

        return updatedActive.toDomain() to newlyUnlocked.firstOrNull()
    }

    suspend fun setActivePlant(plantId: Long) {
        plantDao.setActivePlant(plantId)
    }

    suspend fun plantNewSeed(species: PlantSpecies, nickname: String, spendSeed: Boolean): Boolean {
        database.withTransaction {
            val insertedId = plantDao.insert(
                GardenPlantEntity(
                    species = species.name,
                    nickname = nickname.trim().ifBlank { species.displayName },
                    sessionsLogged = 0,
                    totalMinutes = 0,
                    plantedDateEpochDay = LocalDate.now().toEpochDay(),
                    isActive = true
                )
            )
            plantDao.setActivePlant(insertedId)
        }
        if (spendSeed) {
            preferencesStore.updateGardenRewards(seedsDelta = -1)
        }
        return true
    }

    suspend fun nurtureActivePlantWithSeed(): Boolean {
        val nurtured = database.withTransaction {
            val plants = plantDao.getAllOnce()
            val active = plants.firstOrNull { it.isActive } ?: return@withTransaction false
            plantDao.update(
                active.copy(
                    sessionsLogged = active.sessionsLogged + 1,
                    totalMinutes = active.totalMinutes + 10
                )
            )
            true
        }
        if (!nurtured) return false
        preferencesStore.updateGardenRewards(seedsDelta = -1)
        return true
    }

    // endregion

    // region Mind Reset Notes & Reflections

    suspend fun addMindNote(type: MindNoteType, content: String, moodTag: String = "") {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        mindNoteDao.insert(
            MindNoteEntity(
                type = type.name,
                content = trimmed,
                moodTag = moodTag.trim(),
                createdAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteMindNote(id: Long) {
        mindNoteDao.deleteById(id)
    }

    suspend fun recordMindResetBreathCycle() {
        preferencesStore.updateGardenRewards(mindResetDelta = 1)
    }

    // endregion

    // region Activity Logs, Strict Focus Presets & Custom Activities

    /**
     * Records an activity log (study, lecture, reading, break, gaming, entertainment, custom).
     * Deduplicates strictly by [ActivityLogRecord.sessionKey] so service + UI never double-log.
     */
    suspend fun recordActivityLog(record: ActivityLogRecord): Boolean {
        if (record.sessionKey.isNotBlank() && activityLogDao.countBySessionKey(record.sessionKey) > 0) {
            return false
        }
        activityLogDao.insert(record.toEntity())
        return true
    }

    suspend fun saveStrictFocusPreset(preset: StrictFocusPreset): Long {
        return if (preset.id == 0L) {
            strictFocusPresetDao.insert(preset.toEntity())
        } else {
            strictFocusPresetDao.update(preset.toEntity())
            preset.id
        }
    }

    suspend fun deleteStrictFocusPreset(presetId: Long) {
        strictFocusPresetDao.deleteById(presetId)
    }

    suspend fun addCustomActivity(option: CustomActivityOption): Long {
        return customActivityDao.insert(option.toEntity())
    }

    suspend fun deleteCustomActivity(id: Long) {
        customActivityDao.deleteById(id)
    }

    suspend fun upsertAppCategoryMapping(mapping: AppCategoryMapping) {
        appCategoryMappingDao.upsert(mapping.toEntity())
    }

    suspend fun deleteAppCategoryMapping(packageName: String) {
        appCategoryMappingDao.deleteByPackage(packageName)
    }

    suspend fun upsertStudySession(
        sessionKey: String,
        subjectId: Long?,
        subjectName: String,
        startTimeMs: Long,
        endTimeMs: Long?,
        totalDurationSeconds: Int,
        plannedDurationSeconds: Int,
        sessionStatus: String,
        focusGuardLevel: String,
        activityType: String
    ) {
        if (sessionKey.isBlank()) return
        val existing = studySessionDao.getBySessionKey(sessionKey)
        studySessionDao.upsert(
            StudySessionEntity(
                id = existing?.id ?: 0L,
                sessionKey = sessionKey,
                subjectId = subjectId,
                subjectName = subjectName,
                startTimeMs = existing?.startTimeMs ?: startTimeMs,
                endTimeMs = endTimeMs,
                totalDurationSeconds = totalDurationSeconds.coerceAtLeast(0),
                plannedDurationSeconds = plannedDurationSeconds.coerceAtLeast(1),
                sessionStatus = sessionStatus,
                focusGuardLevel = focusGuardLevel,
                activityType = activityType,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    /**
     * Offline JSON restore for backups generated by [BrainBufferViewModel.buildStudyBackupJson].
     * Non-destructive: merges new subjects & tasks while skipping case-insensitive duplicates.
     */
    suspend fun importStudyBackupJson(json: String): Result<Int> {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Backup JSON is empty."))
        }
        return runCatching {
            val root = org.json.JSONObject(trimmed)
            val appName = root.optString("app", "")
            if (appName.isNotBlank() && !appName.equals("Brain Buffer", ignoreCase = true)) {
                throw IllegalArgumentException("Unrecognized backup format.")
            }

            var restoredCount = 0
            val defaultSubjectColors = listOf(0xFF7B5CFA, 0xFF10B981, 0xFF3B82F6, 0xFFF59E0B, 0xFFEC4899)
            val todayEpoch = LocalDate.now().toEpochDay()

            database.withTransaction {
                val existingSubjectNames = subjectDao.getAllOnce()
                    .map { it.name.trim().lowercase(Locale.ROOT) }
                    .toMutableSet()

                val subjectsArray = root.optJSONArray("subjects")
                if (subjectsArray != null) {
                    for (i in 0 until subjectsArray.length()) {
                        val obj = subjectsArray.optJSONObject(i) ?: continue
                        val name = obj.optString("name", "").trim()
                        if (name.isEmpty()) continue
                        val key = name.lowercase(Locale.ROOT)
                        if (key in existingSubjectNames) continue
                        val targetHours = obj.optDouble("targetHoursPerWeek", 4.0).toFloat().coerceIn(1f, 40f)
                        subjectDao.insert(
                            SubjectEntity(
                                name = name.take(40),
                                colorHex = defaultSubjectColors[i % defaultSubjectColors.size],
                                iconKey = SubjectIcon.BOOK.key,
                                targetHoursPerWeek = targetHours,
                                examDateEpochDay = null
                            )
                        )
                        existingSubjectNames.add(key)
                        restoredCount++
                    }
                }

                val existingTaskTitles = taskDao.getAllOnce()
                    .map { it.title.trim().lowercase(Locale.ROOT) }
                    .toMutableSet()

                val tasksArray = root.optJSONArray("tasks")
                if (tasksArray != null) {
                    for (i in 0 until tasksArray.length()) {
                        val obj = tasksArray.optJSONObject(i) ?: continue
                        val title = obj.optString("title", "").trim()
                        if (title.isEmpty()) continue
                        val key = title.lowercase(Locale.ROOT)
                        if (key in existingTaskTitles) continue
                        val dueTime = obj.optString("dueTime", "6:00 PM").ifBlank { "6:00 PM" }
                        val estimatedMinutes = obj.optInt("estimatedMinutes", 30).coerceIn(5, 240)
                        val priorityLabel = obj.optString("priority", "Medium")
                        val priority = TaskPriority.entries.firstOrNull {
                            it.label.equals(priorityLabel, ignoreCase = true) ||
                                it.name.equals(priorityLabel, ignoreCase = true)
                        } ?: TaskPriority.MEDIUM
                        val completed = obj.optBoolean("completed", false)
                        taskDao.insert(
                            StudyTaskEntity(
                                title = title.take(120),
                                subjectId = null,
                                dueDateEpochDay = todayEpoch,
                                dueTimeLabel = dueTime,
                                estimatedMinutes = estimatedMinutes,
                                priority = priority.name,
                                isCompleted = completed,
                                completedAtMs = if (completed) System.currentTimeMillis() else null,
                                notes = ""
                            )
                        )
                        existingTaskTitles.add(key)
                        restoredCount++
                    }
                }
            }

            val profileObj = root.optJSONObject("profile")
            if (profileObj != null && profileObj.has("dailyTargetMinutes")) {
                val currentPrefs = preferencesStore.getPreferencesOnce()
                val importedTarget = profileObj.optInt("dailyTargetMinutes", currentPrefs.dailyTargetMinutes)
                    .coerceIn(15, 600)
                if (importedTarget != currentPrefs.dailyTargetMinutes) {
                    preferencesStore.updateTimerAndTargetPrefs(
                        preferredFocusMinutes = currentPrefs.preferredFocusMinutes,
                        shortBreakMinutes = currentPrefs.shortBreakMinutes,
                        longBreakMinutes = currentPrefs.longBreakMinutes,
                        dailyTargetMinutes = importedTarget
                    )
                    restoredCount++
                }
            }

            restoredCount
        }
    }

    // endregion

    // region Data Management (Clear All + Explicit Opt-In Demo Preview Mode)

    suspend fun clearAllStudyData() {
        database.withTransaction {
            taskDao.deleteAll()
            sessionDao.deleteAll()
            studySessionDao.deleteAll()
            activityLogDao.deleteAll()
            strictFocusPresetDao.deleteAll()
            customActivityDao.deleteAll()
            appCategoryMappingDao.deleteAll()
            blockedAppDao.deleteAll()
            studyResourceDao.deleteAll()
            syncOutboxDao.deleteAll()
            plantDao.deleteAll()
            mindNoteDao.deleteAll()
            subjectDao.deleteAll()
            purposeSessionDao.deleteAll()
            interventionEventDao.deleteAll()
        }
        preferencesStore.saveActiveTempAccess(null)
        preferencesStore.resetStudyRewardsOnly()
    }

    /**
     * Account isolation: clears local private tables when signing out or switching accounts
     * so Account A's records never leak into Account B on the same device. Cloud data is untouched.
     */
    suspend fun clearLocalTablesForAccountSwitch() {
        clearAllStudyData()
        preferencesStore.resetAccountScopedPreferencesForSwitch()
    }

    /**
     * Explicitly user-triggered preview/demo data loader inside Settings -> Data Management.
     * Never called automatically on app launch.
     */
    suspend fun loadExplicitDemoPreviewData() {
        val today = LocalDate.now().toEpochDay()
        val now = System.currentTimeMillis()

        val mathId = subjectDao.insert(
            SubjectEntity(
                name = "Mathematics",
                colorHex = 0xFF7B5CFA,
                iconKey = SubjectIcon.CALCULATE.key,
                targetHoursPerWeek = 5f,
                examDateEpochDay = today + 11
            )
        )
        val csId = subjectDao.insert(
            SubjectEntity(
                name = "Computer Science",
                colorHex = 0xFF10B981,
                iconKey = SubjectIcon.CODE.key,
                targetHoursPerWeek = 6f,
                examDateEpochDay = today + 18
            )
        )
        val physicsId = subjectDao.insert(
            SubjectEntity(
                name = "Physics",
                colorHex = 0xFF3B82F6,
                iconKey = SubjectIcon.SCIENCE.key,
                targetHoursPerWeek = 4f,
                examDateEpochDay = today + 6
            )
        )

        taskDao.insert(
            StudyTaskEntity(
                title = "Complete Calculus vector fields worksheet",
                subjectId = mathId,
                dueDateEpochDay = today,
                dueTimeLabel = "6:00 PM",
                estimatedMinutes = 40,
                priority = TaskPriority.HIGH.name,
                isCompleted = false,
                completedAtMs = null,
                notes = "Review Green's theorem examples first."
            )
        )
        taskDao.insert(
            StudyTaskEntity(
                title = "Trace dynamic programming recursion tree",
                subjectId = csId,
                dueDateEpochDay = today,
                dueTimeLabel = "8:00 PM",
                estimatedMinutes = 30,
                priority = TaskPriority.MEDIUM.name,
                isCompleted = true,
                completedAtMs = now - 3_600_000L,
                notes = "Compare memoization vs bottom-up table."
            )
        )
        taskDao.insert(
            StudyTaskEntity(
                title = "Summarize Thermodynamics Chapter 5 laws",
                subjectId = physicsId,
                dueDateEpochDay = today + 2,
                dueTimeLabel = "4:30 PM",
                estimatedMinutes = 35,
                priority = TaskPriority.MEDIUM.name,
                isCompleted = false,
                completedAtMs = null,
                notes = "Highlight Carnot cycle equations."
            )
        )

        val demoSessions = listOf(
            FocusSessionEntity(0, mathId, "Mathematics", 0xFF7B5CFA, 25 * 60, 25 * 60, "Practice integrals", FocusMode.FOCUS.name, now - 7_200_000L, today),
            FocusSessionEntity(0, csId, "Computer Science", 0xFF10B981, 30 * 60, 30 * 60, "Graph algorithms", FocusMode.FOCUS.name, now - 86_400_000L, today - 1),
            FocusSessionEntity(0, physicsId, "Physics", 0xFF3B82F6, 45 * 60, 45 * 60, "Optics lab prep", FocusMode.FOCUS.name, now - 172_800_000L, today - 2),
            FocusSessionEntity(0, mathId, "Mathematics", 0xFF7B5CFA, 25 * 60, 25 * 60, "Linear algebra quiz", FocusMode.FOCUS.name, now - 259_200_000L, today - 3)
        )
        demoSessions.forEach { sessionDao.insert(it) }

        if (plantDao.getAllOnce().isEmpty()) {
            plantDao.insert(
                GardenPlantEntity(
                    species = PlantSpecies.LAVENDER_SPIRE.name,
                    nickname = "Twilight Spire",
                    sessionsLogged = 5,
                    totalMinutes = 125,
                    plantedDateEpochDay = today - 4,
                    isActive = true
                )
            )
        }

        preferencesStore.updateGardenRewards(
            seedsDelta = 2,
            newDecorations = setOf(
                GardenDecoration.STEPPING_STONES.key,
                GardenDecoration.WOODEN_BENCH.key
            )
        )
    }

    // endregion

    companion object {
        @Volatile
        private var INSTANCE: BrainBufferRepository? = null

        fun getInstance(context: Context): BrainBufferRepository {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                INSTANCE ?: BrainBufferRepository(
                    database = BrainBufferDatabase.getInstance(appContext),
                    preferencesStore = UserPreferencesStore(appContext),
                    appContext = appContext
                ).also { INSTANCE = it }
            }
        }

        val motivationalNotes = listOf(
            "Small, calm focus blocks beat hours of stressed multitasking.",
            "Make space for one meaningful thing right now—the rest can wait.",
            "Rest is part of learning; your brain consolidates memory in stillness.",
            "Consistency grows quietly, never through guilt.",
            "Start with just 10 gentle minutes. Momentum follows clarity."
        )

        fun distractionTipFor(distractions: Set<String>): String {
            return when {
                "Social media" in distractions ->
                    "Tip for social media: place your phone face-down across the room during your focus block."
                "Notifications" in distractions ->
                    "Tip for notifications: try Do Not Disturb for one study block and batch-check messages on your break."
                "Videos" in distractions ->
                    "Tip for videos: keep one study tab open and save interesting videos to Watch Later for your break."
                "Games" in distractions ->
                    "Tip for games: treat a finished focus block as your guilt-free unlock for a short recharge."
                "Procrastination" in distractions ->
                    "Tip for starting: pick one tiny 15-minute step—beginning is always the heaviest part."
                "Noise" in distractions ->
                    "Tip for noise: headphones or a quiet corner help cue your brain that it's calm study time."
                else ->
                    "Keep only the single book or tab you need for this session—make space for what matters."
            }
        }

        fun buildWeeklyActivity(
            sessions: List<FocusSessionRecord>,
            tasks: List<StudyTask>,
            today: LocalDate = LocalDate.now()
        ): List<DailyActivityStat> {
            return (6 downTo 0).map { daysAgo ->
                val date = today.minusDays(daysAgo.toLong())
                val epochDay = date.toEpochDay()
                val daySessions = sessions.filter { it.mode == FocusMode.FOCUS && it.dateEpochDay == epochDay }
                val dayMinutes = daySessions.sumOf { it.durationSeconds } / 60
                val dayTasksDone = tasks.count { task ->
                    task.isCompleted && (
                        task.completedAtMs?.let { ms ->
                            java.time.Instant.ofEpochMilli(ms)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                                .toEpochDay() == epochDay
                        } ?: (task.dueDateEpochDay == epochDay)
                    )
                }
                DailyActivityStat(
                    dateEpochDay = epochDay,
                    dayShort = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    dayFull = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    focusMinutes = dayMinutes,
                    sessionsCount = daySessions.size,
                    tasksCompleted = dayTasksDone,
                    isToday = daysAgo == 0
                )
            }
        }

        fun computeCurrentStreakDays(
            sessions: List<FocusSessionRecord>,
            today: LocalDate = LocalDate.now()
        ): Int {
            val activeDays = sessions
                .filter { it.mode == FocusMode.FOCUS && it.durationSeconds >= 60 }
                .map { it.dateEpochDay }
                .toSet()
            if (activeDays.isEmpty()) return 0

            val todayEpoch = today.toEpochDay()
            var cursor = if (todayEpoch in activeDays) todayEpoch else todayEpoch - 1
            if (cursor !in activeDays) return 0

            var streak = 0
            while (cursor in activeDays) {
                streak++
                cursor--
            }
            return streak
        }

        fun computeBestStreakDays(sessions: List<FocusSessionRecord>): Int {
            val sortedDays = sessions
                .filter { it.mode == FocusMode.FOCUS && it.durationSeconds >= 60 }
                .map { it.dateEpochDay }
                .distinct()
                .sorted()
            if (sortedDays.isEmpty()) return 0

            var best = 1
            var current = 1
            for (i in 1 until sortedDays.size) {
                if (sortedDays[i] == sortedDays[i - 1] + 1) {
                    current++
                    if (current > best) best = current
                } else {
                    current = 1
                }
            }
            return best
        }
    }
}

private fun SubjectEntity.toDomain() = Subject(
    id = id,
    name = name,
    colorHex = colorHex,
    iconKey = iconKey,
    targetHoursPerWeek = targetHoursPerWeek,
    examDateEpochDay = examDateEpochDay
)

private fun Subject.toEntity() = SubjectEntity(
    id = id,
    name = name,
    colorHex = colorHex,
    iconKey = iconKey,
    targetHoursPerWeek = targetHoursPerWeek,
    examDateEpochDay = examDateEpochDay
)

private fun StudyTaskEntity.toDomain() = StudyTask(
    id = id,
    title = title,
    subjectId = subjectId,
    dueDateEpochDay = dueDateEpochDay,
    dueTimeLabel = dueTimeLabel,
    estimatedMinutes = estimatedMinutes,
    priority = TaskPriority.entries.firstOrNull { it.name == priority } ?: TaskPriority.MEDIUM,
    isCompleted = isCompleted,
    completedAtMs = completedAtMs,
    notes = notes
)

private fun StudyTask.toEntity() = StudyTaskEntity(
    id = id,
    title = title,
    subjectId = subjectId,
    dueDateEpochDay = dueDateEpochDay,
    dueTimeLabel = dueTimeLabel,
    estimatedMinutes = estimatedMinutes,
    priority = priority.name,
    isCompleted = isCompleted,
    completedAtMs = completedAtMs,
    notes = notes
)

private fun FocusSessionEntity.toDomain() = FocusSessionRecord(
    id = id,
    subjectId = subjectId,
    subjectName = subjectName,
    subjectColorHex = subjectColorHex,
    durationSeconds = durationSeconds,
    plannedSeconds = plannedSeconds,
    sessionGoal = sessionGoal,
    mode = FocusMode.entries.firstOrNull { it.name == mode } ?: FocusMode.FOCUS,
    completedAtMs = completedAtMs,
    dateEpochDay = dateEpochDay
)

private fun FocusSessionRecord.toEntity(sessionKey: String = "") = FocusSessionEntity(
    id = id,
    subjectId = subjectId,
    subjectName = subjectName,
    subjectColorHex = subjectColorHex,
    durationSeconds = durationSeconds,
    plannedSeconds = plannedSeconds,
    sessionGoal = sessionGoal,
    mode = mode.name,
    completedAtMs = completedAtMs,
    dateEpochDay = dateEpochDay,
    sessionKey = sessionKey
)

private fun GardenPlantEntity.toDomain() = GardenPlant(
    id = id,
    species = PlantSpecies.entries.firstOrNull { it.name == species } ?: PlantSpecies.LAVENDER_SPIRE,
    nickname = nickname,
    sessionsLogged = sessionsLogged,
    totalMinutes = totalMinutes,
    plantedDateEpochDay = plantedDateEpochDay,
    isActive = isActive
)

private fun MindNoteEntity.toDomain() = MindNote(
    id = id,
    type = MindNoteType.entries.firstOrNull { it.name == type } ?: MindNoteType.DISTRACTION_DUMP,
    content = content,
    moodTag = moodTag,
    createdAtMs = createdAtMs
)

private fun ActivityLogEntity.toDomain() = ActivityLogRecord(
    id = id,
    sessionKey = sessionKey,
    activityType = StudyActivityType.entries.firstOrNull { it.name == activityType } ?: StudyActivityType.OTHER,
    activityLabel = activityLabel,
    category = ActivityCategory.entries.firstOrNull { it.name == category } ?: ActivityCategory.OTHER,
    subjectId = subjectId,
    subjectName = subjectName,
    subjectColorHex = subjectColorHex,
    plannedSeconds = plannedSeconds,
    actualSeconds = actualSeconds,
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    dateEpochDay = dateEpochDay,
    wasPlanned = wasPlanned,
    notes = notes
)

private fun ActivityLogRecord.toEntity() = ActivityLogEntity(
    id = id,
    sessionKey = sessionKey,
    activityType = activityType.name,
    activityLabel = activityLabel,
    category = category.name,
    subjectId = subjectId,
    subjectName = subjectName,
    subjectColorHex = subjectColorHex,
    plannedSeconds = plannedSeconds,
    actualSeconds = actualSeconds,
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    dateEpochDay = dateEpochDay,
    wasPlanned = wasPlanned,
    notes = notes
)

private fun StrictFocusPresetEntity.toDomain() = StrictFocusPreset(
    id = id,
    name = name,
    durationMinutes = durationMinutes,
    subjectId = subjectId,
    activityType = StudyActivityType.entries.firstOrNull { it.name == activityType } ?: StudyActivityType.STUDYING,
    dailyGoalMinutes = dailyGoalMinutes,
    breakPolicy = BreakPolicy.entries.firstOrNull { it.name == breakPolicy } ?: BreakPolicy.AUTO_OFFER_BREAK,
    strictnessLevel = StrictnessLevel.entries.firstOrNull { it.name == strictnessLevel } ?: StrictnessLevel.COMMITTED,
    enableDnd = enableDnd,
    enableAodDisplay = enableAodDisplay,
    allowedAppsCsv = allowedAppsCsv,
    createdAtMs = createdAtMs
)

private fun StrictFocusPreset.toEntity() = StrictFocusPresetEntity(
    id = id,
    name = name,
    durationMinutes = durationMinutes,
    subjectId = subjectId,
    activityType = activityType.name,
    dailyGoalMinutes = dailyGoalMinutes,
    breakPolicy = breakPolicy.name,
    strictnessLevel = strictnessLevel.name,
    enableDnd = enableDnd,
    enableAodDisplay = enableAodDisplay,
    allowedAppsCsv = allowedAppsCsv,
    createdAtMs = createdAtMs
)

private fun CustomActivityEntity.toDomain() = CustomActivityOption(
    id = id,
    label = label,
    category = ActivityCategory.entries.firstOrNull { it.name == category } ?: ActivityCategory.OTHER,
    defaultDurationMinutes = defaultDurationMinutes,
    accentHex = accentHex
)

private fun CustomActivityOption.toEntity() = CustomActivityEntity(
    id = id,
    label = label,
    category = category.name,
    defaultDurationMinutes = defaultDurationMinutes,
    accentHex = accentHex
)

private fun AppCategoryMappingEntity.toDomain() = AppCategoryMapping(
    packageName = packageName,
    appLabel = appLabel,
    category = ActivityCategory.entries.firstOrNull { it.name == category } ?: ActivityCategory.OTHER,
    activityType = StudyActivityType.entries.firstOrNull { it.name == activityType } ?: StudyActivityType.OTHER,
    isAllowedDuringFocus = isAllowedDuringFocus,
    updatedAtMs = updatedAtMs
)

private fun AppCategoryMapping.toEntity() = AppCategoryMappingEntity(
    packageName = packageName,
    appLabel = appLabel,
    category = category.name,
    activityType = activityType.name,
    isAllowedDuringFocus = isAllowedDuringFocus,
    updatedAtMs = updatedAtMs
)

private fun BlockedAppEntity.toDomain() = BlockedAppRule(
    packageName = packageName,
    appLabel = appLabel,
    category = BlockedAppCategory.entries.firstOrNull { it.name == category } ?: BlockedAppCategory.OTHER,
    blockInNormal = blockInNormal,
    blockInHigh = blockInHigh,
    blockInCustom = blockInCustom,
    isBlocked = isBlocked,
    updatedAtMs = updatedAtMs
)

private fun BlockedAppRule.toEntity() = BlockedAppEntity(
    packageName = packageName,
    appLabel = appLabel,
    category = category.name,
    blockInNormal = blockInNormal,
    blockInHigh = blockInHigh,
    blockInCustom = blockInCustom,
    isBlocked = isBlocked,
    updatedAtMs = updatedAtMs
)

private fun StudyResourceEntity.toDomain() = StudyResource(
    id = id,
    subjectId = subjectId,
    title = title,
    url = url,
    resourceType = StudyResourceType.entries.firstOrNull { it.name == resourceType } ?: StudyResourceType.VIDEO,
    channelName = channelName,
    lastOpenedAtMs = lastOpenedAtMs,
    createdAtMs = createdAtMs
)

private fun StudyResource.toEntity() = StudyResourceEntity(
    id = id,
    subjectId = subjectId,
    title = title,
    url = url,
    resourceType = resourceType.name,
    channelName = channelName,
    lastOpenedAtMs = lastOpenedAtMs,
    createdAtMs = createdAtMs
)

private fun PurposeSessionEntity.toDomain() = PurposeSessionRecord(
    id = id,
    sessionKey = sessionKey,
    purposeCategory = SessionPurposeCategory.fromKey(purposeCategory),
    sessionName = sessionName,
    allowedPackages = allowedPackagesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    allowedAppLabels = allowedAppLabelsCsv.split("||").map { it.trim() }.filter { it.isNotEmpty() },
    plannedSeconds = plannedSeconds,
    actualFocusedSeconds = actualFocusedSeconds,
    breakSeconds = breakSeconds,
    temporaryAccessSeconds = temporaryAccessSeconds,
    distractionSeconds = distractionSeconds,
    interventionCount = interventionCount,
    status = status,
    breakPlanMode = BreakPlanMode.fromKey(breakPlanMode),
    linkedTaskId = linkedTaskId,
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    dateEpochDay = dateEpochDay
)

private fun PurposeSessionRecord.toEntity() = PurposeSessionEntity(
    id = id,
    sessionKey = sessionKey,
    purposeCategory = purposeCategory.name,
    sessionName = sessionName,
    allowedPackagesCsv = allowedPackages.joinToString(","),
    allowedAppLabelsCsv = allowedAppLabels.joinToString("||"),
    plannedSeconds = plannedSeconds,
    actualFocusedSeconds = actualFocusedSeconds,
    breakSeconds = breakSeconds,
    temporaryAccessSeconds = temporaryAccessSeconds,
    distractionSeconds = distractionSeconds,
    interventionCount = interventionCount,
    status = status,
    breakPlanMode = breakPlanMode.name,
    linkedTaskId = linkedTaskId,
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    dateEpochDay = dateEpochDay
)

private fun InterventionEventEntity.toDomain() = InterventionEventRecord(
    id = id,
    sessionKey = sessionKey,
    eventType = InterventionEventType.fromKey(eventType),
    packageName = packageName,
    appLabel = appLabel,
    declaredPurpose = declaredPurpose,
    actionTaken = actionTaken,
    plannedSeconds = plannedSeconds,
    extensionSeconds = extensionSeconds,
    actualSeconds = actualSeconds,
    timestampMs = timestampMs,
    dateEpochDay = dateEpochDay
)

private fun InterventionEventRecord.toEntity() = InterventionEventEntity(
    id = id,
    sessionKey = sessionKey,
    eventType = eventType.name,
    packageName = packageName,
    appLabel = appLabel,
    declaredPurpose = declaredPurpose,
    actionTaken = actionTaken,
    plannedSeconds = plannedSeconds,
    extensionSeconds = extensionSeconds,
    actualSeconds = actualSeconds,
    timestampMs = timestampMs,
    dateEpochDay = dateEpochDay
)


