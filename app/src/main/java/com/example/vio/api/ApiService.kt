package com.example.vio.api

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.util.concurrent.TimeUnit

// Model khớp với JSON trả về từ Python
data class AnalyzeResponse(
    val status: String,
    val analysis: String?,
    val error: String?
)

interface ApiService {
    // Đường dẫn này phải khớp với @data_bp.route('/analyze-image') trong Python
    @Multipart
    @POST("analyze-image")
    fun analyzeImage(@Part image: MultipartBody.Part): Call<AnalyzeResponse>
}

object RetrofitClient {
    // ⚠️ QUAN TRỌNG: Thay đổi IP này thành IPv4 máy tính của bạn
    // Chạy lệnh 'ipconfig' trên CMD để lấy IPv4 (ví dụ: 192.168.1.6)
    private const val BASE_URL = "http://192.168.1.4:5000/api/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS) // Gemini xử lý ảnh lâu, cần timeout cao
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