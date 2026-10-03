package com.provacor.sathi.agent

import com.provacor.sathi.core.ai.AIProvider
import com.provacor.sathi.core.ai.NoAIProvider
import com.provacor.sathi.core.log.AgentLog
import com.provacor.sathi.core.log.LogType
import com.provacor.sathi.core.model.AgentIntent
import com.provacor.sathi.core.model.Language
import com.provacor.sathi.core.model.TaskContext
import com.provacor.sathi.core.parse.CommandInterpreter
import com.provacor.sathi.core.plan.TaskPlanner
import com.provacor.sathi.core.response.Responses
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Understand → plan → act → check, one step at a time.
 *
 * A step that fails or needs missing setup stops the task: later steps
 * usually depend on it ("play the first video" after a search that never ran),
 * so they are marked skipped instead of being fired blindly.
 */
class AgentController(
    private val apps: AppCatalog,
    private val executor: ActionExecutor,
    private val speaker: Speaker,
    @Suppress("unused") private val ai: AIProvider = NoAIProvider,
    private val interpreter: CommandInterpreter = CommandInterpreter(),
    private val planner: TaskPlanner = TaskPlanner(),
    private val clock: () -> Long = System::currentTimeMillis,
    /** Mirrors each log line somewhere for developers (Logcat in the app). */
    private val debugSink: (String) -> Unit = {},
) {
    private val log = AgentLog(clock = clock)
    private var context = TaskContext()

    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

    suspend fun handle(command: String, lang: Language, speakReplies: Boolean) {
        val text = command.trim()
        if (text.isEmpty()) return
        speaker.stop()
        record(LogType.COMMAND, text)
        _state.value = AgentState(stage = Stage.UNDERSTANDING, command = text, log = log.snapshot())

        val intents = interpreter.interpret(text)
        record(LogType.UNDERSTANDING, intents.joinToString { it.toString() }.ifEmpty { "(nothing)" })

        if (intents.singleOrNull() == AgentIntent.StopSpeaking) {
            finish(Stage.COMPLETED, Responses.stopped(lang), speak = false, lang = lang)
            return
        }

        _state.update { it.copy(stage = Stage.PLANNING) }
        val plan = planner.plan(intents, context, apps.apps(), clock())
        if (plan.isEmpty) {
            finish(Stage.FAILED, Responses.nothingHeard(lang), speakReplies, lang)
            return
        }
        record(LogType.PLAN, plan.steps.mapIndexed { i, s -> "${i + 1}. ${Responses.describe(s, Language.ENGLISH)}" }.joinToString("\n"))
        _state.update {
            it.copy(stage = Stage.EXECUTING, steps = plan.steps.map { s -> StepView(Responses.describe(s, lang), StepStatus.PENDING) })
        }

        val messages = mutableListOf<String>()
        var allOk = true
        for ((index, step) in plan.steps.withIndex()) {
            setStep(index, StepStatus.RUNNING, null)
            record(LogType.ACTION, Responses.describe(step, Language.ENGLISH))

            val outcome = runCatching { executor.execute(step, lang) }.getOrElse { e ->
                StepOutcome(StepStatus.FAILED, e.message ?: e.javaClass.simpleName)
            }
            record(if (outcome.ok) LogType.RESULT else LogType.ERROR, "${outcome.status}: ${outcome.message}")
            setStep(index, outcome.status, outcome.message)
            messages += outcome.message

            context = TaskContext(
                currentApp = outcome.foregroundApp ?: context.currentApp,
                lastIntent = step.intent,
                lastResultOk = outcome.ok,
                updatedAtMillis = clock(),
            )

            if (!outcome.ok) {
                allOk = false
                for (rest in index + 1 until plan.steps.size) setStep(rest, StepStatus.SKIPPED, null)
                break
            }
            // Give a launched app a moment to come up before the next step acts.
            if (index < plan.steps.lastIndex) delay(STEP_SETTLE_MILLIS)
        }

        finish(if (allOk) Stage.COMPLETED else Stage.FAILED, Responses.summary(messages), speakReplies, lang)
    }

    /** Shown when speech recognition fails before a command exists. */
    fun showMessage(message: String) {
        _state.update { it.copy(stage = Stage.FAILED, response = message, steps = emptyList()) }
    }

    fun clearTask() {
        _state.update { AgentState(log = it.log) }
    }

    private fun finish(stage: Stage, response: String, speak: Boolean, lang: Language) {
        _state.update { it.copy(stage = stage, response = response.ifBlank { null }, log = log.snapshot()) }
        if (speak && response.isNotBlank()) speaker.speak(response, lang)
    }

    private fun setStep(index: Int, status: StepStatus, message: String?) {
        _state.update { s ->
            s.copy(steps = s.steps.mapIndexed { i, v -> if (i == index) v.copy(status = status, message = message) else v })
        }
    }

    private fun record(type: LogType, message: String) {
        val entry = log.add(type, message)
        debugSink("${entry.type}: ${entry.message}")
        _state.update { it.copy(log = log.snapshot()) }
    }

    private companion object {
        const val STEP_SETTLE_MILLIS = 800L
    }
}
