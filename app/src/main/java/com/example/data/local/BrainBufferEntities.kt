package com.example.data.local

import androidx.room.Entity
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
