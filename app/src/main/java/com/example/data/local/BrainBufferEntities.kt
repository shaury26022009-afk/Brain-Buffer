package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val colorHex: Long,
    val iconKey: String,
    val targetHoursPerWeek: Float,
    val examDateEpochDay: Long?,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_tasks")
data class StudyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val subjectId: Long?,
    val dueDateEpochDay: Long,
    val dueTimeLabel: String,
    val estimatedMinutes: Int,
    val priority: String,
    val isCompleted: Boolean,
    val completedAtMs: Long?,
    val notes: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val subjectId: Long?,
    val subjectName: String,
    val subjectColorHex: Long,
    val durationSeconds: Int,
    val plannedSeconds: Int,
    val sessionGoal: String,
    val mode: String,
    val completedAtMs: Long,
    val dateEpochDay: Long,
    val sessionKey: String = ""
)

@Entity(tableName = "garden_plants")
data class GardenPlantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val species: String,
    val nickname: String,
    val sessionsLogged: Int,
    val totalMinutes: Int,
    val plantedDateEpochDay: Long,
    val isActive: Boolean
)

@Entity(tableName = "mind_notes")
data class MindNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val content: String,
    val moodTag: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionKey: String = "",
    val activityType: String,
    val activityLabel: String,
    val category: String,
    val subjectId: Long?,
    val subjectName: String,
    val subjectColorHex: Long,
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val dateEpochDay: Long,
    val wasPlanned: Boolean,
    val notes: String = ""
)

@Entity(tableName = "strict_focus_presets")
data class StrictFocusPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val durationMinutes: Int,
    val subjectId: Long?,
    val activityType: String,
    val dailyGoalMinutes: Int,
    val breakPolicy: String,
    val strictnessLevel: String,
    val enableDnd: Boolean,
    val enableAodDisplay: Boolean,
    val allowedAppsCsv: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_activities")
data class CustomActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val category: String,
    val defaultDurationMinutes: Int,
    val accentHex: Long
)

@Entity(tableName = "app_category_mappings")
data class AppCategoryMappingEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val category: String,
    val activityType: String,
    val isAllowedDuringFocus: Boolean,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "study_sessions",
    indices = [Index(value = ["sessionKey"], unique = true)]
)
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionKey: String,
    val subjectId: Long?,
    val subjectName: String,
    val startTimeMs: Long,
    val endTimeMs: Long?,
    val totalDurationSeconds: Int,
    val plannedDurationSeconds: Int,
    val sessionStatus: String,
    val focusGuardLevel: String,
    val activityType: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val category: String,
    val blockInNormal: Boolean,
    val blockInHigh: Boolean,
    val blockInCustom: Boolean,
    val isBlocked: Boolean,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "study_resources")
data class StudyResourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val subjectId: Long?,
    val title: String,
    val url: String,
    val resourceType: String,
    val channelName: String,
    val lastOpenedAtMs: Long = 0L,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["entityType", "localKey"], unique = true)]
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val entityType: String,
    val localKey: String,
    val operation: String,
    val payloadJson: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String = ""
)

@Entity(
    tableName = "purpose_sessions",
    indices = [Index(value = ["sessionKey"], unique = true)]
)
data class PurposeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionKey: String,
    val purposeCategory: String,
    val sessionName: String,
    val allowedPackagesCsv: String,
    val allowedAppLabelsCsv: String,
    val plannedSeconds: Int,
    val actualFocusedSeconds: Int,
    val breakSeconds: Int,
    val temporaryAccessSeconds: Int,
    val distractionSeconds: Int,
    val interventionCount: Int,
    val status: String,
    val breakPlanMode: String,
    val linkedTaskId: Long?,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val dateEpochDay: Long
)

@Entity(tableName = "intervention_events")
data class InterventionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionKey: String,
    val eventType: String,
    val packageName: String,
    val appLabel: String,
    val declaredPurpose: String,
    val actionTaken: String,
    val plannedSeconds: Int,
    val extensionSeconds: Int,
    val actualSeconds: Int,
    val timestampMs: Long,
    val dateEpochDay: Long
)


