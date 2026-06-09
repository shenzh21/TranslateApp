package com.translate.app.data

import android.content.Context

/**
 * 百度翻译 API 凭据管理器
 * 将 APP ID 和密钥存储在 SharedPreferences 中，由用户自行填写。
 */
object CredentialManager {

    private const val PREFS_NAME = "baidu_credentials"
    private const val KEY_APP_ID = "app_id"
    private const val KEY_SECRET = "secret_key"

    fun getAppId(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_ID, "") ?: ""
    }

    fun getSecretKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SECRET, "") ?: ""
    }

    fun save(context: Context, appId: String, secretKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_ID, appId)
            .putString(KEY_SECRET, secretKey)
            .apply()
    }

    /** 检查凭据是否已配置 */
    fun isConfigured(context: Context): Boolean {
        return getAppId(context).isNotBlank() && getSecretKey(context).isNotBlank()
    }
}
