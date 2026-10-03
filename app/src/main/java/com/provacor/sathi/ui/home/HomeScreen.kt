package com.provacor.sathi.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.provacor.sathi.R
import com.provacor.sathi.agent.Stage
import com.provacor.sathi.agent.StepStatus
import com.provacor.sathi.agent.StepView
import com.provacor.sathi.core.log.LogEntry
import com.provacor.sathi.core.log.LogType
import com.provacor.sathi.core.model.Language
import com.provacor.sathi.ui.components.MicOrb
import com.provacor.sathi.ui.theme.LogTextStyle
import com.provacor.sathi.ui.theme.StatusColors
import com.provacor.sathi.voice.VoiceError

private val BENGALI_EXAMPLES = listOf(
    "ইউটিউব খুলে physics wave সার্চ করো",
    "ক্রোম খোলো",
    "সেটিংস খোলো",
    "তুমি কী করতে পারো?",
)
private val ENGLISH_EXAMPLES = listOf(
    "Open YouTube and search physics wave",
    "Open Chrome",
    "Search the web for today's weather",
    "What can you do?",
)

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val micGranted = remember(resumeTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    val agent = state.agent
    val listening = state.voice.listening
    val working = agent.stage in setOf(Stage.UNDERSTANDING, Stage.PLANNING, Stage.EXECUTING)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { CommandBar(onSubmit = vm::submit) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(onOpenSettings, onOpenPermissions)

            if (!micGranted) {
                Banner(stringResource(R.string.mic_missing_banner), stringResource(R.string.set_up), onOpenPermissions)
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                MicOrb(
                    listening = listening,
                    working = working,
                    level = state.voice.level,
                    label = stringResource(if (listening) R.string.mic_stop else R.string.mic_start),
                    onClick = { if (micGranted) vm.toggleListening() else onOpenPermissions() },
                )
                Text(
                    text = stageLabel(if (listening) null else agent.stage),
                    style = MaterialTheme.typography.headlineSmall,
                    color = stageColor(if (listening) null else agent.stage),
                )
                Spacer(Modifier.size(4.dp))
                val hint = state.voice.partial?.let { "“$it”" }
                    ?: state.voice.error?.let { voiceErrorText(it) }
                    ?: if (agent.command == null) stringResource(R.string.hint_tap_to_speak) else null
                if (hint != null) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.voice.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            agent.command?.let { Section(stringResource(R.string.you_said)) { Text("“$it”", style = MaterialTheme.typography.titleMedium) } }

            if (agent.steps.isNotEmpty()) {
                Section(stringResource(R.string.plan_title)) {
                    agent.steps.forEach { StepRow(it) }
                    if (agent.steps.any { it.status == StepStatus.SENT }) {
                        Text(
                            stringResource(R.string.sent_explainer),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            agent.response?.let { response ->
                Section(stringResource(R.string.response_title)) {
                    Text(response, style = MaterialTheme.typography.bodyLarge)
                    if (state.tts.speaking || state.tts.paused) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (state.tts.speaking) TextButton(onClick = vm::pauseSpeech) { Text(stringResource(R.string.pause)) }
                            if (state.tts.paused) TextButton(onClick = vm::resumeSpeech) { Text(stringResource(R.string.resume)) }
                            TextButton(onClick = vm::stopSpeech) { Text(stringResource(R.string.stop)) }
                        }
                    }
                }
            }

            if (agent.command == null && !listening) {
                Examples(
                    if (state.settings.language == Language.BENGALI) BENGALI_EXAMPLES else ENGLISH_EXAMPLES,
                    onPick = vm::submit,
                )
            }

            if (state.settings.showLog && agent.log.isNotEmpty()) {
                Section(stringResource(R.string.log_title)) { DevLog(agent.log) }
            }

            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit, onOpenPermissions: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
            Text(
                stringResource(R.string.app_tagline).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenPermissions) { Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.permissions)) }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings)) }
    }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

