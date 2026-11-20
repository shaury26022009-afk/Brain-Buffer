package com.example.ui.components

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ActivityCategory
import com.example.model.CustomActivityOption
import com.example.model.StudyActivityType
import com.example.model.Subject
import com.example.ui.theme.BrainBufferThemeTokens

/**
 * Smart Activity Switcher sheet supporting all 9 built-in student activities plus custom user activities.
 * Automatically pauses and preserves any active parent study session while tracking switched time separately.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivitySwitcherSheet(
    subjects: List<Subject>,
    customActivities: List<CustomActivityOption>,
    selectedSubjectId: Long?,
    hasActiveStudySession: Boolean,
    onDismiss: () -> Unit,
    onConfirmSwitch: (
        activityType: StudyActivityType,
        label: String,
        category: ActivityCategory,
        durationMinutes: Int,
        subjectId: Long?,
        wasPlanned: Boolean
    ) -> Unit,
    onAddCustomActivity: (label: String, category: ActivityCategory, defaultMinutes: Int) -> Unit,
    onDeleteCustomActivity: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedBuiltIn by remember { mutableStateOf(StudyActivityType.STUDY_LECTURE) }
    var selectedCustom by remember { mutableStateOf<CustomActivityOption?>(null) }
    var durationMinutes by remember { mutableIntStateOf(StudyActivityType.STUDY_LECTURE.defaultMinutes) }
    var chosenSubjectId by remember { mutableStateOf(selectedSubjectId) }
    var wasPlanned by remember { mutableStateOf(true) }

    var showCustomForm by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customCategory by remember { mutableStateOf(ActivityCategory.OTHER) }
    var customMinutes by remember { mutableIntStateOf(20) }

    val activeLabel = selectedCustom?.label ?: selectedBuiltIn.label
    val activeCategory = selectedCustom?.category ?: selectedBuiltIn.category

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrainBufferThemeTokens.glass.sheetBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag("activity_switcher_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Switch Activity",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close switcher")
                }
            }

            if (hasActiveStudySession) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Your current study timer will pause automatically and stay ready so you can return with one tap.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Text(
                text = "What are you switching to?",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 9 Built-in Activities
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StudyActivityType.entries.forEach { activity ->
                    val isSelected = selectedCustom == null && selectedBuiltIn == activity
                    val accent = Color(activity.accentHex)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accent else BrainBufferThemeTokens.extended.cardBorder
                        ),
                        onClick = {
                            selectedCustom = null
                            selectedBuiltIn = activity
                            durationMinutes = activity.defaultMinutes
                        },
                        modifier = Modifier.testTag("activity_option_${activity.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = iconForActivity(activity),
                                contentDescription = null,
                                tint = if (isSelected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = activity.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Custom Activities Section
            if (customActivities.isNotEmpty()) {
                Text(
                    text = "Your Custom Activities",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    customActivities.forEach { custom ->
                        val isSelected = selectedCustom?.id == custom.id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedCustom = custom
                                durationMinutes = custom.defaultDurationMinutes
                            },
                            label = { Text("${custom.label} · ${custom.defaultDurationMinutes}m") },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (selectedCustom?.id == custom.id) selectedCustom = null
                                        onDeleteCustomActivity(custom.id)
                                    },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Delete ${custom.label}",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Toggle inline custom activity creator
            if (!showCustomForm) {
                OutlinedButton(
                    onClick = { showCustomForm = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("add_custom_activity_toggle")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Custom Activity")
                }
            } else {
                SanctuaryCard(
                    contentPadding = PaddingValues(14.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "New Custom Activity",
                            style = MaterialTheme.typography.titleSmall
                        )
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it.take(32) },
                            label = { Text("Activity name (e.g., Lab prep, Sketching)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ActivityCategory.entries.forEach { cat ->
                                FilterChip(
                                    selected = customCategory == cat,
                                    onClick = { customCategory = cat },
                                    label = { Text(cat.label) }
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(10, 20, 30, 45).forEach { mins ->
                                FilterChip(
                                    selected = customMinutes == mins,
                                    onClick = { customMinutes = mins },
                                    label = { Text("${mins}m") }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showCustomForm = false },
                                modifier = Modifier.weight(1f)
                            ) { Text("Cancel") }
                            Button(
                                onClick = {
                                    if (customName.isNotBlank()) {
                                        onAddCustomActivity(customName.trim(), customCategory, customMinutes)
                                        customName = ""
                                        showCustomForm = false
                                    }
                                },
                                enabled = customName.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) { Text("Save") }
                        }
                    }
                }
            }

            // Duration Picker
            Text(
                text = "Set timer for $activeLabel (${durationMinutes} min)",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 10, 15, 20, 30, 45, 60).forEach { mins ->
                    FilterChip(
                        selected = durationMinutes == mins,
                        onClick = { durationMinutes = mins },
                        label = { Text("${mins}m") },
                        modifier = Modifier.testTag("activity_duration_${mins}m")
                    )
                }
            }

            // Optional Subject tag (for study/lecture/reading/notes)
            if (subjects.isNotEmpty() && activeCategory != ActivityCategory.ENTERTAINMENT && activeCategory != ActivityCategory.BREAK) {
                Text(
                    text = "Related Subject",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    subjects.forEach { subject ->
                        FilterChip(
                            selected = chosenSubjectId == subject.id,
                            onClick = { chosenSubjectId = if (chosenSubjectId == subject.id) null else subject.id },
                            label = { Text(subject.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(subject.accentColor)
                                )
                            }
                        )
                    }
                }
            }

            // Planned vs Unplanned toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Planned activity",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (activeCategory.countsAsFocusedStudy) {
                            "Counts toward focused study stats & garden growth"
                        } else {
                            "Tracked separately under ${activeCategory.label} — never inflates study time"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = wasPlanned,
                    onCheckedChange = { wasPlanned = it }
                )
            }

            LiquidGlassButton(
                onClick = {
                    onConfirmSwitch(
                        selectedBuiltIn,
                        activeLabel,
                        activeCategory,
                        durationMinutes,
                        chosenSubjectId,
                        wasPlanned
                    )
                },
                cornerRadius = 16.dp,
                height = 52.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("confirm_switch_activity_button")
            ) {
                Text(
                    text = "Start $activeLabel (${durationMinutes}m)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
