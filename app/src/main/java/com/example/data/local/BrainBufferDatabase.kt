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
        AppCategoryMappingEntity::class,
        StudySessionEntity::class,
        BlockedAppEntity::class,
        StudyResourceEntity::class,
        SyncOutboxEntity::class,
        PurposeSessionEntity::class,
        InterventionEventEntity::class
    ],
    version = 6,
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
    abstract fun studySessionDao(): StudySessionDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun studyResourceDao(): StudyResourceDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun purposeSessionDao(): PurposeSessionDao
    abstract fun interventionEventDao(): InterventionEventDao

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

        /**
         * Safe non-destructive migration from schema v3 -> v4.
         * Preserves all existing study data and adds durable study_sessions lifecycle table.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS study_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionKey TEXT NOT NULL,
                        subjectId INTEGER,
                        subjectName TEXT NOT NULL,
                        startTimeMs INTEGER NOT NULL,
                        endTimeMs INTEGER,
                        totalDurationSeconds INTEGER NOT NULL,
                        plannedDurationSeconds INTEGER NOT NULL,
                        sessionStatus TEXT NOT NULL,
                        focusGuardLevel TEXT NOT NULL,
                        activityType TEXT NOT NULL,
                        updatedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_study_sessions_sessionKey ON study_sessions (sessionKey)"
                )
            }
        }

        /**
         * Safe non-destructive migration from schema v4 -> v5.
         * Preserves all existing tables (including garden_plants) and adds Focus Guard blocklists,
         * Study-Only YouTube resources, and offline-first Supabase sync outbox.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS blocked_apps (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        appLabel TEXT NOT NULL,
                        category TEXT NOT NULL,
                        blockInNormal INTEGER NOT NULL,
                        blockInHigh INTEGER NOT NULL,
                        blockInCustom INTEGER NOT NULL,
                        isBlocked INTEGER NOT NULL,
                        updatedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS study_resources (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        subjectId INTEGER,
                        title TEXT NOT NULL,
                        url TEXT NOT NULL,
                        resourceType TEXT NOT NULL,
                        channelName TEXT NOT NULL,
                        lastOpenedAtMs INTEGER NOT NULL,
                        createdAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_outbox (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        entityType TEXT NOT NULL,
                        localKey TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        payloadJson TEXT NOT NULL,
                        createdAtMs INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL,
                        lastError TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_sync_outbox_entityType_localKey ON sync_outbox (entityType, localKey)"
                )
            }
        }

        /**
         * Safe non-destructive migration from schema v5 -> v6.
         * Preserves all 13 existing tables and adds purpose-aware session & intervention analytics tables.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS purpose_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionKey TEXT NOT NULL,
                        purposeCategory TEXT NOT NULL,
                        sessionName TEXT NOT NULL,
                        allowedPackagesCsv TEXT NOT NULL,
                        allowedAppLabelsCsv TEXT NOT NULL,
                        plannedSeconds INTEGER NOT NULL,
                        actualFocusedSeconds INTEGER NOT NULL,
                        breakSeconds INTEGER NOT NULL,
                        temporaryAccessSeconds INTEGER NOT NULL,
                        distractionSeconds INTEGER NOT NULL,
                        interventionCount INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        breakPlanMode TEXT NOT NULL,
                        linkedTaskId INTEGER,
                        startedAtMs INTEGER NOT NULL,
                        endedAtMs INTEGER NOT NULL,
                        dateEpochDay INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_purpose_sessions_sessionKey ON purpose_sessions (sessionKey)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS intervention_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionKey TEXT NOT NULL,
                        eventType TEXT NOT NULL,
                        packageName TEXT NOT NULL,
                        appLabel TEXT NOT NULL,
                        declaredPurpose TEXT NOT NULL,
                        actionTaken TEXT NOT NULL,
                        plannedSeconds INTEGER NOT NULL,
                        extensionSeconds INTEGER NOT NULL,
                        actualSeconds INTEGER NOT NULL,
                        timestampMs INTEGER NOT NULL,
                        dateEpochDay INTEGER NOT NULL
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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
