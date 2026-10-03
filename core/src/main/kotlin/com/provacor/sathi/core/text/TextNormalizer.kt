package com.provacor.sathi.core.text

import java.text.Normalizer
import java.util.Locale

/**
 * Brings spoken or typed text into one comparable form:
 * NFC, lower case, Bengali digits to ASCII, chandrabindu dropped
 * (speech engines are inconsistent with "খুঁজো" vs "খুজো"),
 * zero-width joiners removed and whitespace collapsed.
 */
object TextNormalizer {
    private const val CHANDRABINDU = 'ঁ'
    private val zeroWidth = Regex("[​‌‍﻿]")
    private val spaces = Regex("\\s+")

    fun normalize(input: String): String {
        val nfc = Normalizer.normalize(input, Normalizer.Form.NFC)
        val sb = StringBuilder(nfc.length)
        for (ch in nfc) {
            when {
                ch == CHANDRABINDU -> Unit
                ch in '০'..'৯' -> sb.append('0' + (ch - '০'))
                else -> sb.append(ch)
            }
        }
        return sb.toString()
            .replace(zeroWidth, "")
            .lowercase(Locale.ROOT)
            .replace(spaces, " ")
            .trim()
    }

    /** Whitespace tokens with surrounding punctuation trimmed. */
    fun tokens(normalized: String): List<String> =
        normalized.split(' ')
            .map { it.trim('"', '\'', '“', '”', '‘', '’', '(', ')', '?', '!', ':') }
            .filter { it.isNotEmpty() }

    private val bnSuffixes = listOf("টাকে", "টিকে", "-টা", "-টি", "-এ", "-তে", "টা", "টি", "গুলো")

    /** Strips the Bengali classifier/locative endings: "ইউটিউবটা" → "ইউটিউব", "ইউটিউবে" → "ইউটিউব". */
    fun stripBengaliSuffix(token: String, includeLocative: Boolean = false): String {
        var t = token
        for (s in bnSuffixes) {
            if (t.length > s.length + 1 && t.endsWith(s)) {
                t = t.dropLast(s.length)
                break
            }
        }
        if (includeLocative && t.length > 2) {
            if (t.endsWith("তে")) return t.dropLast(2)
            if (t.endsWith("ে") || t.endsWith("এ")) return t.dropLast(1)
        }
        return t
    }

    /** Masks digit runs that could be OTPs, PINs or card numbers before anything is logged. */
    fun redactForLog(text: String): String = text.replace(Regex("\\d{4,}"), "••••")
}
