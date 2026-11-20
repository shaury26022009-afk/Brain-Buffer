package com.example.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.FocusTimerEngine
import com.example.model.ActivityCategory
import com.example.model.NotificationPreferences
import com.example.model.PersistedTimerSnapshot
import com.example.model.StudyActivityType
import com.example.service.TimerNotificationActionReceiver
import java.time.LocalTime
import java.util.Collections

/**
 * Centralized notification channel, privacy, quiet-hours, BRAIN BUFFER LIVE timer,
 * completion reminder, and real Android Distraction Warning notification builder
 * for Focus Guard 3.0 (compatible with Android 10 / API 29 through Android 16 / API 36+).
 */
object BrainBufferNotificationManager {

    const val CHANNEL_ACTIVE_FOCUS = "bb_active_focus"
    const val CHANNEL_DISTRACTION_WARNINGS = "bb_distraction_warnings"
    const val CHANNEL_BREAK_REMINDERS = "bb_break_reminders"
    const val CHANNEL_ACTIVITY_REMINDERS = "bb_activity_reminders"
    const val CHANNEL_UPCOMING_TASKS = "bb_upcoming_tasks"
    const val CHANNEL_EXAM_COUNTDOWNS = "bb_exam_countdowns"
    const val CHANNEL_STUDY_REMINDERS = "bb_study_reminders"
    const val CHANNEL_COMPLETED_SESSIONS = "bb_completed_sessions"

    const val ID_ACTIVE_TIMER = 1001
    const val ID_COMPLETION = 1002
    const val ID_TASK_REMINDER = 1003
    const val ID_EXAM_REMINDER = 1004
    const val ID_STUDY_REMINDER = 1005
    const val ID_DISTRACTION_WARNING = 1006

    const val EXTRA_OPEN_ROUTE = "bb_open_route"
    const val EXTRA_OPEN_CONTROLLED_ACCESS = "bb_open_controlled_access"

    private val postedCompletionSessionKeys: MutableSet<String> =
        Collections.synchronizedSet(LinkedHashSet())

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val channels = listOf(
            NotificationChannel(
                CHANNEL_ACTIVE_FOCUS,
                "Active focus sessions (Live)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent live study & temporary activity timer with quick controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(
                CHANNEL_DISTRACTION_WARNINGS,
                "Distraction warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Gentle study reminders when switching to an unapproved app during High Focus"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_BREAK_REMINDERS,
                "Break reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Gentle reminders when your break wraps up"
            },
            NotificationChannel(
                CHANNEL_ACTIVITY_REMINDERS,
                "Activity & allowance reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders when a lecture, PDF, gaming, or social allowance finishes"
            },
            NotificationChannel(
                CHANNEL_UPCOMING_TASKS,
                "Upcoming tasks",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Calm nudges for due study tasks"
            },
            NotificationChannel(
                CHANNEL_EXAM_COUNTDOWNS,
                "Exam countdowns",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Upcoming exam milestone reminders"
            },
            NotificationChannel(
                CHANNEL_STUDY_REMINDERS,
                "Study reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Optional daily study rhythm reminders"
            },
            NotificationChannel(
                CHANNEL_COMPLETED_SESSIONS,
                "Completed sessions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Celebrations and wrap-up actions when focus blocks complete"
            }
        )

        manager.createNotificationChannels(channels)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun isWithinQuietHours(
        prefs: NotificationPreferences,
        hourOfDay: Int = LocalTime.now().hour
    ): Boolean {
        if (!prefs.quietHoursEnabled) return false
        val start = prefs.quietStartHour.coerceIn(0, 23)
        val end = prefs.quietEndHour.coerceIn(0, 23)
        return if (start == end) {
            false
        } else if (start < end) {
            hourOfDay in start until end
        } else {
            // Midnight wrap-around (e.g., 22:00 to 07:00)
            hourOfDay >= start || hourOfDay < end
        }
    }

