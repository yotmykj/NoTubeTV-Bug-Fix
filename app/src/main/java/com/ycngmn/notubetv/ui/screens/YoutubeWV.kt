package com.ycngmn.notubetv.ui.screens

import android.app.Activity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import com.ycngmn.notubetv.R
import com.ycngmn.notubetv.ui.YoutubeVM
import com.ycngmn.notubetv.ui.components.SettingsDialog
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

    // Состояния для всплывающего меню настроек
    var showSettings by remember { mutableStateOf(false) }
    var isWelcomeShown by remember { mutableStateOf(false) }

    // Перехват кнопки "Назад" с пульта ТВ
    BackHandler {
        when {
            // Если открыто диалоговое окно настроек — закрываем его
            showSettings -> {
                showSettings = false
            }
            // 1. Если у WebView есть обычная история переходов
            navigator.canGoBack -> {
                navigator.navigateBack()
            }
            // 2. Если страница загружена — отправляем событие Escape в YouTube TV
            state.loadingState is LoadingState.Finished -> {
                val backPressScript = """
                    (function() {
                        function dispatchKey(key, keyCode, code) {
                            const downEvent = new KeyboardEvent('keydown', {
                                key: key, keyCode: keyCode, code: code, which: keyCode,
                                bubbles: true, cancelable: true
                            });
                            const upEvent = new KeyboardEvent('keyup', {
                                key: key, keyCode: keyCode, code: code, which: keyCode,
                                bubbles: true, cancelable: true
                            });
                            document.dispatchEvent(downEvent);
                            document.dispatchEvent(upEvent);
                        }
                        dispatchKey('Escape', 27, 'Escape');
                    })();
                """.trimIndent()

                navigator.evaluateJavaScript(backPressScript)
            }
            // 3. В остальных случаях закрываем приложение
            else -> {
                exitTrigger.value = true
            }
        }
    }

    // Загрузка внешних скриптов и обновлений при старте
    LaunchedEffect(Unit) {
        youtubeVM.setScript(fetchScripts())
        getUpdate(context, navigator) { update ->
            if (update != null) youtubeVM.setUpdate(update)
        }
    }

    // Выполняется один раз после полной загрузки страницы
    if (loadingState == LoadingState.Finished) {

        // Приветственное сообщение при первом входе в сессию
        if (!isWelcomeShown) {
            Toast.makeText(context, "Добро пожаловать в NoTube TV! 🚀", Toast.LENGTH_LONG).show()
            isWelcomeShown = true
        }

        // 1. Внешний JS из ViewModel
        if (jsScript != null) {
            navigator.evaluateJavaScript(jsScript)
        }

        // 2. Локальный блокировщик рекламы из res/raw/adblock.js
        try {
            val adblockJs = readRaw(context, R.raw.adblock)
            if (adblockJs.isNotEmpty()) {
                navigator.evaluateJavaScript(adblockJs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. SponsorBlock из res/raw/sponsorblock.js
        try {
            val sponsorBlockJs = readRaw(context, R.raw.sponsorblock)
            if (sponsorBlockJs.isNotEmpty()) {
                navigator.evaluateJavaScript(sponsorBlockJs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Кастомные CSS стили (прозрачность UI)
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

    if (updateData != null) UpdateDialog(updateData, navigator)
    if (showSettings) SettingsDialog(navigator = navigator, onDismiss = { showSettings = false })
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
