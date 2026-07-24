package com.ycngmn.notubetv.utils

import android.content.Context
import com.multiplatform.webview.web.WebViewNavigator
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import org.json.JSONObject

data class ReleaseData(
    val tagName: String,
    val changelog: String,
    val downloadUrl: String
)

private val client by lazy { HttpClient(OkHttp) }

private const val GITHUB_REPO = "yotmykj/NoTubeTV-Bug-Fix"
private const val FETCH_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

suspend fun fetchUpdate(): ReleaseData? {
    return try {
        val req = client.get(FETCH_URL) {
            // ОБЯЗАТЕЛЬНО: GitHub API требует User-Agent
            header("User-Agent", "NoTubeTV-App")
        }
        val res = JSONObject(req.body<String>())
        
        // Проверяем, есть ли прикрепленные файлы (assets)
        val assets = res.optJSONArray("assets") ?: return null
        if (assets.length() == 0) return null

        val commitSHA = Regex("\\b[a-fA-F0-9]{40}\\b")

        ReleaseData(
            tagName = res.optString("tag_name", ""),
            changelog = res.optString("body", "")
                .substringAfter("</ins>", res.optString("body", ""))
                .replace(commitSHA, "")
                .replace(Regex("\\s{2,}"), " "),
            downloadUrl = assets.getJSONObject(0).optString("browser_download_url", "")
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun getUpdate(context: Context, navigator: WebViewNavigator, callback: (ReleaseData?) -> Unit) {
    try {
        val remoteRelease = fetchUpdate() ?: run {
            callback(null)
            return
        }

        val remoteVersion = remoteRelease.tagName.removePrefix("v")
        val localVersion = getLocalVersion(context)

        // Сравниваем версии
        if (isNewerVersion(remote = remoteVersion, local = localVersion)) {
            // Безопасно проверяем, не пропустил ли пользователь эту версию
            getSkipVersion(navigator) { skipped ->
                val skipVersion = skipped?.removeSurrounding("\"")?.removePrefix("v")
                if (skipVersion != remoteVersion) {
                    callback(remoteRelease)
                } else {
                    callback(null)
                }
            }
        } else {
            callback(null)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        callback(null)
    }
}

private fun isNewerVersion(remote: String, local: String): Boolean {
    val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
    val localParts = local.split(".").mapNotNull { it.toIntOrNull() }
    val maxLength = maxOf(remoteParts.size, localParts.size)

    for (i in 0 until maxLength) {
        val r = remoteParts.getOrElse(i) { 0 }
        val l = localParts.getOrElse(i) { 0 }
        if (r > l) return true
        if (r < l) return false
    }
    return false
}

private fun getLocalVersion(context: Context): String {
    return try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        pInfo.versionName ?: "0.0.0"
    } catch (e: Exception) {
        "0.0.0"
    }
}

fun getSkipVersion(navigator: WebViewNavigator, callback: (String?) -> Unit) {
    try {
        navigator.evaluateJavaScript("configRead('skipVersionName')") {
            callback(it)
        }
    } catch (_: Exception) {
        // Если JS еще не загрузился, просто передаем null
        callback(null)
    }
}
