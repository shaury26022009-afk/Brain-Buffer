package com.example.system

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.example.model.AppUsageEntry
import com.example.model.BlockedAppCategory
import com.example.model.CategoryUsageEntry
import com.example.model.DailyScreenTimePoint
import com.example.model.PhoneUsageReport
import com.example.model.UsageAnalyticsCategory
import com.example.model.UsagePeriodMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Genuine Android UsageStatsManager analytics engine.
 *
 * Honesty & privacy guarantees:
 * - Never invents or fabricates screen time when PACKAGE_USAGE_STATS permission is denied.
 * - Computes real per-app durations, daily trend buckets, app launch counts, category totals,
 *   period-over-period comparisons, and data-backed insights entirely on-device.
 */
object PhoneUsageAnalyticsEngine {

    private val shortDayFmt = DateTimeFormatter.ofPattern("EEE")
    private val monthDayFmt = DateTimeFormatter.ofPattern("MMM d")

    suspend fun computeReport(
        context: Context,
        periodMode: UsagePeriodMode,
        customStartEpochDay: Long? = null,
        customEndEpochDay: Long? = null
    ): PhoneUsageReport = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val (startDate, endDate) = when (periodMode) {
            UsagePeriodMode.LAST_7_DAYS -> today.minusDays(6) to today
            UsagePeriodMode.LAST_30_DAYS -> today.minusDays(29) to today
            UsagePeriodMode.SINGLE_DAY -> {
                val target = customStartEpochDay?.let { LocalDate.ofEpochDay(it) } ?: today
                target to target
            }
            UsagePeriodMode.CUSTOM_RANGE -> {
                val rawStart = customStartEpochDay?.let { LocalDate.ofEpochDay(it) } ?: today.minusDays(6)
                val rawEnd = customEndEpochDay?.let { LocalDate.ofEpochDay(it) } ?: today
                if (rawStart.isAfter(rawEnd)) rawEnd to rawStart else rawStart to rawEnd
            }
        }