@Composable
private fun StepRow(step: StepView) {
    val colors = MaterialTheme.colorScheme
    val (label, color) = when (step.status) {
        StepStatus.PENDING -> stringResource(R.string.status_pending) to StatusColors.idle(colors)
        StepStatus.RUNNING -> stringResource(R.string.status_running) to StatusColors.done(colors)
        StepStatus.DONE -> stringResource(R.string.status_done) to StatusColors.done(colors)
        StepStatus.SENT -> stringResource(R.string.status_sent) to StatusColors.done(colors)
        StepStatus.NEEDS_SETUP -> stringResource(R.string.status_needs_setup) to StatusColors.attention(colors)
        StepStatus.FAILED -> stringResource(R.string.status_failed) to StatusColors.failed(colors)
        StepStatus.SKIPPED -> stringResource(R.string.status_skipped) to StatusColors.idle(colors)
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(12.dp), contentAlignment = Alignment.Center) {
            if (step.status == StepStatus.RUNNING) {
                CircularProgressIndicator(Modifier.size(12.dp), color = color, strokeWidth = 2.dp)
            } else {
                // Hollow dot: not yet run, or sent but unconfirmed.
                val hollow = step.status == StepStatus.SENT || step.status == StepStatus.PENDING
                Box(
                    Modifier
                        .size(10.dp)
                        .then(if (hollow) Modifier.border(1.5.dp, color, CircleShape) else Modifier.background(color, CircleShape)),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(step.description, style = MaterialTheme.typography.bodyLarge)
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Medium)
            if (step.status == StepStatus.NEEDS_SETUP || step.status == StepStatus.FAILED) {
                step.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Examples(examples: List<String>, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.try_saying).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            examples.forEach { ex -> SuggestionChip(onClick = { onPick(ex) }, label = { Text(ex) }) }
        }
    }
}

@Composable
private fun DevLog(entries: List<LogEntry>) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.takeLast(40).forEach { e ->
            val tagColor = when (e.type) {
                LogType.ERROR -> colors.error
                LogType.RESULT -> colors.secondary
                LogType.ACTION -> colors.primary
                else -> colors.onSurfaceVariant
            }
            Row {
                Text(e.type.name.padEnd(13), style = LogTextStyle, color = tagColor)
                Text(e.message, style = LogTextStyle, color = colors.onSurface)
            }
        }
    }
}

@Composable
private fun CommandBar(onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val send = {
        if (text.isNotBlank()) {
            onSubmit(text.trim())
            text = ""
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.type_command)) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = { send() }, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
            }
        }
    }
}

@Composable
private fun stageLabel(stage: Stage?): String = stringResource(
    when (stage) {
        null -> R.string.stage_listening
        Stage.READY -> R.string.stage_ready
        Stage.UNDERSTANDING -> R.string.stage_understanding
        Stage.PLANNING -> R.string.stage_planning
        Stage.EXECUTING -> R.string.stage_executing
        Stage.COMPLETED -> R.string.stage_completed
        Stage.FAILED -> R.string.stage_failed
    },
)

@Composable
private fun stageColor(stage: Stage?): Color {
    val c = MaterialTheme.colorScheme
    return when (stage) {
        null -> c.primary
        Stage.COMPLETED -> c.secondary
        Stage.FAILED -> c.error
        else -> c.onBackground
    }
}

@Composable
private fun voiceErrorText(error: VoiceError): String = stringResource(
    when (error) {
        VoiceError.NO_SPEECH -> R.string.voice_error_no_speech
        VoiceError.NETWORK -> R.string.voice_error_network
        VoiceError.PERMISSION -> R.string.voice_error_permission
        VoiceError.BUSY -> R.string.voice_error_busy
        VoiceError.UNAVAILABLE -> R.string.voice_error_unavailable
        VoiceError.LANGUAGE -> R.string.voice_error_language
        VoiceError.OTHER -> R.string.voice_error_other
    },
)
