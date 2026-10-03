package com.provacor.sathi.core.log

import com.provacor.sathi.core.text.TextNormalizer

enum class LogType { COMMAND, UNDERSTANDING, PLAN, ACTION, RESULT, ERROR }

data class LogEntry(val type: LogType, val message: String, val timeMillis: Long)

/**
 * Developer log for each task. Messages pass through [TextNormalizer.redactForLog]
 * so digit runs (OTPs, PINs, card numbers) never reach it. Bounded in size.
 */
class AgentLog(private val capacity: Int = 300, private val clock: () -> Long = System::currentTimeMillis) {
    private val entries = ArrayDeque<LogEntry>()

    @Synchronized
    fun add(type: LogType, message: String): LogEntry {
        val entry = LogEntry(type, TextNormalizer.redactForLog(message), clock())
        entries.addLast(entry)
        while (entries.size > capacity) entries.removeFirst()
        return entry
    }

    @Synchronized
    fun snapshot(): List<LogEntry> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()
}
