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
import com.example.data.local.BrainBufferDatabase
import com.example.data.local.UserPreferencesStore
import com.example.model.NotificationPreferences
import com.example.notifications.BrainBufferNotificationManager
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Battery-friendly periodic WorkManager worker for real daily study reminders,
 * Planner task due alerts, and upcoming exam countdown notifications.
 *
 * Guarantees:
 * - Strictly respects [NotificationPreferences.quietHoursEnabled] (never notifies during quiet hours).
 * - Never notifies while a Focus session is actively running.
 * - Deduplicates to at most one study reminder and one deadline/exam reminder per calendar day.
 * - Automatically cancels when the user disables reminders.
 */
class StudyReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefsStore = UserPreferencesStore(applicationContext)
        val prefs = prefsStore.getPreferencesOnce()
        val notifPrefs = prefs.notificationPrefs

        if (!prefs.onboardingCompleted || !prefs.notificationsEnabled) {
            return Result.success()
        }
        if (!BrainBufferNotificationManager.hasNotificationPermission(applicationContext)) {
            return Result.success()
        }

        val nowHour = LocalTime.now().hour
        if (BrainBufferNotificationManager.isWithinQuietHours(notifPrefs, nowHour)) {
            return Result.success()
        }

        val activeTimer = prefsStore.getTimerSnapshotOnce()
        if (activeTimer.runningSinceEpochMs != null) {
            return Result.success()
        }

        val todayEpoch = LocalDate.now().toEpochDay()
        val db = BrainBufferDatabase.getInstance(applicationContext)
        val statePrefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        BrainBufferNotificationManager.ensureChannels(applicationContext)

        // 1. Upcoming Tasks & Exam Countdowns
        if (notifPrefs.upcomingTasksEnabled || notifPrefs.examCountdownsEnabled) {
            val lastDeadlineDay = statePrefs.getLong(KEY_LAST_DEADLINE_DAY, -1L)
            if (lastDeadlineDay != todayEpoch && nowHour in 8..21) {
                val pendingDueToday = if (notifPrefs.upcomingTasksEnabled) {
                    db.studyTaskDao().observeAll().first().count { !it.isCompleted && it.dueDateEpochDay == todayEpoch }
                } else 0

                val upcomingExams = if (notifPrefs.examCountdownsEnabled) {
                    db.subjectDao().observeAll().first().filter { subj ->
                        val exam = subj.examDateEpochDay
                        exam != null && exam in todayEpoch..(todayEpoch + 3L)
                    }.sortedBy { it.examDateEpochDay }
                } else emptyList()

                val title: String?
                val body: String?
                val channel: String
                when {
                    upcomingExams.isNotEmpty() -> {
                        val nextExam = upcomingExams.first()
                        val daysLeft = (nextExam.examDateEpochDay!! - todayEpoch).toInt()
                        title = if (daysLeft == 0) "Exam Day: ${nextExam.name}" else "${nextExam.name} exam in ${daysLeft}d"
                        body = if (pendingDueToday > 0) {
                            "You also have $pendingDueToday task${if (pendingDueToday == 1) "" else "s"} planned for today."
                        } else {
                            "A calm focused revision block today will help lock it in."
                        }
                        channel = BrainBufferNotificationManager.CHANNEL_EXAM_COUNTDOWNS
                    }
                    pendingDueToday > 0 -> {
                        title = "$pendingDueToday planned task${if (pendingDueToday == 1) "" else "s"} for today"
                        body = "Open Planner to pick your next step and launch Focus in one tap."
                        channel = BrainBufferNotificationManager.CHANNEL_UPCOMING_TASKS
                    }
                    else -> {
                        title = null
                        body = null
                        channel = BrainBufferNotificationManager.CHANNEL_UPCOMING_TASKS
                    }
                }

                if (title != null && body != null) {
                    val posted = postReminderNotification(
                        id = BrainBufferNotificationManager.ID_TASK_REMINDER,
                        channelId = channel,
                        title = title,
                        body = body,
                        hidePrivate = notifPrefs.hidePrivateNotesOnLockScreen,
                        openRoute = "planner"
                    )
                    if (posted) {
                        statePrefs.edit().putLong(KEY_LAST_DEADLINE_DAY, todayEpoch).apply()
                    }
                }
            }
        }

        // 2. Daily Focus Rhythm Reminder (respects preferred study time window)
        if (notifPrefs.studyRemindersEnabled) {
            val lastStudyDay = statePrefs.getLong(KEY_LAST_STUDY_DAY, -1L)
            val targetHourRange = when (prefs.preferredStudyTime.name) {
                "MORNING" -> 8..11
                "AFTERNOON" -> 13..16
                "EVENING" -> 17..20
                "NIGHT" -> 20..22
                else -> 16..20
            }
            if (lastStudyDay != todayEpoch && nowHour in targetHourRange) {
                val todayFocusedSeconds = db.focusSessionDao().observeAll().first()
                    .filter { it.dateEpochDay == todayEpoch }
                    .sumOf { it.durationSeconds }
                val todayFocusedMinutes = todayFocusedSeconds / 60

                if (todayFocusedMinutes < prefs.dailyTargetMinutes) {
                    val remaining = (prefs.dailyTargetMinutes - todayFocusedMinutes).coerceAtLeast(5)
                    val title = if (todayFocusedMinutes == 0) {
                        "Ready for your first focus block?"
                    } else {
                        "${remaining}m left toward today's focus goal"
                    }
                    val body = "Start a calm ${prefs.preferredFocusMinutes}-minute Focus Guard session when you're ready."
                    val posted = postReminderNotification(
                        id = BrainBufferNotificationManager.ID_STUDY_REMINDER,
                        channelId = BrainBufferNotificationManager.CHANNEL_STUDY_REMINDERS,
                        title = title,
                        body = body,
                        hidePrivate = notifPrefs.hidePrivateNotesOnLockScreen,
                        openRoute = "focus"
                    )
                    if (posted) {
                        statePrefs.edit().putLong(KEY_LAST_STUDY_DAY, todayEpoch).apply()
                    }
                }
            }
        }

        return Result.success()
    }

    private fun postReminderNotification(
        id: Int,
        channelId: String,
        title: String,
        body: String,
        hidePrivate: Boolean,
        openRoute: String
    ): Boolean {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BrainBufferNotificationManager.EXTRA_OPEN_ROUTE, openRoute)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicVersion = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_brain_buffer)
            .setContentTitle("BRAIN BUFFER")
            .setContentText("Focus reminder waiting")
            .build()

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_brain_buffer)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setVisibility(
                if (hidePrivate) NotificationCompat.VISIBILITY_PRIVATE
                else NotificationCompat.VISIBILITY_PUBLIC
            )
            .setPublicVersion(publicVersion)
            .setColor(0xFF7B5CFA.toInt())
            .build()

        return BrainBufferNotificationManager.notifySafely(applicationContext, id, notification)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "brain_buffer_study_reminder_worker"
        private const val PREFS_NAME = "bb_study_reminder_state"
        private const val KEY_LAST_STUDY_DAY = "last_study_day"
        private const val KEY_LAST_DEADLINE_DAY = "last_deadline_day"

        fun syncSchedule(
            context: Context,
            notificationsEnabled: Boolean,
            notifPrefs: NotificationPreferences
        ) {
            val shouldRun = notificationsEnabled &&
                (notifPrefs.studyRemindersEnabled || notifPrefs.upcomingTasksEnabled || notifPrefs.examCountdownsEnabled)
            if (!shouldRun) {
                cancel(context)
                return
            }
            val request = PeriodicWorkRequestBuilder<StudyReminderWorker>(3L, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
