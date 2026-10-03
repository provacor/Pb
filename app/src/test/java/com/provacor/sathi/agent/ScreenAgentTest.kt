package com.provacor.sathi.agent

import com.provacor.sathi.core.model.AppInfo
import com.provacor.sathi.core.model.Bounds
import com.provacor.sathi.core.model.DeviceAction
import com.provacor.sathi.core.model.DeviceTarget
import com.provacor.sathi.core.model.Direction
import com.provacor.sathi.core.model.Language
import com.provacor.sathi.core.model.ScreenState
import com.provacor.sathi.core.model.UiElement
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The agent loop with screen control switched on, against a scripted fake screen. */
class ScreenAgentTest {
    private val youtube = AppInfo("YouTube", "com.google.android.youtube")

    private fun el(i: Int, desc: String, top: Int, bottom: Int, clickable: Boolean = true) =
        UiElement(i, null, desc, null, null, clickable, false, false, false, Bounds(0, top, 1080, bottom), if (clickable) i else null)

    private val results = ScreenState(
        youtube.packageName,
        listOf(
            el(0, "Navigate up", 60, 180),
            el(1, "Physics - Waves: Crash Course - 10 minutes - 3.1M views - play video", 300, 1000),
            el(2, "Wave motion explained - 8:14 - play video", 1020, 1700),
        ),
        1080, 2340,
    )
    private val videoPage = ScreenState(youtube.packageName, listOf(el(0, "Video player", 0, 700)), 1080, 2340)

    /** Each successful click moves to the next scripted screen. */
    private class FakeScreen(private val screens: List<ScreenState>) : ScreenController {
        var current = 0
        var changes = 0L
        val clicks = mutableListOf<String>()
        var backs = 0
        override val available = true
        override suspend fun observe() = screens[current]
        override fun changeCount() = changes
        override suspend fun awaitChange(since: Long, timeoutMillis: Long) = if (changes > since) screens[current] else null
        override suspend fun awaitForeground(packageName: String, timeoutMillis: Long) = screens[current].packageName == packageName
        override suspend fun click(element: UiElement): Boolean {
            clicks += element.label
            if (current < screens.lastIndex) { current++; changes++ }
            return true
        }
        override suspend fun setText(element: UiElement, text: String) = true
        override suspend fun submit(element: UiElement) = true
        override suspend fun scroll(direction: Direction) = false
        override fun back() = true.also { backs++; changes++ }
        override fun home() = true
    }

    private class Actions : AppActions {
        override fun launch(app: AppInfo) = true
        override fun searchInApp(app: AppInfo, query: String) = true
        override fun webSearch(query: String) = true
    }

    private class Device : DeviceControls {
        val log = mutableListOf<String>()
        override fun torch(on: Boolean) = true.also { log += "torch:$on" }
        override fun volume(action: DeviceAction) = true.also { log += "volume:$action" }
        override fun openPanel(target: DeviceTarget) = true.also { log += "panel:$target" }
    }

    private fun agent(screen: ScreenController, device: DeviceControls = Device()) = AgentController(
        apps = object : AppCatalog { override suspend fun apps() = listOf(youtube) },
        executor = ActionExecutor(Actions(), screen, device, ownPackage = "com.provacor.sathi"),
        speaker = object : Speaker {
            override fun speak(text: String, language: Language) = Unit
            override fun stop() = Unit
        },
    )

    @Test fun `master example runs to the end and verifies every step`() = runTest {
        val screen = FakeScreen(listOf(results, videoPage))
        val a = agent(screen)
        a.handle("ইউটিউব খোলো, সার্চে physics wave লিখো, প্রথম ভিডিওটা চালাও।", Language.BENGALI, speakReplies = false)
        val s = a.state.value
        assertEquals(listOf(StepStatus.DONE, StepStatus.DONE), s.steps.map { it.status })
        assertTrue(screen.clicks.single().startsWith("Physics - Waves"))
        assertEquals(Stage.COMPLETED, s.stage)
    }

    @Test fun `a tap that changes nothing is a failure, not a success`() = runTest {
        val screen = FakeScreen(listOf(results)) // clicking never changes the screen
        val a = agent(screen)
        a.handle("প্রথম ভিডিওটা চালাও", Language.BENGALI, speakReplies = false)
        assertEquals(StepStatus.FAILED, a.state.value.steps.single().status)
    }

    @Test fun `a missing button is reported after retries`() = runTest {
        val a = agent(FakeScreen(listOf(results)))
        a.handle("subscribe বাটনে ক্লিক করো", Language.BENGALI, speakReplies = false)
        val step = a.state.value.steps.single()
        assertEquals(StepStatus.FAILED, step.status)
        assertTrue(step.message!!.contains("subscribe"))
    }

    @Test fun `send waits for a yes, then taps`() = runTest {
        val chat = ScreenState("com.whatsapp", listOf(el(0, "Send", 2100, 2250)), 1080, 2340)
        val sent = ScreenState("com.whatsapp", listOf(el(0, "Message sent", 300, 400)), 1080, 2340)
        val screen = FakeScreen(listOf(chat, sent))
        val a = agent(screen)

        a.handle("send চাপো", Language.BENGALI, speakReplies = false)
        assertEquals(Stage.CONFIRM, a.state.value.stage)
        assertTrue(screen.clicks.isEmpty())

        a.handle("হ্যাঁ", Language.BENGALI, speakReplies = false)
        assertEquals(listOf("Send"), screen.clicks)
        assertEquals(Stage.COMPLETED, a.state.value.stage)
    }

    @Test fun `no cancels the held action`() = runTest {
        val screen = FakeScreen(listOf(ScreenState("x", listOf(el(0, "Delete", 500, 600)), 1080, 2340)))
        val a = agent(screen)
        a.handle("delete এ চাপ দাও", Language.BENGALI, speakReplies = false)
        a.handle("না", Language.BENGALI, speakReplies = false)
        assertTrue(screen.clicks.isEmpty())
        assertEquals(Stage.COMPLETED, a.state.value.stage)
    }

    @Test fun `back and read screen`() = runTest {
        val screen = FakeScreen(listOf(results))
        val a = agent(screen)
        a.handle("পেছনে যাও", Language.BENGALI, speakReplies = false)
        assertEquals(1, screen.backs)
        assertEquals(StepStatus.DONE, a.state.value.steps.single().status)
    }

    @Test fun `flashlight and volume act directly, wifi opens its panel`() = runTest {
        val device = Device()
        val a = agent(FakeScreen(listOf(results)), device)
        a.handle("টর্চ জ্বালাও", Language.BENGALI, speakReplies = false)
        a.handle("ভলিউম কমাও", Language.BENGALI, speakReplies = false)
        a.handle("ওয়াইফাই চালু করো", Language.BENGALI, speakReplies = false)
        assertEquals(listOf("torch:true", "volume:DOWN", "panel:WIFI"), device.log)
        assertEquals(StepStatus.SENT, a.state.value.steps.single().status)
    }
}
