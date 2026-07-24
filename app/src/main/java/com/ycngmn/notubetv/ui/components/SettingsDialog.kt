package com.ycngmn.notubetv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.multiplatform.webview.web.WebViewNavigator

@Composable
fun SettingsDialog(
    navigator: WebViewNavigator,
    onDismiss: () -> Unit
) {
    var sbEnabled by remember { mutableStateOf(true) }
    var skipSponsors by remember { mutableStateOf(true) }
    var skipIntros by remember { mutableStateOf(true) }
    var showToasts by remember { mutableStateOf(true) }

    fun applySettings() {
        val jsonSettings = """
            {
                "enabled": $sbEnabled,
                "skipSponsor": $skipSponsors,
                "skipIntro": $skipIntros,
                "showToast": $showToasts
            }
        """.trimIndent()

        navigator.evaluateJavaScript("if (window.updateSponsorBlockSettings) { window.updateSponsorBlockSettings('$jsonSettings'); }")
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier
                .width(420.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "⚙️ Настройки NoTube TV",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Тумблер SponsorBlock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Включить SponsorBlock", color = Color.White, fontSize = 16.sp)
                    Switch(
                        checked = sbEnabled,
                        onCheckedChange = {
                            sbEnabled = it
                            applySettings()
                        }
                    )
                }

                // Всплывающие уведомления
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Показывать уведомления", color = Color.White, fontSize = 16.sp)
                    Switch(
                        checked = showToasts,
                        enabled = sbEnabled,
                        onCheckedChange = {
                            showToasts = it
                            applySettings()
                        }
                    )
                }

                // Пропуск интеграций
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Пропускать спонсоров", color = Color.White, fontSize = 16.sp)
                    Switch(
                        checked = skipSponsors,
                        enabled = sbEnabled,
                        onCheckedChange = {
                            skipSponsors = it
                            applySettings()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))
                ) {
                    Text("Закрыть", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
