package com.example.vio.api

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

// Model khớp với JSON trả về từ Python (API Phân tích ảnh)
data class AnalyzeResponse(
    val status: String,
    val analysis: String?,
    val error: String?
)

// [MỚI] Model request để gửi thông báo (khớp với body JSON Python yêu cầu)
data class PushNotificationRequest(
    val userId: String,
    val title: String,
    val body: String
)

interface ApiService {
    // 1. API Phân tích ảnh (Giữ nguyên)
    @Multipart
    @POST("analyze-image")
    fun analyzeImage(@Part image: MultipartBody.Part): Call<AnalyzeResponse>

    // 2. [MỚI] API Gửi thông báo (Backend Python)
    // Đường dẫn khớp với @notifications_bp.route("/notifications/push")
    @POST("notifications/push")
    suspend fun sendPushNotification(@Body request: PushNotificationRequest): Response<Any>
}

object RetrofitClient {
    // ⚠️ QUAN TRỌNG: Đảm bảo IP này đúng với máy tính chạy Python Server
    private const val BASE_URL = "http://192.168.1.7:5000/api/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val instance: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}