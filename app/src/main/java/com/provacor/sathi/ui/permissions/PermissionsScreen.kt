package com.provacor.sathi.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.provacor.sathi.R
import com.provacor.sathi.core.model.Language
import com.provacor.sathi.tts.TextToSpeechManager
import com.provacor.sathi.ui.settings.SettingsViewModel

private enum class Status { OK, MISSING, LATER }

@Composable
fun PermissionsScreen(vm: SettingsViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val tts by vm.ttsState.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    var askedMic by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        tick++
        vm.refreshVoices()
    }

    val micGranted = remember(tick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val recognizerAvailable = remember(tick) { SpeechRecognizer.isRecognitionAvailable(context) }
    val bengaliVoice = tts.ready && Language.BENGALI !in tts.missingVoices

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        askedMic = true
        tick++
    }
    // After a denial Android stops showing the dialog; then only system settings can grant it.
    val micBlocked = askedMic && !micGranted &&
        (context as? Activity)?.let { !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO) } == true

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.onboarding_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        PermissionCard(
            title = stringResource(R.string.perm_mic_title),
            body = stringResource(R.string.perm_mic_body),
            status = if (micGranted) Status.OK else Status.MISSING,
            okLabel = stringResource(R.string.perm_granted),
            actionLabel = when {
                micGranted -> null
                micBlocked -> stringResource(R.string.perm_open_settings)
                else -> stringResource(R.string.perm_allow)
            },
            onAction = {
                if (micBlocked) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    context.startSafely(intent)
                } else {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
        )

        PermissionCard(
            title = stringResource(R.string.perm_recognizer_title),
            body = stringResource(if (recognizerAvailable) R.string.perm_recognizer_body else R.string.voice_error_unavailable),
            status = if (recognizerAvailable) Status.OK else Status.MISSING,
            okLabel = stringResource(R.string.perm_available),
        )

        PermissionCard(
            title = stringResource(R.string.perm_tts_title),
            body = stringResource(R.string.perm_tts_body),
            status = if (bengaliVoice) Status.OK else Status.MISSING,
            okLabel = stringResource(R.string.perm_available),
            actionLabel = if (tts.ready && !bengaliVoice) stringResource(R.string.perm_install_voice) else null,
            onAction = { context.startSafely(TextToSpeechManager.installVoiceDataIntent()) },
        )

        PermissionCard(
            title = stringResource(R.string.perm_a11y_title),
            body = stringResource(R.string.perm_a11y_body),
            status = Status.LATER,
            okLabel = "",
        )

        PermissionCard(
            title = stringResource(R.string.perm_files_title),
            body = stringResource(R.string.perm_files_body),
            status = Status.LATER,
            okLabel = "",
        )

        Spacer(Modifier.size(4.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.continue_label))
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    status: Status,
    okLabel: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val (statusText, statusColor) = when (status) {
        Status.OK -> okLabel to colors.secondary
        Status.MISSING -> stringResource(R.string.perm_missing) to colors.primary
        Status.LATER -> stringResource(R.string.perm_coming) to colors.outline
    }
    Surface(
        color = colors.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusPill(statusText, statusColor)
            }
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = if (status == Status.LATER) colors.onSurfaceVariant.copy(alpha = 0.8f) else colors.onSurfaceVariant,
            )
            if (actionLabel != null) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), contentColor = color, shape = RoundedCornerShape(50)) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

private fun android.content.Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Nothing on this device handles it; the card already explains what is missing.
    }
}
