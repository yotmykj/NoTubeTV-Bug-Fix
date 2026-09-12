package com.ycngmn.notubetv.ui.screens

import android.app.Activity
import android.view.View
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text as TvText
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebContent
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import com.ycngmn.notubetv.ui.YoutubeVM
import com.ycngmn.notubetv.ui.components.UpdateDialog
import com.ycngmn.notubetv.utils.ExitBridge
import com.ycngmn.notubetv.utils.NetworkBridge
import com.ycngmn.notubetv.utils.getUpdate
import com.ycngmn.notubetv.utils.permHandler
import com.ycngmn.notubetv.utils.readAsset

private const val YOUTUBE_URL = "https://www.youtube.com/tv"

private const val GOOGLE_FIX_JS = """
(function() {
    try {
        let meta = document.querySelector('meta[name="viewport"]');
        if (!meta) {
            meta = document.createElement('meta');
            meta.name = 'viewport';
            (document.head || document.documentElement).appendChild(meta);
        }
        meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';

        if (!window.__notube_google_fix_loaded) {
            window.__notube_google_fix_loaded = true;
            const style = document.createElement('style');
            style.textContent = 'html,body{overflow-x:hidden!important;}div[data-view-id]{max-width:100vw!important;box-sizing:border-box!important;}';
            (document.head || document.documentElement).appendChild(style);

            document.addEventListener('keydown', function(e) {
                if (e.key !== 'Enter' && e.keyCode !== 13) return;
                const active = document.activeElement;
                if (!active) return;
                e.preventDefault();
                e.stopPropagation();
                ['pointerdown','mousedown','pointerup','mouseup','click'].forEach(function(type) {
                    active.dispatchEvent(new MouseEvent(type, {
                        bubbles: true,
                        cancelable: true,
                        view: window,
                        buttons: 1
                    }));
                });
            }, true);
        }
    } catch (_) {}
})();
""".trimIndent()


@Composable
fun YoutubeWV(youtubeVM: YoutubeVM = viewModel()) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val state = rememberWebViewState(YOUTUBE_URL)
    val navigator = rememberWebViewNavigator()
    val loadingState = state.loadingState
    val jsScript = youtubeVM.scriptData
    val updateData = youtubeVM.updateData

    var showSplash by remember { mutableStateOf(true) }
    var updateCheckStarted by remember { mutableStateOf(false) }
    val exitTrigger = remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        when {
            navigator.canGoBack -> navigator.navigateBack()
            loadingState is LoadingState.Finished -> {
                navigator.evaluateJavaScript(
                    """
                    (function() {
                        const down = new KeyboardEvent('keydown', {key:'Escape', keyCode:27, code:'Escape', which:27, bubbles:true, cancelable:true});
                        const up = new KeyboardEvent('keyup', {key:'Escape', keyCode:27, code:'Escape', which:27, bubbles:true, cancelable:true});
                        document.dispatchEvent(down);
                        document.dispatchEvent(up);
                    })();
                    """.trimIndent()
                )
            }
            else -> activity.finish()
        }
    }

    LaunchedEffect(Unit) {
        val bundledScript = runCatching { readAsset(context, "userscripts.js") }.getOrDefault("")
        if (bundledScript.isNotBlank()) youtubeVM.setScript(bundledScript)
    }

    LaunchedEffect(loadingState, jsScript) {
        if (loadingState !is LoadingState.Finished || jsScript.isNullOrBlank()) return@LaunchedEffect

        showSplash = false

        val currentUrl = (state.content as? WebContent.Url)?.url.orEmpty()
        if (currentUrl.contains("accounts.google.com")) {
            navigator.evaluateJavaScript(GOOGLE_FIX_JS)
        }

        // A page reload creates a fresh JS global. The guard prevents duplicate
        // MutationObservers when Compose recomposes the finished state.
        val wrappedScript = """
            (function() {
                if (window.__notube_tv_userscript_loaded) return;
                window.__notube_tv_userscript_loaded = true;
                try {
                    ${jsScript}
                } catch (error) {
                    console.error('NoTubeTV userscript failed', error);
                }
            })();
        """.trimIndent()
        navigator.evaluateJavaScript(wrappedScript)

        if (!updateCheckStarted) {
            updateCheckStarted = true
            getUpdate(context, navigator) { update ->
                if (update != null) youtubeVM.setUpdate(update)
            }
        }
    }

    if (updateData != null) {
        UpdateDialog(updateData, navigator)
    }

    if (exitTrigger.value) {
        activity.finish()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        WebView(
            modifier = Modifier.fillMaxSize(),
            state = state,
            navigator = navigator,
            platformWebViewParams = permHandler(context),
            captureBackPresses = false,
            onCreated = { webView ->
                CookieManager.getInstance().apply {
                    setAcceptCookie(true)
                    setAcceptThirdPartyCookies(webView, true)
                    flush()
                }

                state.webSettings.apply {
                    customUserAgentString =
                        "Mozilla/5.0 (DirectFB; Linux armv7l) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36 Cobalt/24.lts.3-gold (gzip) FireTV/AFTMM (Amazon, AFTMM)"
                    isJavaScriptEnabled = true
                    androidWebSettings.apply {
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                }

                webView.apply {
                    addJavascriptInterface(ExitBridge(exitTrigger), "ExitBridge")
                    addJavascriptInterface(NetworkBridge(navigator), "NetworkBridge")
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
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
                        TvText(
                            text = "NoTube TV",
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Загрузка...",
                            color = Color(0xFFA1A1AA),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
