package com.example.system

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.model.ActivityCategory
import com.example.model.AppCategoryMapping
import com.example.model.ControlledAccessOption

data class InstalledAppChoice(
    val packageName: String,
    val label: String,
    val mappedCategory: ActivityCategory? = null
)

/**
 * Play-Policy-compliant app discovery, document launching, and optional user-authorised
 * Android Screen Pinning helper.
 *
 * Guarantees:
 * - Relies exclusively on narrow `<queries>` declarations in AndroidManifest.xml (no QUERY_ALL_PACKAGES).
 * - Uses zero-permission Storage Access Framework (`OpenDocument`) + `Intent.ACTION_VIEW` for PDFs.
 * - Screen Pinning (`startLockTask`) is strictly optional, user-confirmed by Android OS, and always
 *   exitable via Android's standard unpin gesture while preserving emergency calls and accessibility.
 */
object AppLauncherHelper {

    fun queryLaunchableApps(
        context: Context,
        userMappings: List<AppCategoryMapping> = emptyList(),
        limit: Int = 60
    ): List<InstalledAppChoice> {
        val pm = context.packageManager ?: return emptyList()
        val ownPackage = context.packageName
        val mappingByPkg = userMappings.associateBy { it.packageName }

        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }
        }.getOrDefault(emptyList())

        return resolved
            .asSequence()
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == ownPackage) return@mapNotNull null
                val label = runCatching { info.loadLabel(pm)?.toString() }.getOrNull()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { pkg.substringAfterLast('.') }
                InstalledAppChoice(
                    packageName = pkg,
                    label = label,
                    mappedCategory = mappingByPkg[pkg]?.category
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .take(limit)
            .toList()
    }

    /**
     * Returns installed apps most relevant to a Controlled Phone Access option
     * (prioritizing user-configured app categories, followed by safe package heuristics).
     */
    fun queryCandidateAppsForOption(
        context: Context,
        option: ControlledAccessOption,
        userMappings: List<AppCategoryMapping> = emptyList()
    ): List<InstalledAppChoice> {
        val all = queryLaunchableApps(context, userMappings, limit = 80)
        if (all.isEmpty()) return emptyList()

        val mappingByPkg = userMappings.associateBy { it.packageName }
        val scored = all.map { app ->
            val mapped = mappingByPkg[app.packageName]
            val score = when {
                mapped != null && mapped.activityType == option.activityType -> 100
                mapped != null && mapped.category == option.activityType.category -> 80
                matchesHeuristic(app.packageName, app.label, option) -> 50
                else -> 0
            }
            app to score
        }

        val preferred = scored.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<InstalledAppChoice, Int>> { it.second }.thenBy { it.first.label.lowercase() })
            .map { it.first }

        return if (preferred.isNotEmpty()) preferred.take(12) else all.take(12)
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return runCatching {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        }.getOrDefault(false)
    }

    fun openPdfDocument(context: Context, uri: Uri, preferredPackage: String? = null): Boolean {
        return runCatching {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!preferredPackage.isNullOrBlank()) {
                    setPackage(preferredPackage)
                }
            }
            context.startActivity(viewIntent)
            true
        }.getOrElse {
            // Fallback to system chooser if the preferred package cannot view this URI
            runCatching {
                val fallback = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(fallback, "Open Study PDF").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                true
            }.getOrDefault(false)
        }
    }

    fun isAppPinningActive(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return runCatching { am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE }.getOrDefault(false)
    }

    fun startOptionalAppPinning(activity: Activity?): Boolean {
        if (activity == null) return false
        return runCatching {
            activity.startLockTask()
            true
        }.getOrDefault(false)
    }

    fun stopOptionalAppPinning(activity: Activity?): Boolean {
        if (activity == null) return false
        return runCatching {
            if (isAppPinningActive(activity)) {
                activity.stopLockTask()
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun matchesHeuristic(packageName: String, label: String, option: ControlledAccessOption): Boolean {
        val combined = "${packageName.lowercase()} ${label.lowercase()}"
        return when (option) {
            ControlledAccessOption.STUDY_PDF ->
                combined.contains("pdf") || combined.contains("adobe") || combined.contains("xodo") ||
                    combined.contains("drive") || combined.contains("docs") || combined.contains("wps") ||
                    combined.contains("office") || combined.contains("files") || combined.contains("notes")

            ControlledAccessOption.STUDY_LECTURE ->
                combined.contains("youtube") || combined.contains("coursera") || combined.contains("udemy") ||
                    combined.contains("khan") || combined.contains("edx") || combined.contains("unacademy") ||
                    combined.contains("classroom") || combined.contains("vimeo") || combined.contains("vlc")

            ControlledAccessOption.READING_RESEARCH ->
                combined.contains("chrome") || combined.contains("firefox") || combined.contains("brave") ||
                    combined.contains("edge") || combined.contains("kindle") || combined.contains("notion") ||
                    combined.contains("obsidian") || combined.contains("wikipedia") || combined.contains("anki")

            ControlledAccessOption.NOTES_WRITING ->
                combined.contains("notion") || combined.contains("obsidian") || combined.contains("onenote") ||
                    combined.contains("keep") || combined.contains("docs") || combined.contains("evernote") ||
                    combined.contains("note") || combined.contains("word") || combined.contains("goodnotes")

            ControlledAccessOption.GAMES ->
                combined.contains("game") || combined.contains("chess") || combined.contains("minecraft") ||
                    combined.contains("roblox") || combined.contains("supercell") || combined.contains("pubg") ||
                    combined.contains("genshin") || combined.contains("riot")

            ControlledAccessOption.SOCIAL_ENTERTAINMENT ->
                combined.contains("instagram") || combined.contains("tiktok") || combined.contains("musically") ||
                    combined.contains("reddit") || combined.contains("netflix") || combined.contains("twitch") ||
                    combined.contains("twitter") || combined.contains("x.android") || combined.contains("snapchat") ||
                    combined.contains("whatsapp") || combined.contains("discord")

            ControlledAccessOption.TAKE_BREAK,
            ControlledAccessOption.OTHER -> false
        }
    }
}
