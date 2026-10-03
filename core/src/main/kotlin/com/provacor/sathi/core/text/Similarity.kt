package com.provacor.sathi.core.text

import kotlin.math.max
import kotlin.math.min

object Similarity {
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** 1.0 for identical strings, 0.0 for completely different ones. */
    fun ratio(a: String, b: String): Double {
        val len = max(a.length, b.length)
        if (len == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / len
    }
}
