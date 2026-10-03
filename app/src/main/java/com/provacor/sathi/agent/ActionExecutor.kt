package com.provacor.sathi.agent

import com.provacor.sathi.core.model.AgentIntent
import com.provacor.sathi.core.model.AppInfo
import com.provacor.sathi.core.model.Capability
import com.provacor.sathi.core.model.DeviceAction
import com.provacor.sathi.core.model.DeviceTarget
import com.provacor.sathi.core.model.Direction
import com.provacor.sathi.core.model.Language
import com.provacor.sathi.core.model.ScreenState
import com.provacor.sathi.core.model.UiElement
import com.provacor.sathi.core.plan.Confirmation
import com.provacor.sathi.core.plan.PlanStep
import com.provacor.sathi.core.response.Responses
import com.provacor.sathi.core.screen.ScreenQueries
import kotlinx.coroutines.delay

data class StepOutcome(
    val status: StepStatus,
    val message: String,
    /** The app now expected in front, for task memory. */
    val foregroundApp: AppInfo? = null,
) {
    val ok get() = status == StepStatus.DONE || status == StepStatus.SENT
}

/**
 * Runs one plan step, then checks the result.
 *
 * Official intents come first (launch, search). Screen actions go through the
 * [ScreenController]. A step is DONE only when the screen confirms it; an intent
 * that was delivered but could not be confirmed stays SENT.
 */
