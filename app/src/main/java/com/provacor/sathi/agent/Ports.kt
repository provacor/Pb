package com.provacor.sathi.agent

import com.provacor.sathi.core.model.AppInfo
import com.provacor.sathi.core.model.Language

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
