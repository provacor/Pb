package com.provacor.sathi.core.parse

import com.provacor.sathi.core.apps.AppAliases
import com.provacor.sathi.core.model.AgentIntent
import com.provacor.sathi.core.model.Direction
import com.provacor.sathi.core.model.ItemKind
import com.provacor.sathi.core.text.TextNormalizer

/**
 * Offline, rule-based understanding of Bengali, English and mixed commands.
 *
 * It splits a command into clauses ("ইউটিউব খুলে physics search করো" →
 * "ইউটিউব খুলে" + "physics search করো") and classifies each clause.
 * Anything it cannot place becomes [AgentIntent.Unknown], which a network AI
 * provider can take over in a later phase.
 */
class CommandInterpreter(private val aliases: AppAliases = AppAliases()) {

    fun interpret(command: String): List<AgentIntent> {
        val normalized = TextNormalizer.normalize(command)
        if (normalized.isEmpty()) return emptyList()
        return splitClauses(normalized)
            .map { classify(TextNormalizer.tokens(it)) }
            .filterNot { it is AgentIntent.Unknown && it.text.isBlank() }
    }

    // ---- clause splitting -------------------------------------------------

    internal fun splitClauses(normalized: String): List<String> {
        val hard = normalized
            .replace(HARD_SEPARATORS, " | ")
            .split('|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return hard
            .flatMap(::splitSoftConnectors)
            .flatMap(::splitParticiples)
    }

    /** "and" / "এবং" / "আর" split only when the right side starts a new action. */
    private fun splitSoftConnectors(clause: String): List<String> {
        val tokens = TextNormalizer.tokens(clause)
        val out = mutableListOf<String>()
        var start = 0
        for (i in tokens.indices) {
            if (tokens[i] in SOFT_CONNECTORS && i > start && i < tokens.lastIndex) {
                val right = tokens.subList(i + 1, tokens.size)
                if (right.any { it.isActionWord() }) {
                    out += tokens.subList(start, i).joinToString(" ")
                    start = i + 1
                }
            }
        }
        out += tokens.subList(start, tokens.size).joinToString(" ")
        return out.filter { it.isNotBlank() }
    }

    /**
     * Bengali chains actions with a participle: "ইউটিউব খুলে ... খোঁজো",
     * "Youtube open করে ... search করো". Split right after the participle.
     */
    private fun splitParticiples(clause: String): List<String> {
        val tokens = TextNormalizer.tokens(clause)
        val out = mutableListOf<String>()
        var start = 0
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val endOfParticiple = when {
                t in PARTICIPLES -> i
                t == "করে" && i > 0 && tokens[i - 1] in VERBS_TAKING_KORE -> i
                t == "পর" && i > 0 && tokens[i - 1] == "খোলার" -> i
                else -> -1
            }
            if (endOfParticiple >= 0 && endOfParticiple < tokens.lastIndex) {
                val next = tokens[endOfParticiple + 1]
                if (next !in PARTICIPLE_COMPLEMENTS) {
                    out += tokens.subList(start, endOfParticiple + 1).joinToString(" ")
                    start = endOfParticiple + 1
                }
            }
            i++
        }
        out += tokens.subList(start, tokens.size).joinToString(" ")
        return out.filter { it.isNotBlank() }
    }

    // ---- classification ---------------------------------------------------

