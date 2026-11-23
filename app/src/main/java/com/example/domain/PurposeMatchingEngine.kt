package com.example.domain

import com.example.model.PersistedTimerSnapshot
import com.example.model.SessionPurposeCategory
import com.example.model.StudyActivityType
import com.example.model.TemporaryAppAccessSession
import com.example.system.AppUsageAwarenessHelper
import com.example.system.DistractionAlertCoordinator
import java.util.UUID

/**
 * Pure business-logic enforcement for Temporary App Access ("App Switch") and the
 * mandatory maximum 5-minute single-use extension rule.
 *
 * Enforced in domain logic — NEVER only via UI controls:
 * - At most ONE temporary access can be active at a time.
 * - Extension requests > 5 minutes or <= 0 minutes are explicitly rejected.
 * - Duplicate/repeat extension requests for the same temporary access are explicitly rejected.
 * - Temporary access time is NEVER counted as focused time.
 */
object TemporaryAccessPolicy {

    const val MAX_EXTENSION_MINUTES: Int = TemporaryAppAccessSession.MAX_EXTENSION_MINUTES // 5
    const val MIN_ALLOWANCE_MINUTES: Int = 1
    const val MAX_INITIAL_ALLOWANCE_MINUTES: Int = 60

    sealed class StartResult {
        data class Started(
            val session: TemporaryAppAccessSession,
            val replacedPrevious: TemporaryAppAccessSession?
        ) : StartResult()

        data class Rejected(val reason: String) : StartResult()
    }

    sealed class ExtensionResult {
        data class Granted(
            val updated: TemporaryAppAccessSession,
            val grantedMinutes: Int
        ) : ExtensionResult()

        data object RejectedAlreadyExtended : ExtensionResult()
        data class RejectedExceedsMaxLimit(val requestedMinutes: Int, val maxAllowedMinutes: Int) : ExtensionResult()
        data object RejectedSessionClosed : ExtensionResult()
    }

    fun startTemporaryAccess(
        parentSessionId: String,
        packageName: String,
        appLabel: String,
        purposeLabel: String,
        durationMinutes: Int,
        nowEpochMs: Long,
        existingActive: TemporaryAppAccessSession? = null,
        ownPackageName: String = ""
    ): StartResult {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) {
            return StartResult.Rejected("Select an installed app to allow temporarily.")
        }
        if (AppUsageAwarenessHelper.isExemptSystemPackage(cleanPkg, ownPackageName)) {
            return StartResult.Rejected("Essential system apps are already accessible without temporary access.")
        }
        if (durationMinutes !in MIN_ALLOWANCE_MINUTES..MAX_INITIAL_ALLOWANCE_MINUTES) {
            return StartResult.Rejected("Choose a duration between $MIN_ALLOWANCE_MINUTES and $MAX_INITIAL_ALLOWANCE_MINUTES minutes.")
        }
        val replaced = existingActive?.takeIf { it.endedAtEpochMs == null }?.copy(
            endedAtEpochMs = nowEpochMs
        )
        val created = TemporaryAppAccessSession(
            id = "temp_${UUID.randomUUID()}",
            parentSessionId = parentSessionId,
            packageName = cleanPkg,
            appLabel = appLabel.trim().ifBlank { cleanPkg.substringAfterLast('.') },
            purposeLabel = purposeLabel.trim().ifBlank { "Temporary switch" },
            originalAllowanceMinutes = durationMinutes,
            extensionMinutesUsed = 0,
            hasUsedExtension = false,
            startedAtEpochMs = nowEpochMs,
            expiresAtEpochMs = nowEpochMs + durationMinutes * 60_000L,
            endedAtEpochMs = null
        )
        return StartResult.Started(session = created, replacedPrevious = replaced)
    }

    /**
     * Strictly enforces:
     * 1. Session must still be open (not closed/ended).
     * 2. Extension can be used AT MOST ONCE (prevents duplicate extensions / silent renewal).
     * 3. Extension duration must be 1..5 minutes (rejects anything > 5 minutes in business logic).
     */
    fun requestExtension(
        current: TemporaryAppAccessSession,
        requestedMinutes: Int,
        nowEpochMs: Long
    ): ExtensionResult {
        if (current.endedAtEpochMs != null) {
            return ExtensionResult.RejectedSessionClosed
        }
        if (current.hasUsedExtension || current.extensionMinutesUsed >= MAX_EXTENSION_MINUTES) {
            return ExtensionResult.RejectedAlreadyExtended
        }
        if (requestedMinutes <= 0 || requestedMinutes > MAX_EXTENSION_MINUTES) {
            return ExtensionResult.RejectedExceedsMaxLimit(
                requestedMinutes = requestedMinutes,
                maxAllowedMinutes = MAX_EXTENSION_MINUTES
            )
        }
        val baseEpoch = maxOf(nowEpochMs, current.expiresAtEpochMs)
        val updated = current.copy(
            extensionMinutesUsed = current.extensionMinutesUsed + requestedMinutes,
            hasUsedExtension = true,
            expiresAtEpochMs = baseEpoch + requestedMinutes * 60_000L
        )
        return ExtensionResult.Granted(updated = updated, grantedMinutes = requestedMinutes)
    }

    fun closeTemporaryAccess(
        current: TemporaryAppAccessSession,
        nowEpochMs: Long
    ): TemporaryAppAccessSession {
        if (current.endedAtEpochMs != null) return current
        return current.copy(endedAtEpochMs = nowEpochMs)
    }
}

