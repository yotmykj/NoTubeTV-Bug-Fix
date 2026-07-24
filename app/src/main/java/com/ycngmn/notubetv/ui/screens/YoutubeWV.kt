package com.ycngmn.notubetv.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
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

    // Translate native back-presses to 'escape' button press
    BackHandler {
        if (state.loadingState is LoadingState.Finished)
            navigator.evaluateJavaScript(readRaw(context, R.raw.back_bridge))
        else exitTrigger.value = true
    }

    // Fetch scripts and updates at launch
    LaunchedEffect(Unit) {
        youtubeVM.setScript(fetchScripts() )
        getUpdate(context, navigator) { update ->
            if (update != null) youtubeVM.setUpdate(update)
        }
    }

    // При завершении загрузки применяем пользовательские скрипты, блокировщик и стили
    if (loadingState == LoadingState.Finished) {
        if (jsScript != null) {
            navigator.evaluateJavaScript(jsScript)
        }

        // 1. Внедряем блокировщик рекламы из res/raw/adblock.js
        val adblockScript = readRaw(context, R.raw.adblock)
        if (adblockScript.isNotEmpty()) {
            navigator.evaluateJavaScript(adblockScript)
        }

        // 2. Инжект CSS для полупрозрачности плеера и меню
        val transparencyScript = """
            (function() {
                if (document.getElementById('custom-transparency-style')) return;
                var style = document.createElement('style');
                style.id = 'custom-transparency-style';
                style.innerHTML = `
                    ytlr-player-overlay, .ytlr-player-overlay, .ytlr-overlay-background {
                        background: linear-gradient(to top, rgba(0, 0, 0, 0.85) 0%, rgba(0, 0, 0, 0.4) 70%, transparent 100%) !important;
                        background-color: transparent !important;
                    }
                    ytlr-multi-page-menu-system-renderer, ytlr-dialog-renderer {
                        background-color: rgba(18, 18, 18, 0.8) !important;
                    }
                `;
                document.head.appendChild(style);
            })();
        """.trimIndent()

        navigator.evaluateJavaScript(transparencyScript)
    }

    // If any update found, show the dialog.
    if (updateData != null) UpdateDialog(updateData, navigator)
    // If exit button is pressed, 'finish the activity' aka 'exit the app'.
    if (exitTrigger.value) activity.finish()

    // This is the loading screen
    val loading = state.loadingState as? LoadingState.Loading
    if (loading != null) SplashLoading(loading.progress)

    // User-Agent константы для ТВ и для формы авторизации Google
    val tvUA = "Mozilla/5.0 (DirectFB; Linux armv7l) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36 Cobalt/24.lts.3-gold (gzip) FireTV/AFTMM (Amazon, AFTMM)"
    val loginUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

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

            // Set up cookies
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
            cookieManager.flush()

            state.webSettings.apply {
                // User-Agent от Amazon Fire TV Stick 4K (Cobalt)
                customUserAgentString = tvUA
                isJavaScriptEnabled = true

                androidWebSettings.apply {
                    //isDebugInspectorInfoEnabled = true
                    useWideViewPort = true
                    domStorageEnabled = true
                }
            }

            webView.apply {

                // Включаем WebChromeClient для корректной обработки JS-событий формы Google
                webChromeClient = WebChromeClient()

                // Динамически переключаем User-Agent на время входа в Google Аккаунт
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        if (url != null && url.contains("accounts.google.com")) {
                            view?.settings?.userAgentString = loginUA
                        } else {
                            view?.settings?.userAgentString = tvUA
                        }
                    }
                }

                // Bridges the exit button click on the website to handle it natively.
                addJavascriptInterface(ExitBridge(exitTrigger), "ExitBridge")

                /*
                Youtube's content security policy doesn't allow calling fetch on
                3rd party websites (eg. SponsorBlock api). This bridge counters that
                handling the requests on the native side. */
                addJavascriptInterface(NetworkBridge(navigator), "NetworkBridge")

                // Enables hardware acceleration
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                // Set the zoom to 25% to fit the screen. Side-effect of viewport spoofing.
                setInitialScale(25)

                // Hide scrollbars
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
            }
        }
    )
}