        if (!AppUsageAwarenessHelper.hasUsageAccessPermission(context)) {
            return@withContext PhoneUsageReport(
                permissionGranted = false,
                isLoading = false,
                periodMode = periodMode,
                startEpochDay = startDate.toEpochDay(),
                endEpochDay = endDate.toEpochDay(),
                lastRefreshedMs = System.currentTimeMillis()
            )
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext PhoneUsageReport(
                permissionGranted = false,
                isLoading = false,
                periodMode = periodMode,
                startEpochDay = startDate.toEpochDay(),
                endEpochDay = endDate.toEpochDay(),
                errorMessage = "Android UsageStats service is unavailable on this device."
            )

        return@withContext try {

        val pm = context.packageManager
        val ownPackage = context.packageName
        val nowMs = System.currentTimeMillis()

        val daysCount = (endDate.toEpochDay() - startDate.toEpochDay() + 1L).coerceIn(1L, 90L).toInt()
        val appTimeTotals = mutableMapOf<String, Long>()
        val appLaunches = mutableMapOf<String, Int>()
        val appNameCache = mutableMapOf<String, String>()
        val dailyPoints = ArrayList<DailyScreenTimePoint>(daysCount)

        for (offset in 0 until daysCount) {
            val day = startDate.plusDays(offset.toLong())
            val dayStartMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEndMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli().coerceAtMost(nowMs)
            if (dayEndMs <= dayStartMs) {
                dailyPoints.add(
                    DailyScreenTimePoint(
                        dateEpochDay = day.toEpochDay(),
                        label = if (daysCount <= 7) day.format(shortDayFmt) else day.format(monthDayFmt),
                        totalTimeMs = 0L,
                        distractingTimeMs = 0L,
                        studyTimeMs = 0L
                    )
                )
                continue
            }

            val dayUsageMap = queryDayPerAppMs(usm, dayStartMs, dayEndMs, ownPackage, appLaunches)
            var dayTotalMs = 0L
            var dayDistractingMs = 0L
            var dayStudyMs = 0L

            dayUsageMap.forEach { (pkg, ms) ->
                if (ms < 10_000L) return@forEach // skip sub-10s background noise
                val label = appNameCache.getOrPut(pkg) { resolveAppLabel(pm, pkg) }
                val cat = classifyPackage(pkg, label)
                dayTotalMs += ms
                if (cat.isDistracting) dayDistractingMs += ms
                if (cat == UsageAnalyticsCategory.STUDY_PRODUCTIVITY) dayStudyMs += ms
                appTimeTotals[pkg] = (appTimeTotals[pkg] ?: 0L) + ms
            }

            dailyPoints.add(
                DailyScreenTimePoint(
                    dateEpochDay = day.toEpochDay(),
                    label = if (daysCount <= 7) day.format(shortDayFmt) else day.format(monthDayFmt),
                    totalTimeMs = dayTotalMs,
                    distractingTimeMs = dayDistractingMs,
                    studyTimeMs = dayStudyMs
                )
            )
        }

        val totalScreenTimeMs = appTimeTotals.values.sum()
        val dailyAverageMs = if (daysCount > 0) totalScreenTimeMs / daysCount else 0L

        // Previous period comparison (same length immediately before startDate)
        val prevEndDate = startDate.minusDays(1)
        val prevStartDate = prevEndDate.minusDays((daysCount - 1).toLong())
        val prevStartMs = prevStartDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val prevEndMs = prevEndDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val prevTotalMs = queryPeriodTotalMs(usm, prevStartMs, prevEndMs, ownPackage)
        val deltaPercent = if (prevTotalMs >= 60_000L) {
            (((totalScreenTimeMs - prevTotalMs).toDouble() / prevTotalMs.toDouble()) * 100.0).toInt()
        } else {
            null
        }

        val appEntries = appTimeTotals.entries
            .sortedByDescending { it.value }
            .map { (pkg, ms) ->
                val label = appNameCache.getOrPut(pkg) { resolveAppLabel(pm, pkg) }
                val category = classifyPackage(pkg, label)
                val share = if (totalScreenTimeMs > 0L) (ms.toFloat() / totalScreenTimeMs.toFloat()) else 0f
                AppUsageEntry(
                    packageName = pkg,
                    appName = label,
                    category = category,
                    totalTimeMs = ms,
                    launchCount = (appLaunches[pkg] ?: 0).coerceAtLeast(if (ms >= 60_000L) 1 else 0),
                    sharePercent = share
                )
            }

        val categoryEntries = appEntries
            .groupBy { it.category }
            .map { (category, list) ->
                val sumMs = list.sumOf { it.totalTimeMs }
                CategoryUsageEntry(
                    category = category,
                    totalTimeMs = sumMs,
                    sharePercent = if (totalScreenTimeMs > 0L) sumMs.toFloat() / totalScreenTimeMs.toFloat() else 0f,
                    appCount = list.size
                )
            }
            .sortedByDescending { it.totalTimeMs }

        val mostDistracting = appEntries.filter { it.category.isDistracting }.take(6)
        val totalLaunches = appEntries.sumOf { it.launchCount }

        val insights = buildInsights(
            totalScreenTimeMs = totalScreenTimeMs,
            dailyAverageMs = dailyAverageMs,
            deltaPercent = deltaPercent,
            appEntries = appEntries,
            categoryEntries = categoryEntries,
            dailyPoints = dailyPoints
        )

        PhoneUsageReport(
            permissionGranted = true,
            isLoading = false,
            periodMode = periodMode,
            startEpochDay = startDate.toEpochDay(),
            endEpochDay = endDate.toEpochDay(),
            totalScreenTimeMs = totalScreenTimeMs,
            dailyAverageMs = dailyAverageMs,
            previousPeriodTotalMs = prevTotalMs,
            deltaPercentVsPrevious = deltaPercent,
            appUsages = appEntries,
            categoryUsages = categoryEntries,
            dailyTrend = dailyPoints,
            mostDistractingApps = mostDistracting,
            totalLaunches = totalLaunches,
            insights = insights,
            lastRefreshedMs = System.currentTimeMillis(),
            errorMessage = null
        )
        } catch (e: SecurityException) {
            PhoneUsageReport(
                permissionGranted = false,
                isLoading = false,
                periodMode = periodMode,
                startEpochDay = startDate.toEpochDay(),
                endEpochDay = endDate.toEpochDay(),
                lastRefreshedMs = System.currentTimeMillis(),
                errorMessage = "Usage Access permission was revoked. Re-enable it in Android Settings."
            )
        } catch (e: Exception) {
            PhoneUsageReport(
                permissionGranted = true,
                isLoading = false,
                periodMode = periodMode,
                startEpochDay = startDate.toEpochDay(),
                endEpochDay = endDate.toEpochDay(),
                lastRefreshedMs = System.currentTimeMillis(),
                errorMessage = "Unable to read Android usage stats right now (${e.localizedMessage ?: "system error"})."
            )
        }
    }

