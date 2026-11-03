package com.sparrowdesk.sdk

/**
 * Configuration for initializing the SparrowDesk SDK.
 *
 * @param domain         Your SparrowDesk domain (e.g., "yourcompany.sparrowdesk.com")
 * @param token          Your widget token
 * @param debug          When true, the SDK emits diagnostic logs (Android: logcat tag
 *                       "SparrowDeskSDK"; iOS: Xcode console). Leave false in release builds.
 * @param language       Widget language code (e.g., "fr") applied at load. When null the
 *                       widget picks its default language. Change it later with
 *                       [SparrowDeskSDK.setLanguage].
 * @param readyTimeoutMs How long to wait for the widget to become ready before reporting
 *                       [WidgetUnavailableReason.TIMEOUT] through [SparrowDeskSDK.onUnavailable].
 */
data class SparrowDeskConfig(
    val domain: String,
    val token: String,
    val debug: Boolean = false,
    val language: String? = null,
    val readyTimeoutMs: Long = DEFAULT_READY_TIMEOUT_MS
) {
    /**
     * Keeps the three-argument initializer available to Swift / Objective-C, which do not
     * see Kotlin default arguments.
     */
    constructor(domain: String, token: String, debug: Boolean) :
        this(domain, token, debug, null, DEFAULT_READY_TIMEOUT_MS)

    companion object {
        const val DEFAULT_READY_TIMEOUT_MS: Long = 30_000
    }
}
