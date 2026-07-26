package com.ycngmn.notubetv.web

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class NoTubeWebViewClient : WebViewClient() {

    private val blockedHosts = listOf(
        "doubleclick.net",
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "adservice.google.kz",
        "adservice.google.ru",
        "ads.youtube.com",
        "static.doubleclick.net"
    )

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {

        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

        if (blockedHosts.any { url.contains(it, ignoreCase = true) }) {
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                ByteArrayInputStream(ByteArray(0))
            )
        }

        return super.shouldInterceptRequest(view, request)
    }
}
