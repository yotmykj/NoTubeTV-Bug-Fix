package com.ycngmn.notubetv.utils

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.compose.runtime.MutableState

class ExitBridge(val exitTrigger: MutableState<Boolean>) {
    @JavascriptInterface
    fun onExitCalled() {
        Handler(Looper.getMainLooper()).post {
            exitTrigger.value = true
        }
    }
}