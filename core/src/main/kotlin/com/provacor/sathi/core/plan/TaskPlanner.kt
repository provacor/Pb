package com.provacor.sathi.core.plan

import com.provacor.sathi.core.apps.AppResolver
import com.provacor.sathi.core.model.AgentIntent
import com.provacor.sathi.core.model.AppInfo
import com.provacor.sathi.core.model.Capability
import com.provacor.sathi.core.model.TaskContext

/** One step the executor runs, already bound to a concrete installed app where relevant. */
data class PlanStep(
    val intent: AgentIntent,
    /** The installed app this step targets, or null (web search, not found, not app-specific). */
    val app: AppInfo? = null,
) {
    val capability: Capability get() = intent.capability
}

data class Plan(val steps: List<PlanStep>) {
    val isEmpty get() = steps.isEmpty()
}

/**
 * Turns understood intents into executable steps.
 *
 * Prefers direct Android APIs over UI automation: "open YouTube, then search
 * physics" becomes a single in-app search intent sent to YouTube, which needs
 * no screen control at all.
 */
class TaskPlanner(private val resolver: AppResolver = AppResolver()) {

    fun plan(
        intents: List<AgentIntent>,
        context: TaskContext,
        installedApps: List<AppInfo>,
        nowMillis: Long,
    ): Plan {
        val steps = mutableListOf<PlanStep>()
        var currentApp: AppInfo? = context.currentApp?.takeIf { context.isFresh(nowMillis) }

        for (intent in intents.take(MAX_STEPS)) {
            when (intent) {
                is AgentIntent.OpenApp -> {
                    val app = resolver.resolve(intent.appQuery, installedApps)?.app
                    steps += PlanStep(intent, app)
                    if (app != null) currentApp = app
                }

                is AgentIntent.Search -> {
                    val app = intent.appQuery?.let { resolver.resolve(it, installedApps)?.app } ?: currentApp
                    val previous = steps.lastOrNull()
                    if (previous != null && previous.intent is AgentIntent.OpenApp && previous.app != null && previous.app == app) {
                        // The search intent launches the app itself; no separate launch needed.
                        steps[steps.lastIndex] = PlanStep(intent.copy(appQuery = app.label), app)
                    } else {
                        steps += PlanStep(intent, app)
                    }
                    if (app != null) currentApp = app
                }

                else -> steps += PlanStep(intent, currentApp)
            }
        }
        return Plan(steps)
    }

    companion object {
        /** Hard ceiling so a runaway command can never queue an unbounded task. */
        const val MAX_STEPS = 12
    }
}