/**
 * Core BRAIN BUFFER Purpose -> Activity Matching Engine.
 *
 * Compares what the user INTENDS to do (declared purpose + selected allowed apps)
 * with the foreground Android package observed via UsageStatsManager.
 *
 * Honesty & Privacy guarantees:
 * - Operates strictly at the application-package level.
 * - NEVER claims to inspect in-app content, messages, videos, or websites.
 * - Always exempts emergency calling, dialers, Settings, SystemUI, launchers,
 *   accessibility tools, and BRAIN BUFFER itself.
 */
object PurposeMatchingEngine {

    sealed class MatchDecision {
        data object Inactive : MatchDecision()
        data class ExemptSystemApp(val packageName: String) : MatchDecision()
        data class ApprovedForPurpose(
            val packageName: String,
            val appLabel: String,
            val declaredPurpose: SessionPurposeCategory
        ) : MatchDecision()

        data class TemporaryAccessActive(
            val packageName: String,
            val appLabel: String,
            val remainingSeconds: Int
        ) : MatchDecision()

        data class TemporaryAccessExpired(
            val session: TemporaryAppAccessSession,
            val canExtend: Boolean,
            val promptTitle: String,
            val promptMessage: String
        ) : MatchDecision()

        data class PotentialDistraction(
            val packageName: String,
            val appLabel: String,
            val declaredPurpose: SessionPurposeCategory,
            val sessionName: String,
            val message: String,
            val gracePeriodSeconds: Int,
            val shouldEmitAlert: Boolean
        ) : MatchDecision()
    }

