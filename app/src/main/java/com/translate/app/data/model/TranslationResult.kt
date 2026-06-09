package com.translate.app.data.model

import com.google.gson.annotations.SerializedName

/**
 * 百度翻译 API 响应
 */
data class BaiduTranslationResponse(
    @SerializedName("from") val from: String,
    @SerializedName("to") val to: String,
    @SerializedName("trans_result") val transResult: List<BaiduTransResult>?
)

data class BaiduTransResult(
    @SerializedName("src") val src: String,
    @SerializedName("dst") val dst: String
)

/**
 * 统一的翻译结果
 */
data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val fromLanguage: String = "auto",
    val toLanguage: String = "zh",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 语言代码映射
 */
enum class Language(val code: String, val displayName: String) {
    AUTO("auto", "自动检测"),
    ZH("zh", "中文"),
    EN("en", "英文"),
    JP("jp", "日语"),
    KOR("kor", "韩语"),
    FRA("fra", "法语"),
    DE("de", "德语"),
    RU("ru", "俄语"),
    SPA("spa", "西班牙语");
}
