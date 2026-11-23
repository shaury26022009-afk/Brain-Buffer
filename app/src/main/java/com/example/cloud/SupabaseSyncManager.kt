package com.example.cloud

import com.example.data.local.BlockedAppEntity
import com.example.data.local.BrainBufferDatabase
import com.example.data.local.FocusSessionEntity
import com.example.data.local.StudyResourceEntity
import com.example.data.local.StudyTaskEntity
import com.example.data.local.SubjectEntity
import com.example.data.local.SyncOutboxEntity
import com.example.data.local.UserPreferencesStore
import com.example.model.PhoneUsageReport
import com.example.model.SupabaseAuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

data class SyncOutcome(
    val success: Boolean,
    val pushedCount: Int = 0,
    val pulledCount: Int = 0,
    val refreshedSession: SupabaseAuthSession? = null,
    val errorMessage: String? = null,
    val alreadyRunning: Boolean = false,
    val requiresReauth: Boolean = false
)

/**
 * Offline-first Supabase PostgREST synchronization engine.
 *
 * Guarantees:
 * 1. Local Room database is always the fast UI source of truth.
 * 2. A shared [Mutex] prevents duplicate or overlapping sync runs across UI & WorkManager.
 * 3. Pull-before-push for `profiles` and `focus_settings` prevents a new device or fresh
 *    sign-in from overwriting valid cloud data with blank local defaults.
 * 4. Soft-delete tombstones (`is_deleted = true`) propagate deletions across devices and
 *    prevent remotely deleted records from reappearing.
 * 5. Raw per-minute phone usage events and notification text are NEVER uploaded.
 *    Only aggregated `daily_usage_summaries` are pushed when the user explicitly opts in.
 */
