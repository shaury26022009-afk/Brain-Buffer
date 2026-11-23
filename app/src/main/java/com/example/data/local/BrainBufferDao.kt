package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY createdAtMs ASC")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY createdAtMs ASC")
    suspend fun getAllOnce(): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SubjectEntity): Long

    @Update
    suspend fun update(entity: SubjectEntity)

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM subjects")
    suspend fun deleteAll()
}

@Dao
interface StudyTaskDao {
    @Query("SELECT * FROM study_tasks ORDER BY isCompleted ASC, dueDateEpochDay ASC, createdAtMs DESC")
    fun observeAll(): Flow<List<StudyTaskEntity>>

    @Query("SELECT * FROM study_tasks ORDER BY isCompleted ASC, dueDateEpochDay ASC, createdAtMs DESC")
    suspend fun getAllOnce(): List<StudyTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: StudyTaskEntity): Long

    @Update
    suspend fun update(entity: StudyTaskEntity)

    @Query("DELETE FROM study_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE study_tasks SET subjectId = NULL WHERE subjectId = :subjectId")
    suspend fun clearSubjectReference(subjectId: Long)

    @Query("DELETE FROM study_tasks")
    suspend fun deleteAll()
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY completedAtMs DESC")
    fun observeAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY completedAtMs DESC")
    suspend fun getAllOnce(): List<FocusSessionEntity>

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE sessionKey = :sessionKey AND sessionKey != ''")
    suspend fun countBySessionKey(sessionKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FocusSessionEntity): Long

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAll()
}

@Dao
interface GardenPlantDao {
    @Query("SELECT * FROM garden_plants ORDER BY id ASC")
    fun observeAll(): Flow<List<GardenPlantEntity>>

    @Query("SELECT * FROM garden_plants ORDER BY id ASC")
    suspend fun getAllOnce(): List<GardenPlantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GardenPlantEntity): Long

    @Update
    suspend fun update(entity: GardenPlantEntity)

    @Query("UPDATE garden_plants SET isActive = CASE WHEN id = :activeId THEN 1 ELSE 0 END")
    suspend fun setActivePlant(activeId: Long)

    @Query("DELETE FROM garden_plants")
    suspend fun deleteAll()
}

@Dao
interface MindNoteDao {
    @Query("SELECT * FROM mind_notes ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<MindNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MindNoteEntity): Long

    @Query("DELETE FROM mind_notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM mind_notes")
    suspend fun deleteAll()
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY endedAtMs DESC")
    fun observeAll(): Flow<List<ActivityLogEntity>>

    @Query("SELECT COUNT(*) FROM activity_logs WHERE sessionKey = :sessionKey AND sessionKey != ''")
    suspend fun countBySessionKey(sessionKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ActivityLogEntity): Long

    @Query("DELETE FROM activity_logs")
    suspend fun deleteAll()
}

@Dao
interface StrictFocusPresetDao {
    @Query("SELECT * FROM strict_focus_presets ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<StrictFocusPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: StrictFocusPresetEntity): Long

    @Update
    suspend fun update(entity: StrictFocusPresetEntity)

    @Query("DELETE FROM strict_focus_presets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM strict_focus_presets")
    suspend fun deleteAll()
}

@Dao
interface CustomActivityDao {
    @Query("SELECT * FROM custom_activities ORDER BY id ASC")
    fun observeAll(): Flow<List<CustomActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CustomActivityEntity): Long

    @Query("DELETE FROM custom_activities WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM custom_activities")
    suspend fun deleteAll()
}

@Dao
interface AppCategoryMappingDao {
    @Query("SELECT * FROM app_category_mappings ORDER BY appLabel ASC")
    fun observeAll(): Flow<List<AppCategoryMappingEntity>>

    @Query("SELECT * FROM app_category_mappings ORDER BY appLabel ASC")
    suspend fun getAllOnce(): List<AppCategoryMappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppCategoryMappingEntity)

    @Query("DELETE FROM app_category_mappings WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("DELETE FROM app_category_mappings")
    suspend fun deleteAll()
}

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions WHERE sessionKey = :sessionKey LIMIT 1")
    suspend fun getBySessionKey(sessionKey: String): StudySessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StudySessionEntity): Long

    @Query("DELETE FROM study_sessions")
    suspend fun deleteAll()
}

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps ORDER BY appLabel ASC")
    fun observeAll(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps ORDER BY appLabel ASC")
    suspend fun getAllOnce(): List<BlockedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BlockedAppEntity)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("DELETE FROM blocked_apps")
    suspend fun deleteAll()
}

@Dao
interface StudyResourceDao {
    @Query("SELECT * FROM study_resources ORDER BY lastOpenedAtMs DESC, createdAtMs DESC")
    fun observeAll(): Flow<List<StudyResourceEntity>>

    @Query("SELECT * FROM study_resources ORDER BY lastOpenedAtMs DESC, createdAtMs DESC")
    suspend fun getAllOnce(): List<StudyResourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: StudyResourceEntity): Long

    @Query("UPDATE study_resources SET lastOpenedAtMs = :openedAtMs WHERE id = :id")
    suspend fun markOpened(id: Long, openedAtMs: Long)

    @Query("DELETE FROM study_resources WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM study_resources")
    suspend fun deleteAll()
}

@Dao
interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox ORDER BY createdAtMs ASC")
    suspend fun getPendingOnce(): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entity: SyncOutboxEntity): Long

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE sync_outbox SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("DELETE FROM sync_outbox")
    suspend fun deleteAll()
}

@Dao
interface PurposeSessionDao {
    @Query("SELECT * FROM purpose_sessions ORDER BY endedAtMs DESC")
    fun observeAll(): Flow<List<PurposeSessionEntity>>

    @Query("SELECT * FROM purpose_sessions ORDER BY endedAtMs DESC")
    suspend fun getAllOnce(): List<PurposeSessionEntity>

    @Query("SELECT COUNT(*) FROM purpose_sessions WHERE sessionKey = :sessionKey AND sessionKey != ''")
    suspend fun countBySessionKey(sessionKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PurposeSessionEntity): Long

    @Query("DELETE FROM purpose_sessions")
    suspend fun deleteAll()
}

@Dao
interface InterventionEventDao {
    @Query("SELECT * FROM intervention_events ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<InterventionEventEntity>>

    @Query("SELECT * FROM intervention_events ORDER BY timestampMs DESC")
    suspend fun getAllOnce(): List<InterventionEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InterventionEventEntity): Long

    @Query("DELETE FROM intervention_events")
    suspend fun deleteAll()
}


