package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubjectEntity::class,
        StudyTaskEntity::class,
        FocusSessionEntity::class,
        GardenPlantEntity::class,
        MindNoteEntity::class,
        ActivityLogEntity::class,
        StrictFocusPresetEntity::class,
        CustomActivityEntity::class,
        AppCategoryMappingEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class BrainBufferDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun studyTaskDao(): StudyTaskDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun gardenPlantDao(): GardenPlantDao
    abstract fun mindNoteDao(): MindNoteDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun strictFocusPresetDao(): StrictFocusPresetDao
    abstract fun customActivityDao(): CustomActivityDao
    abstract fun appCategoryMappingDao(): AppCategoryMappingDao

    companion object {
        @Volatile
        private var INSTANCE: BrainBufferDatabase? = null

        /**
         * Safe non-destructive migration from schema v1 -> v2.
         * Preserves all existing subjects, study tasks, focus sessions, garden plants, and mind notes.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE focus_sessions ADD COLUMN sessionKey TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS activity_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionKey TEXT NOT NULL,
                        activityType TEXT NOT NULL,
                        activityLabel TEXT NOT NULL,
                        category TEXT NOT NULL,
                        subjectId INTEGER,
                        subjectName TEXT NOT NULL,
                        subjectColorHex INTEGER NOT NULL,
                        plannedSeconds INTEGER NOT NULL,
                        actualSeconds INTEGER NOT NULL,
                        startedAtMs INTEGER NOT NULL,
                        endedAtMs INTEGER NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        wasPlanned INTEGER NOT NULL,
                        notes TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS strict_focus_presets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL,
                        subjectId INTEGER,
                        activityType TEXT NOT NULL,
                        dailyGoalMinutes INTEGER NOT NULL,
                        breakPolicy TEXT NOT NULL,
                        strictnessLevel TEXT NOT NULL,
                        enableDnd INTEGER NOT NULL,
                        enableAodDisplay INTEGER NOT NULL,
                        allowedAppsCsv TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS custom_activities (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        label TEXT NOT NULL,
                        category TEXT NOT NULL,
                        defaultDurationMinutes INTEGER NOT NULL,
                        accentHex INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Safe non-destructive migration from schema v2 -> v3.
         * Preserves all v1 & v2 tables and adds user-configured app-to-category mappings.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_category_mappings (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        appLabel TEXT NOT NULL,
                        category TEXT NOT NULL,
                        activityType TEXT NOT NULL,
                        isAllowedDuringFocus INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): BrainBufferDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BrainBufferDatabase::class.java,
                    "brain_buffer.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
