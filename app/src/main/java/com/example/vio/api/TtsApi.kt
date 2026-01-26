package com.example.vio.api

import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface TtsApiService {
    @POST("api/tts/speak")
    suspend fun getTtsAudio(@Body body: Map<String, String>): ResponseBody
}

// ĐỔI TÊN Ở ĐÂY: RetrofitClient -> TtsClient
object TtsClient {
    // Thay đổi IP này thành IP máy tính hiện tại của bạn (Ví dụ: 192.168.1.6)
    private const val BASE_URL = "http://192.168.1.4:5000/"

    val instance: TtsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TtsApiService::class.java)
    }
}