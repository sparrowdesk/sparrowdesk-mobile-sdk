package com.sparrowdesk.sdk

import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetUnavailableReasonTest {

    @Test
    fun fromStringKnownValues() {
        assertEquals(WidgetUnavailableReason.SUSPENDED, WidgetUnavailableReason.fromString("suspended"))
        assertEquals(
            WidgetUnavailableReason.HIDDEN_BY_VISIBILITY_RULES,
            WidgetUnavailableReason.fromString("hidden_by_visibility_rules")
        )
        assertEquals(WidgetUnavailableReason.TIMEOUT, WidgetUnavailableReason.fromString("timeout"))
        assertEquals(WidgetUnavailableReason.BUNDLE_FAILED, WidgetUnavailableReason.fromString("bundle_failed"))
    }

    @Test
    fun fromStringCaseInsensitive() {
        assertEquals(WidgetUnavailableReason.SUSPENDED, WidgetUnavailableReason.fromString("SUSPENDED"))
        assertEquals(WidgetUnavailableReason.TIMEOUT, WidgetUnavailableReason.fromString("Timeout"))
    }

    @Test
    fun fromStringUnknownValue() {
        assertEquals(WidgetUnavailableReason.UNKNOWN, WidgetUnavailableReason.fromString("something"))
        assertEquals(WidgetUnavailableReason.UNKNOWN, WidgetUnavailableReason.fromString(""))
    }

    @Test
    fun senderTypeFromString() {
        assertEquals(MessageSenderType.AGENT, MessageSenderType.fromString("agent"))
        assertEquals(MessageSenderType.BOT, MessageSenderType.fromString("BOT"))
        assertEquals(MessageSenderType.SYSTEM, MessageSenderType.fromString("system"))
        assertEquals(MessageSenderType.UNKNOWN, MessageSenderType.fromString("user"))
    }
}
