package com.example.vio.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.vio.MainActivity
import com.example.vio.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // 1. Hàm này được gọi tự động khi FCM tạo token mới cho thiết bị
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        saveTokenToDatabase(token)
    }

    // 2. Hàm này được gọi khi nhận tin nhắn từ Server
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Lấy thông tin từ thông báo (Notification payload)
        val title = remoteMessage.notification?.title ?: "Vio"
        val body = remoteMessage.notification?.body ?: "Bạn có tin nhắn mới"

        showNotification(title, body)
    }

    // Lưu token lên Firebase Realtime Database để Backend biết gửi cho ai
    private fun saveTokenToDatabase(token: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            FirebaseDatabase.getInstance().getReference("users")
                .child(currentUser.uid)
                .child("fcmToken")
                .setValue(token)
        }
    }

    // Hiển thị thông báo trên thanh trạng thái
    private fun showNotification(title: String, messageBody: String) {
        // Khi bấm vào thông báo sẽ mở MainActivity
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        // Cờ FLAG_IMMUTABLE bắt buộc cho Android 12 trở lên
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "vio_notification_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Tạo Channel (Bắt buộc cho Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "Tin nhắn Vio"
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Xây dựng giao diện thông báo
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            // Thay icon này bằng icon app của bạn nếu muốn (ví dụ R.drawable.ic_logo)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true) // Tự đóng thông báo khi click vào
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Hiển thị thông báo (ID random để không bị đè thông báo cũ)
        notificationManager.notify(Random.nextInt(), notificationBuilder.build())
    }
}