    fun buildActiveTimerNotification(
        context: Context,
        snapshot: PersistedTimerSnapshot,
        subjectName: String,
        prefs: NotificationPreferences,
        nowEpochMs: Long = System.currentTimeMillis(),
        nowElapsedRealtimeMs: Long? = null,
        liveUpdatesEnabled: Boolean = true
    ): Notification {
        ensureChannels(context)
        val isRunning = snapshot.runningSinceEpochMs != null
        val remainingSec = FocusTimerEngine.remainingSeconds(snapshot, nowEpochMs, nowElapsedRealtimeMs)
        val elapsedSec = FocusTimerEngine.actualFocusedSeconds(snapshot, nowEpochMs, nowElapsedRealtimeMs)
        val totalSec = snapshot.totalSeconds.coerceAtLeast(1)
        val remainingFormatted = formatMinSec(remainingSec)

        val statusPrefix = if (isRunning) {
            when {
                snapshot.isSwitchedActivity -> "Allowance Active"
                snapshot.isStrictFocus -> "High Focus"
                else -> "Live"
            }
        } else {
            "Paused"
        }

        val activityDisplay = buildString {
            append(snapshot.activeActivityLabel)
            if (snapshot.approvedAppLabel.isNotBlank() &&
                !snapshot.activeActivityLabel.contains(snapshot.approvedAppLabel, ignoreCase = true)
            ) {
                append(" · ")
                append(snapshot.approvedAppLabel)
            }
        }

        val title = if (isRunning) {
            "BRAIN BUFFER LIVE"
        } else {
            "BRAIN BUFFER · Paused"
        }

        val privateDetail = buildString {
            if (subjectName.isNotBlank()) {
                append(subjectName)
                append(" — ")
            }
            append(activityDisplay)
            append(" · ")
            append(remainingFormatted)
            append(" remaining")
            if (!prefs.hidePrivateNotesOnLockScreen && snapshot.sessionGoal.isNotBlank()) {
                append(" · ")
                append(snapshot.sessionGoal)
            }
        }

        // Public lock-screen version NEVER exposes private study goals or notes.
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ACTIVE_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_brain_buffer)
            .setContentTitle("BRAIN BUFFER LIVE · $statusPrefix")
            .setContentText(
                if (isRunning) "${snapshot.activeActivityLabel} · $remainingFormatted remaining"
                else "${snapshot.activeActivityLabel} paused · $remainingFormatted left"
            )
            .setOngoing(isRunning)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(totalSec, elapsedSec.coerceIn(0, totalSec), false)
            .build()

