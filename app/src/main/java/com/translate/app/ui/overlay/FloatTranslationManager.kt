package com.translate.app.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.translate.app.data.model.TranslationResult

/**
 * 悬浮翻译窗口管理器
 * 负责在系统窗口层显示翻译结果浮窗
 */
class FloatTranslationManager(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var floatingView: FrameLayout? = null

    // 当前翻译状态
    var translationResult by mutableStateOf<TranslationResult?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isShowing by mutableStateOf(false)
        private set

    /**
     * 显示悬浮翻译窗口
     */
    fun showTranslation(
        originalText: String,
        translatedText: String,
        fromLanguage: String = "auto",
        toLanguage: String = "zh"
    ) {
        val result = TranslationResult(
            originalText = originalText,
            translatedText = translatedText,
            fromLanguage = fromLanguage,
            toLanguage = toLanguage
        )
        translationResult = result
        isLoading = false
        errorMessage = null
        showWindow()
    }

    /**
     * 显示加载状态
     */
    fun showLoading(text: String) {
        isLoading = true
        errorMessage = null
        // 如果窗口已显示，更新状态即可
        if (isShowing) return
        showWindow()
    }

    /**
     * 显示错误信息
     */
    fun showError(error: String) {
        errorMessage = error
        isLoading = false
        translationResult = null
        showWindow()
    }

    /**
     * 创建并显示悬浮窗口
     */
    private fun showWindow() {
        if (floatingView != null) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 100 // 顶部偏移
        }

        floatingView = FrameLayout(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        try {
            windowManager.addView(floatingView, layoutParams)
            isShowing = true

            // 使用 Compose 填充内容
            FloatTranslationComposable(
                context = context,
                container = floatingView!!,
                manager = this
            )
        } catch (e: SecurityException) {
            errorMessage = "需要悬浮窗权限"
        } catch (e: Exception) {
            errorMessage = "显示悬浮窗失败"
        }
    }

    /**
     * 关闭悬浮窗口
     */
    fun dismiss() {
        floatingView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: IllegalArgumentException) {
                // 视图已移除，忽略
            }
        }
        floatingView = null
        isShowing = false
        translationResult = null
        isLoading = false
        errorMessage = null
    }

    /**
     * 检查是否拥有悬浮窗权限
     */
    fun hasOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return android.provider.Settings.canDrawOverlays(context)
        }
        return true
    }
}
