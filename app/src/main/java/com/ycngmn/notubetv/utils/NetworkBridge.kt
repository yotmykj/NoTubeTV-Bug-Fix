package com.ycngmn.notubetv.utils

import android.webkit.JavascriptInterface
import com.multiplatform.webview.web.WebViewNavigator
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class NetworkBridge(
    private val navigator: WebViewNavigator
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = HttpClient(OkHttp)

    @JavascriptInterface
    fun fetch(url: String, videoId: String) {
        scope.launch {
            val result = runCatching {
                val body = client.get(url).body<String>()
                if (body.trimStart().startsWith("[")) filterSponsorBlock(body, videoId) else body
            }.getOrDefault("")

            val encoded = JSONObject.quote(result)
            val script = "window.onNetworkBridgeResponse && window.onNetworkBridgeResponse($encoded);"
            withContext(Dispatchers.Main) {
                navigator.evaluateJavaScript(script)
            }
        }
    }

    private fun filterSponsorBlock(body: String, videoId: String): String {
        return runCatching {
            val json = JSONArray(body)
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                if (item.optString("videoID") == videoId) {
                    return@runCatching item.toString()
                }
            }
            ""
        }.getOrDefault("")
    }
}
