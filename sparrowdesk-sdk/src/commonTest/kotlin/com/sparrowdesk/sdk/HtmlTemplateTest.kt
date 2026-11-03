package com.sparrowdesk.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlTemplateTest {

    private val config = SparrowDeskConfig(
        domain = "test.sparrowdesk.com",
        token = "abc123"
    )

    @Test
    fun htmlContainsWidgetToken() {
        val html = HtmlTemplate.build(config)
        assertTrue(html.contains("abc123"), "HTML should contain the widget token")
    }

    @Test
    fun htmlContainsDomain() {
        val html = HtmlTemplate.build(config)
        assertTrue(html.contains("test.sparrowdesk.com"), "HTML should contain the domain")
    }

    @Test
    fun htmlLoadsScriptFromCdn() {
        val html = HtmlTemplate.build(config)
        assertTrue(
            html.contains("https://assets.cdn.sparrowdesk.com/chatbot/bundle/main.js"),
            "HTML should load the widget script from CDN"
        )
    }

    @Test
    fun htmlSetsUpNativeBridgeCallbacks() {
        val html = HtmlTemplate.build(config)
        assertTrue(html.contains("NativeBridge.postMessage"), "HTML should set up NativeBridge")
    }

    @Test
    fun htmlSignalsWidgetReady() {
        val html = HtmlTemplate.build(config)
        assertTrue(
            html.contains("widgetReady"),
            "HTML should signal widgetReady to native"
        )
    }

    @Test
    fun htmlHasViewportMeta() {
        val html = HtmlTemplate.build(config)
        assertTrue(
            html.contains("viewport"),
            "HTML should have a viewport meta tag for mobile"
        )
    }

    @Test
    fun htmlEscapesTokenWithSpecialChars() {
        val dangerousConfig = SparrowDeskConfig(
            domain = "test.sparrowdesk.com",
            token = "token\"with<script>"
        )
        val html = HtmlTemplate.build(dangerousConfig)
        assertTrue(!html.contains("<script>with"), "Token with script tags should be escaped")
    }

    @Test
    fun htmlOmitsLanguageGlobalByDefault() {
        val html = HtmlTemplate.build(config)
        assertFalse(html.contains("SD_WIDGET_LANGUAGE"))
    }

    @Test
    fun htmlSetsLanguageGlobalBeforeBundle() {
        val html = HtmlTemplate.build(config.copy(language = "fr"))
        val languageIndex = html.indexOf("window.SD_WIDGET_LANGUAGE = \"fr\";")
        assertTrue(languageIndex >= 0, "HTML should set SD_WIDGET_LANGUAGE")
        assertTrue(languageIndex < html.indexOf("document.body.appendChild(script)"))
    }

    @Test
    fun htmlEscapesLanguage() {
        val html = HtmlTemplate.build(config.copy(language = "fr\"</script>"))
        assertFalse(html.contains("fr\"</script>"))
        assertTrue(html.contains("window.SD_WIDGET_LANGUAGE = \"fr\\\"\\x3c/script\\x3e\";"))
    }

    @Test
    fun htmlChecksUnavailableFlag() {
        val html = HtmlTemplate.build(config)
        assertTrue(html.contains("window.SD_WIDGET_UNAVAILABLE"))
        assertTrue(html.contains("\"widgetUnavailable\""))
    }

    @Test
    fun htmlUsesDefaultReadyTimeout() {
        val html = HtmlTemplate.build(config)
        assertTrue(html.contains("var READY_TIMEOUT_MS = 30000;"))
        assertTrue(html.contains("reportUnavailable(\"timeout\")"))
    }

    @Test
    fun htmlUsesConfiguredReadyTimeout() {
        val html = HtmlTemplate.build(config.copy(readyTimeoutMs = 5_000))
        assertTrue(html.contains("var READY_TIMEOUT_MS = 5000;"))
    }

    @Test
    fun htmlReportsBundleFailure() {
        val html = HtmlTemplate.build(config)
        assertTrue(html.contains("script.onerror"))
        assertTrue(html.contains("reportUnavailable(\"bundle_failed\")"))
    }

    @Test
    fun htmlWiresLiveMessageEvents() {
        val html = HtmlTemplate.build(config)
        assertTrue(html.contains("typeof api.onMessage === \"function\""))
        assertTrue(html.contains("postEvent(\"onMessage\""))
        assertTrue(html.contains("typeof api.onUnreadCountChanged === \"function\""))
        assertTrue(html.contains("postEvent(\"onUnreadCountChanged\""))
    }

    @Test
    fun htmlHasSingleScriptTagForAndroidBridgeInjection() {
        val html = HtmlTemplate.build(config)
        assertEquals(1, Regex("<script>").findAll(html).count())
    }
}
