package com.sparrowdesk.sdk

/**
 * Builds the HTML page loaded into the WebView.
 * Sets up the SparrowDesk widget script and the native bridge communication layer.
 */
internal object HtmlTemplate {

    fun build(config: SparrowDeskConfig): String {
        val escapedToken = JsBridge.escapeJs(config.token)
        val escapedDomain = JsBridge.escapeJs(config.domain)
        val debug = if (config.debug) "true" else "false"
        val languageAssignment = config.language?.let {
            "window.SD_WIDGET_LANGUAGE = \"${JsBridge.escapeJs(it)}\";"
        } ?: ""
        val readyTimeoutMs = config.readyTimeoutMs.coerceAtLeast(0)

        // Bridge protocol (JS -> native): the legacy events "widgetReady", "onOpen" and
        // "onClose" stay plain strings; newer events are JSON {"type":...,"payload":{...}}
        // parsed by BridgeMessage.parse.
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        html, body { width: 100%; height: 100%; overflow: hidden; background: transparent; }
    </style>
</head>
<body>
<script>
(function() {
    var DEBUG = $debug;
    var READY_TIMEOUT_MS = $readyTimeoutMs;
    function log(msg) { if (DEBUG) console.log("[SD] " + msg); }
    function warn(msg) { if (DEBUG) console.warn("[SD] " + msg); }

    function post(msg) {
        if (window.NativeBridge) window.NativeBridge.postMessage(msg);
    }
    function postEvent(type, payload) {
        post(JSON.stringify({ type: type, payload: payload }));
    }
    function str(v) { return v === undefined || v === null ? null : String(v); }

    function describe(obj) {
        if (!obj) return String(obj);
        var shape = {};
        for (var k in obj) {
            try { shape[k] = typeof obj[k]; } catch (e) { shape[k] = "?"; }
        }
        return JSON.stringify(shape);
    }

    var tries = 0;
    var startedAt = Date.now();
    var readyCheck = null;
    var finished = false;
    var timeoutReported = false;

    function stopPolling() {
        finished = true;
        if (readyCheck !== null) clearInterval(readyCheck);
        readyCheck = null;
    }

    function reportUnavailable(reason) {
        warn("widget unavailable: " + reason);
        postEvent("widgetUnavailable", { reason: reason });
    }

    function wireApi(api) {
        if (typeof api.onOpen === "function") {
            api.onOpen(function() { post("onOpen"); });
        } else {
            warn("sparrowDesk.onOpen is not a function");
        }
        if (typeof api.onClose === "function") {
            api.onClose(function() { post("onClose"); });
        } else {
            warn("sparrowDesk.onClose is not a function");
        }
        if (typeof api.onMessage === "function") {
            api.onMessage(function(m) {
                m = m || {};
                postEvent("onMessage", {
                    chatId: str(m.chatId),
                    messageId: str(m.messageId),
                    senderType: str(m.senderType),
                    text: str(m.text),
                    createdAt: str(m.createdAt)
                });
            });
        } else {
            warn("sparrowDesk.onMessage is not a function");
        }
        if (typeof api.onUnreadCountChanged === "function") {
            api.onUnreadCountChanged(function(count) {
                var n = Number(count);
                postEvent("onUnreadCountChanged", { count: isFinite(n) && n > 0 ? Math.floor(n) : 0 });
            });
        } else {
            warn("sparrowDesk.onUnreadCountChanged is not a function");
        }
    }

    function checkReady() {
        if (finished) return;
        tries++;
        var api = window.sparrowDesk || window.SparrowDesk;

        // The bundle sets SD_WIDGET_UNAVAILABLE ("suspended" or
        // "hidden_by_visibility_rules") when the widget will never become ready.
        if (window.SD_WIDGET_UNAVAILABLE) {
            stopPolling();
            reportUnavailable(String(window.SD_WIDGET_UNAVAILABLE));
            return;
        }

        // Wait for BOTH the API object AND the bundle's launcher-ready flag;
        // the object can exist before internal init finishes, which caused
        // the first-load openWidget() to be dropped.
        if (api && window.SD_LAUNCHER_READY) {
            stopPolling();
            log("sparrowDesk ready after " + tries + " tries; shape=" + describe(api));
            wireApi(api);
            post("widgetReady");
            return;
        }

        // Report a timeout once, but keep polling: on a slow network the widget can
        // still become ready later, and native recovers when widgetReady arrives.
        var elapsed = Date.now() - startedAt;
        if (!timeoutReported && elapsed >= READY_TIMEOUT_MS) {
            timeoutReported = true;
            var candidates = [];
            if (DEBUG) {
                for (var k in window) {
                    if (/sparrow|chatbot|widget|sd|survey|launcher/i.test(k)) candidates.push(k);
                }
            }
            warn("widgetReady timeout after " + elapsed + "ms. " +
                 "api=" + !!api + " launcherReady=" + !!window.SD_LAUNCHER_READY +
                 " globals=" + JSON.stringify(candidates));
            reportUnavailable("timeout");
        }
    }

    // Inject the SparrowDesk widget
    window.SD_WIDGET_TOKEN = "$escapedToken";
    window.SD_WIDGET_DOMAIN = "$escapedDomain";
    $languageAssignment

    var script = document.createElement("script");
    script.src = "https://assets.cdn.sparrowdesk.com/chatbot/bundle/main.js";
    script.async = true;
    script.onload = function() { log("bundle main.js onload"); };
    script.onerror = function() {
        warn("bundle main.js failed to load");
        if (finished) return;
        stopPolling();
        reportUnavailable("bundle_failed");
    };

    readyCheck = setInterval(checkReady, 100);
    document.body.appendChild(script);
})();
</script>
</body>
</html>
        """.trimIndent()
    }
}
