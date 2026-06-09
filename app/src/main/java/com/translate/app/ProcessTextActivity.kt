package com.translate.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.translate.app.data.CredentialManager
import com.translate.app.data.api.ApiClient
import com.translate.app.data.model.TranslationResult
import com.translate.app.ui.theme.TranslateAppTheme
import kotlinx.coroutines.*

/**
 * 翻译结果浮窗 Activity
 *
 * 以透明 Activity 形式呈现，Activity 本身就是浮窗，
 * 不需要 SYSTEM_ALERT_WINDOW 权限，不会崩溃。
 *
 * 工作方式：
 * 1. 接收文本（来自 PROCESS_TEXT 或悬浮球服务）
 * 2. 在 Compose UI 中直接显示加载 → 翻译结果
 * 3. 自动 8 秒后关闭，点击外部区域也可关闭
 */
class ProcessTextActivity : ComponentActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 让窗口铺满屏幕但不显示状态栏/导航栏装饰
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        val text = extractText(intent)
        if (text.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            TranslateAppTheme(dynamicColor = false) {
                TranslationPopup(
                    text = text,
                    onDismiss = { finish() },
                    onCopy = { copiedText ->
                        copyToClipboard(copiedText)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun extractText(intent: Intent?): String? {
        return intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("translation", text))
    }
}

// ========================================================================
//  Compose 翻译浮窗 UI
// ========================================================================

/**
 * 翻译浮窗 UI。
 * 顶部圆角卡片：原文 → 译文，支持复制。
 * 卡片外区域点击可关闭浮窗。
 */
@Composable
private fun TranslationPopup(
    text: String,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit
) {
    // 状态
    var isLoading by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<TranslationResult?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val ctx = LocalContext.current

    // 执行翻译
    LaunchedEffect(text) {
        val appId = CredentialManager.getAppId(ctx)
        val secretKey = CredentialManager.getSecretKey(ctx)

        if (appId.isBlank() || secretKey.isBlank()) {
            errorMsg = "请先打开 App 设置页面配置百度翻译 APP ID 和密钥"
            isLoading = false
            delay(4000)
            onDismiss()
            return@LaunchedEffect
        }

        val r = ApiClient.translateRepository.translate(
            query = text,
            appId = appId,
            secretKey = secretKey
        )
        r.onSuccess { t ->
            result = t
            isLoading = false
            delay(8000)
            onDismiss()
        }.onFailure { e ->
            errorMsg = when {
                e.message?.contains("Unable to resolve host") == true -> "网络连接失败"
                e.message?.contains("timeout") == true -> "网络请求超时"
                else -> "翻译失败：${e.message ?: "未知错误"}"
            }
            isLoading = false
            delay(5000)
            onDismiss()
        }
    }

    // 全屏背景（透明可点击区域）
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // 顶部翻译卡片
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 12.dp, end = 12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // 标题栏
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "翻译",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 内容
                    when {
                        isLoading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 16.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Text("翻译中…", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        errorMsg != null -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    errorMsg!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        result != null -> {
                            val r = result!!

                            // 原文
                            Text(
                                text = r.originalText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(8.dp))

                            // 译文
                            Text(
                                text = r.translatedText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(Modifier.height(12.dp))

                            // 操作按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { onCopy(r.translatedText) }) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("复制译文", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
