package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.TemporaryAccessPolicy
import com.example.model.SelectedSessionApp
import com.example.model.SessionCelebration
import com.example.model.SessionPurposeCategory
import com.example.model.TemporaryAppAccessSession
import com.example.system.InstalledAppChoice
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassChip
import com.example.ui.components.LiquidGlassSecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private object AppIconBitmapCache {
    private val cache = object : LruCache<String, ImageBitmap>(96) {}

    fun get(packageName: String): ImageBitmap? = synchronized(cache) {
        cache.get(packageName)
    }

    fun put(packageName: String, bitmap: ImageBitmap) {
        synchronized(cache) {
            cache.put(packageName, bitmap)
        }
    }
}

/**
 * Loads the real Android launcher icon for an installed package off the main thread
 * with an in-memory LRU bitmap cache so scrolling app pickers stays silky smooth,
 * falling back gracefully to a deterministic letter avatar when unavailable.
 */
@Composable
fun AppIconBadge(
    packageName: String,
    appLabel: String,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cachedInitial = remember(packageName) {
        if (packageName.isBlank()) null else AppIconBitmapCache.get(packageName)
    }
    val bitmap by produceState<ImageBitmap?>(initialValue = cachedInitial, key1 = packageName) {
        if (packageName.isBlank()) {
            value = null
            return@produceState
        }
        AppIconBitmapCache.get(packageName)?.let { cached ->
            value = cached
            return@produceState
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val targetPx = 96
                val bmp = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                    drawable.bitmap
                } else {
                    val w = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(targetPx) ?: targetPx
                    val h = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(targetPx) ?: targetPx
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { created ->
                        val canvas = Canvas(created)
                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                        drawable.draw(canvas)
                    }
                }
                bmp.asImageBitmap()
            }.getOrNull()
        }
        if (loaded != null) {
            AppIconBitmapCache.put(packageName, loaded)
        }
        value = loaded
    }

    val fallbackColor = remember(packageName, appLabel) {
        val palette = listOf(
            Color(0xFF7B5CFA),
            Color(0xFF3B82F6),
            Color(0xFF10B981),
            Color(0xFFF59E0B),
            Color(0xFF06B6D4),
            Color(0xFFEC4899)
        )
        palette[abs((packageName.ifBlank { appLabel }).hashCode()) % palette.size]
    }
    val initial = appLabel.trim().firstOrNull()?.uppercase() ?: "A"

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = "$appLabel icon",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(fallbackColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = fallbackColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledAppMultiPickerSheet(
    installedApps: List<InstalledAppChoice>,
    initialSelected: List<SelectedSessionApp>,
    onConfirm: (List<SelectedSessionApp>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedMap by remember(initialSelected) {
        mutableStateOf(initialSelected.associate { it.packageName to it.appLabel })
    }

    val filteredApps = remember(installedApps, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) installedApps
        else installedApps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Allowed apps for this session",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Emergency calls, Settings, System UI, accessibility tools, and BRAIN BUFFER stay accessible automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close app picker")
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("allowed_apps_search_input")
            )

            if (selectedMap.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(selectedMap.entries.toList(), key = { "sel_${it.key}" }) { (pkg, label) ->
                        LiquidGlassChip(
                            label = label,
                            selected = true,
                            onClick = { selectedMap = selectedMap - pkg }
                        )
                    }
                }
            }

            if (filteredApps.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (installedApps.isEmpty()) {
                            "No launchable apps were returned on this device."
                        } else {
                            "No installed apps match \"$searchQuery\"."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isChecked = app.packageName in selectedMap
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isChecked) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedMap = if (isChecked) {
                                        selectedMap - app.packageName
                                    } else {
                                        selectedMap + (app.packageName to app.label)
                                    }
                                }
                                .testTag("app_picker_row_${app.packageName}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AppIconBadge(packageName = app.packageName, appLabel = app.label, size = 36.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedMap = if (checked) {
                                            selectedMap + (app.packageName to app.label)
                                        } else {
                                            selectedMap - app.packageName
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selectedMap.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { selectedMap = emptyMap() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear (${selectedMap.size})")
                    }
                }
                LiquidGlassButton(
                    onClick = {
                        onConfirm(
                            selectedMap.entries.map { SelectedSessionApp(it.key, it.value) }
                        )
                    },
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("allowed_apps_confirm_button")
                ) {
                    Text(
                        text = if (selectedMap.isEmpty()) "Done" else "Use ${selectedMap.size} Apps",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppSwitchSheet(
    installedApps: List<InstalledAppChoice>,
    preselectedPackage: String? = null,
    preselectedLabel: String? = null,
    onConfirmSwitch: (packageName: String, appLabel: String, durationMinutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var chosenApp by remember(preselectedPackage, preselectedLabel) {
        mutableStateOf(
            if (!preselectedPackage.isNullOrBlank()) {
                InstalledAppChoice(
                    packageName = preselectedPackage,
                    label = preselectedLabel.orEmpty().ifBlank { preselectedPackage.substringAfterLast('.') }
                )
            } else {
                null
            }
        )
    }
    var durationMinutes by remember { mutableIntStateOf(5) }
    val presets = listOf(3, 5, 10, 15)

    val filtered = remember(installedApps, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) installedApps
        else installedApps.filter {
            it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Temporary App Switch",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pauses your focus timer and allows one app temporarily. Never counted as focus time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close App Switch")
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_switch_search_input")
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { "sw_${it.packageName}" }) { app ->
                    val isSelected = chosenApp?.packageName == app.packageName
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { chosenApp = app }
                            .testTag("app_switch_item_${app.packageName}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppIconBadge(packageName = app.packageName, appLabel = app.label, size = 34.dp)
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "Temporary access duration",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { mins ->
                    LiquidGlassChip(
                        label = "${mins} min",
                        selected = durationMinutes == mins,
                        onClick = { durationMinutes = mins }
                    )
                }
            }

            Text(
                text = "When time expires, you'll be asked to return to focus or request one final extension (max ${TemporaryAccessPolicy.MAX_EXTENSION_MINUTES} min).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LiquidGlassButton(
                onClick = {
                    val target = chosenApp ?: return@LiquidGlassButton
                    onConfirmSwitch(target.packageName, target.label, durationMinutes)
                },
                enabled = chosenApp != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_switch_confirm_button")
            ) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (chosenApp != null) {
                        "Allow ${chosenApp!!.label} for ${durationMinutes}m"
                    } else {
                        "Select an app to switch"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BreakPickerSheet(
    defaultBreakMinutes: Int,
    onStartBreak: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMinutes by remember(defaultBreakMinutes) {
        mutableIntStateOf(defaultBreakMinutes.coerceIn(3, 20))
    }
    val options = listOf(3, 5, 10, 15)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Coffee,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Column {
                    Text(
                        text = "Take a Break",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Focus time pauses during breaks and is never mixed with break time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { mins ->
                    LiquidGlassChip(
                        label = "${mins} min",
                        selected = selectedMinutes == mins,
                        onClick = { selectedMinutes = mins }
                    )
                }
            }

            LiquidGlassButton(
                onClick = { onStartBreak(selectedMinutes) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("break_sheet_start_button")
            ) {
                Text("Start ${selectedMinutes}-Minute Break", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun ExitSessionConfirmationDialog(
    sessionName: String,
    elapsedFormatted: String,
    plannedMinutes: Int,
    onFinishEarly: () -> Unit,
    onCancelSession: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exit \"$sessionName\"?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Elapsed so far: $elapsedFormatted of ${plannedMinutes}m planned.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "• Finish early records only your actual elapsed time (marked Ended Early — never full planned duration).\n" +
                        "• Cancel session discards this timer without logging focus time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onFinishEarly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exit_dialog_finish_early_button")
                ) {
                    Text("Finish Early (Save $elapsedFormatted)")
                }
                OutlinedButton(
                    onClick = onCancelSession,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exit_dialog_cancel_session_button")
                ) {
                    Text("Cancel Session (Discard)")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Keep Focusing")
                }
            }
        }
    )
}

@Composable
fun TemporaryAccessExpiredDialog(
    session: TemporaryAppAccessSession,
    onSwitchToFocus: () -> Unit,
    onRequestExtension: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* User must consciously choose an action */ },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Time is up. Return to your focus session?")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (session.canRequestExtension) {
                        "Your ${session.originalAllowanceMinutes}-minute temporary access to ${session.appLabel} has ended."
                    } else {
                        "Your temporary access and one-time 5-minute extension for ${session.appLabel} have ended."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (session.canRequestExtension) {
                    Text(
                        text = "Need more time is strictly limited to a single extension of up to ${TemporaryAccessPolicy.MAX_EXTENSION_MINUTES} minutes. Temporary time is never counted as focus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSwitchToFocus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("temp_expired_switch_to_focus_button")
                ) {
                    Text("Switch to Focus")
                }
                if (session.canRequestExtension) {
                    OutlinedButton(
                        onClick = { onRequestExtension(TemporaryAccessPolicy.MAX_EXTENSION_MINUTES) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("temp_expired_need_more_time_button")
                    ) {
                        Text("Need more time (+${TemporaryAccessPolicy.MAX_EXTENSION_MINUTES} min max)")
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChangePurposeSheet(
    currentPurpose: SessionPurposeCategory,
    currentSessionName: String,
    currentAllowedApps: List<SelectedSessionApp>,
    installedApps: List<InstalledAppChoice>,
    onConfirmChange: (SessionPurposeCategory, String, List<SelectedSessionApp>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPurpose by remember(currentPurpose) { mutableStateOf(currentPurpose) }
    var sessionName by remember(currentSessionName) { mutableStateOf(currentSessionName) }
    var allowedApps by remember(currentAllowedApps) { mutableStateOf(currentAllowedApps) }
    var showAppPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Change Session Purpose",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Update what you're using your phone for right now. Elapsed focus time stays preserved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SessionPurposeCategory.entries.forEach { cat ->
                    LiquidGlassChip(
                        label = cat.label,
                        selected = selectedPurpose == cat,
                        onClick = {
                            selectedPurpose = cat
                            if (sessionName.isBlank()) {
                                sessionName = cat.defaultSessionPlaceholder
                            }
                        }
                    )
                }
            }

            OutlinedTextField(
                value = sessionName,
                onValueChange = { sessionName = it },
                label = { Text("Session intention") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            LiquidGlassSecondaryButton(
                onClick = { showAppPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Allowed apps (${allowedApps.size} selected)")
            }

            LiquidGlassButton(
                onClick = {
                    onConfirmChange(
                        selectedPurpose,
                        sessionName.trim().ifBlank { selectedPurpose.defaultSessionPlaceholder },
                        allowedApps
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("change_purpose_confirm_button")
            ) {
                Text("Update Purpose", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (showAppPicker) {
        InstalledAppMultiPickerSheet(
            installedApps = installedApps,
            initialSelected = allowedApps,
            onConfirm = { picked ->
                allowedApps = picked
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
    }
}

@Composable
fun PurposeCompletionDialog(
    celebration: SessionCelebration,
    onDismiss: () -> Unit,
    onStartBreak: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                if (celebration.endedEarly) "Focus Session Saved"
                else "Focus Session Complete"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val plannedMin = (celebration.plannedSeconds / 60).coerceAtLeast(1)
                Text(
                    text = if (celebration.endedEarly) {
                        "Logged ${celebration.durationMinutes} min actual focus (of ${plannedMin}m planned) for ${celebration.subjectName}."
                    } else {
                        "Logged ${celebration.durationMinutes} min of ${celebration.activityLabel} (${celebration.subjectName})."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Your session has been saved to Intent vs. Reality Insights.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.testTag("completion_dialog_done_button")
            ) {
                Text("Done")
            }
        },
        dismissButton = if (!celebration.endedEarly) {
            {
                TextButton(
                    onClick = {
                        onDismiss()
                        onStartBreak(5)
                    }
                ) {
                    Text("Take 5m Break")
                }
            }
        } else {
            null
        }
    )
}
