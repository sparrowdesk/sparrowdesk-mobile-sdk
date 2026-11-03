package com.sparrowdesk.sdk

/**
 * Pure functions that produce JavaScript strings for evaluating in the WebView.
 * All user-provided values are escaped to prevent injection.
 */
internal object JsBridge {

    private const val API = "window.sparrowDesk"

    fun openWidget(): String = "$API.openWidget();"

    fun closeWidget(): String = "$API.closeWidget();"

    fun hideWidget(): String = "$API.hideWidget();"

    fun getStatus(): String = "$API.status"

    fun setTags(tags: List<String>): String {
        val escaped = tags.joinToString(",") { "\"${escapeJs(it)}\"" }
        return "$API.setTags([$escaped]);"
    }

    fun setConversationFields(fields: Map<String, String>): String {
        return "$API.setConversationFields(${mapToJsObject(fields)});"
    }

    fun setContactFields(fields: Map<String, String>): String {
        return "$API.setContactFields(${mapToJsObject(fields)});"
    }

    fun setLanguage(language: String): String = "$API.setLanguage(\"${escapeJs(language)}\");"

    /**
     * Forces the widget to reconnect its socket and refetch missed messages. Guarded so it is
     * a no-op on widget builds that do not expose `reconnect` yet.
     */
    fun reconnect(): String =
        "if ($API && typeof $API.reconnect === \"function\") { $API.reconnect(); }"

    private fun mapToJsObject(map: Map<String, String>): String {
        val entries = map.entries.joinToString(",") { (key, value) ->
            "\"${escapeJs(key)}\":\"${escapeJs(value)}\""
        }
        return "{$entries}"
    }

    /**
     * Escapes special characters in a string for safe JavaScript string interpolation.
     */
    internal fun escapeJs(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("<", "\\x3c")  // Prevent </script> injection
            .replace(">", "\\x3e")
    }
}