        val openRoute = if (snapshot.isStrictFocus) "strict_focus" else "focus"
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, openRoute)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            2001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val switchActivityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, openRoute)
            putExtra(EXTRA_OPEN_CONTROLLED_ACCESS, true)
        }
        val switchActivityPendingIntent = PendingIntent.getActivity(
            context,
            2005,
            switchActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ACTIVE_FOCUS)
            .setSmallIcon(R.drawable.ic_stat_brain_buffer)
            .setContentTitle(title)
            .setContentText(privateDetail)
            .setSubText(if (snapshot.isSwitchedActivity) "Study paused · $statusPrefix" else statusPrefix)
            .setContentIntent(contentPendingIntent)
            .setOngoing(isRunning)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(
                if (prefs.hidePrivateNotesOnLockScreen) NotificationCompat.VISIBILITY_PRIVATE
                else NotificationCompat.VISIBILITY_PUBLIC
            )
            .setPublicVersion(publicNotification)
            .setProgress(totalSec, elapsedSec.coerceIn(0, totalSec), false)
            .setColor(0xFF7B5CFA.toInt())

        // Native chronometer countdown updates every second in the OS UI with zero app wakeups!
        if (isRunning && remainingSec > 0) {
            builder
                .setShowWhen(true)
                .setWhen(nowEpochMs + remainingSec * 1000L)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
        }

        // Android 16+ (API 36+) Live Updates promotion hint (safe no-op on API 29-35)
        if (Build.VERSION.SDK_INT >= 36 && liveUpdatesEnabled) {
            builder.extras.putBoolean("android.requestPromotedOngoing", true)
            builder.extras.putString("android.shortCriticalText", remainingFormatted)
        }

        // Action 1: Pause / Resume
        if (isRunning) {
            builder.addAction(
                0,
                "Pause",
                buildActionPendingIntent(context, TimerNotificationActionReceiver.ACTION_PAUSE, 3001)
            )
        } else {
            builder.addAction(
                0,
                "Resume",
                buildActionPendingIntent(context, TimerNotificationActionReceiver.ACTION_RESUME, 3002)
            )
        }

        // Action 2: Return to Study (when in temp activity) OR Switch Activity (when studying)
        if (snapshot.isSwitchedActivity) {
            builder.addAction(
                0,
                "Return to Study",
                buildActionPendingIntent(context, TimerNotificationActionReceiver.ACTION_RETURN_TO_STUDY, 3003)
            )
        } else {
            builder.addAction(
                0,
                "Switch Activity",
                switchActivityPendingIntent
            )
        }

        // Action 3: End
        builder.addAction(
            0,
            "End",
            buildActionPendingIntent(context, TimerNotificationActionReceiver.ACTION_END, 3004)
        )

        return builder.build()
    }

    /**
     * Posts a real Android Distraction Warning notification (Part 7) when optional Usage Access
     * detects switching to an unapproved app during active High Focus / Custom Focus or study allowance.
     *
     * Provides 3 direct, calm actions:
     * 1. [Return to Study]
     * 2. [Allow for 5 min]
     * 3. [Continue Anyway]
     */
    fun postDistractionWarningNotification(
        context: Context,
        snapshot: PersistedTimerSnapshot,
        subjectName: String,
        detectedPackageName: String,
        detectedAppLabel: String,
        prefs: NotificationPreferences,
        masterNotificationsEnabled: Boolean,
        hourOfDay: Int = LocalTime.now().hour,
        soundEnabled: Boolean = true,
        vibrationEnabled: Boolean = true
    ): Boolean {
        if (!masterNotificationsEnabled) return false
        if (!prefs.distractionWarningsEnabled) return false
        if (isWithinQuietHours(prefs, hourOfDay)) return false

        ensureChannels(context)

        val safeSubject = subjectName.ifBlank { "your study session" }
        val safeApp = detectedAppLabel.ifBlank { "another app" }

        val title = "BRAIN BUFFER — Study Reminder"
        val body = when (snapshot.activeActivityType) {
            StudyActivityType.STUDY_LECTURE ->
                "You planned to watch a $safeSubject lecture. You appear to have switched to $safeApp. Return to your lecture?"
            StudyActivityType.STUDY_PDF ->
                "You planned to study PDFs for $safeSubject. You appear to have switched to $safeApp. Return to your study session?"
            StudyActivityType.READING, StudyActivityType.WRITING_NOTES ->
                "You planned ${snapshot.activeActivityLabel.lowercase()} for $safeSubject. You appear to have switched to $safeApp. Return to study?"
            else ->
                "You planned to study $safeSubject. You appear to have switched to $safeApp. Would you like to return to your study session?"
        }

        val openRoute = if (snapshot.isStrictFocus) "strict_focus" else "focus"
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ROUTE, openRoute)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            2006,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicVersion = NotificationCompat.Builder(context, CHANNEL_DISTRACTION_WARNINGS)
            .setSmallIcon(R.drawable.ic_stat_brain_buffer)
            .setContentTitle(title)
            .setContentText("Would you like to return to your study session?")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val builder = NotificationCompat.Builder(context, CHANNEL_DISTRACTION_WARNINGS)
            .setSmallIcon(R.drawable.ic_stat_brain_buffer)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(
                if (prefs.hidePrivateNotesOnLockScreen) NotificationCompat.VISIBILITY_PRIVATE
                else NotificationCompat.VISIBILITY_PUBLIC
            )
            .setPublicVersion(publicVersion)
            .setColor(0xFF7B5CFA.toInt())

        if (!soundEnabled && !vibrationEnabled) {
            builder.setSilent(true)
        } else if (vibrationEnabled) {
            builder.setVibrate(longArrayOf(0L, 140L, 80L, 140L))
        }

        // Action 1: Return to Study
        builder.addAction(
            0,
            "Return to Study",
            buildActionPendingIntent(
                context = context,
                action = TimerNotificationActionReceiver.ACTION_RETURN_TO_STUDY,
                requestCode = 3201,
                sessionId = snapshot.sessionId
            )
        )

        // Action 2: Allow for 5 min (switches cleanly to a 5-min tracked allowance for that app)
        builder.addAction(
            0,
            "Allow for 5 min",
            buildActionPendingIntent(
                context = context,
                action = TimerNotificationActionReceiver.ACTION_ALLOW_5_MIN,
                requestCode = 3202,
                extraMinutes = 5,
                distractionPackage = detectedPackageName,
                distractionAppLabel = safeApp,
                sessionId = snapshot.sessionId
            )
        )

        // Action 3: Continue Anyway (dismisses and suppresses repeated alerts for this app in current session)
        builder.addAction(
            0,
            "Continue Anyway",
            buildActionPendingIntent(
                context = context,
                action = TimerNotificationActionReceiver.ACTION_CONTINUE_ANYWAY,
                requestCode = 3203,
                distractionPackage = detectedPackageName,
                distractionAppLabel = safeApp,
                sessionId = snapshot.sessionId
            )
        )

        return notifySafely(context, ID_DISTRACTION_WARNING, builder.build())
    }

    /**
     * Posts a completion or activity allowance expiration reminder with strict per-session deduplication,
     * category toggle checks, quiet-hours compliance, customizable extension duration, and sound/vibration preferences.
     */
    fun postCompletionNotification(
        context: Context,
        snapshot: PersistedTimerSnapshot,
        subjectName: String,
        prefs: NotificationPreferences,
        masterNotificationsEnabled: Boolean,
        hourOfDay: Int = LocalTime.now().hour,
        studentName: String = "",
        soundEnabled: Boolean = true,
        vibrationEnabled: Boolean = true,
        extensionMinutes: Int = 5
    ): Boolean {
        if (!masterNotificationsEnabled) return false
        if (isWithinQuietHours(prefs, hourOfDay)) return false

        val channelId = when (snapshot.activeActivityCategory) {
            ActivityCategory.BREAK -> {
                if (!prefs.breakRemindersEnabled) return false
                CHANNEL_BREAK_REMINDERS
            }
            ActivityCategory.STUDY -> {
                if (!prefs.completedSessionsEnabled) return false
                CHANNEL_COMPLETED_SESSIONS
            }
            else -> {
                if (!prefs.activityRemindersEnabled) return false
                CHANNEL_ACTIVITY_REMINDERS
            }
        }

        val dedupKey = snapshot.sessionId.ifBlank {
            "${snapshot.activeActivityLabel}_${snapshot.totalSeconds}_${snapshot.activityStartedAtEpochMs ?: 0L}"
        }
        if (!postedCompletionSessionKeys.add(dedupKey)) {
            return false
        }
        if (postedCompletionSessionKeys.size > 64) {
            val first = postedCompletionSessionKeys.firstOrNull()
            if (first != null) postedCompletionSessionKeys.remove(first)
        }

        ensureChannels(context)

        val plannedMins = (snapshot.totalSeconds / 60).coerceAtLeast(1)
        val clampedExtension = extensionMinutes.coerceIn(3, 30)
        val greetingPrefix = if (studentName.isNotBlank() && !prefs.hidePrivateNotesOnLockScreen) {
            "${studentName.trim()} — "
        } else {
            ""
        }

        val title = when (snapshot.activeActivityType) {
            StudyActivityType.STUDY_LECTURE ->
                "${greetingPrefix}Your lecture allowance has finished"
            StudyActivityType.STUDY_PDF ->
                "${greetingPrefix}Your PDF study allowance has finished"
            StudyActivityType.WRITING_NOTES ->
                "${greetingPrefix}Your notes session has finished"
            StudyActivityType.PLAYING_GAME ->
                "${greetingPrefix}Your gaming allowance is finished"
            StudyActivityType.ENTERTAINMENT ->
                "${greetingPrefix}Your entertainment allowance is finished"
            StudyActivityType.SHORT_BREAK ->
                "${greetingPrefix}Break complete · Ready when you are"
            else -> if (snapshot.activeActivityCategory == ActivityCategory.STUDY) {
                "${greetingPrefix}Focus block complete · Well done"
            } else {
                "${greetingPrefix}${snapshot.activeActivityLabel} allowance finished"
            }
        }

        val body = when {
            snapshot.activeActivityType == StudyActivityType.STUDY_LECTURE ->
                "You planned $plannedMins min of study lecture time. Ready to return to $subjectName?"
            snapshot.activeActivityCategory == ActivityCategory.ENTERTAINMENT ->
                "You planned $plannedMins min. Ready to return to studying?"
            snapshot.pausedParentSession != null ->
                "Your paused $subjectName session is waiting — return to study, extend, or take a break."
            snapshot.activeActivityCategory == ActivityCategory.STUDY ->
                if (prefs.hidePrivateNotesOnLockScreen) "Your study block was saved in BRAIN BUFFER."
                else "Saved $plannedMins min of $subjectName."
            else ->
                "Logged $plannedMins min of ${snapshot.activeActivityLabel}."
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(
                EXTRA_OPEN_ROUTE,
                if (snapshot.pausedParentSession?.isStrictFocus == true || snapshot.isStrictFocus) "strict_focus" else "focus"
            )
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            2002,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_brain_buffer)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(0xFF7B5CFA.toInt())

        if (!soundEnabled && !vibrationEnabled) {
            builder.setSilent(true)
        } else if (vibrationEnabled) {
            builder.setVibrate(longArrayOf(0L, 180L, 90L, 180L))
        }

        if (snapshot.pausedParentSession != null) {
            builder.addAction(
                0,
                "Return to Study",
                buildActionPendingIntent(context, TimerNotificationActionReceiver.ACTION_RETURN_TO_STUDY, 3101)
            )
            builder.addAction(
                0,
                "Extend +${clampedExtension}m",
                buildActionPendingIntent(
                    context,
                    TimerNotificationActionReceiver.ACTION_EXTEND,
                    3102,
                    extraMinutes = clampedExtension
                )
            )
            if (snapshot.activeActivityCategory != ActivityCategory.BREAK) {
                builder.addAction(
                    0,
                    "Take a Break",
                    buildActionPendingIntent(context, TimerNotificationActionReceiver.ACTION_TAKE_BREAK, 3104)
                )
            } else {
                builder.addAction(
                    0,
                    "Finish",
                    buildActionPendingIntent(context, TimerNotificationActionReceiver.ACTION_END, 3103)
                )
            }
        } else {
            builder.addAction(
                0,
                "Extend +${clampedExtension}m",
                buildActionPendingIntent(
                    context,
                    TimerNotificationActionReceiver.ACTION_EXTEND,
                    3102,
                    extraMinutes = clampedExtension
                )
            )
            builder.addAction(
                0,
                "Take a Break",
                buildActionPendingIntent(context, TimerNotificationActionReceiver.ACTION_TAKE_BREAK, 3104)
            )
            builder.addAction(
                0,
                "Finish",
                buildActionPendingIntent(context, TimerNotificationActionReceiver.ACTION_END, 3103)
            )
        }

        return notifySafely(context, ID_COMPLETION, builder.build())
    }

    fun cancelActiveTimerNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(ID_ACTIVE_TIMER)
        } catch (_: SecurityException) {
            // Safe no-op
        }
    }

    fun cancelCompletionNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(ID_COMPLETION)
        } catch (_: SecurityException) {
            // Safe no-op
        }
    }

    fun cancelDistractionWarningNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(ID_DISTRACTION_WARNING)
        } catch (_: SecurityException) {
            // Safe no-op
        }
    }

    fun clearPostedSessionKeysForTest() {
        postedCompletionSessionKeys.clear()
    }

    private fun notifySafely(context: Context, id: Int, notification: Notification): Boolean {
        if (!hasNotificationPermission(context)) return false
        return try {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun buildActionPendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
        extraMinutes: Int? = null,
        distractionPackage: String? = null,
        distractionAppLabel: String? = null,
        sessionId: String? = null
    ): PendingIntent {
        val intent = Intent(context, TimerNotificationActionReceiver::class.java).apply {
            this.action = action
            if (extraMinutes != null) {
                putExtra(TimerNotificationActionReceiver.EXTRA_EXTEND_MINUTES, extraMinutes)
            }
            if (!distractionPackage.isNullOrBlank()) {
                putExtra(TimerNotificationActionReceiver.EXTRA_DISTRACTION_PACKAGE, distractionPackage)
            }
            if (!distractionAppLabel.isNullOrBlank()) {
                putExtra(TimerNotificationActionReceiver.EXTRA_DISTRACTION_APP_LABEL, distractionAppLabel)
            }
            if (!sessionId.isNullOrBlank()) {
                putExtra(TimerNotificationActionReceiver.EXTRA_SESSION_ID, sessionId)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun formatMinSec(totalSeconds: Int): String {
        val clamped = totalSeconds.coerceAtLeast(0)
        val mins = clamped / 60
        val secs = clamped % 60
        return "%02d:%02d".format(mins, secs)
    }
}
