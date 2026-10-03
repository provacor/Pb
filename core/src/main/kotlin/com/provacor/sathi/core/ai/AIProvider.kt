package com.provacor.sathi.core.ai

import com.provacor.sathi.core.model.AgentIntent
import com.provacor.sathi.core.model.Language

/**
 * Network or on-device model that handles what the offline interpreter cannot.
 * Adapters (OpenAI-compatible, Anthropic, Gemini, local) implement this; the
 * agent never depends on one vendor. Keys come from secure storage at runtime
 * and are never compiled into the app.
 */
interface AIProvider {
    val id: String

    /** True when this provider sends data off the device; the UI must show that. */
    val usesNetwork: Boolean

    suspend fun understandCommand(command: String, language: Language): AIResult<List<AgentIntent>>

    suspend fun generateResponse(prompt: String, language: Language): AIResult<String>
}

sealed interface AIResult<out T> {
    data class Ok<T>(val value: T) : AIResult<T>
    data class Unavailable(val reason: String) : AIResult<Nothing>
    data class Failed(val reason: String) : AIResult<Nothing>
}

/** Used until an AI provider is configured: everything stays on the device. */
object NoAIProvider : AIProvider {
    override val id = "none"
    override val usesNetwork = false
    override suspend fun understandCommand(command: String, language: Language) =
        AIResult.Unavailable("No AI provider configured")

    override suspend fun generateResponse(prompt: String, language: Language) =
        AIResult.Unavailable("No AI provider configured")
}
