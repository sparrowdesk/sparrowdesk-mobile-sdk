package com.sparrowdesk.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WidgetSessionTest {

    @Test
    fun queuesUntilReadyThenExecutes() {
        val session = WidgetSession()
        assertEquals(WidgetSession.Submit.QUEUED, session.submit("a"))
        assertEquals(WidgetSession.Submit.QUEUED, session.submit("b"))
        assertEquals(listOf("a", "b"), session.markReady())
        assertTrue(session.isReady)
        assertEquals(0, session.pendingCount)
        assertEquals(WidgetSession.Submit.EXECUTE, session.submit("c"))
    }

    @Test
    fun unavailableClearsQueueAndDropsNewCommands() {
        val session = WidgetSession()
        session.submit(JsBridge.openWidget())
        session.markUnavailable(WidgetUnavailableReason.SUSPENDED)
        assertFalse(session.isReady)
        assertTrue(session.isUnavailable)
        assertEquals(0, session.pendingCount)
        assertEquals(WidgetSession.Submit.DROPPED, session.submit(JsBridge.openWidget()))
    }

    @Test
    fun lateReadyAfterTimeoutReplaysStateButNotStaleCommands() {
        val session = WidgetSession()
        session.recordContactFields(mapOf("email" to "a@b.c"))
        session.submit(JsBridge.setContactFields(mapOf("email" to "a@b.c")))
        session.submit(JsBridge.openWidget())
        session.markUnavailable(WidgetUnavailableReason.TIMEOUT)

        val commands = session.markReady()
        assertEquals(listOf(JsBridge.setContactFields(mapOf("email" to "a@b.c"))), commands)
        assertFalse(session.isUnavailable)
    }

    @Test
    fun resetForReloadQueuesLastKnownState() {
        val session = WidgetSession()
        session.markReady()
        session.recordContactFields(mapOf("email" to "old@x.y"))
        session.recordContactFields(mapOf("email" to "new@x.y"))
        session.recordConversationFields(mapOf("priority" to "high"))
        session.recordTags(listOf("vip"))
        session.recordLanguage("fr")

        session.resetForReload()
        assertFalse(session.isReady)
        assertEquals(
            listOf(
                JsBridge.setLanguage("fr"),
                JsBridge.setContactFields(mapOf("email" to "new@x.y")),
                JsBridge.setConversationFields(mapOf("priority" to "high")),
                JsBridge.setTags(listOf("vip"))
            ),
            session.markReady()
        )
    }

    @Test
    fun replayIsEmptyWithoutRecordedState() {
        val session = WidgetSession()
        session.resetForReload()
        assertEquals(0, session.pendingCount)
    }

    @Test
    fun clearForgetsEverything() {
        val session = WidgetSession()
        session.recordTags(listOf("a"))
        session.markUnavailable(WidgetUnavailableReason.TIMEOUT)
        session.clear()
        assertFalse(session.isUnavailable)
        assertEquals(emptyList(), session.replayCommands())
    }
}