    internal fun classify(tokens: List<String>): AgentIntent {
        if (tokens.isEmpty()) return AgentIntent.Unknown("")
        val text = tokens.joinToString(" ")
        val stems = tokens.map { TextNormalizer.stripBengaliSuffix(it) }
        val has = { set: Set<String> -> tokens.any { it in set } || stems.any { it in set } }
        val hasSearch = has(SEARCH)

        if (!hasSearch && tokens.size <= 3 && (has(STOP) || text in STOP_PHRASES)) {
            return AgentIntent.StopSpeaking
        }
        if (has(HELP) || HELP_PHRASES.any { text.contains(it) }) return AgentIntent.Help

        ordinalOf(stems)?.let { ordinal ->
            val kind = kindOf(stems)
            if (!hasSearch && (kind != null || has(SELECT_ACTIONS))) {
                return AgentIntent.SelectItem(ordinal, kind ?: ItemKind.ANY)
            }
        }

        val looksLikeFile = has(STRONG_FILE_WORDS) || (has(MEDIA_WORDS) && has(OWNERSHIP_WORDS))
        if (has(FIND) && looksLikeFile && !has(EXPLICIT_SEARCH) && detectSearchApp(tokens) == null) {
            val query = remainder(tokens, FIND + FIND_EXTRA)
                .split(' ')
                .joinToString(" ") { TextNormalizer.stripBengaliSuffix(it) }
            return AgentIntent.FindFile(query)
        }

        if (hasSearch) {
            val app = detectSearchApp(tokens)
            val drop = SEARCH + TYPE + SEARCH_EXTRA + (app?.tokens ?: emptySet())
            return AgentIntent.Search(remainder(tokens, drop), app?.name)
        }

        if (has(BACK) || BACK_PHRASES.any { text.contains(it) }) return AgentIntent.GoBack
        if (has(HOME)) return AgentIntent.GoHome

        val up = has(UP)
        val down = has(DOWN)
        if (has(SCROLL) || ((up || down) && has(MOVE_VERBS))) {
            return AgentIntent.Scroll(if (up && !down) Direction.UP else Direction.DOWN)
        }

        if (has(READ) || READ_PHRASES.any { text.contains(it) }) return AgentIntent.ReadScreen

        if (has(TYPE)) {
            val body = remainder(tokens, TYPE, fillers = TYPE_FILLERS)
            return if (body.isEmpty()) AgentIntent.Unknown(text) else AgentIntent.TypeText(body)
        }

        if (has(TAP)) {
            val target = remainder(tokens, TAP + TAP_EXTRA)
                .split(' ')
                .joinToString(" ") { TextNormalizer.stripBengaliSuffix(it, includeLocative = true) }
                .trim()
            return if (target.isEmpty()) AgentIntent.Unknown(text) else AgentIntent.Tap(target)
        }

        if (has(OPEN)) {
            // "এই ছবিটা ওপেন করো" points at something on screen, not at an app.
            if (tokens.first() in DEICTIC) {
                val target = remainder(tokens, OPEN + DEICTIC)
                    .split(' ')
                    .joinToString(" ") { TextNormalizer.stripBengaliSuffix(it) }
                    .trim()
                if (target.isNotEmpty()) return AgentIntent.Tap(target)
            }
            val app = remainder(tokens, OPEN)
                .split(' ')
                .joinToString(" ") { TextNormalizer.stripBengaliSuffix(it, includeLocative = true) }
                .trim()
            return if (app.isEmpty()) AgentIntent.Unknown(text) else AgentIntent.OpenApp(app)
        }

        return AgentIntent.Unknown(text)
    }

    private fun remainder(tokens: List<String>, drop: Set<String>, fillers: Set<String> = FILLERS): String =
        tokens.filterNot { it in drop || it in fillers || TextNormalizer.stripBengaliSuffix(it) in drop }
            .map(::stripMixedSuffix)
            .joinToString(" ")
            .trim()

    /** A Bengali ending glued onto an English word is never part of it: "photoটা" → "photo", "pdf-এ" → "pdf". */
    private fun stripMixedSuffix(token: String): String =
        MIXED_SUFFIX.matchEntire(token)?.groupValues?.get(1) ?: token

    private fun ordinalOf(stems: List<String>): Int? {
        for (s in stems) ORDINALS[s]?.let { return it }
        return null
    }

    private fun kindOf(stems: List<String>): ItemKind? = when {
        stems.any { it in VIDEO_WORDS } -> ItemKind.VIDEO
        stems.any { it in IMAGE_WORDS } -> ItemKind.IMAGE
        stems.any { it in LINK_WORDS } -> ItemKind.LINK
        else -> null
    }

    private data class DetectedApp(val name: String, val tokens: Set<String>)

    /**
     * App named inside a search clause: "ইউটিউবে physics খোঁজো",
     * "youtube-এ ...", "search physics on youtube".
     */
    private fun detectSearchApp(tokens: List<String>): DetectedApp? {
        for ((i, tok) in tokens.withIndex()) {
            val next = tokens.getOrNull(i + 1)
            val prev = tokens.getOrNull(i - 1)

            // Bengali alias with a locative ending, or followed by "এ"/"থেকে".
            val stem = TextNormalizer.stripBengaliSuffix(tok, includeLocative = true)
            val hasLocative = stem != tok || next in LOCATIVE_FOLLOWERS
            if (hasLocative && aliases.isKnown(stem) && stem !in GENERIC_ALIASES) {
                return DetectedApp(stem, setOfNotNull(tok, next?.takeIf { it in LOCATIVE_FOLLOWERS }))
            }

            // English app name: "on youtube", "in chrome", "youtube-এ".
            val latin = tok.substringBefore('-').removeSuffix("এ")
            if (latin in LATIN_APPS && (prev in setOf("on", "in") || tok != latin || next in LOCATIVE_FOLLOWERS)) {
                return DetectedApp(latin, setOfNotNull(tok, prev?.takeIf { it == "on" || it == "in" }, next?.takeIf { it in LOCATIVE_FOLLOWERS }))
            }
        }
        return null
    }

