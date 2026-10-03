package com.provacor.sathi.core.screen

import com.provacor.sathi.core.model.ItemKind
import com.provacor.sathi.core.model.ScreenState
import com.provacor.sathi.core.model.UiElement
import com.provacor.sathi.core.text.Similarity
import com.provacor.sathi.core.text.TextNormalizer

/**
 * Finds things on a [ScreenState]. Labels come first (text, then content
 * description, then view id); coordinates are only used to order and filter.
 */
object ScreenQueries {

    /** The element whose label best matches [target] ("সাবস্ক্রাইব" also finds "Subscribe"). */
    fun findByLabel(state: ScreenState, target: String): UiElement? {
        val names = UiSynonyms.expand(target)
        if (names.isEmpty()) return null
        return state.elements
            .asSequence()
            .filter { it.label.isNotBlank() && !it.editable }
            .map { it to labelScore(it, names) }
            .filter { it.second >= 0.6 }
            .sortedWith(compareByDescending<Pair<UiElement, Double>> { it.second }.thenBy { it.first.bounds.top })
            .firstOrNull()?.first
    }

    /** The field to type into: the focused editable field, else the topmost one. */
    fun findTextField(state: ScreenState): UiElement? =
        state.elements.firstOrNull { it.editable && it.focused }
            ?: state.elements.filter { it.editable }.minByOrNull { it.bounds.top }

    /** A search icon or button that opens a search field. */
    fun findSearchButton(state: ScreenState): UiElement? =
        state.elements
            .filter { !it.editable && it.clickTarget != null && isSearchy(it) }
            .minByOrNull { it.bounds.top }

    /**
     * The [ordinal]-th list item in the content area (1-based, -1 = last).
     * Toolbars, tabs and bottom navigation are skipped by position and label.
     * For videos, rows whose label looks like a video (a duration, "views") win.
     */
    fun findItem(state: ScreenState, ordinal: Int, kind: ItemKind): UiElement? {
        val h = state.screenHeight.takeIf { it > 0 } ?: return null
        val w = state.screenWidth.takeIf { it > 0 } ?: return null
        val rows = state.elements
            .asSequence()
            .mapNotNull { state.element(it.clickTarget) }
            .distinctBy { it.index }
            .filter { !it.editable }
            .filter { it.bounds.top >= h * 0.10 && it.bounds.bottom <= h * 0.95 }
            .filter { it.bounds.width >= w * 0.4 && it.bounds.height >= h * 0.05 }
            .filterNot { isChrome(rowLabel(state, it)) }
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
            .toList()
        if (rows.isEmpty()) return null

        val preferred = when (kind) {
            ItemKind.VIDEO -> rows.filter { looksLikeVideo(rowLabel(state, it)) }
            ItemKind.IMAGE -> rows.filter { it.className?.contains("Image", ignoreCase = true) == true || rowHasImage(state, it) }
            else -> emptyList()
        }.ifEmpty { rows }

        return when {
            ordinal == -1 -> preferred.lastOrNull()
            ordinal >= 1 -> preferred.getOrNull(ordinal - 1)
            else -> null
        }
    }

    fun largestScrollable(state: ScreenState): UiElement? =
        state.elements.filter { it.scrollable }.maxByOrNull { it.bounds.area }

    /** Visible text in reading order, for "পড়ে শোনাও". */
    fun readableText(state: ScreenState, maxChars: Int = 700): String {
        val seen = LinkedHashSet<String>()
        state.elements
            .filter { !it.editable }
            .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
            .forEach { e -> e.text?.trim()?.takeIf { it.length >= 2 }?.let(seen::add) }
        val sb = StringBuilder()
        for (t in seen) {
            if (sb.length + t.length > maxChars) break
            if (sb.isNotEmpty()) sb.append(". ")
            sb.append(t)
        }
        return sb.toString()
    }

    /** Label of a row including its children, so a row whose own label is empty can still be judged. */
    internal fun rowLabel(state: ScreenState, row: UiElement): String =
        (listOf(row.label) + state.elements.filter { it.clickTarget == row.index && it.index != row.index }.map { it.label })
            .filter { it.isNotBlank() }
            .joinToString(" ")

