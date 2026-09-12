package com.ycngmn.notubetv.utils

import android.content.Context
import com.multiplatform.webview.web.WebViewNavigator
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import org.json.JSONObject

// GitHub latest-release check is intentionally best-effort: the app must still work
// when GitHub is unavailable.
data class ReleaseData(
    val tagName: String,
    val changelog: String,
    val downloadUrl: String
)

private val client by lazy { HttpClient(OkHttp) }
private const val GITHUB_REPO = "yotmykj/NoTubeTV-Bug-Fix"
private const val FETCH_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

suspend fun fetchUpdate(): ReleaseData? {
    return runCatching {
        val response = client.get(FETCH_URL) {
            header("User-Agent", "NoTubeTV-App")
            header("Accept", "application/vnd.github+json")
        }

        if (response.status.value !in 200..299) return null

        val json = JSONObject(response.body<String>())
        val assets = json.optJSONArray("assets") ?: return null

        var apkUrl = ""
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
                break
            }
        }
        if (apkUrl.isBlank()) return null

        val tag = json.optString("tag_name").trim()
        if (tag.isBlank()) return null

        val rawBody = json.optString("body", "")
        val cleanCommitHashes = Regex("\\b[a-fA-F0-9]{40}\\b")
        val changelog = rawBody
            .substringAfter("</ins>", rawBody)
            .replace(cleanCommitHashes, "")
            .replace(Regex("[ \\t]{2,}"), " ")
            .trim()

        ReleaseData(
            tagName = tag,
            changelog = changelog,
            downloadUrl = apkUrl
        )
    }.getOrNull()
}

suspend fun getUpdate(
    context: Context,
    navigator: WebViewNavigator,
    callback: (ReleaseData?) -> Unit
) {
    val remoteRelease = fetchUpdate() ?: run {
        callback(null)
        return
    }

    val remoteVersion = remoteRelease.tagName.removePrefix("v")
    val localVersion = getLocalVersion(context)

    if (!isNewerVersion(remoteVersion, localVersion)) {
        callback(null)
        return
    }

    getSkipVersion(navigator) { skipped ->
        val skipVersion = skipped
            ?.removeSurrounding("\"")
            ?.removePrefix("v")
            ?.trim()

        callback(if (skipVersion != remoteVersion) remoteRelease else null)
    }
}

private fun isNewerVersion(remote: String, local: String): Boolean {
    val remoteParts = normalizeVersion(remote)
    val localParts = normalizeVersion(local)
    val maxLength = maxOf(remoteParts.size, localParts.size)

    for (i in 0 until maxLength) {
        val remotePart = remoteParts.getOrElse(i) { 0 }
        val localPart = localParts.getOrElse(i) { 0 }
        if (remotePart != localPart) return remotePart > localPart
    }
    return false
}

private fun normalizeVersion(version: String): List<Int> =
    version.removePrefix("v")
        .split('.', '-', '_')
        .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        .ifEmpty { listOf(0) }

private fun getLocalVersion(context: Context): String = runCatching {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
}.getOrDefault("0.0.0")

private fun getSkipVersion(navigator: WebViewNavigator, callback: (String?) -> Unit) {
    runCatching {
        navigator.evaluateJavaScript("window.configRead ? configRead('skipVersionName') : null") {
            callback(it)
        }
    }.onFailure {
        callback(null)
    }
}
