package com.provacor.sathi.core

import com.provacor.sathi.core.model.AgentIntent
import com.provacor.sathi.core.model.AppInfo
import com.provacor.sathi.core.model.TaskContext
import com.provacor.sathi.core.parse.CommandInterpreter
import com.provacor.sathi.core.plan.TaskPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskPlannerTest {
    private val youtube = AppInfo("YouTube", "com.google.android.youtube")
    private val chrome = AppInfo("Chrome", "com.android.chrome")
    private val apps = listOf(youtube, chrome)
    private val planner = TaskPlanner()
    private val ci = CommandInterpreter()
    private val now = 1_000_000L

    private fun plan(cmd: String, ctx: TaskContext = TaskContext()) = planner.plan(ci.interpret(cmd), ctx, apps, now)

    @Test fun `open then search merges into one in-app search`() {
        val p = plan("ইউটিউব খুলে physics wave সার্চ করো")
        assertEquals(1, p.steps.size)
        assertEquals(AgentIntent.Search("physics wave", "YouTube"), p.steps[0].intent)
        assertEquals(youtube, p.steps[0].app)
    }

    @Test fun `follow-up search uses the app from context`() {
        val ctx = TaskContext(currentApp = youtube, updatedAtMillis = now - 1000)
        assertEquals(youtube, plan("এখন physics search করো", ctx).steps.single().app)
    }

    @Test fun `stale context is ignored`() {
        val ctx = TaskContext(currentApp = youtube, updatedAtMillis = now - TaskContext.MAX_AGE_MILLIS - 1)
        assertNull(plan("এখন physics search করো", ctx).steps.single().app)
    }

    @Test fun `unknown app keeps a step with no target`() {
        val step = plan("whatsapp খোলো").steps.single()
        assertNull(step.app)
    }

    @Test fun `steps are capped`() {
        val cmd = (1..30).joinToString(", ") { "পেছনে যাও" }
        assertEquals(TaskPlanner.MAX_STEPS, plan(cmd).steps.size)
    }
}
