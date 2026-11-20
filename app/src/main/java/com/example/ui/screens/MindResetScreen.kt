package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BreathingPattern
import com.example.model.MindNote
import com.example.model.MindNoteType
import com.example.ui.components.LiquidGlassButton
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassSecondaryButton
import com.example.ui.components.LiquidGlassTone
import com.example.ui.components.SanctuaryBackdrop
import com.example.ui.components.SanctuaryCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SubScreenTopBar
import com.example.ui.components.springClickable
import com.example.ui.theme.BrainBufferThemeTokens
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.viewmodel.BrainBufferUiState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class BreathPhase(val label: String, val cue: String) {
    READY("Ready", "Tap Begin for a gentle reset"),
    INHALE("Breathe In", "Slowly through your nose"),
    HOLD("Hold Gently", "Soften your shoulders"),
    EXHALE("Breathe Out", "Let go of tension"),
    REST("Pause", "Notice the quiet")
}

private data class BreakIdea(
    val id: String,
    val title: String,
    val duration: String,
    val detail: String
)

private val GentleBreakIdeas = listOf(
    BreakIdea("water", "Drink a glass of water", "1 min", "Hydration refreshes attention faster than scrolling."),
    BreakIdea("eyes", "20-20-20 Eye Rest", "1 min", "Look at something 20 feet away for 20 seconds."),
    BreakIdea("stretch", "Shoulder & neck release", "2 min", "Roll your shoulders back and unclench your jaw."),
    BreakIdea("walk", "Step away from screens", "5 min", "Walk to another room or window with no phone.")
)

private val GentleReminders = listOf(
    "Losing focus is human. Coming back calmly is the skill.",
    "You do not need to finish everything today — just the next small piece.",
    "A 3-minute pause now saves 30 minutes of foggy studying.",
    "Progress grows quietly, one honest block at a time."
)

