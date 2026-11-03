package com.sparrowdesk.sdk

/**
 * Why the chat widget will not become ready in this session.
 *
 * Delivered through [SparrowDeskSDK.onUnavailable]. Once the widget is unavailable,
 * queued commands are discarded and new commands are dropped.
 */
enum class WidgetUnavailableReason {
    /** The SparrowDesk account is suspended, so the widget is suppressed. */
    SUSPENDED,

    /** A widget visibility rule (for example a contact-field "hide" rule) hides the widget. */
    HIDDEN_BY_VISIBILITY_RULES,

    /** The widget did not become ready within [SparrowDeskConfig.readyTimeoutMs]. */
    TIMEOUT,

    /** The widget bundle failed to load (network error or blocked CDN). */
    BUNDLE_FAILED,

    /** The widget reported a reason this SDK version does not recognise. */
    UNKNOWN;

    companion object {
        fun fromString(value: String): WidgetUnavailableReason = when (value.lowercase()) {
            "suspended" -> SUSPENDED
            "hidden_by_visibility_rules" -> HIDDEN_BY_VISIBILITY_RULES
            "timeout" -> TIMEOUT
            "bundle_failed" -> BUNDLE_FAILED
            else -> UNKNOWN
        }
    }
}
