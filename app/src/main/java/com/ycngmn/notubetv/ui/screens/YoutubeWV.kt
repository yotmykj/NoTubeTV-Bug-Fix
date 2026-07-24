package com.ycngmn.notubetv.ui.screens

import android.app.Activity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import kotlinx.coroutines.delay

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

    // Состояния
    var showSplash by remember { mutableStateOf(true) }
    var countdown by remember { mutableStateOf(5) }
    var showSettings by remember { mutableStateOf(false) }

    // Настоящие настройки вместо выбора 1080p
    var adBlockEnabled by remember { mutableStateOf(true) }
    var sponsorBlockEnabled by remember { mutableStateOf(true) }
    var updateCheckEnabled by remember { mutableStateOf(true) }

    // Логика кнопки "Назад":
    // 1. Во время 5-секундного сплеш-экрана нажатие Back открывает настройки.
    // 2. Внутри настроек нажатие Back закрывает их и запускает YouTube.
    // 3. Внутри YouTube кнопка Back работает штатно (история назад / отправка Escape / выход).
    BackHandler {
        when {
            showSplash -> {
                showSplash = false
                showSettings = true
            }
            showSettings -> {
                showSettings = false
            }
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

    // Таймер обратного отчета на 5 секунд при старте
    LaunchedEffect(Unit) {
        youtubeVM.setScript(fetchScripts())
        getUpdate(context, navigator) { update ->
            if (update != null) youtubeVM.setUpdate(update)
        }

        for (i in 5 downTo 1) {
            if (!showSplash || showSettings) break
            countdown = i
            delay(1000)
        }
        if (showSplash && !showSettings) {
            showSplash = false
        }
    }

    if (loadingState is LoadingState.Finished) {
        if (jsScript != null) {
            navigator.evaluateJavaScript(jsScript)
        }

        if (adBlockEnabled) {
            val adblockJs = readRaw(context, R.raw.adblock)
            if (adblockJs.isNotEmpty()) {
                navigator.evaluateJavaScript(adblockJs)
            }
        }

        if (sponsorBlockEnabled) {
            val sponsorBlockJs = readRaw(context, R.raw.sponsorblock)
            if (sponsorBlockJs.isNotEmpty()) {
                navigator.evaluateJavaScript(sponsorBlockJs)
            }
        }
    }

    if (updateData != null) UpdateDialog(updateData, navigator)
    if (exitTrigger.value) activity.finish()

    Box(modifier = Modifier.fillMaxSize()) {
        // Основной WebView с YouTube TV
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

        // 1. 5-секундный экран загрузки при старте
        if (showSplash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D0D0D)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "NoTube TV",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "Запуск сайта через $countdown сек...",
                        color = Color.Gray,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    Text(
                        text = "Для настроек нажмите кнопку Back",
                        color = Color(0xFFFF0000),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 2. Экран настроек (AdBlock, SponsorBlock)
        if (showSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE60D0D0D)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Настройки NoTube TV",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 32.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 40.dp)
                    ) {
                        CheckboxItem(text = "Блокировщик рекламы (AdBlock)", checked = adBlockEnabled) { adBlockEnabled = it }
                        CheckboxItem(text = "Пропуск спонсоров (SponsorBlock)", checked = sponsorBlockEnabled) { sponsorBlockEnabled = it }
                        CheckboxItem(text = "Проверка обновлений", checked = updateCheckEnabled) { updateCheckEnabled = it }
                    }

                    Button(
                        onClick = { showSettings = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.width(220.dp).height(50.dp)
                    ) {
                        Text(text = "Запустить YouTube", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CheckboxItem(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(420.dp)
            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = if (checked) "☑ $text" else "☐ $text",
            color = Color.White,
            fontSize = 16.sp
        )
    }
}
