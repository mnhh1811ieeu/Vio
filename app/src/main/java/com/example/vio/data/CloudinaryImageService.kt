package com.example.vio.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.vio.BuildConfig
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread
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

        thread {
            val ctxApp = context.applicationContext
            val compressedUri = compressImage(ctxApp, imageUri)

            MediaManager.get().upload(compressedUri)
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

    private fun compressImage(context: Context, uri: Uri): Uri {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return uri
            input.use {
                val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val bitmap = BitmapFactory.decodeStream(it, null, options) ?: return uri

                val (w, h) = bitmap.width to bitmap.height
                val maxSide = 1024
                val scale = if (w > h) maxSide.toFloat() / w else maxSide.toFloat() / h
                val resized = if (scale < 1f) {
                    Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
                } else bitmap

                val outFile = File.createTempFile("avatar_upload_", ".jpg", context.cacheDir)
                FileOutputStream(outFile).use { fos ->
                    resized.compress(Bitmap.CompressFormat.JPEG, 80, fos)
                }
                if (resized !== bitmap) resized.recycle()
                bitmap.recycle()
                Uri.fromFile(outFile)
            }
        } catch (_: Exception) {
            uri
        }
    }
}
