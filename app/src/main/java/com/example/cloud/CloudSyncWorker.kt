package com.example.cloud

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.BrainBufferRepository
import java.util.concurrent.TimeUnit

/**
 * WorkManager background worker for reliable offline-first Supabase sync with automatic retry.
 *
 * Guarantees:
 * - Runs only when network connectivity is available ([NetworkType.CONNECTED]).
 * - Uses exponential backoff on transient failures.
 * - Shares [SupabaseSyncManager]'s mutex so background and foreground sync never overlap.
 * - Persists refreshed tokens back to Keystore-encrypted DataStore storage.
 */
class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!SupabaseConfig.isConfigured) return Result.success()

        val repository = BrainBufferRepository.getInstance(applicationContext)
        val session = repository.getAuthSessionOnce()
        if (!session.isSignedIn) return Result.success()

        val outcome = repository.syncManager.syncAll(
            currentSession = session,
            usageReportForOptInSummary = null
        )

        outcome.refreshedSession?.let { repository.saveAuthSession(it) }

        return when {
            outcome.alreadyRunning -> Result.success()
            outcome.success -> {
                repository.updateLastSyncedAtMs(System.currentTimeMillis())
                Result.success()
            }
            outcome.requiresReauth -> {
                // Do not infinitely retry an invalidated refresh token
                Result.failure()
            }
            else -> Result.retry()
        }
    }
}

/**
 * Deduplicated WorkManager scheduler for offline-first cloud sync retries.
 */
object CloudSyncScheduler {
    private const val UNIQUE_SYNC_WORK_NAME = "brain_buffer_cloud_sync"

    fun scheduleSync(context: Context, replaceExisting: Boolean = false) {
        if (!SupabaseConfig.isConfigured) return
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L,
                    TimeUnit.SECONDS
                )
                .build()

            val policy = if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(UNIQUE_SYNC_WORK_NAME, policy, request)
        }
    }

    fun cancelSync(context: Context) {
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_SYNC_WORK_NAME)
        }
    }
}
