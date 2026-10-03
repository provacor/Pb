package com.provacor.sathi.core.response

import com.provacor.sathi.core.model.AgentIntent
import com.provacor.sathi.core.model.DeviceAction
import com.provacor.sathi.core.model.DeviceTarget
import com.provacor.sathi.core.model.Direction
import com.provacor.sathi.core.model.ItemKind
import com.provacor.sathi.core.model.Language
import com.provacor.sathi.core.model.Language.BENGALI
import com.provacor.sathi.core.model.Language.ENGLISH
import com.provacor.sathi.core.plan.PlanStep

/** Everything the agent says, in short spoken-style Bengali and English. */
object Responses {

    fun describe(step: PlanStep, lang: Language): String {
        val app = step.app?.label
        return when (val i = step.intent) {
            is AgentIntent.OpenApp -> pick(lang, "${app ?: i.appQuery} খোলা", "Open ${app ?: i.appQuery}")
            is AgentIntent.Search -> when {
                i.query.isEmpty() -> pick(lang, "সার্চ", "Search")
                app != null -> pick(lang, "$app-এ \"${i.query}\" সার্চ", "Search \"${i.query}\" in $app")
                else -> pick(lang, "ওয়েবে \"${i.query}\" সার্চ", "Search the web for \"${i.query}\"")
            }
            is AgentIntent.FindFile -> pick(lang, "ফাইল খোঁজা: ${i.query}", "Find file: ${i.query}")
            AgentIntent.GoBack -> pick(lang, "পেছনে যাওয়া", "Go back")
            AgentIntent.GoHome -> pick(lang, "হোম স্ক্রিনে যাওয়া", "Go to home screen")
            is AgentIntent.Scroll -> if (i.direction == Direction.UP) pick(lang, "উপরে স্ক্রল", "Scroll up") else pick(lang, "নিচে স্ক্রল", "Scroll down")
            is AgentIntent.TypeText -> pick(lang, "লেখা: \"${i.text}\"", "Type \"${i.text}\"")
            is AgentIntent.Tap -> pick(lang, "\"${i.target}\"-এ চাপ দেওয়া", "Tap \"${i.target}\"")
            is AgentIntent.SelectItem -> pick(lang, "${ordinalBn(i.ordinal)} ${kindBn(i.kind)} খোলা", "Open the ${ordinalEn(i.ordinal)} ${kindEn(i.kind)}")
            AgentIntent.ReadScreen -> pick(lang, "স্ক্রিনের লেখা পড়া", "Read the screen")
            is AgentIntent.DeviceControl -> describeDevice(i.target, i.action, lang)
            AgentIntent.StopSpeaking -> pick(lang, "থামা", "Stop")
            AgentIntent.Help -> pick(lang, "সাহায্য", "Help")
            is AgentIntent.Unknown -> pick(lang, "বোঝা যায়নি: \"${i.text}\"", "Not understood: \"${i.text}\"")
        }
    }

    fun opened(app: String, lang: Language) = pick(lang, "$app খুলছি।", "Opening $app.")
    fun appNotFound(query: String, lang: Language) =
        pick(lang, "\"$query\" নামে কোনো অ্যাপ এই ফোনে পাইনি।", "I couldn't find an app called \"$query\" on this phone.")
    fun launchFailed(app: String, lang: Language) =
        pick(lang, "$app খুলতে পারিনি।", "I couldn't open $app.")
    fun searchingIn(query: String, app: String, lang: Language) =
        pick(lang, "$app-এ \"$query\" সার্চ করছি।", "Searching $app for \"$query\".")
    fun searchingWeb(query: String, lang: Language) =
        pick(lang, "ওয়েবে \"$query\" সার্চ করছি।", "Searching the web for \"$query\".")
    fun appCantSearch(app: String, lang: Language) =
        pick(lang, "$app সরাসরি সার্চ নেয় না। সাথীর স্ক্রিন নিয়ন্ত্রণ (Accessibility) চালু করলে আমি নিজে লিখে সার্চ করতে পারব।",
            "$app doesn't accept a direct search. Turn on Sathi's screen control (Accessibility) and I can type the search myself.")
    fun askSearchQuery(lang: Language) = pick(lang, "কী সার্চ করব?", "What should I search for?")

    fun needsAccessibility(step: PlanStep, lang: Language) =
        pick(lang, "\"${describe(step, BENGALI)}\" করতে সাথীর স্ক্রিন নিয়ন্ত্রণ (Accessibility) চালু করতে হবে। অনুমতি পেজ থেকে চালু করুন।",
            "\"${describe(step, ENGLISH)}\" needs Sathi's screen control (Accessibility). Turn it on from the Permissions page.")

