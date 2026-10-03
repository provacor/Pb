package com.provacor.sathi

import android.app.Application
import android.content.Context
import android.util.Log
import com.provacor.sathi.agent.ActionExecutor
import com.provacor.sathi.agent.AgentController
import com.provacor.sathi.apps.AppLauncher
import com.provacor.sathi.apps.InstalledAppsRepository
import com.provacor.sathi.settings.SettingsRepository
import com.provacor.sathi.tts.TextToSpeechManager

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
    val settings = SettingsRepository(context)
    val tts = TextToSpeechManager(context)
    private val apps = InstalledAppsRepository(context)
    private val launcher = AppLauncher(context)
    val agent = AgentController(apps, ActionExecutor(launcher), tts, debugSink = { Log.d("Agent", it) })
}

val Context.container: AppContainer get() = (applicationContext as SathiApp).container
