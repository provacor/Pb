package com.provacor.sathi.agent

import com.provacor.sathi.core.log.LogEntry

enum class Stage { READY, UNDERSTANDING, PLANNING, EXECUTING, COMPLETED, FAILED }

enum class StepStatus {
    PENDING,
    RUNNING,
    DONE,

    /** The intent was delivered, but nothing has confirmed the result on screen. */
    SENT,

    /** Needs a capability that is not set up (screen control, files). */
    NEEDS_SETUP,
    FAILED,
    SKIPPED,
}

data class StepView(val description: String, val status: StepStatus, val message: String? = null)

data class AgentState(
    val stage: Stage = Stage.READY,
    val command: String? = null,
    val steps: List<StepView> = emptyList(),
    val response: String? = null,
    val log: List<LogEntry> = emptyList(),
)
