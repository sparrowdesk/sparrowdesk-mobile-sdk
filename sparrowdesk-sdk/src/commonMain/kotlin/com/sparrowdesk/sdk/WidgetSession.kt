package com.sparrowdesk.sdk

/**
 * Platform-independent readiness and command-queue state shared by the Android and iOS
 * implementations. Not thread-safe: callers use it from the main thread only.
 *
 * It also remembers the last values passed to the identity/state setters (contact fields,
 * conversation fields, tags, language) so they can be replayed after the WebView content
 * process dies and the page is reloaded.
 */
internal class WidgetSession {

    enum class Submit { EXECUTE, QUEUED, DROPPED }

    var isReady: Boolean = false
        private set

    var unavailableReason: WidgetUnavailableReason? = null
        private set

    val isUnavailable: Boolean get() = unavailableReason != null

    private val pending = mutableListOf<String>()
    val pendingCount: Int get() = pending.size

    private var contactFields: Map<String, String>? = null
    private var conversationFields: Map<String, String>? = null
    private var tags: List<String>? = null
    private var language: String? = null

    fun recordContactFields(fields: Map<String, String>) { contactFields = fields.toMap() }
    fun recordConversationFields(fields: Map<String, String>) { conversationFields = fields.toMap() }
    fun recordTags(tags: List<String>) { this.tags = tags.toList() }
    fun recordLanguage(language: String) { this.language = language }

    /**
     * Decides what to do with [js]: run it now (ready), queue it (still loading), or drop it
     * (the widget reported it is unavailable).
     */
    fun submit(js: String): Submit = when {
        isReady -> Submit.EXECUTE
        isUnavailable -> Submit.DROPPED
        else -> {
            pending.add(js)
            Submit.QUEUED
        }
    }

    /**
     * Marks the widget ready and returns the commands to run now, in order. If the widget had
     * been reported unavailable (only possible after a [WidgetUnavailableReason.TIMEOUT] on a
     * slow network), the queue was discarded, so the recorded state is replayed instead.
     */
    fun markReady(): List<String> {
        val toRun = if (isUnavailable) replayCommands() else pending.toList()
        pending.clear()
        isReady = true
        unavailableReason = null
        return toRun
    }

    /** Records that the widget will not become ready and discards queued commands. */
    fun markUnavailable(reason: WidgetUnavailableReason) {
        isReady = false
        unavailableReason = reason
        pending.clear()
    }

    /**
     * Resets readiness for a page reload after the content process died, queueing the
     * recorded state so it is applied once the reloaded widget is ready.
     */
    fun resetForReload() {
        isReady = false
        unavailableReason = null
        pending.clear()
        pending.addAll(replayCommands())
    }

    /** Clears everything (used by destroy). */
    fun clear() {
        isReady = false
        unavailableReason = null
        pending.clear()
        contactFields = null
        conversationFields = null
        tags = null
        language = null
    }

    fun replayCommands(): List<String> = buildList {
        language?.let { add(JsBridge.setLanguage(it)) }
        contactFields?.let { add(JsBridge.setContactFields(it)) }
        conversationFields?.let { add(JsBridge.setConversationFields(it)) }
        tags?.let { add(JsBridge.setTags(it)) }
    }
}