    private fun queryDayPerAppMs(
        usm: UsageStatsManager,
        startMs: Long,
        endMs: Long,
        ownPackage: String,
        launchAccumulator: MutableMap<String, Int>
    ): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        // Prefer UsageEvents for accurate foreground session duration & launch counts within exact day boundaries
        val events = runCatching { usm.queryEvents(startMs, endMs) }.getOrNull()
        if (events != null) {
            val activeStart = mutableMapOf<String, Long>()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if (isSystemNoisePackage(pkg, ownPackage)) continue
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        activeStart[pkg] = event.timeStamp
                        launchAccumulator[pkg] = (launchAccumulator[pkg] ?: 0) + 1
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val started = activeStart.remove(pkg)
                        if (started != null && event.timeStamp > started) {
                            val delta = (event.timeStamp - started).coerceAtMost(4 * 60 * 60 * 1000L)
                            result[pkg] = (result[pkg] ?: 0L) + delta
                        }
                    }
                }
            }
            // Close any session still active at endMs
            activeStart.forEach { (pkg, started) ->
                if (endMs > started) {
                    val delta = (endMs - started).coerceAtMost(2 * 60 * 60 * 1000L)
                    result[pkg] = (result[pkg] ?: 0L) + delta
                }
            }
        }

        // Fallback to aggregate queryUsageStats if UsageEvents returned empty (e.g. older window beyond Android event retention ~7-10 days)
        if (result.isEmpty()) {
            val stats = runCatching {
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, endMs)
            }.getOrDefault(emptyList())
            stats.forEach { stat ->
                val pkg = stat.packageName ?: return@forEach
                if (isSystemNoisePackage(pkg, ownPackage)) return@forEach
                val fg = stat.totalTimeInForeground
                if (fg > 0L) {
                    result[pkg] = (result[pkg] ?: 0L) + fg
                }
            }
        }
        return result
    }

    private fun queryPeriodTotalMs(
        usm: UsageStatsManager,
        startMs: Long,
        endMs: Long,
        ownPackage: String
    ): Long {
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, endMs)
        }.getOrDefault(emptyList())
        return stats
            .filter { !isSystemNoisePackage(it.packageName.orEmpty(), ownPackage) }
            .sumOf { it.totalTimeInForeground.coerceAtLeast(0L) }
    }

    private fun isSystemNoisePackage(pkg: String, ownPackage: String): Boolean {
        if (pkg.isBlank() || pkg == ownPackage) return true
        val lower = pkg.lowercase()
        return lower == "android" ||
            lower.contains("systemui") ||
            lower.contains("launcher") ||
            lower.contains("trebuchet") ||
            lower.contains("nexuslauncher") ||
            lower.contains("permissioncontroller") ||
            lower.contains("inputmethod") ||
            lower.contains("keyguard")
    }

    private fun resolveAppLabel(pm: PackageManager, packageName: String): String {
        return runCatching {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info)?.toString()?.ifBlank { packageName } ?: packageName
        }.getOrDefault(packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() })
    }

    fun classifyPackage(packageName: String, appLabel: String = ""): UsageAnalyticsCategory {
        val p = packageName.lowercase()
        val l = appLabel.lowercase()
        return when {
            p.contains("tiktok") || p.contains("musically") || p.contains("moj") ||
                p.contains("josh") || p.contains("sharechat") || p.contains("snapchat") ||
                l.contains("reels") || l.contains("shorts") -> UsageAnalyticsCategory.SHORT_VIDEO

            p.contains("instagram") || p.contains("facebook") || p.contains("twitter") ||
                p.contains("reddit") || p.contains("threads") || p.contains("pinterest") ||
                p.contains("x.android") || p.contains("tumblr") -> UsageAnalyticsCategory.SOCIAL_MEDIA

            p.contains("game") || p.contains("supercell") || p.contains("pubg") ||
                p.contains("garena") || p.contains("roblox") || p.contains("minecraft") ||
                p.contains("ea.gp") || p.contains("activision") || p.contains("mihoyo") ||
                p.contains("epicgames") || l.contains("clash") -> UsageAnalyticsCategory.GAMING

            p.contains("netflix") || p.contains("primevideo") || p.contains("hotstar") ||
                p.contains("disney") || p.contains("twitch") || p.contains("youtube") ||
                p.contains("hulu") || p.contains("max") || p.contains("spotify") -> UsageAnalyticsCategory.OTT_VIDEO

            p.contains("amazon") || p.contains("flipkart") || p.contains("myntra") ||
                p.contains("meesho") || p.contains("shein") || p.contains("temu") ||
                p.contains("ebay") || p.contains("etsy") || p.contains("walmart") ||
                p.contains("aliexpress") || p.contains("shopee") || p.contains("nykaa") ||
                p.contains("ajio") || p.contains("shop") || l.contains("shop") -> UsageAnalyticsCategory.SHOPPING

            p.contains("docs") || p.contains("sheets") || p.contains("slides") ||
                p.contains("drive") || p.contains("notion") || p.contains("obsidian") ||
                p.contains("anki") || p.contains("coursera") || p.contains("udemy") ||
                p.contains("khanacademy") || p.contains("duolingo") || p.contains("pdf") ||
                p.contains("adobe.reader") || p.contains("kindle") || p.contains("onenote") ||
                p.contains("classroom") || p.contains("keep") || p.contains("calculator") ||
                p.contains("calendar") || p.contains("word") || p.contains("excel") -> UsageAnalyticsCategory.STUDY_PRODUCTIVITY

            p.contains("whatsapp") || p.contains("telegram") || p.contains("signal") ||
                p.contains("messaging") || p.contains("mms") || p.contains("gm") ||
                p.contains("mail") || p.contains("outlook") || p.contains("slack") ||
                p.contains("teams") || p.contains("discord") || p.contains("dialer") ||
                p.contains("phone") || p.contains("contacts") -> UsageAnalyticsCategory.COMMUNICATION

            else -> UsageAnalyticsCategory.OTHER
        }
    }

    fun suggestBlockedCategory(packageName: String, appLabel: String = ""): BlockedAppCategory {
        return when (classifyPackage(packageName, appLabel)) {
            UsageAnalyticsCategory.SOCIAL_MEDIA -> BlockedAppCategory.SOCIAL_MEDIA
            UsageAnalyticsCategory.SHORT_VIDEO -> BlockedAppCategory.SHORT_VIDEO
            UsageAnalyticsCategory.GAMING -> BlockedAppCategory.GAMES
            UsageAnalyticsCategory.OTT_VIDEO -> BlockedAppCategory.ENTERTAINMENT
            UsageAnalyticsCategory.SHOPPING -> BlockedAppCategory.SHOPPING
            UsageAnalyticsCategory.COMMUNICATION,
            UsageAnalyticsCategory.STUDY_PRODUCTIVITY -> BlockedAppCategory.OTHER
            UsageAnalyticsCategory.OTHER -> BlockedAppCategory.OTHER
        }
    }

    private fun buildInsights(
        totalScreenTimeMs: Long,
        dailyAverageMs: Long,
        deltaPercent: Int?,
        appEntries: List<AppUsageEntry>,
        categoryEntries: List<CategoryUsageEntry>,
        dailyPoints: List<DailyScreenTimePoint>
    ): List<String> {
        if (totalScreenTimeMs < 60_000L) {
            return listOf("Not enough recorded screen activity in this period yet. Check back after normal phone use.")
        }
        val insights = mutableListOf<String>()

        val topApp = appEntries.firstOrNull()
        if (topApp != null) {
            val pct = (topApp.sharePercent * 100f).toInt()
            insights.add(
                "${topApp.appName} accounts for $pct% of your screen time (${formatDurationCompact(topApp.totalTimeMs)}) across ${topApp.launchCount} opens."
            )
        }

        val distractingMs = categoryEntries.filter { it.category.isDistracting }.sumOf { it.totalTimeMs }
        val distractingPct = ((distractingMs.toFloat() / totalScreenTimeMs.toFloat()) * 100f).toInt()
        if (distractingMs >= 10 * 60_000L) {
            insights.add(
                "Distracting apps took ${formatDurationCompact(distractingMs)} ($distractingPct% of total). Adding your top distracting app to Focus Guard can reclaim that time."
            )
        } else {
            insights.add(
                "Distracting app usage stayed low at $distractingPct% — strong digital discipline."
            )
        }

        if (deltaPercent != null) {
            val direction = if (deltaPercent < 0) "${kotlin.math.abs(deltaPercent)}% less" else "$deltaPercent% more"
            insights.add("You spent $direction screen time compared with the previous matching period.")
        }

        val peakDay = dailyPoints.maxByOrNull { it.totalTimeMs }
        if (peakDay != null && peakDay.totalTimeMs > dailyAverageMs && dailyPoints.size > 1) {
            val dayName = LocalDate.ofEpochDay(peakDay.dateEpochDay).format(monthDayFmt)
            insights.add("Heaviest usage day was $dayName with ${formatDurationCompact(peakDay.totalTimeMs)} total.")
        }

        return insights.take(4)
    }

    fun formatDurationCompact(durationMs: Long): String {
        val totalMinutes = (durationMs / 60_000L).toInt().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            totalMinutes > 0 -> "${mins}m"
            durationMs >= 1_000L -> "<1m"
            else -> "0m"
        }
    }

    fun formatEpochDay(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(monthDayFmt)

    fun formatTimestamp(ms: Long): String {
        if (ms <= 0L) return "Never"
        val dt = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        return dt.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
    }
}
