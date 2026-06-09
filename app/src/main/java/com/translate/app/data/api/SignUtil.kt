package com.translate.app.data.api

import java.security.MessageDigest

/**
 * 百度翻译签名工具
 * 签名生成方式: MD5(appid + q + salt + 密钥)
 */
object SignUtil {

    /**
     * 生成百度翻译 API 签名
     * @param appId 百度 APP ID
     * @param query 待翻译文本
     * @param salt 随机数
     * @param secretKey 密钥
     * @return MD5 签名
     */
    fun generateSign(appId: String, query: String, salt: String, secretKey: String): String {
        val raw = appId + query + salt + secretKey
        return MessageDigest.getInstance("MD5")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