    fun confirmTap(target: String, lang: Language) =
        pick(lang, "\"$target\" চাপলে কাজটা আর ফেরানো যাবে না। চাপব? হ্যাঁ বা না বলুন।",
            "Tapping \"$target\" can't be undone. Should I tap it? Say yes or no.")
    fun cancelled(lang: Language) = pick(lang, "ঠিক আছে, করলাম না।", "Okay, I won't.")

    // ---- screen control results ----
    fun openedVerified(app: String, lang: Language) = pick(lang, "$app খুলেছে।", "$app is open.")
    fun openedUnverified(app: String, lang: Language) =
        pick(lang, "$app খোলার অনুরোধ পাঠিয়েছি, কিন্তু স্ক্রিনে খুলতে দেখিনি।", "I asked $app to open but didn't see it on screen.")
    fun searchedIn(query: String, app: String, lang: Language) =
        pick(lang, "$app-এ \"$query\" সার্চ করেছি।", "Searched $app for \"$query\".")
    fun noSearchBox(app: String, lang: Language) =
        pick(lang, "$app-এ সার্চের ঘর খুঁজে পাইনি।", "I couldn't find a search box in $app.")
    fun tapped(target: String, lang: Language) = pick(lang, "\"$target\"-এ চাপ দিয়েছি।", "Tapped \"$target\".")
    fun notOnScreen(target: String, lang: Language) =
        pick(lang, "স্ক্রিনে \"$target\" খুঁজে পাইনি।", "I couldn't find \"$target\" on the screen.")
    fun noChange(lang: Language) =
        pick(lang, "চাপ দিয়েছি, কিন্তু স্ক্রিনে কিছু বদলায়নি।", "I tapped it, but nothing changed on screen.")
    fun typed(text: String, lang: Language) = pick(lang, "\"$text\" লিখেছি।", "Typed \"$text\".")
    fun noTextField(lang: Language) = pick(lang, "লেখার ঘর খুঁজে পাইনি।", "I couldn't find a text field.")
    fun typeFailed(lang: Language) = pick(lang, "লেখাটা বসাতে পারিনি।", "I couldn't enter the text.")
    fun scrolled(direction: Direction, lang: Language) =
        if (direction == Direction.UP) pick(lang, "উপরে স্ক্রল করেছি।", "Scrolled up.") else pick(lang, "নিচে স্ক্রল করেছি।", "Scrolled down.")
    fun scrollEnd(lang: Language) = pick(lang, "আর স্ক্রল করা যাচ্ছে না।", "Can't scroll any further.")
    fun wentBack(lang: Language) = pick(lang, "পেছনে গিয়েছি।", "Went back.")
    fun wentHome(lang: Language) = pick(lang, "হোম স্ক্রিনে গিয়েছি।", "Went to the home screen.")
    fun globalFailed(lang: Language) = pick(lang, "কাজটা করা গেল না।", "That didn't work.")
    fun selected(ordinal: Int, kind: ItemKind, lang: Language) =
        pick(lang, "${ordinalBn(ordinal)} ${kindBn(kind)}টা খুলেছি।", "Opened the ${ordinalEn(ordinal)} ${kindEn(kind)}.")
    fun itemNotFound(kind: ItemKind, lang: Language) =
        pick(lang, "স্ক্রিনে কোনো ${kindBn(kind)} খুঁজে পাইনি।", "I couldn't find any ${kindEn(kind)} on screen.")
    fun screenEmpty(lang: Language) = pick(lang, "স্ক্রিনে পড়ার মতো কোনো লেখা পাইনি।", "There's no text on screen to read.")
    fun ownScreen(lang: Language) =
        pick(lang, "এখন সাথীর নিজের স্ক্রিন খোলা। অন্য অ্যাপ খুলে তারপর বলুন।", "Sathi's own screen is open. Open another app first, then ask.")

    // ---- device ----
    fun device(target: DeviceTarget, action: DeviceAction, ok: Boolean, lang: Language): String {
        if (!ok) return pick(lang, "${targetBn(target)} নিয়ন্ত্রণ করতে পারিনি।", "I couldn't control the ${targetEn(target)}.")
        return when (target) {
            DeviceTarget.FLASHLIGHT -> if (action == DeviceAction.OFF) pick(lang, "টর্চ বন্ধ করেছি।", "Flashlight off.") else pick(lang, "টর্চ জ্বালিয়েছি।", "Flashlight on.")
            DeviceTarget.VOLUME -> when (action) {
                DeviceAction.UP -> pick(lang, "ভলিউম বাড়িয়েছি।", "Volume up.")
                DeviceAction.DOWN -> pick(lang, "ভলিউম কমিয়েছি।", "Volume down.")
                DeviceAction.MUTE, DeviceAction.OFF -> pick(lang, "সাউন্ড মিউট করেছি।", "Muted.")
                else -> pick(lang, "সাউন্ড চালু করেছি।", "Sound on.")
            }
            else -> pick(
                lang,
                "${targetBn(target)}-এর সেটিং খুলেছি। Android অ্যাপকে নিজে এটা বদলাতে দেয় না, তাই সুইচটা আপনি চাপুন।",
                "I opened the ${targetEn(target)} setting. Android doesn't let apps flip it, so tap the switch.",
            )
        }
    }

