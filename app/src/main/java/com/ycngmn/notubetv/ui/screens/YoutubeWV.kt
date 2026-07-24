package com.ycngmn.notubetv.ui.screens

import android.app.Activity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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

    var showSplash by remember { mutableStateOf(true) }
    var countdown by remember { mutableStateOf(5) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var isEnglish by remember { mutableStateOf(false) }

    var adBlockEnabled by remember { mutableStateOf(true) }
    var sponsorBlockEnabled by remember { mutableStateOf(true) }
    var sponsorAutoSkip by remember { mutableStateOf(true) }
    var sponsorIntroSkip by remember { mutableStateOf(true) }
    var updateCheckEnabled by remember { mutableStateOf(true) }

    val firstItemFocusRequester = remember { FocusRequester() }
    val aboutBackFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showSettings) {
        if (showSettings && !showAbout) {
            delay(100)
            try {
                firstItemFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(showAbout) {
        if (showAbout) {
            delay(100)
            try {
                aboutBackFocusRequester.requestFocus()
            } catch (e: Exception) {}
        }
    }

    BackHandler {
        when {
            showAbout -> {
                showAbout = false
                showSettings = true
            }
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

    LaunchedEffect(Unit) {
        youtubeVM.setScript(fetchScripts())
        getUpdate(context, navigator) { update ->
            if (update != null) youtubeVM.setUpdate(update)
        }

        for (i in 5 downTo 1) {
            if (!showSplash || showSettings || showAbout) break
            countdown = i
            delay(1000)
        }
        if (showSplash && !showSettings && !showAbout) {
            showSplash = false
        }
    }

    LaunchedEffect(loadingState, adBlockEnabled, sponsorBlockEnabled) {
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
    }

    if (updateData != null && updateCheckEnabled) UpdateDialog(updateData, navigator)
    if (exitTrigger.value) activity.finish()

    val loading = state.loadingState as? LoadingState.Loading
    if (loading != null && !showSplash && !showSettings && !showAbout) SplashLoading(loading.progress)

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

        // 1. Экран загрузки
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
                        text = "NoTube TV by manas",
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

        // 2. Экран настроек
        if (showSettings && !showAbout) {
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
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        CheckboxItem(
                            text = "Блокировщик рекламы (AdBlock + Shorts)",
                            checked = adBlockEnabled,
                            modifier = Modifier.focusRequester(firstItemFocusRequester)
                        ) { adBlockEnabled = it }
                        
                        CheckboxItem(text = "Включить SponsorBlock", checked = sponsorBlockEnabled) { sponsorBlockEnabled = it }
                        CheckboxItem(text = "Авто-пропуск спонсорских вставок", checked = sponsorAutoSkip) { sponsorAutoSkip = it }
                        CheckboxItem(text = "Пропуск интро / паузы / подписок", checked = sponsorIntroSkip) { sponsorIntroSkip = it }
                        CheckboxItem(text = "Проверка обновлений", checked = updateCheckEnabled) { updateCheckEnabled = it }
                    }

                    MenuButton(text = "О программе / About") {
                        showSettings = false
                        showAbout = true
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showSettings = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .width(460.dp)
                            .height(48.dp)
                    ) {
                        Text(text = "Запустить YouTube", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Отдельный экран "О программе" (About) с поддержкой RU / EN
        if (showAbout) {
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
                    Row(
                        modifier = Modifier.width(520.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEnglish) "About NoTube TV" else "О программе",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Кнопка переключения языка
                        MenuButton(
                            text = if (isEnglish) "🌐 RU" else "🌐 EN",
                            modifier = Modifier.width(100.dp)
                        ) {
                            isEnglish = !isEnglish
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Box(
                        modifier = Modifier
                            .width(520.dp)
                            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "NoTube TV by manas",
                                color = Color(0xFFFF0000),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = if (isEnglish) 
                                    "About App:\nNoTube TV is a modified YouTube TV client for Android TV designed to provide a seamless viewing experience with built-in AdBlock, Shorts ad filtration, and SponsorBlock.\n\nWhy I made it:\nI created this application because I wanted to enjoy YouTube on the big screen without intrusive commercials, sponsored segments, and annoying shorts ads, giving full freedom and control to users."
                                else 
                                    "О программе:\nNoTube TV — это модифицированный клиент YouTube TV для Android TV, созданный для комфортного просмотра видео без навязчивой рекламы, роликов в Shorts и спонсорских интеграций.\n\nПочему я это сделал:\nЯ создал это приложение, потому что мне хотелось смотреть любимый контент на большом экране телевизора без рекламных пауз, отвлекающих вставок и лишнего мусора, обеспечив максимальное удобство и полный контроль.",
                                color = Color.LightGray,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 14.dp)
                            )
                            Text(
                                text = "Developer: manas\nEmail: saparbektv@gmail.com",
                                color = Color(0xFF00D46A),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    MenuButton(
                        text = if (isEnglish) "Back to Settings" else "Назад в настройки",
                        modifier = Modifier.focusRequester(aboutBackFocusRequester)
                    ) {
                        showAbout = false
                        showSettings = true
                    }
                }
            }
        }
    }
}

@Composable
fun CheckboxItem(
    text: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .width(460.dp)
            .background(
                color = if (isFocused) Color(0xFF333333) else Color(0xFF1E1E1E),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFFCC0000) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    onCheckedChange(!checked)
                    true
                } else {
                    false
                }
            }
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = if (checked) "☑ $text" else "☐ $text",
            color = Color.White,
            fontSize = 15.sp
        )
    }
}

@Composable
fun MenuButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .width(460.dp)
            .background(
                color = if (isFocused) Color(0xFF333333) else Color(0xFF1E1E1E),
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color(0xFFCC0000) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionCenter || event.key == Key.Enter)) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
