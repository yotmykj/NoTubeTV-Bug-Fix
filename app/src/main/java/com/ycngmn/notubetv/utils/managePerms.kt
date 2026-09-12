package com.ycngmn.notubetv.utils

import android.content.Context
import android.content.pm.PackageManager
import android.webkit.PermissionRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.multiplatform.webview.web.AccompanistWebChromeClient
import com.multiplatform.webview.web.PlatformWebViewParams

@Composable
fun permHandler(context: Context): PlatformWebViewParams {
    val pendingRequest = remember { mutableStateOf<PermissionRequest?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val request = pendingRequest.value
        pendingRequest.value = null
        if (request == null) return@rememberLauncherForActivityResult

        if (granted) {
            val resources = request.resources.filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
            if (resources.isNotEmpty()) request.grant(resources.toTypedArray()) else request.deny()
        } else {
            request.deny()
        }
    }

    val chrome = remember(context) {
        object : AccompanistWebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val needsAudio = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                if (!needsAudio) {
                    request.deny()
                    return
                }

                if (hasPermission(context)) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                } else {
                    pendingRequest.value = request
                    permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    return PlatformWebViewParams(chromeClient = chrome)
}

fun hasPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(
    context,
    android.Manifest.permission.RECORD_AUDIO
) == PackageManager.PERMISSION_GRANTED
