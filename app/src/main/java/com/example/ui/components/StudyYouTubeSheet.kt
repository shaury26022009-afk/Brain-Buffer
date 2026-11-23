package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Subscriptions
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.StudyResource
import com.example.model.StudyResourceType
import com.example.model.Subject
import com.example.system.AppLauncherHelper
import com.example.ui.theme.BrainBufferThemeTokens

/**
 * Dedicated Study-Only YouTube experience.
 *
 * Technical honesty:
 * - Android does not let third-party apps inspect arbitrary playback inside YouTube's official app.
 * - Instead, Brain Buffer gives students a clean allowlist of Approved Study Video Links,
 *   Playlists, and Study Channels grouped by subject with one-tap launch and quick return to Focus.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudyYouTubeSheet(
    resources: List<StudyResource>,
    subjects: List<Subject>,
    onAddResource: (title: String, url: String, type: StudyResourceType, subjectId: Long?, channelName: String) -> Unit,
    onLaunchResource: (StudyResource) -> Unit,
    onDeleteResource: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedSubjectFilter by remember { mutableStateOf<Long?>(null) }
    var showAddForm by remember { mutableStateOf(false) }
    var titleInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var channelInput by remember { mutableStateOf("") }
    var typeInput by remember { mutableStateOf(StudyResourceType.VIDEO) }
    var subjectForNew by remember { mutableStateOf<Long?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }

    val filteredResources = remember(resources, selectedSubjectFilter) {
        if (selectedSubjectFilter == null) resources
        else resources.filter { it.subjectId == selectedSubjectFilter }
    }
    val recentOpened = remember(resources) {
        resources.filter { it.lastOpenedAtMs > 0L }.sortedByDescending { it.lastOpenedAtMs }.take(4)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BrainBufferThemeTokens.glass.sheetBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("study_youtube_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Study-Only YouTube",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Approved lectures, playlists & study channels",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { showAddForm = !showAddForm },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("study_yt_add_toggle")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (showAddForm) "Close" else "Add Link")
                }
            }

            // Honest scope notice
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, BrainBufferThemeTokens.extended.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Launch your saved study videos, playlists, and channels directly so you skip the distracting home feed. Return to Focus anytime from the live timer notification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Add Approved Study Link form
            if (showAddForm) {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tone = LiquidGlassTone.SUBTLE,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Save Approved Study Resource",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StudyResourceType.entries.forEach { t ->
                                FilterChip(
                                    selected = typeInput == t,
                                    onClick = { typeInput = t },
                                    label = { Text(t.label) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it.take(100) },
                            label = { Text("Lecture / Playlist / Channel Title") },
                            placeholder = { Text("e.g., MIT 18.06 Linear Algebra") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("study_yt_title_input")
                        )
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = {
                                urlInput = it
                                urlError = null
                            },
                            label = { Text("HTTPS Link") },
                            placeholder = { Text("https://youtube.com/watch?v=...") },
                            isError = urlError != null,
                            supportingText = urlError?.let { msg -> { Text(msg) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("study_yt_url_input")
                        )
                        OutlinedTextField(
                            value = channelInput,
                            onValueChange = { channelInput = it.take(60) },
                            label = { Text("Channel or Educator (optional)") },
                            placeholder = { Text("e.g., 3Blue1Brown, Khan Academy") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (subjects.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = subjectForNew == null,
                                    onClick = { subjectForNew = null },
                                    label = { Text("General") }
                                )
                                subjects.forEach { subject ->
                                    FilterChip(
                                        selected = subjectForNew == subject.id,
                                        onClick = { subjectForNew = subject.id },
                                        label = { Text(subject.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                        }
                        Button(
                            onClick = {
                                val candidateUrl = urlInput.trim().let {
                                    if (it.startsWith("http://", ignoreCase = true)) "https://${it.removePrefix("http://")}"
                                    else if (!it.startsWith("https://", ignoreCase = true) && it.contains("youtu")) "https://$it"
                                    else it
                                }
                                if (!AppLauncherHelper.isValidStudyUrl(candidateUrl)) {
                                    urlError = "Enter a valid https:// YouTube or lecture link."
                                    return@Button
                                }
                                if (titleInput.isBlank()) {
                                    urlError = "Please give this study link a title."
                                    return@Button
                                }
                                onAddResource(titleInput, candidateUrl, typeInput, subjectForNew, channelInput)
                                titleInput = ""
                                urlInput = ""
                                channelInput = ""
                                showAddForm = false
                            },
                            modifier = Modifier.fillMaxWidth().testTag("study_yt_save_button")
                        ) {
                            Text("Save to Approved Study Library")
                        }
                    }
                }
            }

            // Subject Filter Row
            if (subjects.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedSubjectFilter == null,
                        onClick = { selectedSubjectFilter = null },
                        label = { Text("All Subjects") }
                    )
                    subjects.forEach { subj ->
                        FilterChip(
                            selected = selectedSubjectFilter == subj.id,
                            onClick = { selectedSubjectFilter = subj.id },
                            label = { Text(subj.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }

            // Recent Study Videos
            if (recentOpened.isNotEmpty() && selectedSubjectFilter == null) {
                Text(
                    text = "Recent Study Videos",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                recentOpened.forEach { resource ->
                    StudyResourceRow(
                        resource = resource,
                        subjectName = subjects.firstOrNull { it.id == resource.subjectId }?.name,
                        onOpen = {
                            onLaunchResource(resource)
                            AppLauncherHelper.openStudyUrl(context, resource.url)
                        },
                        onDelete = { onDeleteResource(resource.id) }
                    )
                }
            }

            // Full Approved Library
            Text(
                text = "Approved Study Library (${filteredResources.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (filteredResources.isEmpty()) {
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tone = LiquidGlassTone.SUBTLE
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SmartDisplay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = "No approved study links saved yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap 'Add Link' to save YouTube lectures, course playlists, or study channels for distraction-free launching during Focus sessions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                filteredResources.forEach { resource ->
                    StudyResourceRow(
                        resource = resource,
                        subjectName = subjects.firstOrNull { it.id == resource.subjectId }?.name,
                        onOpen = {
                            onLaunchResource(resource)
                            AppLauncherHelper.openStudyUrl(context, resource.url)
                        },
                        onDelete = { onDeleteResource(resource.id) }
                    )
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Return to Focus")
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun StudyResourceRow(
    resource: StudyResource,
    subjectName: String?,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = when (resource.resourceType) {
        StudyResourceType.VIDEO -> Icons.Rounded.PlayArrow
        StudyResourceType.PLAYLIST -> Icons.Rounded.PlaylistPlay
        StudyResourceType.CHANNEL -> Icons.Rounded.Subscriptions
    }
    val accent = when (resource.resourceType) {
        StudyResourceType.VIDEO -> Color(0xFF3B82F6)
        StudyResourceType.PLAYLIST -> Color(0xFF8B5CF6)
        StudyResourceType.CHANNEL -> Color(0xFF10B981)
    }

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        tone = LiquidGlassTone.SUBTLE,
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resource.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = buildString {
                    append(resource.resourceType.label)
                    if (resource.channelName.isNotBlank()) append(" · ${resource.channelName}")
                    if (!subjectName.isNullOrBlank()) append(" · $subjectName")
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Remove study resource",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