private val ReflectionMoods = listOf("Calm", "Focused", "Refreshed", "Tired", "Hopeful")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MindResetScreen(
    uiState: BrainBufferUiState,
    onBack: () -> Unit,
    onCycleCompleted: () -> Unit,
    onAddDistractionNote: (String) -> Unit,
    onAddReflection: (text: String, moodTag: String) -> Unit,
    onDeleteMindNote: (Long) -> Unit,
    onReturnToFocus: () -> Unit
) {
    val extended = BrainBufferThemeTokens.extended
    val reducedMotion = BrainBufferThemeTokens.reducedMotion

    var selectedPattern by remember { mutableStateOf(BreathingPattern.CALM_4_4_6) }
    var isBreathing by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(BreathPhase.READY) }
    var secondsLeftInPhase by remember { mutableIntStateOf(0) }
    var cyclesThisVisit by remember { mutableIntStateOf(0) }

    var reminderIndex by remember { mutableIntStateOf(0) }
    val completedBreakIds = remember { mutableStateListOf<String>() }

    var dumpInput by remember { mutableStateOf("") }
    var reflectionInput by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf(ReflectionMoods.first()) }

    val dumpNotes = remember(uiState.mindNotes) {
        uiState.mindNotes.filter { it.type == MindNoteType.DISTRACTION_DUMP }
    }
    val reflectionNotes = remember(uiState.mindNotes) {
        uiState.mindNotes.filter { it.type == MindNoteType.SESSION_REFLECTION }
    }

    LaunchedEffect(isBreathing, selectedPattern) {
        if (!isBreathing) {
            phase = BreathPhase.READY
            secondsLeftInPhase = 0
            return@LaunchedEffect
        }
        val sequence = listOf(
            BreathPhase.INHALE to selectedPattern.inhaleSec,
            BreathPhase.HOLD to selectedPattern.holdSec,
            BreathPhase.EXHALE to selectedPattern.exhaleSec,
            BreathPhase.REST to selectedPattern.restSec
        )
        while (isBreathing) {
            for ((stepPhase, durationSec) in sequence) {
                if (!isBreathing) break
                if (durationSec <= 0) continue
                phase = stepPhase
                for (remaining in durationSec downTo 1) {
                    if (!isBreathing) break
                    secondsLeftInPhase = remaining
                    delay(1_000L)
                }
            }
            if (isBreathing) {
                cyclesThisVisit += 1
                onCycleCompleted()
            }
        }
    }

    val targetOrbScale = when (phase) {
        BreathPhase.READY -> 0.66f
        BreathPhase.INHALE -> 1.0f
        BreathPhase.HOLD -> 1.0f
        BreathPhase.EXHALE -> 0.62f
        BreathPhase.REST -> 0.62f
    }
    val animDuration = when {
        reducedMotion -> 0
        phase == BreathPhase.INHALE -> selectedPattern.inhaleSec * 1000
        phase == BreathPhase.EXHALE -> selectedPattern.exhaleSec * 1000
        else -> 450
    }
    val orbScale by animateFloatAsState(
        targetValue = targetOrbScale,
        animationSpec = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
        label = "breathOrbScale"
    )

    SanctuaryBackdrop {
        Column(modifier = Modifier.fillMaxSize()) {
            SubScreenTopBar(
                title = "Mind Reset",
                subtitle = "Breathe, clear distractions & reflect",
                onBack = onBack
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                        .testTag("mind_reset_list"),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // 1. Calm Hero Banner + Personalized Distraction Tip
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                                .clip(RoundedCornerShape(22.dp))
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_mind_reset_calm),
                                contentDescription = "Calm twilight sanctuary",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color(0x55000000),
                                                Color(0xCC0A0A0A)
                                            )
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color(0x40FFFFFF)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Spa,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "${uiState.preferences.mindResetCount} resets completed",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { reminderIndex = (reminderIndex + 1) % GentleReminders.size },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = "Next gentle reminder",
                                            tint = Color.White
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = GentleReminders[reminderIndex],
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = uiState.distractionTip,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Guided Breathing Exercise
                    item {
                        SanctuaryCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("breathing_card")
                        ) {
                            SectionHeader(
                                title = "Guided Breathing",
                                subtitle = if (cyclesThisVisit > 0) "$cyclesThisVisit cycle${if (cyclesThisVisit == 1) "" else "s"} completed now" else "Choose a gentle cadence"
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BreathingPattern.entries.forEach { pattern ->
                                    val selected = pattern == selectedPattern
                                    LiquidGlassCard(
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize(),
                                        tone = if (selected) LiquidGlassTone.ACCENT else LiquidGlassTone.SUBTLE,
                                        cornerRadius = 14.dp,
                                        borderAccent = if (selected) MaterialTheme.colorScheme.primary else null,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                        onClick = {
                                            selectedPattern = pattern
                                            if (isBreathing) {
                                                isBreathing = false
                                            }
                                        }
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = pattern.title,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = pattern.subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Breathing Orb
                            val primaryColor = MaterialTheme.colorScheme.primary
                            val sageColor = extended.sageAccent
                            val orbTint by animateColorAsState(
                                targetValue = when (phase) {
                                    BreathPhase.EXHALE, BreathPhase.REST -> sageColor
                                    else -> primaryColor
                                },
                                label = "orbTint"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(196.dp)) {
                                    val maxRadius = size.minDimension / 2f
                                    drawCircle(
                                        color = orbTint.copy(alpha = 0.12f),
                                        radius = maxRadius * orbScale
                                    )
                                    drawCircle(
                                        color = orbTint.copy(alpha = 0.22f),
                                        radius = maxRadius * orbScale * 0.78f
                                    )
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            listOf(
                                                orbTint.copy(alpha = 0.85f),
                                                orbTint.copy(alpha = 0.45f)
                                            )
                                        ),
                                        radius = maxRadius * orbScale * 0.56f
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = phase.label,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    if (isBreathing && secondsLeftInPhase > 0) {
                                        Text(
                                            text = "${secondsLeftInPhase}s",
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            Text(
                                text = phase.cue,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            if (isBreathing) {
                                LiquidGlassSecondaryButton(
                                    onClick = { isBreathing = false },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("toggle_breathing_button"),
                                    cornerRadius = 15.dp,
                                    height = 50.dp
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pause Breathing", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                }
                            } else {
                                LiquidGlassButton(
                                    onClick = { isBreathing = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("toggle_breathing_button"),
                                    cornerRadius = 15.dp,
                                    height = 50.dp
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Air,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Begin Breathing Exercise", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }

                    // 3. Short Break Suggestions
                    item {
                        SanctuaryCard(modifier = Modifier.fillMaxWidth()) {
                            SectionHeader(
                                title = "Short Break Ideas",
                                subtitle = "Tap any reset you try"
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GentleBreakIdeas.forEach { idea ->
                                    val done = idea.id in completedBreakIds
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .springClickable {
                                                if (done) completedBreakIds.remove(idea.id)
                                                else completedBreakIds.add(idea.id)
                                            }
                                            .minimumInteractiveComponentSize(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (done) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (done) extended.sageAccent.copy(alpha = 0.5f) else extended.cardBorder
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (done) extended.sageAccent
                                                        else MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (done) Icons.Rounded.Check else Icons.Rounded.SelfImprovement,
                                                    contentDescription = null,
                                                    tint = if (done) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = idea.title,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = idea.duration,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                Text(
                                                    text = idea.detail,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Distraction-Clearing Space (Brain Dump saved locally in Room)
                    item {
                        SanctuaryCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("distraction_dump_card")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Distraction-Clearing Space",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Park random thoughts here so your mind stays clear",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = dumpInput,
                                onValueChange = { dumpInput = it },
                                placeholder = { Text("What's pulling at your attention right now?") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("distraction_note_input"),
                                shape = RoundedCornerShape(14.dp),
                                maxLines = 3
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (dumpInput.isNotBlank()) {
                                        onAddDistractionNote(dumpInput.trim())
                                        dumpInput = ""
                                    }
                                },
                                enabled = dumpInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("save_distraction_note_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Park Thought & Clear Mind")
                            }

                            if (dumpNotes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    dumpNotes.take(6).forEach { note ->
                                        SavedMindNoteRow(
                                            note = note,
                                            onDelete = { onDeleteMindNote(note.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Simple Session Reflection (saved locally in Room)
                    item {
                        SanctuaryCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("session_reflection_card")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EditNote,
                                    contentDescription = null,
                                    tint = extended.sageAccent
                                )
                                Column {
                                    Text(
                                        text = "Quick Session Reflection",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "How did your study energy feel?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ReflectionMoods.forEach { mood ->
                                    val selected = mood == selectedMood
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .springClickable { selectedMood = mood }
                                            .minimumInteractiveComponentSize(),
                                        shape = RoundedCornerShape(50),
                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else extended.cardBorder
                                        )
                                    ) {
                                        Text(
                                            text = mood,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = reflectionInput,
                                onValueChange = { reflectionInput = it },
                                placeholder = { Text("One thing that went well, or what to try next...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reflection_note_input"),
                                shape = RoundedCornerShape(14.dp),
                                maxLines = 3
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    if (reflectionInput.isNotBlank()) {
                                        onAddReflection(reflectionInput.trim(), selectedMood)
                                        reflectionInput = ""
                                    }
                                },
                                enabled = reflectionInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("save_reflection_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Reflection")
                            }

                            if (reflectionNotes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    reflectionNotes.take(5).forEach { note ->
                                        SavedMindNoteRow(
                                            note = note,
                                            onDelete = { onDeleteMindNote(note.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 6. Return to Focus CTA
                    item {
                        Button(
                            onClick = onReturnToFocus,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("mind_reset_return_focus_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Return to Focus Space",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedMindNoteRow(
    note: MindNote,
    onDelete: () -> Unit
) {
    val dateLabel = remember(note.createdAtMs) {
        SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(note.createdAtMs))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, BrainBufferThemeTokens.extended.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (note.moodTag.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = BrainBufferThemeTokens.extended.sageAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${note.moodTag} · $dateLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                }
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (note.moodTag.isBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Clear note",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
