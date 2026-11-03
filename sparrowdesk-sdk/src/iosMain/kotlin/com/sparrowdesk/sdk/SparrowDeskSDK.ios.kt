package com.sparrowdesk.sdk

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol

@OptIn(ExperimentalForeignApi::class)
actual class SparrowDeskSDK actual constructor(private val config: SparrowDeskConfig) {

    private var webView: WKWebView? = null
    private var userContentController: WKUserContentController? = null
    private var messageHandler: MessageHandler? = null
    private var navigationDelegate: NavigationDelegate? = null
    private val session = WidgetSession()
    private var isHidden = false
    private var didBecomeActiveObserver: NSObjectProtocol? = null

    private var onOpenCallback: SparrowDeskCallback? = null
    private var onCloseCallback: SparrowDeskCallback? = null
    private var onUnavailableCallback: ((WidgetUnavailableReason) -> Unit)? = null
    private var onMessageCallback: ((SparrowDeskMessage) -> Unit)? = null
    private var onUnreadCountCallback: ((Int) -> Unit)? = null

    /**
     * Creates the WKWebView and attaches it to the given parent UIView.
     * Must be called from the main thread before using any widget methods.
     *
     * The SDK observes `UIApplicationDidBecomeActiveNotification` and calls [resume] when the
     * app returns to the foreground; the observer is removed in [destroy].
     *
     * @param parentView The UIView to which the WKWebView will be added
     */
    fun attach(parentView: UIView) {
        if (webView != null) {
            logd("attach: already attached, ignoring")
            return
        }
        logd("attach: domain=${config.domain}")

        val contentController = WKUserContentController()
        userContentController = contentController

        // Inject the NativeBridge adapter script at document start
        val bridgeScript = WKUserScript(
            source = """
                window.NativeBridge = {
                    postMessage: function(msg) {
                        window.webkit.messageHandlers.NativeBridge.postMessage(msg);
                    }
                };
            """.trimIndent(),
            injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
            forMainFrameOnly = true
        )
        contentController.addUserScript(bridgeScript)

        // Register the native message handler
        val handler = MessageHandler()
        messageHandler = handler
        contentController.addScriptMessageHandler(handler, "NativeBridge")

        val configuration = WKWebViewConfiguration().apply {
            this.userContentController = contentController
        }

        val navDelegate = NavigationDelegate()
        navigationDelegate = navDelegate

        val wv = WKWebView(
            frame = parentView.bounds,
            configuration = configuration
        ).apply {
            setAutoresizingMask(
                UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            )
            setOpaque(false)
            setNavigationDelegate(navDelegate)
        }

        webView = wv
        parentView.addSubview(wv)
        if (isHidden) applyHidden(wv, hidden = true)

        didBecomeActiveObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ -> resume() }

        loadHtml(wv)
        logd("attach: HTML loaded")
    }

    actual fun openWidget() {
        logd("openWidget()")
        runOrQueue(JsBridge.openWidget())
    }

    actual fun closeWidget() {
        logd("closeWidget()")
        runOrQueue(JsBridge.closeWidget())
    }

    actual fun hideWidget() {
        logd("hideWidget()")
        runOrQueue(JsBridge.hideWidget())
    }

    actual fun onOpen(callback: SparrowDeskCallback) {
        logd("onOpen: callback registered")
        onOpenCallback = callback
    }

    actual fun onClose(callback: SparrowDeskCallback) {
        logd("onClose: callback registered")
        onCloseCallback = callback
    }

    actual fun onUnavailable(callback: (WidgetUnavailableReason) -> Unit) {
        logd("onUnavailable: callback registered")
        onUnavailableCallback = callback
    }

    actual fun onMessage(callback: (SparrowDeskMessage) -> Unit) {
        logd("onMessage: callback registered")
        onMessageCallback = callback
    }

    actual fun onUnreadCountChanged(callback: (Int) -> Unit) {
        logd("onUnreadCountChanged: callback registered")
        onUnreadCountCallback = callback
    }

    actual fun setTags(tags: List<String>) {
        logd("setTags: $tags")
        session.recordTags(tags)
        runOrQueue(JsBridge.setTags(tags))
    }

    actual fun setConversationFields(fields: Map<String, String>) {
        logd("setConversationFields: keys=${fields.keys}")
        session.recordConversationFields(fields)
        runOrQueue(JsBridge.setConversationFields(fields))
    }

    actual fun setContactFields(fields: Map<String, String>) {
        logd("setContactFields: keys=${fields.keys}")
        session.recordContactFields(fields)
        runOrQueue(JsBridge.setContactFields(fields))
    }

    actual fun setLanguage(language: String) {
        logd("setLanguage: $language")
        session.recordLanguage(language)
        runOrQueue(JsBridge.setLanguage(language))
    }

    actual fun resume() {
        if (!session.isReady) {
            logd("resume: widget not ready, ignoring")
            return
        }
        logd("resume: reconnecting")
        executeJs(JsBridge.reconnect())
    }

    actual fun getStatus(callback: (WidgetStatus) -> Unit) {
        logd("getStatus()")
        if (session.isUnavailable) {
            logd("getStatus: widget unavailable, returning UNKNOWN")
            callback(WidgetStatus.UNKNOWN)
            return
        }
        val wv = webView ?: run {
            logw("getStatus: WKWebView not attached, returning UNKNOWN")
            callback(WidgetStatus.UNKNOWN)
            return
        }
        wv.evaluateJavaScript(JsBridge.getStatus()) { result, _ ->
            val raw = (result as? String) ?: ""
            logd("getStatus result=$raw")
            callback(WidgetStatus.fromString(raw))
        }
    }

    actual fun show() {
        logd("show()")
        isHidden = false
        webView?.let { applyHidden(it, hidden = false) }
    }

    actual fun hide() {
        logd("hide()")
        isHidden = true
        webView?.let { applyHidden(it, hidden = true) }
    }

    actual fun destroy() {
        logd("destroy()")
        didBecomeActiveObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        didBecomeActiveObserver = null

        // Remove script message handler to break retain cycle
        userContentController?.removeScriptMessageHandlerForName("NativeBridge")

        webView?.setNavigationDelegate(null)
        webView?.stopLoading()
        webView?.removeFromSuperview()
        webView = null
        userContentController = null
        messageHandler = null
        navigationDelegate = null
        isHidden = false
        session.clear()
        onOpenCallback = null
        onCloseCallback = null
        onUnavailableCallback = null
        onMessageCallback = null
        onUnreadCountCallback = null
    }

    // --- Private helpers ---

    private fun loadHtml(wv: WKWebView) {
        val html = HtmlTemplate.build(config)
        wv.loadHTMLString(html, baseURL = NSURL(string = "https://${config.domain}"))
    }

    // hide() uses alpha = 0 and disables interaction instead of setHidden(true). A hidden
    // WKWebView (or one removed from the visible hierarchy) can have its web content process
    // suspended by WebKit, which drops the widget's socket and stops live messages. An
    // invisible-but-present view keeps the page running.
    private fun applyHidden(wv: WKWebView, hidden: Boolean) {
        wv.setHidden(false)
        wv.setAlpha(if (hidden) 0.0 else 1.0)
        wv.setUserInteractionEnabled(!hidden)
    }

    /**
     * WebKit killed the web content process (memory pressure or a crash), leaving a blank page
     * and a dead socket. Reload the page and replay the last-known contact fields,
     * conversation fields, tags and language once the reloaded widget is ready.
     */
    private fun handleContentProcessTerminated(wv: WKWebView) {
        SDKLogger.e(TAG, "WKWebView web content process terminated; reloading widget")
        if (wv != webView) return
        session.resetForReload()
        loadHtml(wv)
    }

    private fun runOrQueue(js: String) {
        when (session.submit(js)) {
            WidgetSession.Submit.EXECUTE -> {
                logd("runOrQueue: executing (widget ready)")
                executeJs(js)
            }
            WidgetSession.Submit.QUEUED ->
                logd("runOrQueue: queued (pending=${session.pendingCount})")
            WidgetSession.Submit.DROPPED ->
                logw("runOrQueue: widget unavailable (${session.unavailableReason}), dropping command")
        }
    }

    private fun executeJs(js: String) {
        val wv = webView ?: run {
            logw("executeJs: WKWebView is null, dropping command")
            return
        }
        wv.evaluateJavaScript(js, completionHandler = null)
    }

    private fun handleBridgeMessage(message: BridgeMessage) {
        when (message) {
            BridgeMessage.WidgetReady -> {
                val commands = session.markReady()
                logd("bridge: widgetReady; flushing ${commands.size} command(s)")
                commands.forEach { executeJs(it) }
            }
            BridgeMessage.Open -> {
                logd("bridge: onOpen")
                onOpenCallback?.onEvent()
            }
            BridgeMessage.Close -> {
                logd("bridge: onClose")
                onCloseCallback?.onEvent()
            }
            is BridgeMessage.Unavailable -> {
                logw("bridge: widgetUnavailable reason=${message.reason}")
                session.markUnavailable(message.reason)
                onUnavailableCallback?.invoke(message.reason)
            }
            is BridgeMessage.Message -> {
                // Never log message text (HIPAA accounts).
                logd("bridge: onMessage sender=${message.message.senderType}")
                onMessageCallback?.invoke(message.message)
            }
            is BridgeMessage.UnreadCountChanged -> {
                logd("bridge: onUnreadCountChanged count=${message.count}")
                onUnreadCountCallback?.invoke(message.count)
            }
            is BridgeMessage.Unknown -> logw("bridge: unknown message ${message.description}")
        }
    }

    private fun logd(message: String) {
        if (config.debug) SDKLogger.d(TAG, message)
    }

    private fun logw(message: String) {
        if (config.debug) SDKLogger.w(TAG, message)
    }

    private fun loge(message: String) {
        if (config.debug) SDKLogger.e(TAG, message)
    }

    /**
     * Handles messages posted from JavaScript via window.NativeBridge.postMessage().
     * WebKit delivers script messages on the main thread, so callbacks run there too.
     */
    private inner class MessageHandler : NSObject(), WKScriptMessageHandlerProtocol {
        override fun userContentController(
            userContentController: WKUserContentController,
            didReceiveScriptMessage: WKScriptMessage
        ) {
            val message = didReceiveScriptMessage.body as? String ?: run {
                logw("bridge: non-string message body")
                return
            }
            handleBridgeMessage(BridgeMessage.parse(message))
        }
    }

    /**
     * Surfaces WKWebView navigation failures so JS/network errors are visible.
     */
    private inner class NavigationDelegate : NSObject(), WKNavigationDelegateProtocol {
        override fun webView(
            webView: WKWebView,
            didFailProvisionalNavigation: WKNavigation?,
            withError: NSError
        ) {
            loge("WKWebView didFailProvisionalNavigation: ${withError.localizedDescription}")
        }

        override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
            handleContentProcessTerminated(webView)
        }
    }

    private companion object {
        const val TAG = "SparrowDeskSDK"
    }
}