    /**
     * Evaluates the currently observed foreground package against the active session's
     * declared purpose, selected allowed apps, and single active temporary access session.
     */
    fun evaluateForegroundApp(
        observedPackage: String,
        observedAppLabel: String,
        snapshot: PersistedTimerSnapshot,
        activeTempAccess: TemporaryAppAccessSession?,
        nowEpochMs: Long,
        ownPackageName: String = "",
        gracePeriodSeconds: Int = 15,
        cooldownSeconds: Int = 90,
        enforceGracePeriod: Boolean = false
    ): MatchDecision {
        if (!snapshot.hasStarted) return MatchDecision.Inactive

        // During a Break, monitoring is paused so user rests freely without distraction alerts.
        if (snapshot.isOnBreak) {
            DistractionAlertCoordinator.clearForegroundDwell(snapshot.sessionId)
            return MatchDecision.Inactive
        }

        // Check active or recently-expired temporary app access first (even if parent focus is paused)
        if (activeTempAccess != null && activeTempAccess.endedAtEpochMs == null) {
            if (activeTempAccess.isExpired(nowEpochMs)) {
                return MatchDecision.TemporaryAccessExpired(
                    session = activeTempAccess,
                    canExtend = activeTempAccess.canRequestExtension,
                    promptTitle = "Time is up. Return to your focus session?",
                    promptMessage = if (activeTempAccess.canRequestExtension) {
                        "Your ${activeTempAccess.originalAllowanceMinutes}m temporary access to ${activeTempAccess.appLabel} has ended."
                    } else {
                        "Your temporary access and 5-minute extension for ${activeTempAccess.appLabel} have ended."
                    }
                )
            }
            if (observedPackage.equals(activeTempAccess.packageName, ignoreCase = true)) {
                DistractionAlertCoordinator.clearForegroundDwell(snapshot.sessionId)
                return MatchDecision.TemporaryAccessActive(
                    packageName = observedPackage,
                    appLabel = activeTempAccess.appLabel,
                    remainingSeconds = activeTempAccess.remainingSeconds(nowEpochMs)
                )
            }
        }

        // If focus timer itself is paused (and not inside an expired temp access), do not nag
        if (snapshot.runningSinceEpochMs == null) {
            DistractionAlertCoordinator.clearForegroundDwell(snapshot.sessionId)
            return MatchDecision.Inactive
        }

        val cleanPkg = observedPackage.trim()
        if (cleanPkg.isEmpty()) return MatchDecision.Inactive

        if (AppUsageAwarenessHelper.isExemptSystemPackage(cleanPkg, ownPackageName)) {
            DistractionAlertCoordinator.clearForegroundDwell(snapshot.sessionId)
            return MatchDecision.ExemptSystemApp(cleanPkg)
        }

        val resolvedLabel = observedAppLabel.trim().ifBlank { cleanPkg.substringAfterLast('.') }
        val allowedSet = snapshot.allowedPackages.map { it.lowercase() }.toSet()
        if (cleanPkg.lowercase() in allowedSet ||
            snapshot.approvedPackageName.equals(cleanPkg, ignoreCase = true)
        ) {
            DistractionAlertCoordinator.clearForegroundDwell(snapshot.sessionId)
            return MatchDecision.ApprovedForPurpose(
                packageName = cleanPkg,
                appLabel = resolvedLabel,
                declaredPurpose = snapshot.purposeCategory
            )
        }

        val purposeLabel = snapshot.purposeCategory.label
        val sessionTitle = snapshot.sessionGoal.ifBlank { purposeLabel }
        val safeGraceSec = gracePeriodSeconds.coerceIn(0, 120)
        val cooldownReady = DistractionAlertCoordinator.shouldEmitAlert(
            sessionId = snapshot.sessionId,
            packageName = cleanPkg,
            nowEpochMs = nowEpochMs,
            cooldownSeconds = cooldownSeconds
        )
        val graceSatisfied = if (enforceGracePeriod) {
            DistractionAlertCoordinator.hasMetGracePeriod(
                sessionId = snapshot.sessionId,
                packageName = cleanPkg,
                nowEpochMs = nowEpochMs,
                gracePeriodSeconds = safeGraceSec
            )
        } else {
            true
        }

        return MatchDecision.PotentialDistraction(
            packageName = cleanPkg,
            appLabel = resolvedLabel,
            declaredPurpose = snapshot.purposeCategory,
            sessionName = sessionTitle,
            message = "You're in $purposeLabel mode ($sessionTitle), but $resolvedLabel isn't in your allowed list.",
            gracePeriodSeconds = safeGraceSec,
            shouldEmitAlert = cooldownReady && graceSatisfied
        )
    }

    /**
     * Switches the user's declared purpose mid-session when their genuine intention changes
     * (e.g., from Study to Communication), optionally adding the newly needed app to the
     * allowed list while preserving accurate time accounting.
     */
    fun switchPurpose(
        current: PersistedTimerSnapshot,
        newPurpose: SessionPurposeCategory,
        newSessionName: String,
        newAllowedPackages: Set<String>,
        newAllowedLabels: List<String>
    ): PersistedTimerSnapshot {
        val cleanName = newSessionName.trim().ifBlank { newPurpose.defaultSessionPlaceholder }
        return current.copy(
            purposeCategory = newPurpose,
            sessionGoal = cleanName,
            activeActivityType = newPurpose.defaultActivityType,
            activeActivityLabel = "$cleanName · ${newPurpose.label}",
            activeActivityCategory = newPurpose.defaultActivityType.category,
            allowedPackages = newAllowedPackages,
            allowedAppLabels = newAllowedLabels
        )
    }
}