class SupabaseSyncManager(
    private val database: BrainBufferDatabase,
    private val preferencesStore: UserPreferencesStore,
    private val authClient: SupabaseAuthClient = SupabaseAuthClient(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val syncMutex = Mutex()

    private class UnauthorizedSyncException(message: String) : Exception(message)

    private data class RemotePullResult(
        val pulledCount: Int,
        val remoteDeletedKeys: Set<Pair<String, String>>,
        val remoteKnownKeys: Set<Pair<String, String>>
    )

    suspend fun enqueueUpsert(entityType: String, localKey: String, payload: JSONObject) {
        database.syncOutboxDao().enqueue(
            SyncOutboxEntity(
                entityType = entityType,
                localKey = localKey,
                operation = "UPSERT",
                payloadJson = payload.toString(),
                createdAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun enqueueDelete(entityType: String, localKey: String, payload: JSONObject = JSONObject()) {
        database.syncOutboxDao().enqueue(
            SyncOutboxEntity(
                entityType = entityType,
                localKey = localKey,
                operation = "DELETE",
                payloadJson = payload.toString(),
                createdAtMs = System.currentTimeMillis()
            )
        )
    }

    /**
     * Full bidirectional offline-first sync:
     * 1. Acquire non-blocking mutex so foreground & WorkManager never overlap.
     * 2. Refresh expired access token automatically.
     * 3. Pull-before-push `profiles` so blank local defaults never clobber valid cloud profile fields.
     * 4. Bidirectional `focus_settings` pull/push (Focus Guard, Notification Prefs, Family Controls).
     * 5. Flush queued local outbox mutations (using `is_deleted = true` tombstones for deletes).
     * 6. Pull remote collections, apply tombstones (remote deletes), and merge newer remote updates into Room.
     * 7. Push remaining local-only unsynced records (never resurrecting tombstoned or pending-delete rows).
     * 8. Optionally push aggregated `daily_usage_summaries` only when explicitly opted in.
     */
    suspend fun syncAll(
        currentSession: SupabaseAuthSession,
        usageReportForOptInSummary: PhoneUsageReport? = null
    ): SyncOutcome = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext SyncOutcome(
                success = false,
                errorMessage = "Supabase credentials not configured yet."
            )
        }
        if (!currentSession.isSignedIn) {
            return@withContext SyncOutcome(success = false, errorMessage = "Not signed in.")
        }
        if (!syncMutex.tryLock()) {
            return@withContext SyncOutcome(success = true, alreadyRunning = true)
        }

        var refreshedSession: SupabaseAuthSession? = null
        var pushed = 0
        var pulled = 0
        var hadAnyFailure = false

        try {
            // 1. Refresh session if expired or close to expiry
            var activeSession = currentSession
            if (activeSession.isExpired() && activeSession.refreshToken.isNotBlank()) {
                when (val refreshRes = authClient.refreshSession(activeSession.refreshToken)) {
                    is SupabaseAuthResult.Success -> {
                        activeSession = refreshRes.data
                        refreshedSession = refreshRes.data
                        preferencesStore.saveAuthSession(refreshRes.data)
                    }
                    is SupabaseAuthResult.Error -> {
                        return@withContext SyncOutcome(
                            success = false,
                            errorMessage = refreshRes.message,
                            requiresReauth = refreshRes.isUnauthorized
                        )
                    }
                }
            }

            val lastSyncedAtMs = preferencesStore.getLastSyncedAtMsOnce()

            // 2. Pull-before-push user profile (prevents cloud profile data loss on first login / new device)
            val (profilePushed, profilePulled, profileOk) = syncProfileSafely(activeSession)
            pushed += profilePushed
            pulled += profilePulled
            if (!profileOk) hadAnyFailure = true

            // 3. Bidirectional sync for focus_settings (Focus Guard, notifications, family controls)
            val (settingsPushed, settingsPulled, settingsOk) = syncFocusSettingsSafely(activeSession)
            pushed += settingsPushed
            pulled += settingsPulled
            if (!settingsOk) hadAnyFailure = true

            // 4. Flush queued local outbox operations first (deletes become remote tombstones)
            val pending = database.syncOutboxDao().getPendingOnce()
            for (item in pending) {
                val ok = processOutboxItem(item, activeSession)
                if (ok) {
                    database.syncOutboxDao().deleteById(item.id)
                    pushed++
                } else {
                    hadAnyFailure = true
                    database.syncOutboxDao().markFailed(item.id, "Retry scheduled")
                }
            }

            // Snapshot any outbox items that failed so pull/batch-push never overwrite or resurrect them
            val remainingPendingOps: Map<Pair<String, String>, String> = database.syncOutboxDao()
                .getPendingOnce()
                .associate { (it.entityType to it.localKey) to it.operation }

            // 5. Pull remote collections, apply tombstones (remote deletions), and merge newer remote edits
            val pullResult = pullRemoteCollections(
                session = activeSession,
                remainingPendingOps = remainingPendingOps,
                lastSyncedAtMs = lastSyncedAtMs
            )
            pulled += pullResult.pulledCount

            // 6. Push remaining local-only unsynced records (excluding tombstoned & pending-delete items)
            val (batchPushed, batchOk) = pushLocalCollectionsBatch(
                session = activeSession,
                remainingPendingOps = remainingPendingOps,
                remoteDeletedKeys = pullResult.remoteDeletedKeys,
                remoteKnownKeys = pullResult.remoteKnownKeys
            )
            pushed += batchPushed
            if (!batchOk) hadAnyFailure = true

            // 7. Optional opt-in daily usage summary push (ONLY aggregated daily totals, never raw logs)
            val prefs = preferencesStore.getPreferencesOnce()
            if (prefs.cloudUsageSummarySyncEnabled &&
                usageReportForOptInSummary != null &&
                usageReportForOptInSummary.permissionGranted &&
                usageReportForOptInSummary.dailyTrend.isNotEmpty()
            ) {
                val rows = JSONArray()
                usageReportForOptInSummary.dailyTrend.takeLast(14).forEach { day ->
                    rows.put(
                        JSONObject().apply {
                            put("user_id", activeSession.userId)
                            put("date_epoch_day", day.dateEpochDay)
                            put("total_screen_minutes", (day.totalTimeMs / 60_000L).toInt())
                            put("study_minutes", (day.studyTimeMs / 60_000L).toInt())
                            put("distracting_minutes", (day.distractingTimeMs / 60_000L).toInt())
                        }
                    )
                }
                if (postgrestUpsertArray(
                        "daily_usage_summaries?on_conflict=user_id,date_epoch_day",
                        rows,
                        activeSession.accessToken
                    )
                ) {
                    pushed += rows.length()
                } else {
                    hadAnyFailure = true
                }
            }

            SyncOutcome(
                success = !hadAnyFailure,
                pushedCount = pushed,
                pulledCount = pulled,
                refreshedSession = refreshedSession,
                errorMessage = if (hadAnyFailure) {
                    "Some changes could not sync yet. Automatic retry is scheduled."
                } else {
                    null
                }
            )
        } catch (authEx: UnauthorizedSyncException) {
            SyncOutcome(
                success = false,
                pushedCount = pushed,
                pulledCount = pulled,
                refreshedSession = refreshedSession,
                errorMessage = authEx.message ?: "Your session expired. Please sign in again.",
                requiresReauth = true
            )
        } catch (e: Exception) {
            SyncOutcome(
                success = false,
                pushedCount = pushed,
                pulledCount = pulled,
                refreshedSession = refreshedSession,
                errorMessage = e.message ?: "Cloud sync interrupted. Changes remain safely queued offline."
            )
        } finally {
            syncMutex.unlock()
        }
    }

    /**
     * Priority 1: Pull remote `profiles` row BEFORE pushing local profile data.
     * Guarantees a first login or new device hydrates existing cloud fields and never
     * overwrites valid cloud values with blank local defaults.
     */
    private suspend fun syncProfileSafely(session: SupabaseAuthSession): Triple<Int, Int, Boolean> {
        val encodedUid = encodeQueryValue(session.userId)
        val remoteRows = postgrestSelect("profiles?id=eq.$encodedUid&limit=1", session.accessToken)
            ?: return Triple(0, 0, false)

        val remoteObj = if (remoteRows.length() > 0) remoteRows.optJSONObject(0) else null
        val isFirstHydration = !preferencesStore.isProfileHydratedForUser(session.userId)
        val localUpdatedAtMs = preferencesStore.getProfileUpdatedAtMsOnce()
        val remoteUpdatedAtMs = parseIsoTimestampMs(remoteObj?.optString("updated_at", ""))

        val remoteDisplayName = remoteObj?.optString("display_name", "").orEmpty().trim()
        val remoteAvatarKey = remoteObj?.optString("avatar_key", "").orEmpty().trim()
        val remoteStudyLevel = remoteObj?.optString("study_level", "").orEmpty().trim()
        val remoteCourse = remoteObj?.optString("course_or_exam_name", "").orEmpty().trim()
        val remoteOnboardingCompleted = remoteObj?.optBoolean("onboarding_completed", false) ?: false
        val remotePreferencesJson = remoteObj?.optJSONObject("preferences")

        var pulled = 0
        var pushed = 0

        if (remoteObj != null) {
            val currentLocal = preferencesStore.getPreferencesOnce()
            val preferRemote = isFirstHydration || remoteUpdatedAtMs > localUpdatedAtMs
            val hasBlankLocalToFill =
                (currentLocal.studentName.isBlank() && remoteDisplayName.isNotBlank()) ||
                    (currentLocal.courseOrExamName.isBlank() && remoteCourse.isNotBlank()) ||
                    (!currentLocal.onboardingCompleted && remoteOnboardingCompleted)

            if (preferRemote || hasBlankLocalToFill) {
                preferencesStore.applyRemoteProfile(
                    displayName = remoteDisplayName,
                    avatarKey = remoteAvatarKey,
                    studyLevelName = remoteStudyLevel,
                    courseOrExamName = remoteCourse,
                    onboardingCompleted = remoteOnboardingCompleted,
                    preferencesJson = remotePreferencesJson,
                    remoteUpdatedAtMs = remoteUpdatedAtMs,
                    preferRemote = preferRemote
                )
                if (preferRemote) pulled++
            }
        }

        preferencesStore.markProfileHydratedForUser(
            userId = session.userId,
            syncedAtMs = maxOf(remoteUpdatedAtMs, localUpdatedAtMs, System.currentTimeMillis())
        )

        // Decide whether we should push merged profile back to cloud
        val mergedPrefs = preferencesStore.getPreferencesOnce()
        val safeDisplayName = mergedPrefs.studentName.trim().ifBlank { remoteDisplayName }
        val safeAvatarKey = mergedPrefs.avatarKey.trim().ifBlank { remoteAvatarKey.ifBlank { "spark" } }
        val safeCourse = mergedPrefs.courseOrExamName.trim().ifBlank { remoteCourse }
        val safeOnboarding = mergedPrefs.onboardingCompleted || remoteOnboardingCompleted

        val localFillsRemoteGap = remoteObj != null && (
            (remoteDisplayName.isBlank() && safeDisplayName.isNotBlank()) ||
                (remoteCourse.isBlank() && safeCourse.isNotBlank()) ||
                (!remoteOnboardingCompleted && safeOnboarding)
            )
        val shouldPushProfile = remoteObj == null ||
            (!isFirstHydration && localUpdatedAtMs > remoteUpdatedAtMs) ||
            localFillsRemoteGap

        if (!shouldPushProfile) {
            return Triple(pushed, pulled, true)
        }

        val profilePayload = JSONObject().apply {
            put("id", session.userId)
            put("email", session.email)
            put("display_name", safeDisplayName)
            put("avatar_key", safeAvatarKey)
            put("study_level", mergedPrefs.studyLevel.name)
            put("course_or_exam_name", safeCourse)
            put("onboarding_completed", safeOnboarding)
            put("preferences", JSONObject().apply {
                put("preferredFocusMinutes", mergedPrefs.preferredFocusMinutes)
                put("shortBreakMinutes", mergedPrefs.shortBreakMinutes)
                put("longBreakMinutes", mergedPrefs.longBreakMinutes)
                put("dailyTargetMinutes", mergedPrefs.dailyTargetMinutes)
                put("themeMode", mergedPrefs.themeMode.name)
                put("performanceOverride", mergedPrefs.performanceOverride.name)
                put("cloudUsageSummarySyncEnabled", mergedPrefs.cloudUsageSummarySyncEnabled)
            })
        }
        val ok = postgrestUpsert("profiles?on_conflict=id", profilePayload, session.accessToken)
        if (ok) pushed++
        return Triple(pushed, pulled, ok)
    }

    /**
     * Priority 2: Complete bidirectional `focus_settings` push and pull.
     * Syncs Focus Guard settings, notification preferences, and transparent family controls.
     */
    private suspend fun syncFocusSettingsSafely(session: SupabaseAuthSession): Triple<Int, Int, Boolean> {
        val encodedUid = encodeQueryValue(session.userId)
        val remoteRows = postgrestSelect("focus_settings?user_id=eq.$encodedUid&limit=1", session.accessToken)
            ?: return Triple(0, 0, false)

        val remoteObj = if (remoteRows.length() > 0) remoteRows.optJSONObject(0) else null
        val isFirstHydration = !preferencesStore.isFocusSettingsHydratedForUser(session.userId)
        val localUpdatedAtMs = preferencesStore.getFocusSettingsUpdatedAtMsOnce()
        val remoteUpdatedAtMs = parseIsoTimestampMs(remoteObj?.optString("updated_at", ""))

        if (remoteObj != null && (isFirstHydration || remoteUpdatedAtMs > localUpdatedAtMs)) {
            val remoteSettingsJson = remoteObj.optJSONObject("settings_json")
            val remoteNotifJson = remoteObj.optJSONObject("notification_prefs_json")
            val remoteFamilyJson = remoteObj.optJSONObject("family_controls_json")
            val hasRemotePayload = (remoteSettingsJson != null && remoteSettingsJson.length() > 0) ||
                (remoteNotifJson != null && remoteNotifJson.length() > 0) ||
                (remoteFamilyJson != null && remoteFamilyJson.length() > 0)

            if (hasRemotePayload) {
                preferencesStore.applyRemoteFocusSettings(
                    settingsJson = remoteSettingsJson,
                    notificationPrefsJson = remoteNotifJson,
                    familyControlsJson = remoteFamilyJson,
                    remoteUpdatedAtMs = remoteUpdatedAtMs
                )
                preferencesStore.markFocusSettingsHydratedForUser(
                    userId = session.userId,
                    syncedAtMs = maxOf(remoteUpdatedAtMs, System.currentTimeMillis())
                )
                return Triple(0, 1, true)
            }
        }

        // Push local focus_settings when no remote row exists yet or local is newer
        val shouldPush = remoteObj == null || localUpdatedAtMs > remoteUpdatedAtMs
        if (!shouldPush) {
            preferencesStore.markFocusSettingsHydratedForUser(session.userId)
            return Triple(0, 0, true)
        }

        val prefs = preferencesStore.getPreferencesOnce()
        val payload = JSONObject().apply {
            put("user_id", session.userId)
            put("settings_json", preferencesStore.exportFocusGuardSettingsJson(prefs.focusGuardSettings))
            put("notification_prefs_json", preferencesStore.exportNotificationPrefsJson(prefs.notificationPrefs))
            put("family_controls_json", preferencesStore.exportFamilyControlSettingsJson(prefs.familyControlSettings))
        }
        val ok = postgrestUpsert("focus_settings?on_conflict=user_id", payload, session.accessToken)
        if (ok) {
            preferencesStore.markFocusSettingsHydratedForUser(session.userId)
        }
        return Triple(if (ok) 1 else 0, 0, ok)
    }

    private fun processOutboxItem(item: SyncOutboxEntity, session: SupabaseAuthSession): Boolean {
        val json = runCatching { JSONObject(item.payloadJson) }.getOrDefault(JSONObject())
        json.put("user_id", session.userId)
        val encodedUid = encodeQueryValue(session.userId)
        val encodedKey = encodeQueryValue(item.localKey)

        return when (item.entityType) {
            "subjects" -> {
                if (item.operation == "DELETE") {
                    postgrestPatch(
                        "subjects?user_id=eq.$encodedUid&local_id=eq.$encodedKey",
                        JSONObject().put("is_deleted", true),
                        session.accessToken
                    )
                } else {
                    json.put("is_deleted", false)
                    postgrestUpsert("subjects?on_conflict=user_id,local_id", json, session.accessToken)
                }
            }
            "planner_tasks" -> {
                if (item.operation == "DELETE") {
                    postgrestPatch(
                        "planner_tasks?user_id=eq.$encodedUid&local_id=eq.$encodedKey",
                        JSONObject().put("is_deleted", true),
                        session.accessToken
                    )
                } else {
                    json.put("is_deleted", false)
                    postgrestUpsert("planner_tasks?on_conflict=user_id,local_id", json, session.accessToken)
                }
            }
            "focus_sessions" -> {
                postgrestUpsert("focus_sessions?on_conflict=user_id,session_key", json, session.accessToken)
            }
            "blocked_apps" -> {
                if (item.operation == "DELETE") {
                    postgrestDelete(
                        "blocked_apps?user_id=eq.$encodedUid&package_name=eq.$encodedKey",
                        session.accessToken
                    )
                } else {
                    postgrestUpsert("blocked_apps?on_conflict=user_id,package_name", json, session.accessToken)
                }
            }
            "study_resources" -> {
                if (item.operation == "DELETE") {
                    postgrestPatch(
                        "study_resources?user_id=eq.$encodedUid&local_id=eq.$encodedKey",
                        JSONObject().put("is_deleted", true),
                        session.accessToken
                    )
                } else {
                    json.put("is_deleted", false)
                    postgrestUpsert("study_resources?on_conflict=user_id,local_id", json, session.accessToken)
                }
            }
            else -> true
        }
    }

    /**
     * Pulls remote rows (including `is_deleted = true` tombstones), removes remotely deleted
     * records from local Room, and merges newer remote updates into existing Room rows.
     */
    private suspend fun pullRemoteCollections(
        session: SupabaseAuthSession,
        remainingPendingOps: Map<Pair<String, String>, String>,
        lastSyncedAtMs: Long
    ): RemotePullResult {
        var pulled = 0
        val remoteDeletedKeys = mutableSetOf<Pair<String, String>>()
        val remoteKnownKeys = mutableSetOf<Pair<String, String>>()
        val encodedUid = encodeQueryValue(session.userId)

        // 1. Pull subjects (including soft-delete tombstones)
        val localSubjectsById = database.subjectDao().getAllOnce().associateBy { it.id }
        postgrestSelect("subjects?user_id=eq.$encodedUid", session.accessToken)?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val localId = obj.optLong("local_id", 0L)
                if (localId <= 0L) continue
                val key = "subjects" to localId.toString()
                val pendingOp = remainingPendingOps[key]
                val isDeleted = obj.optBoolean("is_deleted", false)

                if (isDeleted) {
                    remoteDeletedKeys += key
                    if (pendingOp != "UPSERT" && localId in localSubjectsById) {
                        database.studyTaskDao().clearSubjectReference(localId)
                        database.subjectDao().deleteById(localId)
                        pulled++
                    }
                    continue
                }

                remoteKnownKeys += key
                if (pendingOp != null) continue

                val remoteUpdatedAtMs = parseIsoTimestampMs(obj.optString("updated_at", ""))
                val existing = localSubjectsById[localId]
                val incoming = SubjectEntity(
                    id = localId,
                    name = obj.optString("name", "Subject"),
                    colorHex = obj.optLong("color_hex", 0xFF7B5CFA),
                    iconKey = obj.optString("icon_key", "book"),
                    targetHoursPerWeek = obj.optDouble("target_hours_per_week", 4.0).toFloat(),
                    examDateEpochDay = if (obj.isNull("exam_date_epoch_day")) null else obj.optLong("exam_date_epoch_day"),
                    createdAtMs = existing?.createdAtMs ?: System.currentTimeMillis()
                )
                if (existing == null) {
                    database.subjectDao().insert(incoming)
                    pulled++
                } else if (shouldApplyRemoteUpdate(remoteUpdatedAtMs, lastSyncedAtMs) && incoming != existing) {
                    database.subjectDao().update(incoming)
                    pulled++
                }
            }
        }

        // 2. Pull planner tasks (including soft-delete tombstones)
        val localTasksById = database.studyTaskDao().getAllOnce().associateBy { it.id }
        postgrestSelect("planner_tasks?user_id=eq.$encodedUid", session.accessToken)?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val localId = obj.optLong("local_id", 0L)
                if (localId <= 0L) continue
                val key = "planner_tasks" to localId.toString()
                val pendingOp = remainingPendingOps[key]
                val isDeleted = obj.optBoolean("is_deleted", false)

                if (isDeleted) {
                    remoteDeletedKeys += key
                    if (pendingOp != "UPSERT" && localId in localTasksById) {
                        database.studyTaskDao().deleteById(localId)
                        pulled++
                    }
                    continue
                }

                remoteKnownKeys += key
                if (pendingOp != null) continue

                val remoteUpdatedAtMs = parseIsoTimestampMs(obj.optString("updated_at", ""))
                val existing = localTasksById[localId]
                val incoming = StudyTaskEntity(
                    id = localId,
                    title = obj.optString("title", "Task"),
                    subjectId = if (obj.isNull("subject_local_id")) null else obj.optLong("subject_local_id"),
                    dueDateEpochDay = obj.optLong("due_date_epoch_day", java.time.LocalDate.now().toEpochDay()),
                    dueTimeLabel = obj.optString("due_time_label", "6:00 PM"),
                    estimatedMinutes = obj.optInt("estimated_minutes", 30),
                    priority = obj.optString("priority", "MEDIUM"),
                    isCompleted = obj.optBoolean("is_completed", false),
                    completedAtMs = if (obj.isNull("completed_at_ms")) null else obj.optLong("completed_at_ms"),
                    notes = obj.optString("notes", ""),
                    createdAtMs = existing?.createdAtMs ?: System.currentTimeMillis()
                )
                if (existing == null) {
                    database.studyTaskDao().insert(incoming)
                    pulled++
                } else if (shouldApplyRemoteUpdate(remoteUpdatedAtMs, lastSyncedAtMs) && incoming != existing) {
                    database.studyTaskDao().update(incoming)
                    pulled++
                }
            }
        }

        // 3. Pull focus sessions (append-only, deduplicated by session_key)
        postgrestSelect(
            "focus_sessions?user_id=eq.$encodedUid&order=completed_at_ms.desc&limit=100",
            session.accessToken
        )?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sessionKey = obj.optString("session_key", "")
                if (sessionKey.isBlank()) continue
                remoteKnownKeys += ("focus_sessions" to sessionKey)
                if (database.focusSessionDao().countBySessionKey(sessionKey) == 0) {
                    database.focusSessionDao().insert(
                        FocusSessionEntity(
                            subjectId = if (obj.isNull("subject_local_id")) null else obj.optLong("subject_local_id"),
                            subjectName = obj.optString("subject_name", "General Study"),
                            subjectColorHex = obj.optLong("subject_color_hex", 0xFF7B5CFA),
                            durationSeconds = obj.optInt("duration_seconds", 0),
                            plannedSeconds = obj.optInt("planned_seconds", 0),
                            sessionGoal = obj.optString("session_goal", ""),
                            mode = obj.optString("mode", "FOCUS"),
                            completedAtMs = obj.optLong("completed_at_ms", System.currentTimeMillis()),
                            dateEpochDay = obj.optLong("date_epoch_day", java.time.LocalDate.now().toEpochDay()),
                            sessionKey = sessionKey
                        )
                    )
                    pulled++
                }
            }
        }

        // 4. Pull blocked apps (merge newer updates & reconcile remote deletions after initial sync)
        val localBlockedByPkg = database.blockedAppDao().getAllOnce().associateBy { it.packageName }
        postgrestSelect("blocked_apps?user_id=eq.$encodedUid", session.accessToken)?.let { arr ->
            val remotePackages = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pkg = obj.optString("package_name", "")
                if (pkg.isBlank()) continue
                remotePackages += pkg
                val key = "blocked_apps" to pkg
                remoteKnownKeys += key
                if (remainingPendingOps.containsKey(key)) continue

                val remoteUpdatedAtMs = parseIsoTimestampMs(obj.optString("updated_at", ""))
                val existing = localBlockedByPkg[pkg]
                val incoming = BlockedAppEntity(
                    packageName = pkg,
                    appLabel = obj.optString("app_label", pkg),
                    category = obj.optString("category", "OTHER"),
                    blockInNormal = obj.optBoolean("block_in_normal", false),
                    blockInHigh = obj.optBoolean("block_in_high", true),
                    blockInCustom = obj.optBoolean("block_in_custom", true),
                    isBlocked = obj.optBoolean("is_blocked", true),
                    updatedAtMs = if (remoteUpdatedAtMs > 0L) remoteUpdatedAtMs else System.currentTimeMillis()
                )
                val contentChanged = existing == null ||
                    existing.appLabel != incoming.appLabel ||
                    existing.category != incoming.category ||
                    existing.blockInNormal != incoming.blockInNormal ||
                    existing.blockInHigh != incoming.blockInHigh ||
                    existing.blockInCustom != incoming.blockInCustom ||
                    existing.isBlocked != incoming.isBlocked

                if (existing == null) {
                    database.blockedAppDao().upsert(incoming)
                    pulled++
                } else if (contentChanged && (remoteUpdatedAtMs >= existing.updatedAtMs || shouldApplyRemoteUpdate(remoteUpdatedAtMs, lastSyncedAtMs))) {
                    database.blockedAppDao().upsert(incoming)
                    pulled++
                }
            }

            // Reconcile remote deletions for blocked_apps on already-synced devices
            if (lastSyncedAtMs > 0L) {
                localBlockedByPkg.keys.forEach { localPkg ->
                    val key = "blocked_apps" to localPkg
                    if (localPkg !in remotePackages && !remainingPendingOps.containsKey(key)) {
                        database.blockedAppDao().deleteByPackage(localPkg)
                        remoteDeletedKeys += key
                        pulled++
                    }
                }
            }
        }

        // 5. Pull study resources (including soft-delete tombstones)
        val localResourcesById = database.studyResourceDao().getAllOnce().associateBy { it.id }
        postgrestSelect("study_resources?user_id=eq.$encodedUid", session.accessToken)?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val localId = obj.optLong("local_id", 0L)
                if (localId <= 0L) continue
                val key = "study_resources" to localId.toString()
                val pendingOp = remainingPendingOps[key]
                val isDeleted = obj.optBoolean("is_deleted", false)

                if (isDeleted) {
                    remoteDeletedKeys += key
                    if (pendingOp != "UPSERT" && localId in localResourcesById) {
                        database.studyResourceDao().deleteById(localId)
                        pulled++
                    }
                    continue
                }

                remoteKnownKeys += key
                if (pendingOp != null) continue

                val remoteUpdatedAtMs = parseIsoTimestampMs(obj.optString("updated_at", ""))
                val existing = localResourcesById[localId]
                val incoming = StudyResourceEntity(
                    id = localId,
                    subjectId = if (obj.isNull("subject_local_id")) null else obj.optLong("subject_local_id"),
                    title = obj.optString("title", "Study Video"),
                    url = obj.optString("url", ""),
                    resourceType = obj.optString("resource_type", "VIDEO"),
                    channelName = obj.optString("channel_name", ""),
                    lastOpenedAtMs = maxOf(
                        obj.optLong("last_opened_at_ms", 0L),
                        existing?.lastOpenedAtMs ?: 0L
                    ),
                    createdAtMs = existing?.createdAtMs ?: System.currentTimeMillis()
                )
                if (existing == null) {
                    database.studyResourceDao().insert(incoming)
                    pulled++
                } else if (shouldApplyRemoteUpdate(remoteUpdatedAtMs, lastSyncedAtMs) && incoming != existing) {
                    database.studyResourceDao().insert(incoming)
                    pulled++
                }
            }
        }

        return RemotePullResult(
            pulledCount = pulled,
            remoteDeletedKeys = remoteDeletedKeys,
            remoteKnownKeys = remoteKnownKeys
        )
    }

    /**
     * Pushes only local records not already present remotely (e.g., offline guest data created
     * before first sign-in), while strictly skipping any item with a remote tombstone or pending
     * outbox mutation so deleted records can never reappear.
     */
    private suspend fun pushLocalCollectionsBatch(
        session: SupabaseAuthSession,
        remainingPendingOps: Map<Pair<String, String>, String>,
        remoteDeletedKeys: Set<Pair<String, String>>,
        remoteKnownKeys: Set<Pair<String, String>>
    ): Pair<Int, Boolean> {
        var count = 0
        var allOk = true

        val subjects = database.subjectDao().getAllOnce().filter { s ->
            val key = "subjects" to s.id.toString()
            key !in remoteDeletedKeys && key !in remoteKnownKeys && !remainingPendingOps.containsKey(key)
        }
        if (subjects.isNotEmpty()) {
            val arr = JSONArray()
            subjects.forEach { s ->
                arr.put(JSONObject().apply {
                    put("user_id", session.userId)
                    put("local_id", s.id)
                    put("name", s.name)
                    put("color_hex", s.colorHex)
                    put("icon_key", s.iconKey)
                    put("target_hours_per_week", s.targetHoursPerWeek.toDouble())
                    if (s.examDateEpochDay != null) put("exam_date_epoch_day", s.examDateEpochDay) else put("exam_date_epoch_day", JSONObject.NULL)
                    put("is_deleted", false)
                })
            }
            if (postgrestUpsertArray("subjects?on_conflict=user_id,local_id", arr, session.accessToken)) {
                count += subjects.size
            } else {
                allOk = false
            }
        }

        val tasks = database.studyTaskDao().getAllOnce().filter { t ->
            val key = "planner_tasks" to t.id.toString()
            key !in remoteDeletedKeys && key !in remoteKnownKeys && !remainingPendingOps.containsKey(key)
        }
        if (tasks.isNotEmpty()) {
            val arr = JSONArray()
            tasks.forEach { t ->
                arr.put(JSONObject().apply {
                    put("user_id", session.userId)
                    put("local_id", t.id)
                    if (t.subjectId != null) put("subject_local_id", t.subjectId) else put("subject_local_id", JSONObject.NULL)
                    put("title", t.title)
                    put("due_date_epoch_day", t.dueDateEpochDay)
                    put("due_time_label", t.dueTimeLabel)
                    put("estimated_minutes", t.estimatedMinutes)
                    put("priority", t.priority)
                    put("is_completed", t.isCompleted)
                    if (t.completedAtMs != null) put("completed_at_ms", t.completedAtMs) else put("completed_at_ms", JSONObject.NULL)
                    put("notes", t.notes)
                    put("is_deleted", false)
                })
            }
            if (postgrestUpsertArray("planner_tasks?on_conflict=user_id,local_id", arr, session.accessToken)) {
                count += tasks.size
            } else {
                allOk = false
            }
        }

        val sessions = database.focusSessionDao().getAllOnce().take(100).filter { s ->
            val sessionKey = s.sessionKey.ifBlank { "local_${s.id}_${s.completedAtMs}" }
            val key = "focus_sessions" to sessionKey
            key !in remoteKnownKeys && !remainingPendingOps.containsKey(key)
        }
        if (sessions.isNotEmpty()) {
            val arr = JSONArray()
            sessions.forEach { s ->
                val key = s.sessionKey.ifBlank { "local_${s.id}_${s.completedAtMs}" }
                arr.put(JSONObject().apply {
                    put("user_id", session.userId)
                    put("session_key", key)
                    if (s.subjectId != null) put("subject_local_id", s.subjectId) else put("subject_local_id", JSONObject.NULL)
                    put("subject_name", s.subjectName)
                    put("subject_color_hex", s.subjectColorHex)
                    put("duration_seconds", s.durationSeconds)
                    put("planned_seconds", s.plannedSeconds)
                    put("session_goal", s.sessionGoal)
                    put("mode", s.mode)
                    put("completed_at_ms", s.completedAtMs)
                    put("date_epoch_day", s.dateEpochDay)
                })
            }
            if (postgrestUpsertArray("focus_sessions?on_conflict=user_id,session_key", arr, session.accessToken)) {
                count += sessions.size
            } else {
                allOk = false
            }
        }

        val blockedApps = database.blockedAppDao().getAllOnce().filter { b ->
            val key = "blocked_apps" to b.packageName
            key !in remoteDeletedKeys && key !in remoteKnownKeys && !remainingPendingOps.containsKey(key)
        }
        if (blockedApps.isNotEmpty()) {
            val arr = JSONArray()
            blockedApps.forEach { b ->
                arr.put(JSONObject().apply {
                    put("user_id", session.userId)
                    put("package_name", b.packageName)
                    put("app_label", b.appLabel)
                    put("category", b.category)
                    put("block_in_normal", b.blockInNormal)
                    put("block_in_high", b.blockInHigh)
                    put("block_in_custom", b.blockInCustom)
                    put("is_blocked", b.isBlocked)
                })
            }
            if (postgrestUpsertArray("blocked_apps?on_conflict=user_id,package_name", arr, session.accessToken)) {
                count += blockedApps.size
            } else {
                allOk = false
            }
        }

        val studyResources = database.studyResourceDao().getAllOnce().filter { r ->
            val key = "study_resources" to r.id.toString()
            key !in remoteDeletedKeys && key !in remoteKnownKeys && !remainingPendingOps.containsKey(key)
        }
        if (studyResources.isNotEmpty()) {
            val arr = JSONArray()
            studyResources.forEach { r ->
                arr.put(JSONObject().apply {
                    put("user_id", session.userId)
                    put("local_id", r.id)
                    if (r.subjectId != null) put("subject_local_id", r.subjectId) else put("subject_local_id", JSONObject.NULL)
                    put("title", r.title)
                    put("url", r.url)
                    put("resource_type", r.resourceType)
                    put("channel_name", r.channelName)
                    put("last_opened_at_ms", r.lastOpenedAtMs)
                    put("is_deleted", false)
                })
            }
            if (postgrestUpsertArray("study_resources?on_conflict=user_id,local_id", arr, session.accessToken)) {
                count += studyResources.size
            } else {
                allOk = false
            }
        }

        return count to allOk
    }

    private fun shouldApplyRemoteUpdate(remoteUpdatedAtMs: Long, lastSyncedAtMs: Long): Boolean {
        if (lastSyncedAtMs <= 0L) return true
        if (remoteUpdatedAtMs <= 0L) return false
        return remoteUpdatedAtMs >= lastSyncedAtMs
    }

    private fun parseIsoTimestampMs(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(raw).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
            .getOrDefault(0L)
    }

    private fun encodeQueryValue(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    private fun postgrestUpsert(pathAndQuery: String, payload: JSONObject, accessToken: String): Boolean {
        val request = Request.Builder()
            .url(SupabaseConfig.restUrl(pathAndQuery))
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (response.code == 401) throw UnauthorizedSyncException("Your session expired. Please sign in again.")
            response.isSuccessful
        }
    }

    private fun postgrestUpsertArray(pathAndQuery: String, payload: JSONArray, accessToken: String): Boolean {
        val request = Request.Builder()
            .url(SupabaseConfig.restUrl(pathAndQuery))
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (response.code == 401) throw UnauthorizedSyncException("Your session expired. Please sign in again.")
            response.isSuccessful
        }
    }

    private fun postgrestPatch(pathAndQuery: String, payload: JSONObject, accessToken: String): Boolean {
        val request = Request.Builder()
            .url(SupabaseConfig.restUrl(pathAndQuery))
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .patch(payload.toString().toRequestBody(jsonMediaType))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (response.code == 401) throw UnauthorizedSyncException("Your session expired. Please sign in again.")
            response.isSuccessful
        }
    }

    private fun postgrestDelete(pathAndQuery: String, accessToken: String): Boolean {
        val request = Request.Builder()
            .url(SupabaseConfig.restUrl(pathAndQuery))
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .delete()
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (response.code == 401) throw UnauthorizedSyncException("Your session expired. Please sign in again.")
            response.isSuccessful
        }
    }

    private fun postgrestSelect(pathAndQuery: String, accessToken: String): JSONArray? {
        val request = Request.Builder()
            .url(SupabaseConfig.restUrl(pathAndQuery))
            .addHeader("apikey", SupabaseConfig.anonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (response.code == 401) throw UnauthorizedSyncException("Your session expired. Please sign in again.")
            if (!response.isSuccessful) return@use null
            val body = response.body?.string().orEmpty()
            runCatching { JSONArray(body) }.getOrNull()
        }
    }
}
