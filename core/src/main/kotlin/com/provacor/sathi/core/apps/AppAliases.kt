package com.provacor.sathi.core.apps

import com.provacor.sathi.core.text.TextNormalizer

/**
 * Spoken names → names an app's launcher label may carry.
 * These are display names, not package names: the resolver still matches
 * against whatever is actually installed, so "গ্যালারি" finds "Gallery" on
 * one phone and "Photos" on another.
 */
class AppAliases(raw: Map<String, List<String>> = DEFAULT) {
    private val map: Map<String, List<String>> =
        raw.entries.associate { (k, v) -> TextNormalizer.normalize(k) to v.map(TextNormalizer::normalize) }

    /** Longest alias first, so "ফাইল ম্যানেজার" wins over "ফাইল". */
    val spokenNames: List<String> = map.keys.sortedByDescending { it.length }

    fun expand(spoken: String): List<String> {
        val n = TextNormalizer.normalize(spoken)
        return (listOf(n) + (map[n] ?: emptyList())).distinct()
    }

    fun isKnown(spoken: String): Boolean = TextNormalizer.normalize(spoken) in map

    companion object {
        val DEFAULT: Map<String, List<String>> = mapOf(
            "ইউটিউব" to listOf("youtube"),
            "ইউটিউব মিউজিক" to listOf("youtube music", "yt music"),
            "ক্রোম" to listOf("chrome"),
            "ব্রাউজার" to listOf("chrome", "browser", "internet", "firefox"),
            "browser" to listOf("chrome", "internet", "firefox"),
            "গুগল" to listOf("google"),
            "গ্যালারি" to listOf("gallery", "photos", "album"),
            "গ্যালারী" to listOf("gallery", "photos", "album"),
            "gallery" to listOf("photos", "album"),
            "ফটো" to listOf("photos", "gallery"),
            "ফাইল ম্যানেজার" to listOf("files", "file manager", "my files", "file"),
            "ফাইল" to listOf("files", "file manager", "my files"),
            "ফাইলস" to listOf("files", "file manager", "my files"),
            "file manager" to listOf("files", "my files", "file"),
            "ক্যামেরা" to listOf("camera"),
            "সেটিংস" to listOf("settings"),
            "সেটিং" to listOf("settings"),
            "হোয়াটসঅ্যাপ" to listOf("whatsapp"),
            "হোয়াটস অ্যাপ" to listOf("whatsapp"),
            "ফেসবুক" to listOf("facebook"),
            "মেসেঞ্জার" to listOf("messenger"),
            "ইনস্টাগ্রাম" to listOf("instagram"),
            "টেলিগ্রাম" to listOf("telegram"),
            "ইমো" to listOf("imo"),
            "টিকটক" to listOf("tiktok"),
            "টুইটার" to listOf("x", "twitter"),
            "লিংকডইন" to listOf("linkedin"),
            "ম্যাপ" to listOf("maps"),
            "ম্যাপস" to listOf("maps"),
            "প্লে স্টোর" to listOf("play store"),
            "প্লেস্টোর" to listOf("play store"),
            "জিমেইল" to listOf("gmail"),
            "ইমেইল" to listOf("gmail", "email", "mail"),
            "ক্যালকুলেটর" to listOf("calculator"),
            "ঘড়ি" to listOf("clock"),
            "অ্যালার্ম" to listOf("clock", "alarm"),
            "ক্যালেন্ডার" to listOf("calendar"),
            "ফোন" to listOf("phone", "dialer"),
            "ডায়ালার" to listOf("phone", "dialer"),
            "মেসেজ" to listOf("messages", "messaging"),
            "এসএমএস" to listOf("messages", "messaging"),
            "কন্টাক্ট" to listOf("contacts"),
            "কন্টাক্টস" to listOf("contacts"),
            "মিউজিক" to listOf("music", "youtube music"),
            "গান" to listOf("music", "youtube music", "spotify"),
            "নোট" to listOf("notes", "keep notes", "keep"),
            "নোটস" to listOf("notes", "keep notes", "keep"),
            "ড্রাইভ" to listOf("drive"),
            "বিকাশ" to listOf("bkash"),
            "নগদ" to listOf("nagad"),
            "রকেট" to listOf("rocket"),
            "স্পটিফাই" to listOf("spotify"),
            "নেটফ্লিক্স" to listOf("netflix"),
            "জুম" to listOf("zoom"),
            "গুগল মিট" to listOf("meet"),
        )
    }
}
