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

    fun openDocument(context: Context, uri: Uri, preferredPackage: String? = null): Boolean {
        return runCatching {
            val mime = context.contentResolver.getType(uri) ?: "*/*"
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!preferredPackage.isNullOrBlank()) {
                    setPackage(preferredPackage)
                }
            }
            context.startActivity(viewIntent)
            true
        }.getOrElse {
            runCatching {
                val fallback = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(fallback, "Open Study Document").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                true
            }.getOrDefault(false)
        }
    }

    fun openDialer(context: Context): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * Validates and opens an approved study YouTube/lecture HTTPS URL safely.
     * Rejects non-HTTPS schemes (javascript:, file:, content:, intent:) to prevent unsafe redirects.
     */
    fun isValidStudyUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return false
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return false
        val host = uri.host?.lowercase().orEmpty()
        return host.isNotBlank() && host.contains('.')
    }

    fun openStudyUrl(context: Context, rawUrl: String): Boolean {
        if (!isValidStudyUrl(rawUrl)) return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl.trim())).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
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
            ControlledAccessOption.STUDY_LECTURE ->
                combined.contains("youtube") || combined.contains("coursera") || combined.contains("udemy") ||
                    combined.contains("khan") || combined.contains("edx") || combined.contains("unacademy") ||
                    combined.contains("classroom") || combined.contains("vimeo") || combined.contains("vlc")

            ControlledAccessOption.SMS_AND_EMAILS ->
                combined.contains("gm") || combined.contains("mail") || combined.contains("outlook") ||
                    combined.contains("messaging") || combined.contains("mms") || combined.contains("sms") ||
                    combined.contains("messages") || combined.contains("proton") || combined.contains("spark")

            ControlledAccessOption.FILES_AND_DOCUMENTS ->
                combined.contains("files") || combined.contains("docs") || combined.contains("word") ||
                    combined.contains("notion") || combined.contains("obsidian") || combined.contains("onenote") ||
                    combined.contains("keep") || combined.contains("office") || combined.contains("wps")

            ControlledAccessOption.CALLS ->
                combined.contains("dialer") || combined.contains("phone") || combined.contains("contacts")

            ControlledAccessOption.PDFS_AND_DRIVE ->
                combined.contains("pdf") || combined.contains("adobe") || combined.contains("xodo") ||
                    combined.contains("drive") || combined.contains("docs") || combined.contains("wps") ||
                    combined.contains("dropbox") || combined.contains("onedrive")

            ControlledAccessOption.CUSTOM -> false
        }
    }
}