    private fun String.isActionWord(): Boolean {
        val s = TextNormalizer.stripBengaliSuffix(this)
        return this in ACTION_WORDS || s in ACTION_WORDS
    }

    companion object {
        private fun set(vararg words: String): Set<String> = words.map(TextNormalizer::normalize).toSet()

        private val HARD_SEPARATORS =
            Regex("[।॥,;!?]|\\.(?=\\s|$)|(?<=\\s|^)(and then|then|after that|তারপর|তার পর|এরপর|এর পর|তারপরে)(?=\\s|$)")
        private val SOFT_CONNECTORS = set("and", "এবং", "আর", "ও")

        private val PARTICIPLES = set("খুলে", "খুঁজে", "লিখে", "চেপে", "গিয়ে", "গিয়ে")
        private val VERBS_TAKING_KORE = set("open", "ওপেন", "search", "সার্চ", "type", "টাইপ", "click", "ক্লিক", "tap", "ট্যাপ", "scroll", "স্ক্রল", "চালু")
        private val PARTICIPLE_COMPLEMENTS = set("দাও", "দিন", "দে", "দেও", "দেন", "বের", "ফেলো", "ফেল")

        private val OPEN = set("খোলো", "খুলো", "খুলুন", "খোল", "খুলে", "খোলেন", "খোলার", "পর", "open", "ওপেন", "launch", "লঞ্চ", "start", "চালু", "চালাও", "চালান", "যাও")
        private val SEARCH = set("search", "সার্চ", "সার্চে", "খুঁজো", "খুঁজুন", "খোঁজো", "খোঁজ", "খুঁজে", "খোঁজা", "look")
        private val SEARCH_EXTRA = set(
            "for", "up", "দিয়ে", "এ", "-এ", "on", "in", "করে", "বের", "ফলাফল",
            "web", "ওয়েবে", "internet", "ইন্টারনেটে", "online", "অনলাইনে",
        )
        private val FIND = set("find", "খুঁজে", "খোঁজো", "খুঁজো", "খুঁজুন", "বের", "locate")
        private val FIND_EXTRA = set("থেকে", "from", "in", "করো", "ফাইল", "ম্যানেজার", "manager")
        private val EXPLICIT_SEARCH = set("search", "সার্চ", "সার্চে")
        private val STRONG_FILE_WORDS = set("pdf", "পিডিএফ", "file", "ফাইল", "document", "ডকুমেন্ট", "docx", "txt", "pdfs")
        private val MEDIA_WORDS = set("photo", "ছবি", "image", "picture", "video", "ভিডিও", "screenshot", "স্ক্রিনশট")
        private val OWNERSHIP_WORDS = set("my", "আমার", "latest", "সর্বশেষ", "শেষ", "আজকের", "today's", "today", "recent", "নতুন", "গ্যালারি", "gallery", "named", "নামের")
        private val DEICTIC = set("এই", "ওই", "this", "that")
        private val TYPE_FILLERS = set("করো", "করুন", "দাও", "দিন", "প্লিজ", "please")
        private val BACK = set("back", "ব্যাক", "পেছনে", "পিছনে", "পেছন", "পিছন", "পেছনের")
        private val BACK_PHRASES = set("ফিরে যাও", "ফিরে যান", "go back", "আগের পেজে")
        private val HOME = set("home", "হোম", "হোমে", "হোমস্ক্রিন")
        private val SCROLL = set("scroll", "স্ক্রল")
        private val UP = set("up", "উপরে", "ওপরে", "উপর", "ওপর", "ওঠো", "উঠো", "ওঠাও")
        private val DOWN = set("down", "নিচে", "নীচে", "নিচের", "নামো", "নামাও")
        private val MOVE_VERBS = set("যাও", "যান", "নামো", "নামাও", "ওঠো", "উঠো", "ওঠাও", "করো", "go", "move", "আরও", "আরো")
        private val READ = set("read", "পড়ো", "পড়", "পড়ুন", "শোনাও", "শুনাও")
        private val READ_PHRASES = set("পড়ে শোনাও", "পড়ে দাও", "read out", "read this")
        private val TYPE = set("type", "টাইপ", "লিখো", "লেখো", "লিখুন", "লিখে", "write")
        private val TAP = set("click", "ক্লিক", "tap", "ট্যাপ", "চাপো", "চাপুন", "চাপ", "press", "প্রেস")
        private val TAP_EXTRA = set("on", "button", "বাটন", "বাটনে", "উপর")
        private val SELECT_ACTIONS = set("চালাও", "চালান", "play", "প্লে", "খোলো", "খুলো", "খুলুন", "open", "ওপেন", "ক্লিক", "click", "tap", "ট্যাপ", "চাপো", "সিলেক্ট", "select", "দেখাও", "show")
        private val STOP = set("থামো", "থামাও", "থামুন", "চুপ")
        private val STOP_PHRASES = set("stop", "stop speaking", "বন্ধ করো", "বন্ধ", "cancel", "বাতিল")
        private val HELP = set("help", "সাহায্য", "হেল্প")
        private val HELP_PHRASES = set("কী করতে পারো", "কি করতে পারো", "what can you do")

        private val VIDEO_WORDS = set("ভিডিও", "video", "ভিডিয়ো")
        private val IMAGE_WORDS = set("ছবি", "image", "photo", "picture", "ফটো")
        private val LINK_WORDS = set("লিংক", "link", "result", "রেজাল্ট", "ফলাফল")

        private val ORDINALS: Map<String, Int> = buildMap {
            listOf("প্রথম", "first", "1st", "1ম", "এক নম্বর").forEach { put(TextNormalizer.normalize(it), 1) }
            listOf("দ্বিতীয়", "second", "2nd", "2য়").forEach { put(TextNormalizer.normalize(it), 2) }
            listOf("তৃতীয়", "third", "3rd", "3য়").forEach { put(TextNormalizer.normalize(it), 3) }
            listOf("চতুর্থ", "fourth", "4th").forEach { put(TextNormalizer.normalize(it), 4) }
            listOf("পঞ্চম", "fifth", "5th").forEach { put(TextNormalizer.normalize(it), 5) }
            listOf("শেষ", "শেষের", "last").forEach { put(TextNormalizer.normalize(it), -1) }
        }

        private val MIXED_SUFFIX = Regex("^(.*[a-z0-9])-?[\u0980-\u09FF]+$")

        private val LOCATIVE_FOLLOWERS = set("এ", "-এ", "থেকে", "এর", "এতে")

        /** Bengali aliases that are ordinary nouns too ("ছবি", "গান"), so never an app inside a search. */
        private val GENERIC_ALIASES = set("ছবি", "ফটো", "গান", "ফাইল", "ফাইলস", "নোট", "নোটস", "ফোন", "মেসেজ", "মিউজিক", "ঘড়ি", "ক্যামেরা", "ব্রাউজার")

        private val LATIN_APPS = set(
            "youtube", "chrome", "google", "gmail", "maps", "facebook", "messenger", "instagram",
            "telegram", "tiktok", "spotify", "netflix", "linkedin", "twitter", "drive", "play store", "playstore",
        )

        private val FILLERS = set(
            "আমার", "জন্য", "একটু", "প্লিজ", "প্লীজ", "please", "দয়া", "করে", "করো", "করুন", "কর", "করবে",
            "দাও", "দিন", "দে", "দেও", "তো", "এখন", "now", "the", "a", "an", "to", "me", "my", "for",
            "এই", "ওই", "একটা", "একটি", "অ্যাপ", "app", "অ্যাপটা", "অ্যাপটি", "টা", "টি", "তুমি", "আপনি",
            "just", "can", "you", "could", "would", "ভাই", "হ্যাঁ", "hey", "সাথী", "sathi",
        )

        private val ACTION_WORDS: Set<String> =
            OPEN + SEARCH + BACK + HOME + SCROLL + READ + TYPE + TAP + SELECT_ACTIONS + STOP + FIND - set("পর", "যাও", "start", "look", "চাপ")
    }
}
