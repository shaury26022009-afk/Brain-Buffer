package com.example.system

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.example.model.ActivityCategory
import com.example.model.AppCategoryMapping
import com.example.model.AppUsageSuggestion
import com.example.model.FocusGuardLevel
import com.example.model.PersistedTimerSnapshot
import com.example.model.StudyActivityType
import com.example.model.UnapprovedAppWarning
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe cooldown and "Continue Anyway" suppression coordinator for distraction warnings.
 * Guarantees the student is never spammed with repeated distraction notifications.
 */
object DistractionAlertCoordinator {
    private val lastAlertByKey = ConcurrentHashMap<String, Long>()
    private val suppressedKeys = ConcurrentHashMap.newKeySet<String>()

    private fun key(sessionId: String, packageName: String): String {
        val safeSession = sessionId.ifBlank { "active" }
        return "$safeSession|${packageName.lowercase()}"
    }

    fun shouldEmitAlert(
        sessionId: String,
        packageName: String,
        nowEpochMs: Long,
        cooldownSeconds: Int = 90
    ): Boolean {
        if (packageName.isBlank()) return false
        val k = key(sessionId, packageName)
        if (k in suppressedKeys) return false
        val last = lastAlertByKey[k] ?: return true
        val cooldownMs = cooldownSeconds.coerceIn(15, 600) * 1000L
        return (nowEpochMs - last) >= cooldownMs
    }

    fun markNotified(sessionId: String, packageName: String, nowEpochMs: Long) {
        if (packageName.isBlank()) return
        lastAlertByKey[key(sessionId, packageName)] = nowEpochMs
    }

    fun suppressForSession(sessionId: String, packageName: String) {
        if (packageName.isBlank()) return
        suppressedKeys.add(key(sessionId, packageName))
    }

    fun clearSession(sessionId: String) {
        val prefix = "${sessionId.ifBlank { "active" }}|"
        lastAlertByKey.keys.removeIf { it.startsWith(prefix) }
        suppressedKeys.removeIf { it.startsWith(prefix) }
    }

    fun resetAllForTest() {
        lastAlertByKey.clear()
        suppressedKeys.clear()
    }
}

/**
 * Optional, local-only Android Usage Access helper for Focus Guard 3.0.
 *
 * Privacy & Play Policy guarantees:
 * - Strictly opt-in: does nothing unless the user explicitly enables App-Usage Awareness in Settings
 *   AND grants Usage Access in Android system settings.
 * - Uses package-level UsageEvents / UsageStats ONLY — never AccessibilityService, never reads
 *   screen content, video titles, browser URLs, or messages, and never transmits data off-device.
 * - Always exempts phone dialer, emergency calls, Settings, SystemUI, launchers, IMEs, and
 *   accessibility tools.
 */
object AppUsageAwarenessHelper {

