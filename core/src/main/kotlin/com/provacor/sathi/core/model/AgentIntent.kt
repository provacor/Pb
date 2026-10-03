package com.provacor.sathi.core.model

/**
 * One understood instruction. A spoken command can produce several of these,
 * for example "ইউটিউব খুলে physics search করো" gives [OpenApp] then [Search].
 */
sealed interface AgentIntent {
    /** What the device must provide before this intent can run. */
    val capability: Capability

    data class OpenApp(val appQuery: String) : AgentIntent {
        override val capability get() = Capability.NONE
    }

    /** Search for [query], inside the app named by [appQuery] when one is given. */
    data class Search(val query: String, val appQuery: String? = null) : AgentIntent {
        override val capability get() = Capability.NONE
    }

    /** Find a file on the device ("find my latest PDF"). */
    data class FindFile(val query: String) : AgentIntent {
        override val capability get() = Capability.FILES
    }

    data object GoBack : AgentIntent {
        override val capability get() = Capability.ACCESSIBILITY
    }

    data object GoHome : AgentIntent {
        override val capability get() = Capability.ACCESSIBILITY
    }

    data class Scroll(val direction: Direction) : AgentIntent {
        override val capability get() = Capability.ACCESSIBILITY
    }

    data class TypeText(val text: String) : AgentIntent {
        override val capability get() = Capability.ACCESSIBILITY
    }

    data class Tap(val target: String) : AgentIntent {
        override val capability get() = Capability.ACCESSIBILITY
    }

    /** "প্রথম ভিডিওটা চালাও": pick the item at [ordinal] (1-based, -1 = last). */
    data class SelectItem(val ordinal: Int, val kind: ItemKind) : AgentIntent {
        override val capability get() = Capability.ACCESSIBILITY
    }

    data object ReadScreen : AgentIntent {
        override val capability get() = Capability.ACCESSIBILITY
    }

    /** Flashlight, volume, and the switches Android only lets the user flip (Wi-Fi, Bluetooth…). */
    data class DeviceControl(val target: DeviceTarget, val action: DeviceAction) : AgentIntent {
        override val capability get() = Capability.NONE
    }

    data object StopSpeaking : AgentIntent {
        override val capability get() = Capability.NONE
    }

    data object Help : AgentIntent {
        override val capability get() = Capability.NONE
    }

    /** Not understood by the offline parser. Later phases hand these to the AI provider. */
    data class Unknown(val text: String) : AgentIntent {
        override val capability get() = Capability.AI
    }
}

enum class Direction { UP, DOWN }

enum class DeviceTarget { FLASHLIGHT, WIFI, BLUETOOTH, MOBILE_DATA, LOCATION, VOLUME, BRIGHTNESS }

enum class DeviceAction { ON, OFF, UP, DOWN, MUTE, OPEN }

enum class ItemKind { VIDEO, IMAGE, LINK, ANY }

enum class Capability {
    /** Works with plain Android intents. */
    NONE,

    /** Needs the agent's Accessibility Service to see and touch the screen. */
    ACCESSIBILITY,

    /** Needs storage access. */
    FILES,

    /** Needs a network AI provider. */
    AI,
}
