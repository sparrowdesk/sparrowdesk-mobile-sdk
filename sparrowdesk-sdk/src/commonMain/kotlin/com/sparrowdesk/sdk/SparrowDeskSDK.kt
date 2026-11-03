package com.sparrowdesk.sdk

/**
 * SparrowDesk Chat Widget SDK for mobile platforms.
 *
 * Usage:
 * ```
 * val sdk = SparrowDeskSDK(SparrowDeskConfig(
 *     domain = "yourcompany.sparrowdesk.com",
 *     token = "your-widget-token"
 * ))
 *
 * // Android: sdk.attach(context, parentViewGroup)
 * // iOS:     sdk.attach(parentUIView)
 *
 * sdk.setContactFields(mapOf("email" to "user@example.com"))
 * sdk.openWidget()
 * ```
 */
expect class SparrowDeskSDK(config: SparrowDeskConfig) {

    /** Opens the chat widget. */
    fun openWidget()

    /** Closes the chat widget. */
    fun closeWidget()

    /** Hides both the launcher button and the widget panel. */
    fun hideWidget()

    /** Registers a callback that fires when the widget opens. */
    fun onOpen(callback: SparrowDeskCallback)

    /** Registers a callback that fires when the widget closes. */
    fun onClose(callback: SparrowDeskCallback)

    /** Attaches tags to the current chat session. */
    fun setTags(tags: List<String>)

    /** Sets conversation field values (use internal field names). */
    fun setConversationFields(fields: Map<String, String>)

    /** Sets contact fields for user identification (email, name, etc.). */
    fun setContactFields(fields: Map<String, String>)

    /**
     * Switches the widget language at runtime (e.g., "fr"). To pick the language at load,
     * set [SparrowDeskConfig.language] instead.
     */
    fun setLanguage(language: String)

    /**
     * Registers a callback that fires when the widget will not become ready: the account is
     * suspended, a visibility rule hides the widget, the bundle failed to load, or
     * [SparrowDeskConfig.readyTimeoutMs] elapsed. Queued commands are discarded when this
     * fires. Invoked on the main thread.
     *
     * After a [WidgetUnavailableReason.TIMEOUT] the widget may still become ready on a slow
     * network; the SDK then re-applies the last contact fields, conversation fields, tags and
     * language, but not open/close/hide calls.
     */
    fun onUnavailable(callback: (WidgetUnavailableReason) -> Unit)

    /**
     * Registers a callback for inbound chat messages (agent, bot or system), for example to
     * show a native badge or local notification while the widget is closed or hidden.
     * Invoked on the main thread.
     */
    fun onMessage(callback: (SparrowDeskMessage) -> Unit)

    /**
     * Registers a callback for the widget's unread message count. It fires once with the
     * current count after load and again whenever it changes. Invoked on the main thread.
     */
    fun onUnreadCountChanged(callback: (Int) -> Unit)

    /**
     * Asks the widget to reconnect its socket and fetch messages missed while the app was in
     * the background. No-op until the widget is ready.
     *
     * The SDK calls this automatically when the app returns to the foreground (Android: the
     * `ON_RESUME` event of the `LifecycleOwner` passed to `attach`; iOS:
     * `UIApplicationDidBecomeActiveNotification`). Hosts can also call it manually, for
     * example when the Android `Context` passed to `attach` is not a `LifecycleOwner`.
     */
    fun resume()

    /** Queries the current widget status asynchronously. Reports UNKNOWN while unavailable. */
    fun getStatus(callback: (WidgetStatus) -> Unit)

    /** Shows the WebView container again after [hide]. */
    fun show()

    /**
     * Hides the WebView container. The view is collapsed rather than marked hidden, so the
     * page keeps running and the chat connection stays alive.
     */
    fun hide()

    /** Destroys the WebView and cleans up all resources. */
    fun destroy()
}
