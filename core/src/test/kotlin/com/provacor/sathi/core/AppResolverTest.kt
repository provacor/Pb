package com.provacor.sathi.core

import com.provacor.sathi.core.apps.AppResolver
import com.provacor.sathi.core.model.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppResolverTest {
    private val youtube = AppInfo("YouTube", "com.google.android.youtube")
    private val ytMusic = AppInfo("YouTube Music", "com.google.android.apps.youtube.music")
    private val chrome = AppInfo("Chrome", "com.android.chrome")
    private val photos = AppInfo("Photos", "com.google.android.apps.photos")
    private val files = AppInfo("Files by Google", "com.google.android.apps.nbu.files")
    private val myFiles = AppInfo("My Files", "com.sec.android.app.myfiles")
    private val settings = AppInfo("Settings", "com.android.settings")
    private val apps = listOf(youtube, ytMusic, chrome, photos, files, settings)
    private val r = AppResolver()

    private fun find(q: String, list: List<AppInfo> = apps) = r.resolve(q, list)?.app

    @Test fun `bengali names`() {
        assertEquals(youtube, find("ইউটিউব"))
        assertEquals(chrome, find("ক্রোম"))
        assertEquals(settings, find("সেটিংস"))
        assertEquals(ytMusic, find("ইউটিউব মিউজিক"))
    }

    @Test fun `english names, case and typos`() {
        assertEquals(youtube, find("youtube"))
        assertEquals(youtube, find("YouTube app"))
        assertEquals(youtube, find("youtub"))
    }

    @Test fun `category names match what the phone actually has`() {
        assertEquals(photos, find("গ্যালারি"))
        assertEquals(files, find("file manager"))
        assertEquals(myFiles, find("ফাইল ম্যানেজার", listOf(myFiles, settings)))
    }

    @Test fun `unknown app`() = assertNull(find("whatsapp"))
}
