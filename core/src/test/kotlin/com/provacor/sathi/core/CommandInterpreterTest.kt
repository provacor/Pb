package com.provacor.sathi.core

import com.provacor.sathi.core.model.AgentIntent.FindFile
import com.provacor.sathi.core.model.AgentIntent.GoBack
import com.provacor.sathi.core.model.AgentIntent.GoHome
import com.provacor.sathi.core.model.AgentIntent.Help
import com.provacor.sathi.core.model.AgentIntent.OpenApp
import com.provacor.sathi.core.model.AgentIntent.ReadScreen
import com.provacor.sathi.core.model.AgentIntent.Scroll
import com.provacor.sathi.core.model.AgentIntent.Search
import com.provacor.sathi.core.model.AgentIntent.SelectItem
import com.provacor.sathi.core.model.AgentIntent.StopSpeaking
import com.provacor.sathi.core.model.AgentIntent.Tap
import com.provacor.sathi.core.model.AgentIntent.TypeText
import com.provacor.sathi.core.model.AgentIntent.Unknown
import com.provacor.sathi.core.model.Direction
import com.provacor.sathi.core.model.ItemKind
import com.provacor.sathi.core.parse.CommandInterpreter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandInterpreterTest {
    private val ci = CommandInterpreter()
    private fun parse(s: String) = ci.interpret(s)

    @Test fun `bengali open`() = assertEquals(listOf(OpenApp("ইউটিউব")), parse("ইউটিউব খোলো"))

    @Test fun `bengali open with classifier and chandrabindu variants`() {
        assertEquals(listOf(OpenApp("ইউটিউব")), parse("ইউটিউবটা খুলুন"))
        assertEquals(listOf(OpenApp("ক্রোম")), parse("একটু ক্রোম ওপেন করো প্লিজ"))
    }

    @Test fun `natural bengali verb forms`() {
        assertEquals(listOf(OpenApp("ইউটিউব")), parse("ইউটিউব টা খুলতে বলছি"))
        assertEquals(listOf(OpenApp("ইউটিউব")), parse("আমি ইউটিউব খুলতে চাই"))
        assertEquals(listOf(OpenApp("ফেসবুক")), parse("ফেসবুক খুলবা"))
        assertEquals(listOf(OpenApp("ইউটিউব")), parse("ইউটিউবটা একটু ওপেন করে দাও তো"))
        assertEquals(listOf(OpenApp("ক্রোম")), parse("ক্রোম খুলে দাও"))
        assertEquals(listOf(Search("physics")), parse("physics খুঁজতে বলছি"))
        assertEquals(listOf(GoBack), parse("পিছনে যাও তো"))
    }

    @Test fun `english open`() {
        assertEquals(listOf(OpenApp("youtube")), parse("Open YouTube"))
        assertEquals(listOf(OpenApp("chrome")), parse("open chrome"))
        assertEquals(listOf(OpenApp("gallery")), parse("Open Gallery"))
        assertEquals(listOf(OpenApp("file manager")), parse("Open File Manager"))
    }

    @Test fun `master example - open, search, play first video`() {
        assertEquals(
            listOf(OpenApp("ইউটিউব"), Search("physics wave"), SelectItem(1, ItemKind.VIDEO)),
            parse("ইউটিউব খোলো, সার্চে physics wave লিখো, প্রথম ভিডিওটা চালাও।"),
        )
    }

    @Test fun `participle chain - khule`() {
        assertEquals(
            listOf(OpenApp("ইউটিউব"), Search("physics lecture")),
            parse("ইউটিউব খুলে আমার জন্য physics lecture খুঁজে দাও"),
        )
    }

    @Test fun `mixed language participle - open kore`() {
        assertEquals(listOf(OpenApp("youtube"), Search("physics")), parse("Youtube open করে physics search করো"))
    }

    @Test fun `english chain with and and then`() {
        assertEquals(
            listOf(OpenApp("youtube"), Search("physics wave"), SelectItem(1, ItemKind.VIDEO)),
            parse("Open YouTube and search physics wave then play the first video"),
        )
    }

    @Test fun `and inside a search query does not split`() {
        assertEquals(listOf(Search("rock and roll")), parse("search rock and roll"))
    }

    @Test fun `search names its app`() {
        assertEquals(listOf(Search("physics", "ইউটিউব")), parse("ইউটিউবে physics খোঁজো"))
        assertEquals(listOf(Search("physics", "youtube")), parse("search physics on youtube"))
        assertEquals(listOf(Search("cat videos", "youtube")), parse("youtube-এ cat videos সার্চ করো"))
    }

    @Test fun `web search words are not part of the query`() {
        assertEquals(listOf(Search("today's weather")), parse("Search the web for today's weather"))
        assertEquals(listOf(Search("ঢাকার আবহাওয়া")), parse("ইন্টারনেটে ঢাকার আবহাওয়া সার্চ করো"))
    }

    @Test fun `generic noun is not taken for an app inside a search`() {
        assertEquals(listOf(Search("বিড়ালের ছবি")), parse("বিড়ালের ছবি সার্চ করো"))
    }

    @Test fun `follow-up search uses context later`() = assertEquals(listOf(Search("physics")), parse("এখন physics search করো"))

    @Test fun `navigation`() {
        assertEquals(listOf(GoBack), parse("পেছনে যাও"))
        assertEquals(listOf(GoBack), parse("Go back"))
        assertEquals(listOf(GoHome), parse("হোমে যাও"))
        assertEquals(listOf(Scroll(Direction.UP)), parse("উপরে স্ক্রল করো"))
        assertEquals(listOf(Scroll(Direction.DOWN)), parse("আরও নিচে যাও"))
        assertEquals(listOf(Scroll(Direction.DOWN)), parse("scroll down"))
    }

    @Test fun `read, type, tap`() {
        assertEquals(listOf(ReadScreen), parse("এই লেখাটা পড়ে শোনাও"))
        assertEquals(listOf(ReadScreen), parse("read this text"))
        assertEquals(listOf(TypeText("hello world")), parse("type hello world"))
        assertEquals(listOf(Tap("settings")), parse("tap on settings"))
        assertEquals(listOf(Tap("সাবস্ক্রাইব")), parse("সাবস্ক্রাইব বাটনে ক্লিক করো"))
    }

    @Test fun `this thing open is a tap, not an app launch`() = assertEquals(listOf(Tap("ছবি")), parse("এই ছবিটা ওপেন করো"))

    @Test fun `file search`() {
        assertEquals(listOf(FindFile("latest pdf")), parse("Find my latest PDF."))
        assertEquals(listOf(FindFile("আজকের ছবি")), parse("ফাইল ম্যানেজার থেকে আজকের ছবিটা খুঁজে বের করো"))
        assertEquals(listOf(OpenApp("file manager"), FindFile("latest photo")), parse("File manager open করে latest photoটা বের করো"))
    }

    @Test fun `stop and help`() {
        assertEquals(listOf(StopSpeaking), parse("থামো"))
        assertEquals(listOf(StopSpeaking), parse("stop"))
        assertEquals(listOf(Help), parse("তুমি কী করতে পারো?"))
    }

    @Test fun `questions for the AI stay unknown`() {
        assertTrue(parse("এই ভিডিওটা কী নিয়ে সেটা বলো").single() is Unknown)
        assertTrue(parse("এই PDFটা summarize করো").single() is Unknown)
    }

    @Test fun `bengali ending on an english word is dropped`() =
        assertEquals(listOf(Search("physics lecture")), parse("physics lectureটা সার্চ করো"))

    @Test fun `bengali digits normalize`() = assertEquals(listOf(Search("class 10 physics")), parse("class ১০ physics সার্চ করো"))

    @Test fun `empty input`() = assertEquals(emptyList<Any>(), parse("   "))
}
