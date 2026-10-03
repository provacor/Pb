package com.provacor.sathi.core.model

/**
 * Short-term memory between commands, so that "এখন physics search করো"
 * after "ইউটিউব খোলো" searches inside YouTube. Holds no message or form content.
 */
data class TaskContext(
    val currentApp: AppInfo? = null,
    val lastIntent: AgentIntent? = null,
    val lastResultOk: Boolean? = null,
    val updatedAtMillis: Long = 0L,
) {
    fun isFresh(nowMillis: Long, maxAgeMillis: Long = MAX_AGE_MILLIS): Boolean =
        updatedAtMillis > 0 && nowMillis - updatedAtMillis <= maxAgeMillis

    companion object {
        const val MAX_AGE_MILLIS = 10 * 60 * 1000L
    }
}
