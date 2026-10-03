package com.provacor.sathi.agent

import com.provacor.sathi.core.model.AgentIntent
import com.provacor.sathi.core.model.AppInfo
import com.provacor.sathi.core.model.Capability
import com.provacor.sathi.core.model.Language
import com.provacor.sathi.core.plan.PlanStep
import com.provacor.sathi.core.response.Responses

data class StepOutcome(
    val status: StepStatus,
    val message: String,
    /** The app now expected in front, for task memory. */
    val foregroundApp: AppInfo? = null,
) {
    val ok get() = status == StepStatus.DONE || status == StepStatus.SENT
}

/**
 * Runs one plan step through official Android intents.
 *
 * Launch and search intents end in [StepStatus.SENT], not DONE: sending an
 * intent proves nothing about what the screen shows. Confirming it needs the
 * Accessibility Service (phase 2), which plugs in here as a verifier.
 */
class ActionExecutor(private val launcher: AppActions) {

    fun execute(step: PlanStep, lang: Language): StepOutcome {
        val intent = step.intent
        return when {
            intent is AgentIntent.OpenApp -> openApp(intent, step.app, lang)
            intent is AgentIntent.Search -> search(intent, step.app, lang)
            intent is AgentIntent.Help -> StepOutcome(StepStatus.DONE, Responses.help(lang))
            intent is AgentIntent.StopSpeaking -> StepOutcome(StepStatus.DONE, "")
            intent is AgentIntent.Unknown -> StepOutcome(StepStatus.FAILED, Responses.notUnderstood(intent.text, lang))
            step.capability == Capability.ACCESSIBILITY -> StepOutcome(StepStatus.NEEDS_SETUP, Responses.needsAccessibility(step, lang))
            step.capability == Capability.FILES -> StepOutcome(StepStatus.NEEDS_SETUP, Responses.filesNotYet(lang))
            else -> StepOutcome(StepStatus.FAILED, Responses.notUnderstood(Responses.describe(step, lang), lang))
        }
    }

    private fun openApp(intent: AgentIntent.OpenApp, app: AppInfo?, lang: Language): StepOutcome {
        if (app == null) return StepOutcome(StepStatus.FAILED, Responses.appNotFound(intent.appQuery, lang))
        return if (launcher.launch(app)) {
            StepOutcome(StepStatus.SENT, Responses.opened(app.label, lang), app)
        } else {
            StepOutcome(StepStatus.FAILED, Responses.launchFailed(app.label, lang))
        }
    }

    private fun search(intent: AgentIntent.Search, app: AppInfo?, lang: Language): StepOutcome {
        if (intent.query.isBlank()) return StepOutcome(StepStatus.FAILED, Responses.askSearchQuery(lang))
        if (app == null) {
            return if (launcher.webSearch(intent.query)) {
                StepOutcome(StepStatus.SENT, Responses.searchingWeb(intent.query, lang))
            } else {
                StepOutcome(StepStatus.FAILED, Responses.notUnderstood(intent.query, lang))
            }
        }
        if (launcher.searchInApp(app, intent.query)) {
            return StepOutcome(StepStatus.SENT, Responses.searchingIn(intent.query, app.label, lang), app)
        }
        // The app takes no search intent: open it and say what is missing.
        val opened = launcher.launch(app)
        return StepOutcome(StepStatus.NEEDS_SETUP, Responses.appCantSearch(app.label, lang), app.takeIf { opened })
    }
}
