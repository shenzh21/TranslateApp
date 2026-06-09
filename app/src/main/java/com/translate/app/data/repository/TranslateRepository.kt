package com.translate.app.data.repository

import com.translate.app.data.api.BaiduTranslateService
import com.translate.app.data.api.SignUtil
import com.translate.app.data.model.TranslationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 翻译仓库 - 封装翻译 API 调用逻辑
 */
class TranslateRepository(
    private val baiduService: BaiduTranslateService
) {
    /**
     * 使用百度翻译 API 翻译文本
     * @param query 待翻译文本
     * @param appId 百度 APP ID（由用户配置）
     * @param secretKey 百度密钥（由用户配置）
     * @param from 源语言代码 (默认 auto)
     * @param to 目标语言代码 (默认 zh)
     * @return TranslationResult
     */
    suspend fun translate(
        query: String,
        appId: String,
        secretKey: String,
        from: String = "auto",
        to: String = "zh"
    ): Result<TranslationResult> = withContext(Dispatchers.IO) {
        try {
            // 生成签名参数
            val salt = UUID.randomUUID().toString().replace("-", "").take(10)
            val sign = SignUtil.generateSign(
                appId = appId,
                query = query,
                salt = salt,
                secretKey = secretKey
            )

            // 调用百度翻译 API
            val response = baiduService.translate(
                query = query,
                from = from,
                to = to,
                appId = appId,
                salt = salt,
                sign = sign
            )

            // 解析结果
            val transResult = response.transResult
            if (transResult.isNullOrEmpty()) {
                Result.failure(Exception("翻译结果为空"))
            } else {
                val translatedText = transResult.joinToString("\n") { it.dst }
                Result.success(
                    TranslationResult(
                        originalText = query,
                        translatedText = translatedText,
                        fromLanguage = response.from,
                        toLanguage = response.to
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
