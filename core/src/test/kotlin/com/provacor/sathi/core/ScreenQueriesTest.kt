package com.provacor.sathi.core

import com.provacor.sathi.core.model.Bounds
import com.provacor.sathi.core.model.ItemKind
import com.provacor.sathi.core.model.ScreenState
import com.provacor.sathi.core.model.UiElement
import com.provacor.sathi.core.screen.ScreenQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenQueriesTest {
    private val elements = mutableListOf<UiElement>()

    private fun el(
        text: String? = null, desc: String? = null, top: Int, bottom: Int,
        clickable: Boolean = false, editable: Boolean = false, parent: Int? = null,
        left: Int = 0, right: Int = 1080, id: String? = null, focused: Boolean = false,
    ): Int {
        val i = elements.size
        elements += UiElement(
            i, text, desc, id, null, clickable, editable, false, focused,
            Bounds(left, top, right, bottom), if (clickable) i else parent,
        )
        return i
    }

    /** Roughly what YouTube's results page exposes to accessibility. */
    private fun youtubeResults(): ScreenState {
        el(desc = "Navigate up", top = 60, bottom = 180, clickable = true, right = 140)
        el(text = "physics wave", top = 60, bottom = 180, clickable = true, left = 160, right = 900, id = "com.google.android.youtube:id/search_query")
        el(desc = "Search with your voice", top = 60, bottom = 180, clickable = true, left = 920)
        el(text = "All", top = 200, bottom = 280, clickable = true, right = 200)
        val v1 = el(desc = "Physics - Waves: Crash Course - 10 minutes, 2 seconds - Go to channel - CrashCourse - 3.1M views - play video", top = 300, bottom = 1000, clickable = true)
        el(text = "Physics - Waves: Crash Course", top = 820, bottom = 900, parent = v1)
        el(desc = "Wave motion explained - 8:14 - 900K views - play video", top = 1020, bottom = 1700, clickable = true)
        el(text = "Home", top = 2150, bottom = 2300, clickable = true, right = 270)
        el(text = "Shorts", top = 2150, bottom = 2300, clickable = true, left = 270, right = 540)
        return ScreenState("com.google.android.youtube", elements.toList(), 1080, 2340)
    }

    @Test fun `first video skips toolbar, chips and bottom navigation`() {
        val s = youtubeResults()
        val first = ScreenQueries.findItem(s, 1, ItemKind.VIDEO)!!
        assertTrue(first.label.startsWith("Physics - Waves"))
        assertTrue(ScreenQueries.findItem(s, 2, ItemKind.VIDEO)!!.label.startsWith("Wave motion"))
        assertTrue(ScreenQueries.findItem(s, -1, ItemKind.VIDEO)!!.label.startsWith("Wave motion"))
        assertNull(ScreenQueries.findItem(s, 3, ItemKind.VIDEO))
    }

    @Test fun `label search in either language, through the clickable parent`() {
        el(text = "Subscribe", top = 500, bottom = 600, clickable = true)
        val row = el(desc = null, top = 700, bottom = 900, clickable = true)
        el(text = "Settings", top = 720, bottom = 800, parent = row)
        val s = ScreenState("x", elements.toList(), 1080, 2340)
        assertEquals("Subscribe", ScreenQueries.findByLabel(s, "সাবস্ক্রাইব")!!.text)
        val settings = ScreenQueries.findByLabel(s, "settings")!!
        assertEquals(row, settings.clickTarget)
        assertNull(ScreenQueries.findByLabel(s, "download"))
    }

    @Test fun `text field and search button`() {
        val s = youtubeResults()
        assertNull(ScreenQueries.findTextField(s))
        // On a results page the query bar itself opens search; the voice button never does.
        assertEquals("physics wave", ScreenQueries.findSearchButton(s)!!.text)

        elements.clear()
        el(desc = "Search", top = 60, bottom = 180, clickable = true, left = 900)
        el(text = "", top = 300, bottom = 400, editable = true, focused = true)
        val s2 = ScreenState("x", elements.toList(), 1080, 2340)
        assertEquals("Search", ScreenQueries.findSearchButton(s2)!!.description)
        assertTrue(ScreenQueries.findTextField(s2)!!.editable)
    }

    @Test fun `readable text keeps reading order and skips duplicates`() {
        el(text = "Second line", top = 300, bottom = 350)
        el(text = "First line", top = 100, bottom = 150)
        el(text = "First line", top = 400, bottom = 450)
        assertEquals("First line. Second line", ScreenQueries.readableText(ScreenState("x", elements.toList(), 1080, 2340)))
    }
}
