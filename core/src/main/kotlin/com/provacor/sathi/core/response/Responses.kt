package com.provacor.sathi.core.response

import com.provacor.sathi.core.model.AgentIntent
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
        pick(lang, "$app সরাসরি সার্চ নেয় না। স্ক্রিনে লিখে সার্চ করতে Accessibility দরকার, সেটা পরের ধাপে আসছে।",
            "$app doesn't accept a direct search. Typing into its search box needs Accessibility, which comes in the next phase.")
    fun askSearchQuery(lang: Language) = pick(lang, "কী সার্চ করব?", "What should I search for?")

    fun needsAccessibility(step: PlanStep, lang: Language) =
        pick(lang, "\"${describe(step, BENGALI)}\" করতে স্ক্রিন নিয়ন্ত্রণ (Accessibility) লাগবে। এটা পরের ধাপে যোগ হবে।",
            "\"${describe(step, ENGLISH)}\" needs screen control (Accessibility), which is added in the next phase.")
    fun filesNotYet(lang: Language) =
        pick(lang, "ফাইল খোঁজার সুবিধা এখনো তৈরি হয়নি।", "File search isn't available yet.")
    fun notUnderstood(text: String, lang: Language) =
        pick(lang, "\"$text\" বুঝতে পারিনি। আরেকভাবে বলবেন?", "I didn't understand \"$text\". Could you say it another way?")
    fun stopped(lang: Language) = pick(lang, "ঠিক আছে।", "Okay.")
    fun nothingHeard(lang: Language) = pick(lang, "কিছু শুনতে পাইনি।", "I didn't hear anything.")

    fun help(lang: Language) = pick(
        lang,
        "আমি অ্যাপ খুলতে পারি, ইউটিউব বা ওয়েবে সার্চ করতে পারি। যেমন বলুন: \"ইউটিউব খুলে physics wave সার্চ করো\"।",
        "I can open apps and search YouTube or the web. Try: \"Open YouTube and search physics wave\".",
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