    private val exemptSystemPackages = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.server.telecom",
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.android.emergency",
        "com.android.phone",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.documentsui",
        "com.google.android.documentsui",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.oneplus.launcher",
        "com.android.deskclock",
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage"
    )

    private val exemptSystemTokens = listOf(
        "launcher",
        "systemui",
        "incallui",
        "telecom",
        "dialer",
        "emergency",
        "cellbroadcast",
        "inputmethod",
        "talkback",
        "accessibility",
        "permissioncontroller",
        "packageinstaller",
        "documentsui",
        "trebuchet",
        "quickstep"
    )

    fun isExemptSystemPackage(packageName: String, ownPackageName: String = ""): Boolean {
        val pkg = packageName.trim().lowercase()
        if (pkg.isEmpty()) return true
        if (ownPackageName.isNotBlank() && pkg == ownPackageName.lowercase()) return true
        if (pkg in exemptSystemPackages) return true
        return exemptSystemTokens.any { token -> pkg.contains(token) }
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.applicationContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Queries only the last 3 minutes of local foreground app usage when opted in and permitted.
     * Returns at most one gentle activity suggestion for the most recently used non-self app.
     */
    fun detectRecentAppSuggestion(
        context: Context,
        enabledByUser: Boolean,
        userMappings: List<AppCategoryMapping> = emptyList(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): AppUsageSuggestion? {
        val recentPkg = queryCurrentForegroundPackage(context, enabledByUser, nowEpochMs) ?: return null
        return matchPackageToSuggestion(recentPkg, userMappings)
    }

    /**
     * Compares the current foreground package against the student's active Focus Guard session
     * or approved temporary activity. Never inspects app content, never blocks or force-closes apps,
     * and never pretends package names reveal whether YouTube/browser content is study or entertainment.
     */
    fun detectUnapprovedAppMismatch(
        context: Context,
        enabledByUser: Boolean,
        snapshot: PersistedTimerSnapshot,
        userMappings: List<AppCategoryMapping> = emptyList(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): UnapprovedAppWarning? {
        if (!snapshot.hasStarted || snapshot.runningSinceEpochMs == null) return null
        val recentPkg = queryCurrentForegroundPackage(context, enabledByUser, nowEpochMs) ?: return null
        return evaluateAppAgainstApprovedActivity(
            observedPackage = recentPkg,
            snapshot = snapshot,
            userMappings = userMappings,
            ownPackageName = context.packageName
        )
    }

    /**
     * Pure evaluator comparing an observed package against the active Focus Guard session.
     *
     * Distinguishes:
     * 1. Exempt system / emergency / dialer / launcher / IME / accessibility apps -> ALWAYS allowed
     * 2. Currently approved activity package (`snapshot.approvedPackageName`) -> allowed
     * 3. User-approved study apps (`mapping.isAllowedDuringFocus == true`) -> allowed during study
     * 4. Approved entertainment / break allowances -> only flags switching to a *different* unapproved app
     * 5. Unplanned distractions during High Focus, Custom Focus, or Study Temporary Activity -> gentle warning
     */
    fun evaluateAppAgainstApprovedActivity(
        observedPackage: String,
        snapshot: PersistedTimerSnapshot,
        userMappings: List<AppCategoryMapping> = emptyList(),
        ownPackageName: String = ""
    ): UnapprovedAppWarning? {
        if (observedPackage.isBlank()) return null
        if (isExemptSystemPackage(observedPackage, ownPackageName)) return null

        // Never warn if the student explicitly approved this exact package
        if (snapshot.approvedPackageName.equals(observedPackage, ignoreCase = true)) {
            return null
        }

        val mapped = userMappings.firstOrNull { it.packageName.equals(observedPackage, ignoreCase = true) }
        val fallbackSuggestion = matchPackageToSuggestion(observedPackage, userMappings)
        val observedCategory = mapped?.category ?: fallbackSuggestion?.suggestedActivity?.category
        val displayName = mapped?.appLabel
            ?.ifBlank { null }
            ?: fallbackSuggestion?.appDisplayName
            ?: observedPackage.substringAfterLast('.').replaceFirstChar { it.uppercase() }

        // User explicitly marked this app as allowed during study
        if (mapped?.isAllowedDuringFocus == true) return null

        val inDedicatedStudy = (snapshot.isStrictFocus || snapshot.focusGuardLevel != FocusGuardLevel.NORMAL) &&
            !snapshot.isSwitchedActivity
        val inStudyTempActivity = snapshot.isSwitchedActivity &&
            (snapshot.activeActivityCategory == ActivityCategory.LECTURE ||
                snapshot.activeActivityCategory == ActivityCategory.READING)
        val inApprovedBreakOrEntertainment = snapshot.isSwitchedActivity &&
            (snapshot.activeActivityCategory == ActivityCategory.BREAK ||
                snapshot.activeActivityCategory == ActivityCategory.ENTERTAINMENT ||
                snapshot.activeActivityCategory == ActivityCategory.OTHER)

        // During an approved entertainment allowance with NO specific package chosen, don't nag
        if (inApprovedBreakOrEntertainment && snapshot.approvedPackageName.isBlank()) {
            return null
        }

        // During an approved entertainment allowance WITH a specific app chosen, only gently flag switching to another entertainment app
        if (inApprovedBreakOrEntertainment && snapshot.approvedPackageName.isNotBlank()) {
            return if (observedCategory == ActivityCategory.ENTERTAINMENT) {
                UnapprovedAppWarning(
                    packageName = observedPackage,
                    appDisplayName = displayName,
                    observedCategory = observedCategory,
                    message = "You approved ${snapshot.approvedAppLabel.ifBlank { snapshot.activeActivityLabel }}, but switched to $displayName. Stay on your planned allowance or return to study?"
                )
            } else {
                null
            }
        }

        // During active High/Custom study or approved Lecture/PDF/Reading/Notes temporary activity:
        return when {
            (inDedicatedStudy || inStudyTempActivity) &&
                observedCategory == ActivityCategory.ENTERTAINMENT -> {
                val contextPhrase = when (snapshot.activeActivityType) {
                    StudyActivityType.STUDY_LECTURE -> "watch a study lecture"
                    StudyActivityType.STUDY_PDF -> "study your PDF"
                    StudyActivityType.READING -> "do reading & research"
                    StudyActivityType.WRITING_NOTES -> "take study notes"
                    else -> "study"
                }
                UnapprovedAppWarning(
                    packageName = observedPackage,
                    appDisplayName = displayName,
                    observedCategory = observedCategory,
                    message = "You planned to $contextPhrase. You appear to have switched to $displayName. Return to your session?"
                )
            }

            inDedicatedStudy && observedCategory == ActivityCategory.LECTURE -> {
                UnapprovedAppWarning(
                    packageName = observedPackage,
                    appDisplayName = displayName,
                    observedCategory = observedCategory,
                    message = "You switched to $displayName during High Focus. Allow 5 min for a study lecture or return to study?"
                )
            }

            inStudyTempActivity &&
                snapshot.approvedPackageName.isNotBlank() &&
                observedCategory != null &&
                observedCategory != snapshot.activeActivityCategory -> {
                UnapprovedAppWarning(
                    packageName = observedPackage,
                    appDisplayName = displayName,
                    observedCategory = observedCategory,
                    message = "You planned ${snapshot.activeActivityLabel}, but switched to $displayName. Return to your study activity?"
                )
            }

            else -> null
        }
    }

    /**
     * Queries the current foreground external package using UsageEvents first (real-time accuracy
     * for app switches) and falling back safely to UsageStats within the last 2 minutes.
     */
    fun queryCurrentForegroundPackage(
        context: Context,
        enabledByUser: Boolean,
        nowEpochMs: Long = System.currentTimeMillis()
    ): String? {
        if (!enabledByUser || !hasUsageStatsPermission(context)) return null
        val usageStatsManager =
            context.applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return null
        val ownPackage = context.packageName

        // 1. Real-time UsageEvents check (last 30 seconds)
        val fromEvents = runCatching {
            val events = usageStatsManager.queryEvents(nowEpochMs - 30_000L, nowEpochMs)
            val event = UsageEvents.Event()
            var latestPkg: String? = null
            var latestTs = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForegroundEvent = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                if (isForegroundEvent && event.timeStamp >= latestTs && !event.packageName.isNullOrBlank()) {
                    latestPkg = event.packageName
                    latestTs = event.timeStamp
                }
            }
            latestPkg
        }.getOrNull()

        if (!fromEvents.isNullOrBlank()) {
            // If the user is currently inside BRAIN BUFFER or an exempt system UI, no external distraction
            return if (isExemptSystemPackage(fromEvents, ownPackage)) null else fromEvents
        }

        // 2. Fallback: most recently used external package in the last 2 minutes
        val windowStart = nowEpochMs - 2 * 60 * 1000L
        val stats = runCatching {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                windowStart,
                nowEpochMs
            )
        }.getOrNull()

        if (stats.isNullOrEmpty()) return null
        val mostRecent = stats
            .asSequence()
            .filter { it.lastTimeUsed >= windowStart && it.totalTimeInForeground > 0L }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
            ?: return null

        return if (isExemptSystemPackage(mostRecent, ownPackage)) null else mostRecent
    }

    /**
     * Pure, transparent mapping prioritizing user-configured [AppCategoryMapping] rules first,
     * then falling back to common package-family suggestions.
     */
    fun matchPackageToSuggestion(
        packageName: String,
        userMappings: List<AppCategoryMapping> = emptyList()
    ): AppUsageSuggestion? {
        val customMapping = userMappings.firstOrNull { it.packageName.equals(packageName, ignoreCase = true) }
        if (customMapping != null) {
            return AppUsageSuggestion(
                packageName = customMapping.packageName,
                appDisplayName = customMapping.appLabel.ifBlank { friendlyNameFor(packageName.lowercase()) },
                suggestedActivity = customMapping.activityType,
                suggestedDurationMinutes = customMapping.activityType.defaultMinutes
            )
        }

        val pkg = packageName.lowercase()
        return when {
            pkg.contains("pdf") || pkg.contains("adobe.reader") || pkg.contains("xodo") ->
                AppUsageSuggestion(
                    packageName = packageName,
                    appDisplayName = friendlyNameFor(pkg),
                    suggestedActivity = StudyActivityType.STUDY_PDF,
                    suggestedDurationMinutes = 25
                )

            pkg.contains("youtube") || pkg.contains("coursera") || pkg.contains("udemy") ||
                pkg.contains("khanacademy") || pkg.contains("edx") || pkg.contains("unacademy") ->
                AppUsageSuggestion(
                    packageName = packageName,
                    appDisplayName = friendlyNameFor(pkg),
                    suggestedActivity = StudyActivityType.STUDY_LECTURE,
                    suggestedDurationMinutes = 25
                )

            pkg.contains("notion") || pkg.contains("obsidian") || pkg.contains("onenote") ||
                pkg.contains("keep") || pkg.contains("docs.editors") || pkg.contains("evernote") ->
                AppUsageSuggestion(
                    packageName = packageName,
                    appDisplayName = friendlyNameFor(pkg),
                    suggestedActivity = StudyActivityType.WRITING_NOTES,
                    suggestedDurationMinutes = 25
                )

            pkg.contains("kindle") || pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("brave") ->
                AppUsageSuggestion(
                    packageName = packageName,
                    appDisplayName = friendlyNameFor(pkg),
                    suggestedActivity = StudyActivityType.READING,
                    suggestedDurationMinutes = 25
                )

            pkg.contains("supercell") || pkg.contains("pubg") || pkg.contains("minecraft") ||
                pkg.contains("roblox") || pkg.contains("riotgames") || pkg.contains("genshin") ||
                pkg.contains("chess") || pkg.contains("bgmi") || pkg.contains("freefire") ||
                pkg.contains("callofduty") || pkg.contains(".game") ->
                AppUsageSuggestion(
                    packageName = packageName,
                    appDisplayName = friendlyNameFor(pkg),
                    suggestedActivity = StudyActivityType.PLAYING_GAME,
                    suggestedDurationMinutes = 20
                )

            pkg.contains("instagram") || pkg.contains("tiktok") || pkg.contains("musically") ||
                pkg.contains("netflix") || pkg.contains("reddit") || pkg.contains("twitch") ||
                pkg.contains("twitter") || pkg.contains("x.android") || pkg.contains("snapchat") ||
                pkg.contains("facebook") ->
                AppUsageSuggestion(
                    packageName = packageName,
                    appDisplayName = friendlyNameFor(pkg),
                    suggestedActivity = StudyActivityType.ENTERTAINMENT,
                    suggestedDurationMinutes = 15
                )

            else -> null
        }
    }

    private fun friendlyNameFor(pkg: String): String = when {
        pkg.contains("youtube") -> "YouTube"
        pkg.contains("coursera") -> "Coursera"
        pkg.contains("udemy") -> "Udemy"
        pkg.contains("khanacademy") -> "Khan Academy"
        pkg.contains("notion") -> "Notion"
        pkg.contains("obsidian") -> "Obsidian"
        pkg.contains("onenote") -> "OneNote"
        pkg.contains("keep") -> "Google Keep"
        pkg.contains("docs.editors") -> "Google Docs"
        pkg.contains("kindle") -> "Kindle"
        pkg.contains("adobe.reader") -> "PDF Reader"
        pkg.contains("chrome") -> "Chrome"
        pkg.contains("firefox") -> "Firefox"
        pkg.contains("brave") -> "Brave"
        pkg.contains("instagram") -> "Instagram"
        pkg.contains("tiktok") || pkg.contains("musically") -> "TikTok"
        pkg.contains("netflix") -> "Netflix"
        pkg.contains("reddit") -> "Reddit"
        pkg.contains("twitch") -> "Twitch"
        pkg.contains("snapchat") -> "Snapchat"
        pkg.contains("facebook") -> "Facebook"
        pkg.contains("minecraft") -> "Minecraft"
        pkg.contains("roblox") -> "Roblox"
        pkg.contains("supercell") -> "Supercell Game"
        pkg.contains("pubg") || pkg.contains("bgmi") -> "PUBG / BGMI"
        pkg.contains("chess") -> "Chess"
        else -> "Recent App"
    }
}
