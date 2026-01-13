package com.example.vio

import android.app.Application
import com.cloudinary.android.MediaManager

class VioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
        val apiKey = BuildConfig.CLOUDINARY_API_KEY
        val apiSecret = BuildConfig.CLOUDINARY_API_SECRET
        if (cloudName.isNotBlank() && apiKey.isNotBlank() && apiSecret.isNotBlank()) {
            val config = mapOf(
                "cloud_name" to cloudName,
                "api_key" to apiKey,
                "api_secret" to apiSecret,
                "secure" to true
            )
            MediaManager.init(this, config)
        }
    }
}
