package com.provacor.sathi.core.plan

import com.provacor.sathi.core.screen.UiSynonyms
import com.provacor.sathi.core.text.TextNormalizer

/**
 * Actions that are hard to undo (sending, deleting, paying, calling, posting)
 * are only carried out after the user answers yes.
 */
object Confirmation {
    private fun set(vararg w: String) = w.map(TextNormalizer::normalize).toSet()

    private val YES = set("হ্যাঁ", "হ্যা", "হা", "হুম", "yes", "yeah", "ok", "okay", "ঠিক", "ওকে", "করো", "করুন", "পাঠাও", "চাপো", "sure", "confirm")
    private val NO = set("না", "no", "নাহ", "বাতিল", "cancel", "থাক", "থামো", "stop", "don't")

    private val CONSEQUENTIAL = set(
        "send", "সেন্ড", "পাঠাও", "পাঠান", "post", "পোস্ট", "publish", "share", "শেয়ার",
        "delete", "ডিলিট", "মুছুন", "মুছে", "remove", "uninstall", "আনইনস্টল",
        "buy", "কিনুন", "কিনো", "pay", "পেমেন্ট", "পে", "order", "অর্ডার", "place order", "checkout", "purchase",
        "call", "কল", "submit", "সাবমিট", "confirm", "transfer", "ট্রান্সফার", "সেন্ড মানি",
    )

    fun isYes(answer: String): Boolean = firstWord(answer) in YES

    fun isNo(answer: String): Boolean = firstWord(answer) in NO

    /** True when tapping something labelled [target] would send, delete, pay, call or post. */
    fun isConsequential(target: String): Boolean {
        val words = UiSynonyms.expand(target).flatMap { it.split(' ') } + TextNormalizer.normalize(target)
        return words.any { it in CONSEQUENTIAL }
    }

    private fun firstWord(text: String): String =
        TextNormalizer.tokens(TextNormalizer.normalize(text)).firstOrNull().orEmpty()
}