    private fun rowHasImage(state: ScreenState, row: UiElement) =
        state.elements.any { it.clickTarget == row.index && it.className?.contains("Image", ignoreCase = true) == true }

    private fun labelScore(e: UiElement, names: List<String>): Double {
        val label = TextNormalizer.normalize(e.label)
        val id = e.viewId?.substringAfterLast('/')?.lowercase()?.replace('_', ' ')
        var best = 0.0
        for (n in names) {
            val s = when {
                label == n -> 1.0
                label.startsWith(n) -> 0.9
                n.length >= 2 && label.split(' ').contains(n) -> 0.85
                n.length >= 3 && label.contains(n) -> 0.75
                id != null && id == n -> 0.7
                else -> Similarity.ratio(label, n).let { if (it >= 0.75) it * 0.75 else 0.0 }
            }
            if (s > best) best = s
        }
        if (best > 0 && e.clickTarget != null) best += 0.05
        return best
    }

    private val durationPattern = Regex("\\b\\d{1,2}:\\d{2}\\b")
    private val videoWords = listOf("minute", "second", "hour", "views", "watching", "play video", "মিনিট", "সেকেন্ড", "ঘণ্টা", "ভিউ", "বার দেখা")

    internal fun looksLikeVideo(label: String): Boolean {
        val l = label.lowercase()
        return durationPattern.containsMatchIn(l) || videoWords.any { l.contains(it) }
    }

    private val searchWords = listOf("search", "সার্চ", "খুঁজুন", "খুজুন", "অনুসন্ধান")

    private fun isSearchy(e: UiElement): Boolean {
        val l = (e.label + " " + (e.viewId ?: "")).lowercase()
        return searchWords.any { l.contains(it) } && !l.contains("voice") && !l.contains("ভয়েস")
    }

    private val chromeLabels = setOf(
        "search", "filter", "filters", "home", "shorts", "subscriptions", "you", "library", "navigate up",
        "more options", "cast", "voice search", "back", "menu", "notifications", "create",
        "সার্চ", "ফিল্টার", "হোম", "সাবস্ক্রিপশন", "আপনি", "লাইব্রেরি", "আরও বিকল্প", "পিছনে যান",
    )

    private fun isChrome(label: String): Boolean {
        val l = TextNormalizer.normalize(label)
        return l.isEmpty() || l in chromeLabels.map(TextNormalizer::normalize)
    }
}

/** Common button words in both languages, so either spoken form finds the on-screen one. */
object UiSynonyms {
    private val groups: List<Set<String>> = listOf(
        setOf("search", "সার্চ", "খুঁজুন", "অনুসন্ধান"),
        setOf("subscribe", "সাবস্ক্রাইব"),
        setOf("like", "লাইক"),
        setOf("share", "শেয়ার"),
        setOf("send", "সেন্ড", "পাঠাও", "পাঠান"),
        setOf("ok", "okay", "ঠিক আছে", "ওকে"),
        setOf("yes", "হ্যাঁ"),
        setOf("no", "না"),
        setOf("cancel", "বাতিল", "ক্যানসেল"),
        setOf("next", "পরবর্তী", "নেক্সট"),
        setOf("back", "পিছনে", "ব্যাক"),
        setOf("play", "প্লে", "চালান"),
        setOf("pause", "পজ", "থামান"),
        setOf("settings", "সেটিংস"),
        setOf("comments", "কমেন্ট", "মন্তব্য"),
        setOf("download", "ডাউনলোড"),
        setOf("install", "ইনস্টল"),
        setOf("open", "ওপেন", "খুলুন"),
        setOf("save", "সেভ", "সংরক্ষণ"),
        setOf("delete", "ডিলিট", "মুছুন"),
        setOf("call", "কল"),
        setOf("message", "মেসেজ", "বার্তা"),
    ).map { g -> g.map(TextNormalizer::normalize).toSet() }

    fun expand(word: String): List<String> {
        val n = TextNormalizer.normalize(word)
        if (n.isEmpty()) return emptyList()
        return (listOf(n) + (groups.firstOrNull { n in it } ?: emptySet())).distinct()
    }
}
