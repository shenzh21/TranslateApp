package com.translate.app.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * API 客户端 - 提供 Retrofit 实例
 */
object ApiClient {

    private const val BAIDU_BASE_URL = "https://api.fanyi.baidu.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BAIDU_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val baiduTranslateService: BaiduTranslateService =
        retrofit.create(BaiduTranslateService::class.java)

    val translateRepository: com.translate.app.data.repository.TranslateRepository
        get() = com.translate.app.data.repository.TranslateRepository(baiduTranslateService)
}
