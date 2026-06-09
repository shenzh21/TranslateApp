package com.translate.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.translate.app.App
import com.translate.app.MainActivity
import com.translate.app.R
import com.translate.app.data.CredentialManager
import com.translate.app.data.api.ApiClient
import com.translate.app.ui.overlay.FloatTranslationManager
import kotlinx.coroutines.*

/**
 * 剪贴板监听服务
 * 在后台监听剪贴板变化，自动弹出翻译悬浮窗
 */
class ClipboardMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var clipboardManager: ClipboardManager
    private var lastClipText: String? = null

    // 用于去重的上次翻译文本
    private var lastTranslatedText: String? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipboardChange()
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        clipboardManager.addPrimaryClipChangedListener(clipListener)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleClipboardChange() {
        val clip = clipboardManager.primaryClip ?: return
        if (clip.itemCount == 0) return

        val text = clip.getItemAt(0).text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        // 去重
        if (text == lastClipText) return
        lastClipText = text

        // 忽略太短或太长的文本
        if (text.length < 2 || text.length > 5000) return

        // 如果和上次翻译文本相同，忽略
        if (text == lastTranslatedText) return
        lastTranslatedText = text

        // 显示悬浮翻译
        showFloatingTranslation(text)
    }

    private fun showFloatingTranslation(text: String) {
        val floatManager = FloatTranslationManager(this)
        // 检查权限
        if (!floatManager.hasOverlayPermission()) return

        floatManager.showLoading(text)

        val appId = CredentialManager.getAppId(this)
        val secretKey = CredentialManager.getSecretKey(this)
        if (appId.isBlank() || secretKey.isBlank()) return

        serviceScope.launch {
            val result = ApiClient.translateRepository.translate(
                query = text,
                appId = appId,
                secretKey = secretKey
            )
            result.onSuccess { translation ->
                floatManager.showTranslation(
                    originalText = translation.originalText,
                    translatedText = translation.translatedText,
                    fromLanguage = translation.fromLanguage,
                    toLanguage = translation.toLanguage
                )
            }.onFailure { error ->
                floatManager.showError("翻译失败：${error.message ?: "未知错误"}")
            }

            // 延迟自动关闭
            delay(8000)
            floatManager.dismiss()
        }
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, App.CLIPBOARD_CHANNEL_ID)
            .setContentTitle("划词翻译")
            .setContentText("正在监听剪贴板…")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ClipboardMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClipboardMonitorService::class.java))
        }
    }
}
