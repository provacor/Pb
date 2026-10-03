package com.provacor.sathi.agent

import com.provacor.sathi.core.model.AppInfo
import com.provacor.sathi.core.model.DeviceAction
import com.provacor.sathi.core.model.DeviceTarget
import com.provacor.sathi.core.model.Direction
import com.provacor.sathi.core.model.Language
import com.provacor.sathi.core.model.ScreenState
import com.provacor.sathi.core.model.UiElement

// Seams between the agent and Android, so the agent loop runs in plain JVM tests.

interface AppCatalog {
    suspend fun apps(): List<AppInfo>
}

interface AppActions {
    fun launch(app: AppInfo): Boolean
    fun searchInApp(app: AppInfo, query: String): Boolean
    fun webSearch(query: String): Boolean
}

interface Speaker {
    fun speak(text: String, language: Language)
    fun stop()
}

/** Seeing and touching the screen. Backed by the Accessibility Service on a phone. */
interface ScreenController {
    /** False until the user turns on Sathi's Accessibility Service. */
    val available: Boolean

    suspend fun observe(): ScreenState?

    /** Grows every time the screen reports a change; pair with [awaitChange]. */
    fun changeCount(): Long

    /** Waits for a change after [since], lets it settle, and returns the new screen (null on timeout). */
    suspend fun awaitChange(since: Long, timeoutMillis: Long): ScreenState?

    /** True once [packageName] is the app in front. */
    suspend fun awaitForeground(packageName: String, timeoutMillis: Long): Boolean

    suspend fun click(element: UiElement): Boolean
    suspend fun setText(element: UiElement, text: String): Boolean

    /** The keyboard's enter/search key on [element]. */
    suspend fun submit(element: UiElement): Boolean
    suspend fun scroll(direction: Direction): Boolean
    fun back(): Boolean
    fun home(): Boolean
}

object NoScreen : ScreenController {
    override val available = false
    override suspend fun observe(): ScreenState? = null
    override fun changeCount() = 0L
    override suspend fun awaitChange(since: Long, timeoutMillis: Long): ScreenState? = null
    override suspend fun awaitForeground(packageName: String, timeoutMillis: Long) = false
    override suspend fun click(element: UiElement) = false
    override suspend fun setText(element: UiElement, text: String) = false
    override suspend fun submit(element: UiElement) = false
    override suspend fun scroll(direction: Direction) = false
    override fun back() = false
    override fun home() = false
}

interface DeviceControls {
    fun torch(on: Boolean): Boolean
    fun volume(action: DeviceAction): Boolean

    /** Opens the system panel for a switch Android doesn't let apps change. */
    fun openPanel(target: DeviceTarget): Boolean
}