class ActionExecutor(
    private val launcher: AppActions,
    private val screen: ScreenController = NoScreen,
    private val device: DeviceControls? = null,
    private val ownPackage: String = "",
) {

    /** [confirmed] is true when the user already said yes to this step. */
    suspend fun execute(step: PlanStep, lang: Language, confirmed: Boolean = false): StepOutcome {
        val intent = step.intent
        if (step.capability == Capability.ACCESSIBILITY && !screen.available) {
            return StepOutcome(StepStatus.NEEDS_SETUP, Responses.needsAccessibility(step, lang))
        }
        return when (intent) {
            is AgentIntent.OpenApp -> openApp(intent, step.app, lang)
            is AgentIntent.Search -> search(intent, step.app, lang)
            is AgentIntent.SelectItem -> selectItem(intent, lang)
            is AgentIntent.Tap ->
                if (!confirmed && Confirmation.isConsequential(intent.target)) {
                    StepOutcome(StepStatus.NEEDS_CONFIRMATION, Responses.confirmTap(intent.target, lang))
                } else {
                    tap(intent.target, lang)
                }
            is AgentIntent.TypeText -> type(intent.text, lang)
            is AgentIntent.Scroll -> scroll(intent, lang)
            AgentIntent.GoBack -> global(screen.back(), Responses.wentBack(lang), lang)
            AgentIntent.GoHome -> global(screen.home(), Responses.wentHome(lang), lang)
            AgentIntent.ReadScreen -> readScreen(lang)
            is AgentIntent.DeviceControl -> deviceControl(intent, lang)
            AgentIntent.Help -> StepOutcome(StepStatus.DONE, Responses.help(lang))
            AgentIntent.StopSpeaking -> StepOutcome(StepStatus.DONE, "")
            is AgentIntent.FindFile -> StepOutcome(StepStatus.NEEDS_SETUP, Responses.filesNotYet(lang))
            is AgentIntent.Unknown -> StepOutcome(StepStatus.FAILED, Responses.notUnderstood(intent.text, lang))
        }
    }

    // ---- apps and search ----------------------------------------------------

    private suspend fun openApp(intent: AgentIntent.OpenApp, app: AppInfo?, lang: Language): StepOutcome {
        if (app == null) return StepOutcome(StepStatus.FAILED, Responses.appNotFound(intent.appQuery, lang))
        if (!launcher.launch(app)) return StepOutcome(StepStatus.FAILED, Responses.launchFailed(app.label, lang))
        if (!screen.available) return StepOutcome(StepStatus.SENT, Responses.opened(app.label, lang), app)
        return if (screen.awaitForeground(app.packageName, LAUNCH_TIMEOUT)) {
            StepOutcome(StepStatus.DONE, Responses.openedVerified(app.label, lang), app)
        } else {
            StepOutcome(StepStatus.SENT, Responses.openedUnverified(app.label, lang), app)
        }
    }

    private suspend fun search(intent: AgentIntent.Search, app: AppInfo?, lang: Language): StepOutcome {
        val query = intent.query
        if (query.isBlank()) return StepOutcome(StepStatus.FAILED, Responses.askSearchQuery(lang))
        if (app == null) {
            return if (launcher.webSearch(query)) {
                StepOutcome(StepStatus.SENT, Responses.searchingWeb(query, lang))
            } else {
                StepOutcome(StepStatus.FAILED, Responses.notUnderstood(query, lang))
            }
        }
        if (launcher.searchInApp(app, query)) {
            if (!screen.available) return StepOutcome(StepStatus.SENT, Responses.searchingIn(query, app.label, lang), app)
            return if (screen.awaitForeground(app.packageName, LAUNCH_TIMEOUT)) {
                StepOutcome(StepStatus.DONE, Responses.searchedIn(query, app.label, lang), app)
            } else {
                StepOutcome(StepStatus.SENT, Responses.searchingIn(query, app.label, lang), app)
            }
        }
        // No search intent: open the app and type into its search box.
        val opened = screen.observe()?.packageName == app.packageName || launcher.launch(app)
        if (!screen.available) {
            return StepOutcome(StepStatus.NEEDS_SETUP, Responses.appCantSearch(app.label, lang), app.takeIf { opened })
        }
        if (!opened || !screen.awaitForeground(app.packageName, LAUNCH_TIMEOUT)) {
            return StepOutcome(StepStatus.FAILED, Responses.openedUnverified(app.label, lang))
        }
        val field = openTextField() ?: return StepOutcome(StepStatus.FAILED, Responses.noSearchBox(app.label, lang), app)
        val since = screen.changeCount()
        if (!screen.setText(field, query)) return StepOutcome(StepStatus.FAILED, Responses.typeFailed(lang), app)
        screen.submit(field)
        screen.awaitChange(since, CHANGE_TIMEOUT)
        return StepOutcome(StepStatus.DONE, Responses.searchedIn(query, app.label, lang), app)
    }

    // ---- screen actions ------------------------------------------------------

    private suspend fun selectItem(intent: AgentIntent.SelectItem, lang: Language): StepOutcome {
        val (state, item) = findWithRetry { ScreenQueries.findItem(it, intent.ordinal, intent.kind) }
            ?: return StepOutcome(StepStatus.FAILED, Responses.itemNotFound(intent.kind, lang))
        return clickAndVerify(state, item, Responses.selected(intent.ordinal, intent.kind, lang), lang)
    }

    private suspend fun tap(target: String, lang: Language): StepOutcome {
        val found = findWithRetry(scrollBetweenTries = true) { ScreenQueries.findByLabel(it, target) }
            ?: return StepOutcome(StepStatus.FAILED, Responses.notOnScreen(target, lang))
        return clickAndVerify(found.first, found.second, Responses.tapped(target, lang), lang)
    }

    private suspend fun clickAndVerify(state: ScreenState, element: UiElement, okMessage: String, lang: Language): StepOutcome {
        val target = state.element(element.clickTarget) ?: element
        val since = screen.changeCount()
        if (!screen.click(target)) return StepOutcome(StepStatus.FAILED, Responses.globalFailed(lang))
        val after = screen.awaitChange(since, CHANGE_TIMEOUT)
        return if (after != null && after.signature != state.signature) {
            StepOutcome(StepStatus.DONE, okMessage)
        } else {
            StepOutcome(StepStatus.FAILED, Responses.noChange(lang))
        }
    }

    private suspend fun type(text: String, lang: Language): StepOutcome {
        val field = openTextField() ?: return StepOutcome(StepStatus.FAILED, Responses.noTextField(lang))
        if (!screen.setText(field, text)) return StepOutcome(StepStatus.FAILED, Responses.typeFailed(lang))
        delay(SETTLE)
        val typed = screen.observe()?.let { ScreenQueries.findTextField(it) }?.text.orEmpty()
        return if (typed.contains(text, ignoreCase = true)) {
            StepOutcome(StepStatus.DONE, Responses.typed(text, lang))
        } else {
            StepOutcome(StepStatus.FAILED, Responses.typeFailed(lang))
        }
    }

    /** The visible text field, or the one revealed by tapping a search icon. */
    private suspend fun openTextField(): UiElement? {
        repeat(3) {
            val state = screen.observe() ?: return null
            ScreenQueries.findTextField(state)?.let { return it }
            val button = ScreenQueries.findSearchButton(state) ?: return null
            val since = screen.changeCount()
            screen.click(state.element(button.clickTarget) ?: button)
            screen.awaitChange(since, CHANGE_TIMEOUT)
        }
        return null
    }

    private suspend fun scroll(intent: AgentIntent.Scroll, lang: Language): StepOutcome {
        val since = screen.changeCount()
        if (!screen.scroll(intent.direction)) return StepOutcome(StepStatus.FAILED, Responses.scrollEnd(lang))
        return if (screen.awaitChange(since, CHANGE_TIMEOUT) != null) {
            StepOutcome(StepStatus.DONE, Responses.scrolled(intent.direction, lang))
        } else {
            StepOutcome(StepStatus.FAILED, Responses.scrollEnd(lang))
        }
    }

    private suspend fun global(done: Boolean, okMessage: String, lang: Language): StepOutcome {
        if (!done) return StepOutcome(StepStatus.FAILED, Responses.globalFailed(lang))
        delay(SETTLE)
        return StepOutcome(StepStatus.DONE, okMessage)
    }

    private suspend fun readScreen(lang: Language): StepOutcome {
        val state = screen.observe() ?: return StepOutcome(StepStatus.FAILED, Responses.screenEmpty(lang))
        if (state.packageName == ownPackage) return StepOutcome(StepStatus.FAILED, Responses.ownScreen(lang))
        val text = ScreenQueries.readableText(state)
        return if (text.isBlank()) StepOutcome(StepStatus.FAILED, Responses.screenEmpty(lang)) else StepOutcome(StepStatus.DONE, text)
    }

    /**
     * Looks for something on screen, giving a loading screen up to three tries,
     * optionally scrolling between them. Returns the screen it was found on.
     */
    private suspend fun findWithRetry(
        scrollBetweenTries: Boolean = false,
        find: (ScreenState) -> UiElement?,
    ): Pair<ScreenState, UiElement>? {
        repeat(FIND_TRIES) { attempt ->
            val state = screen.observe()
            if (state != null) find(state)?.let { return state to it }
            if (attempt < FIND_TRIES - 1) {
                if (scrollBetweenTries && attempt > 0) {
                    val since = screen.changeCount()
                    if (screen.scroll(Direction.DOWN)) screen.awaitChange(since, CHANGE_TIMEOUT)
                } else {
                    delay(RETRY_DELAY)
                }
            }
        }
        return null
    }

    // ---- device ----------------------------------------------------------------

    private fun deviceControl(intent: AgentIntent.DeviceControl, lang: Language): StepOutcome {
        val controls = device ?: return StepOutcome(StepStatus.FAILED, Responses.device(intent.target, intent.action, false, lang))
        val ok = when (intent.target) {
            DeviceTarget.FLASHLIGHT -> controls.torch(intent.action != DeviceAction.OFF)
            DeviceTarget.VOLUME -> controls.volume(intent.action)
            else -> controls.openPanel(intent.target)
        }
        val status = when {
            !ok -> StepStatus.FAILED
            intent.target == DeviceTarget.FLASHLIGHT || intent.target == DeviceTarget.VOLUME -> StepStatus.DONE
            else -> StepStatus.SENT // the panel is open; the user flips the switch
        }
        return StepOutcome(status, Responses.device(intent.target, intent.action, ok, lang))
    }

    private companion object {
        const val LAUNCH_TIMEOUT = 5_000L
        const val CHANGE_TIMEOUT = 3_000L
        const val SETTLE = 500L
        const val RETRY_DELAY = 1_000L
        const val FIND_TRIES = 3
    }
}
