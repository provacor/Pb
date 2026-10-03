package com.provacor.sathi.core.model

/** Languages the agent listens and replies in. [tag] is a BCP-47 tag for speech APIs. */
enum class Language(val tag: String) {
    BENGALI("bn-BD"),
    ENGLISH("en-US");

    companion object {
        fun fromTag(tag: String?): Language =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) || it.name.equals(tag, ignoreCase = true) }
                ?: BENGALI
    }
}
