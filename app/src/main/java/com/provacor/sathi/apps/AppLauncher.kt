package com.provacor.sathi.apps

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.provacor.sathi.agent.AppActions
import com.provacor.sathi.core.model.AppInfo

/**
 * Launches apps and searches through official intents only:
 * the launcher intent, ACTION_SEARCH / ACTION_WEB_SEARCH, and a plain web URL.
 */
class AppLauncher(private val context: Context) : AppActions {

    override fun launch(app: AppInfo): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return start(intent)
    }

    /** Sends the query straight to [app] if it accepts a search intent (YouTube, Chrome, Play Store, …). */
    override fun searchInApp(app: AppInfo, query: String): Boolean {
        val candidates = listOf(Intent.ACTION_SEARCH, Intent.ACTION_WEB_SEARCH).map { action ->
            Intent(action)
                .setPackage(app.packageName)
                .putExtra(SearchManager.QUERY, query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val intent = candidates.firstOrNull { it.resolveActivity(context.packageManager) != null } ?: return false
        return start(intent)
    }

    override fun webSearch(query: String): Boolean {
        val search = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (search.resolveActivity(context.packageManager) != null && start(search)) return true
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(view)
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No activity for $intent", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Not allowed to start $intent", e)
        false
    }

    private companion object {
        const val TAG = "AppLauncher"
    }
}
