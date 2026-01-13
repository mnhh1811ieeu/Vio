package com.example.vio.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.vio.BuildConfig
import kotlin.concurrent.thread

object CloudinaryImageService {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun ensureInitialized(context: Context): Boolean {
        val alreadyInit = try {
            MediaManager.get()
            true
        } catch (_: IllegalStateException) {
            false
        }
        if (alreadyInit) return true

        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
        val apiKey = BuildConfig.CLOUDINARY_API_KEY
        val apiSecret = BuildConfig.CLOUDINARY_API_SECRET

        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            return false
        }

        val config = mapOf(
            "cloud_name" to cloudName,
            "api_key" to apiKey,
            "api_secret" to apiSecret,
            "secure" to true
        )
        MediaManager.init(context.applicationContext, config)
        return true
    }

    fun uploadAvatar(
        context: Context,
        uid: String,
        imageUri: Uri,
        onResult: (Result<String>) -> Unit
    ) {
        if (!ensureInitialized(context)) {
            onResult(Result.failure(IllegalStateException("Cloudinary not configured")))
            return
        }

        MediaManager.get().upload(imageUri)
            .option("resource_type", "image")
            .option("public_id", "avatars/$uid")
            .option("overwrite", true)
            .option("invalidate", true)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}

                override fun onError(requestId: String?, error: ErrorInfo?) {
                    mainHandler.post {
                        onResult(Result.failure(IllegalStateException(error?.description ?: "Upload failed")))
                    }
                }

                override fun onSuccess(
                    requestId: String?,
                    resultData: MutableMap<Any?, Any?>?
                ) {
                    val url = resultData?.get("secure_url") as? String
                    mainHandler.post {
                        if (url.isNullOrBlank()) {
                            onResult(Result.failure(IllegalStateException("Missing secure_url")))
                        } else {
                            onResult(Result.success(url))
                        }
                    }
                }
            })
            .dispatch()
    }

    fun deleteAvatar(
        context: Context,
        uid: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        thread {
            if (!ensureInitialized(context)) {
                postResult(onResult, Result.failure(IllegalStateException("Cloudinary not configured")))
                return@thread
            }
            try {
                MediaManager.get()
                    .cloudinary
                    .uploader()
                    .destroy("avatars/$uid", mapOf("invalidate" to true))
                postResult(onResult, Result.success(Unit))
            } catch (e: Exception) {
                postResult(onResult, Result.failure(e))
            }
        }
    }

    private fun postResult(cb: (Result<Unit>) -> Unit, result: Result<Unit>) {
        mainHandler.post { cb(result) }
    }
}
