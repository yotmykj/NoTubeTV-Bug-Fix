package com.ycngmn.notubetv.ui.screens

import android.app.Activity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import com.ycngmn.notubetv.R
import com.ycngmn.notubetv.ui.YoutubeVM
import com.ycngmn.notubetv.ui.components.UpdateDialog
import com.ycngmn.notubetv.utils.ExitBridge
import com.ycngmn.notubetv.utils.NetworkBridge
import com.ycngmn.notubetv.utils.fetchScripts
import com.ycngmn.notubetv.utils.getUpdate
import com.ycngmn.notubetv.utils.permHandler
import com.ycngmn.notubetv.utils.readRaw

@Composable
fun YoutubeWV(youtubeVM: YoutubeVM = viewModel()) {

    val context = LocalContext.current
    val activity = context as Activity

    val state = rememberWebViewState("https://www.youtube.com/tv")
    val navigator = rememberWebViewNavigator()

    val jsScript = youtubeVM.scriptData
    val updateData = youtubeVM.updateData

    val loadingState = state.loadingState
    val exitTrigger = remember { mutableStateOf(false) }

    // Перехват кнопки "Назад" с пульта ТВ
    BackHandler {
        when {
            navigator.canGoBack -> {
                navigator.navigateBack()
            }
            state.loadingState is LoadingState.Finished -> {
                val backPressScript = """
                    (function() {
                        const downEvent = new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, code: 'Escape', which: 27, bubbles: true, cancelable: true });
                        const upEvent = new KeyboardEvent('keyup', { key: 'Escape', keyCode: 27, code: 'Escape', which: 27, bubbles: true, cancelable: true });
                        document.dispatchEvent(downEvent);
                        document.dispatchEvent(upEvent);
                    })();
                """.trimIndent()
                navigator.evaluateJavaScript(backPressScript)
            }
            else -> {
                exitTrigger.value = true
            }
        }
    }

    LaunchedEffect(Unit) {
        youtubeVM.setScript(fetchScripts())
        getUpdate(context, navigator) { update ->
            if (update != null) youtubeVM.setUpdate(update)
        }
    }

    if (loadingState == LoadingState.Finished) {
        if (jsScript != null) {
            navigator.evaluateJavaScript(jsScript)
        }

        val adblockJs = readRaw(context, R.raw.adblock)
        if (adblockJs.isNotEmpty()) {
            navigator.evaluateJavaScript(adblockJs)
        }

        val sponsorBlockJs = readRaw(context, R.raw.sponsorblock)
        if (sponsorBlockJs.isNotEmpty()) {
            navigator.evaluateJavaScript(sponsorBlockJs)
        }
    }

    if (updateData != null) UpdateDialog(updateData, navigator)
    if (exitTrigger.value) activity.finish()

    val loading = state.loadingState as? LoadingState.Loading
    if (loading != null) SplashLoading(loading.progress)

    WebView(
        modifier = Modifier.fillMaxSize(),
        state = state,
        navigator = navigator,
        platformWebViewParams = permHandler(context),
        captureBackPresses = false,
        onCreated = { webView ->

            (activity.window).setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
            cookieManager.flush()

            state.webSettings.apply {
                customUserAgentString = "Mozilla/5.0 (DirectFB; Linux armv7l) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36 Cobalt/24.lts.3-gold (gzip) FireTV/AFTMM (Amazon, AFTMM)"
                isJavaScriptEnabled = true

                androidWebSettings.apply {
                    useWideViewPort = true
                    domStorageEnabled = true
                }
            }

            webView.apply {
                webChromeClient = WebChromeClient()

                addJavascriptInterface(ExitBridge(exitTrigger), "ExitBridge")
                addJavascriptInterface(NetworkBridge(navigator), "NetworkBridge")

                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                setInitialScale(25)

                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
            }
        }
    )
}
