package com.sparrowdesk.sdk

/**
 * A message posted from the WebView through `window.NativeBridge.postMessage`.
 *
 * Legacy events are plain strings (`"widgetReady"`, `"onOpen"`, `"onClose"`). Newer events
 * are JSON strings shaped `{"type":"<name>","payload":{...}}`.
 */
internal sealed class BridgeMessage {
    data object WidgetReady : BridgeMessage()
    data object Open : BridgeMessage()
    data object Close : BridgeMessage()
    data class Unavailable(val reason: WidgetUnavailableReason) : BridgeMessage()
    data class Message(val message: SparrowDeskMessage) : BridgeMessage()
    data class UnreadCountChanged(val count: Int) : BridgeMessage()

    /**
     * A message this SDK version does not understand. [description] is safe to log: it holds
     * the legacy string or the JSON `type`, never the payload (which may carry message text).
     */
    data class Unknown(val description: String) : BridgeMessage()

    companion object {
        fun parse(raw: String): BridgeMessage {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("{")) {
                return when (trimmed) {
                    "widgetReady" -> WidgetReady
                    "onOpen" -> Open
                    "onClose" -> Close
                    else -> Unknown("'$trimmed'")
                }
            }

            val root = try {
                MiniJson.parse(trimmed) as? Map<*, *>
            } catch (e: IllegalArgumentException) {
                null
            } ?: return Unknown("malformed JSON")

            val type = root["type"] as? String ?: return Unknown("JSON without type")
            val payload = root["payload"] as? Map<*, *> ?: emptyMap<String, Any?>()

            return when (type) {
                "widgetUnavailable" -> Unavailable(
                    WidgetUnavailableReason.fromString(payload.string("reason") ?: "")
                )
                "onUnreadCountChanged" -> {
                    val count = (payload["count"] as? Double)?.toInt()
                        ?: return Unknown("$type without numeric count")
                    UnreadCountChanged(count.coerceAtLeast(0))
                }
                "onMessage" -> {
                    val chatId = payload.string("chatId")
                    val messageId = payload.string("messageId")
                    if (chatId == null || messageId == null) {
                        return Unknown("$type without chatId/messageId")
                    }
                    Message(
                        SparrowDeskMessage(
                            chatId = chatId,
                            messageId = messageId,
                            senderType = MessageSenderType.fromString(
                                payload.string("senderType") ?: ""
                            ),
                            text = payload.string("text") ?: "",
                            createdAt = payload.string("createdAt")
                        )
                    )
                }
                // Also accept the legacy events wrapped in JSON.
                "widgetReady" -> WidgetReady
                "onOpen" -> Open
                "onClose" -> Close
                else -> Unknown("type '$type'")
            }
        }

        /** Reads a string field, accepting numbers too (IDs and timestamps may be numeric). */
        private fun Map<*, *>.string(key: String): String? = when (val v = this[key]) {
            is String -> v
            is Double -> if (v % 1.0 == 0.0 && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
            is Boolean -> v.toString()
            else -> null
        }
    }
}

/**
 * Minimal JSON reader for bridge messages (the SDK has no serialization dependency).
 * Produces Map<String, Any?>, List<Any?>, String, Double, Boolean or null.
 * Throws IllegalArgumentException on malformed input.
 */
internal object MiniJson {

    fun parse(text: String): Any? {
        val reader = Reader(text)
        reader.skipWhitespace()
        val value = reader.readValue()
        reader.skipWhitespace()
        require(reader.atEnd()) { "Trailing characters at ${reader.pos}" }
        return value
    }

    private class Reader(private val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWhitespace() {
            while (pos < s.length && s[pos].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) pos++
        }

        private fun peek(): Char {
            require(pos < s.length) { "Unexpected end of input" }
            return s[pos]
        }

        private fun expect(c: Char) {
            require(peek() == c) { "Expected '$c' at $pos" }
            pos++
        }

        fun readValue(): Any? = when (val c = peek()) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            't' -> readLiteral("true", true)
            'f' -> readLiteral("false", false)
            'n' -> readLiteral("null", null)
            else -> if (c == '-' || c in '0'..'9') readNumber() else throw IllegalArgumentException("Unexpected '$c' at $pos")
        }

        private fun readLiteral(word: String, value: Any?): Any? {
            require(s.startsWith(word, pos)) { "Expected '$word' at $pos" }
            pos += word.length
            return value
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') { pos++; return result }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                result[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return result }
                    else -> throw IllegalArgumentException("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (peek() == ']') { pos++; return result }
            while (true) {
                skipWhitespace()
                result.add(readValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return result }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = peek()
                pos++
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = peek().also { pos++ }) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                require(pos + 4 <= s.length) { "Bad unicode escape at $pos" }
                                val code = s.substring(pos, pos + 4).toIntOrNull(16)
                                    ?: throw IllegalArgumentException("Bad unicode escape at $pos")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Bad escape '\\$e' at $pos")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Double {
            val start = pos
            while (pos < s.length && s[pos].let { it == '-' || it == '+' || it == '.' || it == 'e' || it == 'E' || it in '0'..'9' }) pos++
            return s.substring(start, pos).toDoubleOrNull()
                ?: throw IllegalArgumentException("Bad number at $start")
        }
    }
}
