package com.provacor.sathi.apps

import android.content.Context
import android.content.Intent
import com.provacor.sathi.agent.AppCatalog
import com.provacor.sathi.core.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Apps that appear in the launcher. Visibility comes from the MAIN/LAUNCHER
 * `<queries>` entry in the manifest, so no QUERY_ALL_PACKAGES permission is needed.
 */
class InstalledAppsRepository(private val context: Context) : AppCatalog {
    private val mutex = Mutex()
    private var cache: List<AppInfo> = emptyList()
    private var loadedAt = 0L

    override suspend fun apps(): List<AppInfo> = apps(forceRefresh = false)

    suspend fun apps(forceRefresh: Boolean): List<AppInfo> = mutex.withLock {
        val now = System.currentTimeMillis()
        if (forceRefresh || cache.isEmpty() || now - loadedAt > MAX_AGE_MILLIS) {
            cache = withContext(Dispatchers.IO) { load() }
            loadedAt = now
        }
        cache
    }

    @Suppress("DEPRECATION") // The int-flags overload is fine on every supported API level.
    private fun load(): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .map { AppInfo(it.loadLabel(pm).toString(), it.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private companion object {
        const val MAX_AGE_MILLIS = 60_000L
    }
}
