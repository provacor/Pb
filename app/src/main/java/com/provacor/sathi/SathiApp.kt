package com.provacor.sathi

import android.app.Application
import android.content.Context
import android.util.Log
import com.provacor.sathi.accessibility.AccessibilityBridge
import com.provacor.sathi.agent.ActionExecutor
import com.provacor.sathi.agent.AgentController
import com.provacor.sathi.apps.AppLauncher
import com.provacor.sathi.apps.InstalledAppsRepository
import com.provacor.sathi.device.AndroidDeviceControls
import com.provacor.sathi.settings.Settings
import com.provacor.sathi.settings.SettingsRepository
import com.provacor.sathi.tts.TextToSpeechManager
import com.provacor.sathi.voice.SpeechRecognizerManager
import com.provacor.sathi.voice.VoiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SathiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-wired singletons; small enough that a DI framework would only add weight. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings = SettingsRepository(context)
    val currentSettings: StateFlow<Settings> = settings.settings.stateIn(appScope, SharingStarted.Eagerly, Settings())
    val tts = TextToSpeechManager(context)
    val screen = AccessibilityBridge
    private val apps = InstalledAppsRepository(context)
    private val launcher = AppLauncher(context)
    val agent = AgentController(
        apps,
        ActionExecutor(launcher, screen, AndroidDeviceControls(context), ownPackage = context.packageName),
        tts,
        debugSink = { Log.d("Agent", it) },
    )
    val voice = VoiceController(SpeechRecognizerManager(context), agent, tts, currentSettings)
}

val Context.container: AppContainer get() = (applicationContext as SathiApp).container
