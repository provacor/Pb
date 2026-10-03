package com.provacor.sathi.settings

import com.provacor.sathi.core.model.Language

data class Settings(
    val language: Language = Language.BENGALI,
    val speakReplies: Boolean = true,
    val preferOffline: Boolean = false,
    val showLog: Boolean = true,
    val onboardingDone: Boolean = false,
    /** Keep listening in the background (foreground service with a notification). */
    val handsFree: Boolean = false,
)
