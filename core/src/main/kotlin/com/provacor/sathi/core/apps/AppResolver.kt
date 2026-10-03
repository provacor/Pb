package com.provacor.sathi.core.apps

import com.provacor.sathi.core.model.AppInfo
import com.provacor.sathi.core.text.Similarity
import com.provacor.sathi.core.text.TextNormalizer

data class AppMatch(val app: AppInfo, val score: Double)

/** Picks the installed app a spoken name most likely refers to. */
class AppResolver(private val aliases: AppAliases = AppAliases()) {

    fun resolve(query: String, apps: List<AppInfo>): AppMatch? {
        val cleaned = clean(query)
        if (cleaned.isEmpty()) return null
        val names = aliases.expand(cleaned)
        return apps
            .map { app -> AppMatch(app, names.maxOf { score(it, app) }) }
            .filter { it.score >= THRESHOLD }
            .maxWithOrNull(compareBy<AppMatch> { it.score }.thenBy { -it.app.label.length })
    }

    private fun clean(query: String): String =
        TextNormalizer.tokens(TextNormalizer.normalize(query))
            .filterNot { it in NOISE }
            .joinToString(" ") { TextNormalizer.stripBengaliSuffix(it) }
            .trim()

    private fun score(name: String, app: AppInfo): Double {
        val label = TextNormalizer.normalize(app.label)
        if (label == name) return 1.0
        val labelWords = label.split(' ')
        if (labelWords.firstOrNull() == name || label.startsWith("$name ")) return 0.92
        if (name in labelWords) return 0.85
        val pkgParts = app.packageName.lowercase().split('.')
        if (name.replace(" ", "") in pkgParts.drop(1)) return 0.8
        if (name.length >= 4 && label.contains(name)) return 0.75
        val fuzzy = Similarity.ratio(name, label)
        return if (fuzzy >= 0.75) fuzzy * 0.8 else 0.0
    }

    companion object {
        const val THRESHOLD = 0.6
        private val NOISE = setOf("app", "অ্যাপ", "অ্যাপটা", "অ্যাপটি", "এপ", "the", "my", "আমার")
    }
}
