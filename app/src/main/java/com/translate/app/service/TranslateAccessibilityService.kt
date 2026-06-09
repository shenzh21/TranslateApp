package com.translate.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍服务
 *
 * 监听全局文本选中事件，记录用户选中的文字。
 * 当用户点击悬浮球时，FloatingBubbleService 会读取此服务捕获的文本，
 * 实现"选中 → 点击悬浮球 → 翻译"的无缝体验。
 *
 * 用户需要先在 系统设置 → 无障碍 → 已安装的应用 → 划词翻译 中开启此服务。
 */
class TranslateAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        val config = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = config
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                captureSelectedText(event)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                // 部分浏览器可能只发 TEXT_CHANGED，也尝试捕获
                if (lastSelectedText == null) {
                    captureSelectedText(event)
                }
            }
        }
    }

    override fun onInterrupt() {}

    /**
     * 从事件中提取选中的文本
     */
    private fun captureSelectedText(event: AccessibilityEvent) {
        // 方法1：从事件的文本列表中获取（部分应用会在这里放选中的文本）
        if (event.text.isNotEmpty()) {
            val fullText = event.text.joinToString("")
            if (fullText.isNotBlank()) {
                val fromIndex = event.fromIndex.coerceAtLeast(0)
                val toIndex = event.toIndex.coerceAtLeast(0)
                if (toIndex > fromIndex && toIndex <= fullText.length) {
                    lastSelectedText = fullText.substring(fromIndex, toIndex)
                    return
                }
            }
        }

        // 方法2：通过根节点查找当前选中文本
        val source = event.source ?: return
        val selectedText = extractSelectedTextFromNode(source)
        if (!selectedText.isNullOrBlank()) {
            lastSelectedText = selectedText
        }
        source.recycle()
    }

    /**
     * 从节点中提取选中文本（递归查找）
     */
    private fun extractSelectedTextFromNode(node: AccessibilityNodeInfo): String? {
        // 检查当前节点是否有选中文本
        if (node.isPassword) return null

        val text = node.text?.toString()
        val selectionStart = node.textSelectionStart
        val selectionEnd = node.textSelectionEnd

        if (text != null && selectionStart >= 0 && selectionEnd > selectionStart && selectionEnd <= text.length) {
            return text.substring(selectionStart, selectionEnd)
        }

        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = extractSelectedTextFromNode(child)
            child.recycle()
            if (!result.isNullOrBlank()) return result
        }

        return null
    }

    /**
     * 主动获取当前选中文本（由外部调用）
     */
    fun fetchCurrentSelectedText(): String? {
        val root = rootInActiveWindow ?: return null
        val text = extractSelectedTextFromNode(root)
        root.recycle()
        return text
    }

    companion object {
        /** 最近一次捕获的选中文本 */
        @Volatile
        var lastSelectedText: String? = null
            private set

        private var instance: TranslateAccessibilityService? = null

        /**
         * 获取当前选中的文本
         * 先尝试静态缓存，再尝试实时抓取
         */
        fun getSelectedText(): String? {
            // 优先用缓存的文本
            val cached = lastSelectedText
            if (!cached.isNullOrBlank()) return cached

            // 缓存为空，尝试实时抓取
            return try {
                instance?.fetchCurrentSelectedText()
            } catch (_: Exception) {
                null
            }
        }
    }
}