    private fun describeDevice(target: DeviceTarget, action: DeviceAction, lang: Language): String {
        val verbBn = when (action) {
            DeviceAction.ON -> "চালু"; DeviceAction.OFF -> "বন্ধ"; DeviceAction.UP -> "বাড়ানো"
            DeviceAction.DOWN -> "কমানো"; DeviceAction.MUTE -> "মিউট"; DeviceAction.OPEN -> "সেটিং"
        }
        val verbEn = when (action) {
            DeviceAction.ON -> "on"; DeviceAction.OFF -> "off"; DeviceAction.UP -> "up"
            DeviceAction.DOWN -> "down"; DeviceAction.MUTE -> "mute"; DeviceAction.OPEN -> "settings"
        }
        return pick(lang, "${targetBn(target)} $verbBn", "${targetEn(target).replaceFirstChar { it.uppercase() }} $verbEn")
    }

    private fun targetBn(t: DeviceTarget) = when (t) {
        DeviceTarget.FLASHLIGHT -> "টর্চ"; DeviceTarget.WIFI -> "Wi-Fi"; DeviceTarget.BLUETOOTH -> "ব্লুটুথ"
        DeviceTarget.MOBILE_DATA -> "ইন্টারনেট"; DeviceTarget.LOCATION -> "লোকেশন"; DeviceTarget.VOLUME -> "ভলিউম"
        DeviceTarget.BRIGHTNESS -> "উজ্জ্বলতা"
    }

    private fun targetEn(t: DeviceTarget) = when (t) {
        DeviceTarget.FLASHLIGHT -> "flashlight"; DeviceTarget.WIFI -> "Wi-Fi"; DeviceTarget.BLUETOOTH -> "Bluetooth"
        DeviceTarget.MOBILE_DATA -> "internet"; DeviceTarget.LOCATION -> "location"; DeviceTarget.VOLUME -> "volume"
        DeviceTarget.BRIGHTNESS -> "brightness"
    }
    fun filesNotYet(lang: Language) =
        pick(lang, "ফাইল খোঁজার সুবিধা এখনো তৈরি হয়নি।", "File search isn't available yet.")
    fun notUnderstood(text: String, lang: Language) =
        pick(lang, "\"$text\" বুঝতে পারিনি। আরেকভাবে বলবেন?", "I didn't understand \"$text\". Could you say it another way?")
    fun stopped(lang: Language) = pick(lang, "ঠিক আছে।", "Okay.")
    fun nothingHeard(lang: Language) = pick(lang, "কিছু শুনতে পাইনি।", "I didn't hear anything.")

    fun help(lang: Language) = pick(
        lang,
        "আমি অ্যাপ খুলতে, সার্চ করতে, স্ক্রিনে চাপ দিতে, স্ক্রল করতে, লিখতে, স্ক্রিন পড়ে শোনাতে, টর্চ আর ভলিউম নিয়ন্ত্রণ করতে পারি। যেমন বলুন: \"ইউটিউব খুলে physics wave সার্চ করো, প্রথম ভিডিওটা চালাও\"।",
        "I can open apps, search, tap, scroll, type, read the screen aloud, and control the flashlight and volume. Try: \"Open YouTube, search physics wave, play the first video\".",
    )

    /** Spoken summary: the message of every step, failed or not, joined. */
    fun summary(messages: List<String>): String = messages.filter { it.isNotBlank() }.joinToString(" ")

    private fun pick(lang: Language, bn: String, en: String) = if (lang == BENGALI) bn else en

    private fun ordinalBn(n: Int) = when (n) {
        1 -> "প্রথম"; 2 -> "দ্বিতীয়"; 3 -> "তৃতীয়"; 4 -> "চতুর্থ"; 5 -> "পঞ্চম"; -1 -> "শেষ"; else -> "$n নম্বর"
    }

    private fun ordinalEn(n: Int) = when (n) {
        1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"; -1 -> "last"; else -> "#$n"
    }

    private fun kindBn(k: ItemKind) = when (k) {
        ItemKind.VIDEO -> "ভিডিও"; ItemKind.IMAGE -> "ছবি"; ItemKind.LINK -> "ফলাফল"; ItemKind.ANY -> "আইটেম"
    }

    private fun kindEn(k: ItemKind) = when (k) {
        ItemKind.VIDEO -> "video"; ItemKind.IMAGE -> "image"; ItemKind.LINK -> "result"; ItemKind.ANY -> "item"
    }
}
