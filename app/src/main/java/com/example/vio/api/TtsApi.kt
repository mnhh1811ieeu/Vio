package com.example.vio.api // Package mới tạo

import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// 1. Định nghĩa Interface gọi lên Server Python
interface TtsApiService {
    // Gọi vào đường dẫn http://IP:PORT/api/tts/speak mà bạn đã viết ở backend Flask
    @POST("api/tts/speak")
    suspend fun getTtsAudio(@Body body: Map<String, String>): ResponseBody
}

// 2. Tạo Object Singleton để dùng chung cho toàn app
object RetrofitClient {
    // Thay đổi IP cũ thành IP máy tính hiện tại của bạn
    // Lưu ý: Đừng quên dấu / ở cuối
    private const val BASE_URL = "http://192.168.1.7:5000/"

    val instance: TtsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TtsApiService::class.java)
    }
}