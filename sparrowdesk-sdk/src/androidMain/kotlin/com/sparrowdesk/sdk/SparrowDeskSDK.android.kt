package com.sparrowdesk.sdk

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

actual class SparrowDeskSDK actual constructor(private val config: SparrowDeskConfig) {

    private var webView: WebView? = null
    private var attachedContext: Context? = null
    private var attachedParent: ViewGroup? = null
    private val session = WidgetSession()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isHidden = false
    private var savedSize: Pair<Int, Int>? = null

    private var lifecycleOwner: LifecycleOwner? = null
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                webView?.onResume()
                resume()
            }
            Lifecycle.Event.ON_PAUSE -> webView?.onPause()
            else -> Unit
        }
    }

    private var onOpenCallback: SparrowDeskCallback? = null
    private var onCloseCallback: SparrowDeskCallback? = null
    private var onUnavailableCallback: ((WidgetUnavailableReason) -> Unit)? = null
    private var onMessageCallback: ((SparrowDeskMessage) -> Unit)? = null
    private var onUnreadCountCallback: ((Int) -> Unit)? = null

    /**
     * Creates the WebView and attaches it to the given parent ViewGroup.
     * Must be called from the main thread before using any widget methods.
     *
     * If [context] is a `LifecycleOwner` (e.g. an AppCompat/ComponentActivity), the SDK
     * observes it: `ON_RESUME` calls [resume] and `WebView.onResume()`, `ON_PAUSE` calls
     * `WebView.onPause()`. Otherwise call [resume] yourself when the app returns to the
     * foreground.
     *
     * @param context Android Context (Activity or Application)
     * @param parent  The ViewGroup to which the WebView will be added. Use a plain container
     *                such as FrameLayout: [hide] collapses the WebView to 0x0, which a
     *                ConstraintLayout parent would treat as MATCH_CONSTRAINT.
     */
    fun attach(context: Context, parent: ViewGroup) {
        if (webView != null) {
            logd("attach: already attached, ignoring")
            return
        }
        logd("attach: domain=${config.domain}")
        attachedContext = context
        attachedParent = parent

        val layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        createAndLoadWebView(context, parent, -1, layoutParams)

        (context as? LifecycleOwner)?.let { owner ->
            lifecycleOwner = owner
            owner.lifecycle.addObserver(lifecycleObserver)
        }
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
            logw("getStatus: WebView not attached, returning UNKNOWN")
            callback(WidgetStatus.UNKNOWN)
            return
        }
        mainHandler.post {
            wv.evaluateJavascript(JsBridge.getStatus()) { result ->
                logd("getStatus result=$result")
                val status = WidgetStatus.fromString(
                    result?.removeSurrounding("\"") ?: ""
                )
                callback(status)
            }
        }
    }

    actual fun show() {
        logd("show()")
        mainHandler.post {
            isHidden = false
            webView?.let { restoreSize(it) }
        }
    }

    actual fun hide() {
        logd("hide()")
        mainHandler.post {
            isHidden = true
            webView?.let { collapse(it) }
        }
    }

    actual fun destroy() {
        logd("destroy()")
        mainHandler.post {
            lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
            lifecycleOwner = null
            webView?.let { destroyWebView(it) }
            webView = null
            attachedContext = null
            attachedParent = null
            isHidden = false
            savedSize = null
            session.clear()
            onOpenCallback = null
            onCloseCallback = null
            onUnavailableCallback = null
            onMessageCallback = null
            onUnreadCountCallback = null
        }
    }

    // --- Private helpers ---

    @SuppressLint("SetJavaScriptEnabled")
    private fun createAndLoadWebView(
        context: Context,
        parent: ViewGroup,
        index: Int,
        layoutParams: ViewGroup.LayoutParams
    ) {
        val wv = WebView(context).apply {
            this.layoutParams = layoutParams
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false

            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    loge(
                        "WebView onReceivedError: url=${request?.url} " +
                            "code=${error?.errorCode} desc=${error?.description}"
                    )
                }

                // Only invoked on API 26+, where RenderProcessGoneDetail exists.
                @TargetApi(Build.VERSION_CODES.O)
                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean = handleRenderProcessGone(view, detail?.didCrash())
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        logd(
                            "JS console [${it.messageLevel()}] " +
                                "${it.sourceId()}:${it.lineNumber()} ${it.message()}"
                        )
                    }
                    return true
                }
            }

            // Register the native bridge
            addJavascriptInterface(NativeBridgeInterface(), "NativeBridgeAndroid")
        }

        webView = wv
        parent.addView(wv, index)
        if (isHidden) collapse(wv)

        wv.loadDataWithBaseURL(
            "https://${config.domain}",
            buildHtml(),
            "text/html",
            "UTF-8",
            null
        )
        logd("attach: HTML loaded")
    }

    /** Builds the HTML and injects the platform-specific NativeBridge adapter. */
    private fun buildHtml(): String {
        val html = HtmlTemplate.build(config)
        // Replace only the first <script> occurrence
        return html.replaceFirst(
            "<script>",
            """<script>
window.NativeBridge = {
    postMessage: function(msg) {
        NativeBridgeAndroid.postMessage(msg);
    }
};
"""
        )
    }

    /**
     * The OS killed (or crashed) the renderer, typically while the app was in the background.
     * The WebView instance is unusable after this, so replace it with a fresh one, reload the
     * page and replay the last-known contact fields, conversation fields, tags and language.
     * Returning true keeps the host app alive.
     */
    private fun handleRenderProcessGone(view: WebView?, didCrash: Boolean?): Boolean {
        SDKLogger.e(TAG, "WebView render process gone (didCrash=$didCrash); recreating WebView")
        val dead = view ?: webView ?: return true
        if (dead !== webView) {
            // A WebView we already replaced or destroyed; just make sure it is gone.
            (dead.parent as? ViewGroup)?.removeView(dead)
            dead.destroy()
            return true
        }

        val context = attachedContext
        val parent = (dead.parent as? ViewGroup) ?: attachedParent
        val index = parent?.indexOfChild(dead) ?: -1
        // Reuse the existing params: when hidden they are already collapsed and savedSize
        // still holds the original size for show().
        val layoutParams = dead.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        destroyWebView(dead)
        webView = null
        session.resetForReload()

        if (context == null || parent == null) {
            SDKLogger.e(TAG, "render process gone: no context/parent to recreate the WebView")
            return true
        }
        createAndLoadWebView(context, parent, index, layoutParams)
        return true
    }

    private fun destroyWebView(wv: WebView) {
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.removeJavascriptInterface("NativeBridgeAndroid")
        wv.stopLoading()
        wv.destroy()
    }

    // hide() collapses the WebView to 0x0 instead of setting View.GONE/INVISIBLE. Android
    // WebView ties the page's visibility (document.hidden) to the View's visibility; once the
    // page is hidden, Chromium throttles its timers (to about once per minute after five
    // minutes), which stalls the widget's socket reconnects and live messages. A zero-size
    // VISIBLE view keeps the page "visible" while taking no space and receiving no touches.
    private fun collapse(wv: WebView) {
        wv.visibility = View.VISIBLE
        val lp = wv.layoutParams ?: return
        if (savedSize == null) savedSize = lp.width to lp.height
        lp.width = 0
        lp.height = 0
        wv.layoutParams = lp
    }

    private fun restoreSize(wv: WebView) {
        wv.visibility = View.VISIBLE
        val (width, height) = savedSize ?: return
        savedSize = null
        val lp = wv.layoutParams ?: return
        lp.width = width
        lp.height = height
        wv.layoutParams = lp
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
            logw("executeJs: WebView is null, dropping command")
            return
        }
        mainHandler.post {
            wv.evaluateJavascript(js, null)
        }
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
     * JavaScript interface that receives messages from the WebView.
     * Methods annotated with @JavascriptInterface are called from a background thread,
     * so every message is handled (and every callback invoked) on the main thread.
     */
    private inner class NativeBridgeInterface {
        @JavascriptInterface
        fun postMessage(message: String) {
            val parsed = BridgeMessage.parse(message)
            mainHandler.post { handleBridgeMessage(parsed) }
        }
    }

    private companion object {
        const val TAG = "SparrowDeskSDK"
    }
}
