package com.sparrowdesk.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BridgeMessageTest {

    @Test
    fun legacyPlainStrings() {
        assertEquals(BridgeMessage.WidgetReady, BridgeMessage.parse("widgetReady"))
        assertEquals(BridgeMessage.Open, BridgeMessage.parse("onOpen"))
        assertEquals(BridgeMessage.Close, BridgeMessage.parse("onClose"))
    }

    @Test
    fun unknownPlainString() {
        assertIs<BridgeMessage.Unknown>(BridgeMessage.parse("somethingElse"))
    }

    @Test
    fun widgetUnavailableReasons() {
        fun parse(reason: String) =
            BridgeMessage.parse("""{"type":"widgetUnavailable","payload":{"reason":"$reason"}}""")
        assertEquals(BridgeMessage.Unavailable(WidgetUnavailableReason.SUSPENDED), parse("suspended"))
        assertEquals(
            BridgeMessage.Unavailable(WidgetUnavailableReason.HIDDEN_BY_VISIBILITY_RULES),
            parse("hidden_by_visibility_rules")
        )
        assertEquals(BridgeMessage.Unavailable(WidgetUnavailableReason.TIMEOUT), parse("timeout"))
        assertEquals(BridgeMessage.Unavailable(WidgetUnavailableReason.BUNDLE_FAILED), parse("bundle_failed"))
        assertEquals(BridgeMessage.Unavailable(WidgetUnavailableReason.UNKNOWN), parse("new_reason"))
    }

    @Test
    fun widgetUnavailableWithoutPayloadIsUnknownReason() {
        assertEquals(
            BridgeMessage.Unavailable(WidgetUnavailableReason.UNKNOWN),
            BridgeMessage.parse("""{"type":"widgetUnavailable"}""")
        )
    }

    @Test
    fun unreadCount() {
        assertEquals(
            BridgeMessage.UnreadCountChanged(3),
            BridgeMessage.parse("""{"type":"onUnreadCountChanged","payload":{"count":3}}""")
        )
        assertEquals(
            BridgeMessage.UnreadCountChanged(0),
            BridgeMessage.parse("""{ "type" : "onUnreadCountChanged" , "payload" : { "count" : 0 } }""")
        )
    }

    @Test
    fun unreadCountWithoutNumberIsUnknown() {
        assertIs<BridgeMessage.Unknown>(
            BridgeMessage.parse("""{"type":"onUnreadCountChanged","payload":{"count":"3"}}""")
        )
    }

    @Test
    fun inboundMessage() {
        val raw = """{"type":"onMessage","payload":{"chatId":"c1","messageId":"m1",""" +
            """"senderType":"agent","text":"Hi \"there\"\né 😀","createdAt":"2026-10-22T10:00:00Z"}}"""
        val parsed = BridgeMessage.parse(raw)
        assertEquals(
            BridgeMessage.Message(
                SparrowDeskMessage(
                    chatId = "c1",
                    messageId = "m1",
                    senderType = MessageSenderType.AGENT,
                    text = "Hi \"there\"\né 😀",
                    createdAt = "2026-10-22T10:00:00Z"
                )
            ),
            parsed
        )
    }

    @Test
    fun inboundMessageToleratesNumericIdsAndNulls() {
        val raw = """{"type":"onMessage","payload":{"chatId":42,"messageId":7,""" +
            """"senderType":"bot","text":null,"createdAt":1761127200000,"extra":[1,{"a":true}]}}"""
        val msg = assertIs<BridgeMessage.Message>(BridgeMessage.parse(raw)).message
        assertEquals("42", msg.chatId)
        assertEquals("7", msg.messageId)
        assertEquals(MessageSenderType.BOT, msg.senderType)
        assertEquals("", msg.text)
        assertEquals("1761127200000", msg.createdAt)
    }

    @Test
    fun inboundMessageMissingCreatedAt() {
        val raw = """{"type":"onMessage","payload":{"chatId":"c","messageId":"m","senderType":"system","text":"x"}}"""
        val msg = assertIs<BridgeMessage.Message>(BridgeMessage.parse(raw)).message
        assertEquals(MessageSenderType.SYSTEM, msg.senderType)
        assertNull(msg.createdAt)
    }

    @Test
    fun inboundMessageWithoutIdsIsUnknown() {
        assertIs<BridgeMessage.Unknown>(
            BridgeMessage.parse("""{"type":"onMessage","payload":{"text":"hello"}}""")
        )
    }

    @Test
    fun unknownDescriptionNeverContainsPayload() {
        val unknown = assertIs<BridgeMessage.Unknown>(
            BridgeMessage.parse("""{"type":"onMessage","payload":{"text":"secret body"}}""")
        )
        assertTrue(!unknown.description.contains("secret"))
        val unknownType = assertIs<BridgeMessage.Unknown>(
            BridgeMessage.parse("""{"type":"futureEvent","payload":{"text":"secret body"}}""")
        )
        assertTrue(!unknownType.description.contains("secret"))
    }

    @Test
    fun malformedJsonIsUnknown() {
        assertIs<BridgeMessage.Unknown>(BridgeMessage.parse("""{"type":"onMessage","""))
        assertIs<BridgeMessage.Unknown>(BridgeMessage.parse("""{"payload":{}}"""))
        assertIs<BridgeMessage.Unknown>(BridgeMessage.parse("""{"type":"x"} trailing"""))
    }

    @Test
    fun miniJsonParsesValues() {
        val parsed = MiniJson.parse("""{"a":[1,-2.5,1e3],"b":{"c":false},"d":null,"e":"\/"}""")
        assertEquals(
            mapOf("a" to listOf(1.0, -2.5, 1000.0), "b" to mapOf("c" to false), "d" to null, "e" to "/"),
            parsed
        )
    }

    @Test
    fun miniJsonRejectsBadInput() {
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("""{"a":}""") }
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("""{"a":"unterminated}""") }
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("""{"a":"\x"}""") }
    }
}
