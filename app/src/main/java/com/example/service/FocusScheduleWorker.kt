package com.example.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.BrainBufferRepository
import com.example.model.FocusGuardSettings
import com.example.notifications.BrainBufferNotificationManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Lightweight, Play-Policy-compliant WorkManager worker that evaluates the user's
 * optional Focus Guard scheduled blocking window and posts a calm reminder when
 * entering a scheduled focus window if no session is running yet.
 *
 * Zero battery overhead when scheduled blocking is turned off (work is cancelled).
 */
class FocusScheduleWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = BrainBufferRepository.getInstance(applicationContext)
        val prefs = repository.getPreferencesOnce()
        val settings = prefs.focusGuardSettings
        if (!settings.scheduledBlockingEnabled) {
            cancelSchedule(applicationContext)
            return Result.success()
        }

        val nowTime = LocalTime.now()
        val today = LocalDate.now()
        if (!isWithinScheduledWindow(settings, today.dayOfWeek, nowTime.hour)) {
            return Result.success()
        }

        val snapshot = repository.getTimerSnapshotOnce()
        if (snapshot.hasStarted && snapshot.runningSinceEpochMs != null) {
            // Focus session is already actively running
            return Result.success()
        }

        if (!prefs.notificationsEnabled ||
            !prefs.notificationPrefs.studyRemindersEnabled ||
            BrainBufferNotificationManager.isWithinQuietHours(prefs.notificationPrefs, nowTime.hour)
        ) {
            return Result.success()
        }

        BrainBufferNotificationManager.ensureChannels(applicationContext)
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BrainBufferNotificationManager.EXTRA_OPEN_ROUTE, "strict_focus")
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            4101,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val windowLabel = "${formatHour(settings.scheduleStartHour)} – ${formatHour(settings.scheduleEndHour)}"
        val notification = NotificationCompat.Builder(
            applicationContext,
            BrainBufferNotificationManager.CHANNEL_STUDY_REMINDERS
        )
            .setSmallIcon(R.drawable.ic_stat_brain_buffer)
            .setContentTitle("Scheduled Focus Guard Window")
            .setContentText("Your scheduled focus window ($windowLabel) is active. Tap to start Focus Guard.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF7B5CFA.toInt())
            .build()

        BrainBufferNotificationManager.notifySafely(
            applicationContext,
            ID_SCHEDULED_FOCUS_REMINDER,
            notification
        )
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "brain_buffer_focus_schedule_worker"
        private const val ID_SCHEDULED_FOCUS_REMINDER = 1042

        fun syncSchedule(context: Context, settings: FocusGuardSettings) {
            if (!settings.scheduledBlockingEnabled) {
                cancelSchedule(context)
                return
            }
            val request = PeriodicWorkRequestBuilder<FocusScheduleWorker>(
                15, TimeUnit.MINUTES
            ).build()

            runCatching {
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }

        fun cancelSchedule(context: Context) {
            runCatching {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
            }
        }

        fun isWithinScheduledWindow(
            settings: FocusGuardSettings,
            dayOfWeek: DayOfWeek = LocalDate.now().dayOfWeek,
            hourOfDay: Int = LocalTime.now().hour
        ): Boolean {
            if (!settings.scheduledBlockingEnabled) return false
            val dayCode = dayOfWeek.name.take(3).uppercase()
            val allowedDays = settings.scheduleDaysCsv
                .split(",")
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .toSet()
            if (allowedDays.isNotEmpty() && dayCode !in allowedDays) return false

            val start = settings.scheduleStartHour.coerceIn(0, 23)
            val end = settings.scheduleEndHour.coerceIn(0, 23)
            return if (start < end) {
                hourOfDay in start until end
            } else if (start > end) {
                hourOfDay >= start || hourOfDay < end
            } else {
                true
            }
        }

        fun formatHour(hour: Int): String {
            val h = hour.coerceIn(0, 23)
            val suffix = if (h < 12) "AM" else "PM"
            val display = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            return "$display:00 $suffix"
        }
    }
}
