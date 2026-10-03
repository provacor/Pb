package com.provacor.sathi.agent

import com.provacor.sathi.core.model.AppInfo
import com.provacor.sathi.core.model.Language
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentControllerTest {
    private val youtube = AppInfo("YouTube", "com.google.android.youtube")
    private val chrome = AppInfo("Chrome", "com.android.chrome")
    private val notes = AppInfo("Notes", "com.example.notes")

    private class FakeActions(private val searchable: Set<String>) : AppActions {
        val calls = mutableListOf<String>()
        override fun launch(app: AppInfo) = true.also { calls += "launch:${app.label}" }
        override fun searchInApp(app: AppInfo, query: String) =
            (app.packageName in searchable).also { calls += "search:${app.label}:$query" }
        override fun webSearch(query: String) = true.also { calls += "web:$query" }
    }

    private class FakeSpeaker : Speaker {
        val spoken = mutableListOf<String>()
        var stops = 0
        override fun speak(text: String, language: Language) { spoken += text }
        override fun stop() { stops++ }
    }

    private val actions = FakeActions(searchable = setOf(youtube.packageName, chrome.packageName))
    private val speaker = FakeSpeaker()
    private val agent = AgentController(
        apps = object : AppCatalog { override suspend fun apps() = listOf(youtube, chrome, notes) },
        executor = ActionExecutor(actions),
        speaker = speaker,
    )

    private suspend fun run(cmd: String, lang: Language = Language.BENGALI) = agent.handle(cmd, lang, speakReplies = true)
    private val state get() = agent.state.value

    @Test fun `open YouTube sends the launch intent`() = runTest {
        run("Open YouTube.", Language.ENGLISH)
        assertEquals(listOf("launch:YouTube"), actions.calls)
        assertEquals(Stage.COMPLETED, state.stage)
        // Sent, not "done": nothing has looked at the screen yet.
        assertEquals(StepStatus.SENT, state.steps.single().status)
        assertEquals("Opening YouTube.", speaker.spoken.single())
    }

    @Test fun `open then search becomes one in-app search`() = runTest {
        run("ইউটিউব খুলে physics wave সার্চ করো")
        assertEquals(listOf("search:YouTube:physics wave"), actions.calls)
        assertEquals(Stage.COMPLETED, state.stage)
    }

    @Test fun `master example stops honestly at the step that needs screen control`() = runTest {
        run("ইউটিউব খোলো, সার্চে physics wave লিখো, প্রথম ভিডিওটা চালাও।")
        assertEquals(listOf("search:YouTube:physics wave"), actions.calls)
        assertEquals(listOf(StepStatus.SENT, StepStatus.NEEDS_SETUP), state.steps.map { it.status })
        assertEquals(Stage.FAILED, state.stage)
    }

    @Test fun `a failed step skips the rest instead of acting blindly`() = runTest {
        run("whatsapp খোলো, তারপর ক্রোম খোলো")
        assertTrue(actions.calls.isEmpty())
        assertEquals(listOf(StepStatus.FAILED, StepStatus.SKIPPED), state.steps.map { it.status })
    }

    @Test fun `follow-up uses the app from the previous command`() = runTest {
        run("ইউটিউব খোলো")
        run("এখন physics search করো")
        assertEquals(listOf("launch:YouTube", "search:YouTube:physics"), actions.calls)
    }

    @Test fun `app without a search intent is opened and the gap is explained`() = runTest {
        run("Notes খোলো")
        run("এখন shopping list search করো")
        assertEquals(listOf("launch:Notes", "search:Notes:shopping list", "launch:Notes"), actions.calls)
        assertEquals(StepStatus.NEEDS_SETUP, state.steps.single().status)
    }

    @Test fun `go back needs screen control`() = runTest {
        run("Go back.", Language.ENGLISH)
        assertTrue(actions.calls.isEmpty())
        assertEquals(StepStatus.NEEDS_SETUP, state.steps.single().status)
        assertTrue(state.response!!.contains("Accessibility"))
    }

    @Test fun `find my latest pdf is a file search`() = runTest {
        run("Find my latest PDF.", Language.ENGLISH)
        assertEquals("Find file: latest pdf", state.steps.single().description)
        assertEquals(StepStatus.NEEDS_SETUP, state.steps.single().status)
    }

    @Test fun `web search when no app is named`() = runTest {
        run("search the web for dhaka weather", Language.ENGLISH)
        assertEquals(listOf("web:dhaka weather"), actions.calls)
    }

    @Test fun `stop silences speech and says nothing`() = runTest {
        run("থামো")
        assertTrue(speaker.stops > 0)
        assertTrue(speaker.spoken.isEmpty())
    }

    @Test fun `unknown command is reported, not guessed`() = runTest {
        run("এই ভিডিওটা কী নিয়ে সেটা বলো")
        assertTrue(actions.calls.isEmpty())
        assertEquals(StepStatus.FAILED, state.steps.single().status)
    }

    @Test fun `developer log has every stage and no long digit runs`() = runTest {
        run("search otp 482913 on youtube", Language.ENGLISH)
        val types = state.log.map { it.type.name }
        assertEquals(listOf("COMMAND", "UNDERSTANDING", "PLAN", "ACTION", "RESULT"), types)
        assertTrue(state.log.none { it.message.contains("482913") })
    }
}
