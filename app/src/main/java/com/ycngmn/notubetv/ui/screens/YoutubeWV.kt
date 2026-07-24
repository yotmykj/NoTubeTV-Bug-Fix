package com.ycngmn.notubetv.ui.screens

import android.app.Activity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    var showSplash by remember { mutableStateOf(true) }

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
                activity.finish()
            }
        }
    }

    LaunchedEffect(Unit) {
        youtubeVM.setScript(fetchScripts())
        getUpdate(context, navigator) { update ->
            if (update != null) youtubeVM.setUpdate(update)
        }
    }

    LaunchedEffect(loadingState) {
        if (loadingState is LoadingState.Finished) {
            showSplash = false

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
    }

    if (updateData != null) UpdateDialog(updateData, navigator)
    if (exitTrigger.value) activity.finish()

    Box(modifier = Modifier.fillMaxSize()) {
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
                        mediaPlaybackRequiresUserGesture = false
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

        if (showSplash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF09090B)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(420.dp)
                        .background(Color(0xFF18181B), RoundedCornerShape(20.dp))
                        .border(1.dp, Color(0xFF27272A), RoundedCornerShape(20.dp))
                        .padding(32.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "NoTube TV",
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = "Загрузка...",
                            color = Color(0xFFA1A1AA),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
