package com.example.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ActivityAllowanceConfig
import com.example.model.ActivityCategory
import com.example.model.AppCategoryMapping
import com.example.model.ControlledAccessOption
import com.example.model.StudyActivityType
import com.example.system.AppLauncherHelper
import com.example.system.InstalledAppChoice
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.JetBrainsMonoFamily

/**
 * Controlled Phone Access ("What do you need your phone for?"):
 * Fast, calm, large-touch-target allowance selector allowing students to pick a reason,
 * choose a duration (presets or custom), optionally pick a PDF via SAF or an installed app
 * (Play-compliant `<queries>`), and seamlessly pause & preserve the active study session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlledPhoneAccessSheet(
    allowances: ActivityAllowanceConfig,
    appCategoryMappings: List<AppCategoryMapping>,
    todayEntertainmentMinutes: Int,
    hasActiveStudySession: Boolean,
    hapticsEnabled: Boolean,
    onDismiss: () -> Unit,
    onApproveActivity: (
        activityType: StudyActivityType,
        activityLabel: String,
        category: ActivityCategory,
        durationMinutes: Int,
        approvedPackageName: String,
        approvedAppLabel: String,
        approvedDocumentUri: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrainBufferThemeTokens.glass.sheetBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        ControlledPhoneAccessContent(
            allowances = allowances,
            appCategoryMappings = appCategoryMappings,
            todayEntertainmentMinutes = todayEntertainmentMinutes,
            hasActiveStudySession = hasActiveStudySession,
            hapticsEnabled = hapticsEnabled,
            onDismiss = onDismiss,
            onApproveActivity = onApproveActivity
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlledPhoneAccessContent(
    allowances: ActivityAllowanceConfig,
    appCategoryMappings: List<AppCategoryMapping>,
    todayEntertainmentMinutes: Int,
    hasActiveStudySession: Boolean,
    hapticsEnabled: Boolean,
    onDismiss: () -> Unit,
    onApproveActivity: (
        activityType: StudyActivityType,
        activityLabel: String,
        category: ActivityCategory,
        durationMinutes: Int,
        approvedPackageName: String,
        approvedAppLabel: String,
        approvedDocumentUri: String
    ) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var selectedOption by remember { mutableStateOf<ControlledAccessOption?>(null) }
    var durationMinutes by remember { mutableIntStateOf(15) }
    var customMinutesText by remember { mutableStateOf("") }
    var customReasonLabel by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<InstalledAppChoice?>(null) }
    var showAllApps by remember { mutableStateOf(false) }
    var pickedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var pickedPdfName by remember { mutableStateOf("") }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pickedPdfUri = uri
            pickedPdfName = resolveDocumentDisplayName(context, uri)
        }
    }

    val entertainmentLimit = allowances.dailyEntertainmentLimitMinutes.coerceAtLeast(10)
    val entertainmentRemaining = (entertainmentLimit - todayEntertainmentMinutes).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("controlled_phone_access_sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (selectedOption == null) {
            // Step 1: Fast, calm 8-category controlled phone access selector
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "What do you need your phone for?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (hasActiveStudySession) {
                        "Your study session pauses safely and resumes with every remaining second intact."
                    } else {
                        "Choose an intentional activity with a clear time allowance."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Gentle Daily Entertainment Limit indicator
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (entertainmentRemaining == 0) {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                border = BorderStroke(1.dp, BrainBufferThemeTokens.extended.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = if (entertainmentRemaining == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Daily entertainment budget",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${todayEntertainmentMinutes}m / ${entertainmentLimit}m",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (entertainmentRemaining == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // All 7 large touch target options
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlledAccessOption.entries.forEach { option ->
                    val accent = Color(option.activityType.accentHex)
                    val defaultMins = allowances.defaultMinutesFor(option)
                    LiquidGlassCard(
                        tone = LiquidGlassTone.SUBTLE,
                        cornerRadius = 16.dp,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        onClick = {
                            if (hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            selectedOption = option
                            durationMinutes = defaultMins
                            customMinutesText = ""
                            selectedApp = null
                            showAllApps = false
                            pickedPdfUri = null
                            pickedPdfName = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("controlled_access_option_${option.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LiquidGlassIconContainer(
                                size = 42.dp,
                                accentColor = accent,
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Icon(
                                    imageVector = iconForActivity(option.activityType),
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = option.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = accent.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "${defaultMins}m",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Never mind — stay focused")
            }
        } else {
            // Step 2: Allowance Duration + Optional PDF / App Selection + Confirm
            val option = selectedOption!!
            val accent = Color(option.activityType.accentHex)
            val candidateApps = remember(option, showAllApps, appCategoryMappings) {
                if (!option.supportsAppPicker) {
                    emptyList()
                } else if (showAllApps) {
                    AppLauncherHelper.queryLaunchableApps(context, appCategoryMappings, limit = 40)
                } else {
                    AppLauncherHelper.queryCandidateAppsForOption(context, option, appCategoryMappings)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { selectedOption = null },
                    modifier = Modifier.testTag("controlled_access_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to options"
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = option.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (option.activityType.category == ActivityCategory.ENTERTAINMENT) {
                            "Tracked separately from focused study"
                        } else {
                            "Timed allowance with gentle return reminder"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Custom reason input for "Other"
            if (option == ControlledAccessOption.OTHER) {
                OutlinedTextField(
                    value = customReasonLabel,
                    onValueChange = { customReasonLabel = it.take(40) },
                    label = { Text("What do you need to do? (optional)") },
                    placeholder = { Text("e.g., Check exam timetable, Call parents") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("controlled_access_other_input")
                )
            }

            // Duration selector: fast presets + custom minutes
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "How long do you need?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val presets = (listOf(5, 10, 15, 20, 30) + option.defaultPresets).distinct().sorted()
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { mins ->
                        FilterChip(
                            selected = durationMinutes == mins && customMinutesText.isBlank(),
                            onClick = {
                                durationMinutes = mins
                                customMinutesText = ""
                            },
                            label = { Text("${mins}m") },
                            modifier = Modifier.testTag("controlled_access_duration_${mins}m")
                        )
                    }
                }
                OutlinedTextField(
                    value = customMinutesText,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(3)
                        customMinutesText = digits
                        digits.toIntOrNull()?.let { parsed ->
                            if (parsed in 1..180) durationMinutes = parsed
                        }
                    },
                    label = { Text("Or enter custom minutes (1–180)") },
                    placeholder = { Text("Current: ${durationMinutes} min") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("controlled_access_custom_minutes")
                )
            }

            // SAF PDF Picker for Study PDF / Documents (zero broad storage permissions)
            if (option.supportsPdfPicker) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = accent.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (pickedPdfName.isNotBlank()) pickedPdfName else "Choose a Study PDF (optional)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Uses Android's private file picker — no storage permission needed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("controlled_access_pick_pdf_button")
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (pickedPdfUri == null) "Pick PDF" else "Change")
                        }
                    }
                }
            }

            // Play-compliant installed app selector
            if (option.supportsAppPicker) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Choose app to open (optional)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        TextButton(onClick = { showAllApps = !showAllApps }) {
                            Text(if (showAllApps) "Suggested" else "Show all")
                        }
                    }

                    if (candidateApps.isEmpty()) {
                        Text(
                            text = "No matching apps found — you can still start the timed allowance and open your app manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            candidateApps.forEach { app ->
                                val chosen = selectedApp?.packageName == app.packageName
                                FilterChip(
                                    selected = chosen,
                                    onClick = {
                                        selectedApp = if (chosen) null else app
                                    },
                                    label = {
                                        Text(
                                            text = app.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = if (chosen) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }

            // Gentle Entertainment Budget notice (never punitive, never force-closes)
            if (option.activityType.category == ActivityCategory.ENTERTAINMENT &&
                todayEntertainmentMinutes + durationMinutes > entertainmentLimit
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (entertainmentRemaining == 0) {
                                "You've already reached today's ${entertainmentLimit}m entertainment budget. You're still free to continue — we'll gently remind you when ${durationMinutes}m is up."
                            } else {
                                "You have ${entertainmentRemaining}m left in today's ${entertainmentLimit}m entertainment budget. Consider a shorter timer or proceed intentionally."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            val finalMinutes = durationMinutes.coerceIn(1, 180)
            val resolvedLabel = when {
                option == ControlledAccessOption.OTHER && customReasonLabel.isNotBlank() -> customReasonLabel.trim()
                pickedPdfName.isNotBlank() -> "PDF · $pickedPdfName"
                selectedApp != null -> "${option.shortTitle} · ${selectedApp!!.label}"
                else -> option.title
            }

            LiquidGlassButton(
                onClick = {
                    if (hapticsEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onApproveActivity(
                        option.activityType,
                        resolvedLabel,
                        option.activityType.category,
                        finalMinutes,
                        selectedApp?.packageName.orEmpty(),
                        selectedApp?.label.orEmpty(),
                        pickedPdfUri?.toString().orEmpty()
                    )
                    // Launch chosen PDF or installed app right after starting the tracked allowance
                    when {
                        pickedPdfUri != null -> {
                            AppLauncherHelper.openPdfDocument(context, pickedPdfUri!!, selectedApp?.packageName)
                        }
                        selectedApp != null -> {
                            AppLauncherHelper.launchApp(context, selectedApp!!.packageName)
                        }
                    }
                },
                cornerRadius = 16.dp,
                height = 54.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("controlled_access_confirm_button")
            ) {
                Icon(
                    imageVector = if (pickedPdfUri != null || selectedApp != null) Icons.Rounded.Launch else Icons.Rounded.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        pickedPdfUri != null -> "Open PDF & Track ${finalMinutes}m"
                        selectedApp != null -> "Open ${selectedApp!!.label} · ${finalMinutes}m"
                        else -> "Start ${option.shortTitle} · ${finalMinutes}m"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

private fun resolveDocumentDisplayName(context: android.content.Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.take(36)
            } else null
        }
    }.getOrNull().orEmpty().ifBlank {
        uri.lastPathSegment?.substringAfterLast('/')?.take(36) ?: "Study Document.pdf"
    }
}
