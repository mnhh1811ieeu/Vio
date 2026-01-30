package com.example.vio

import com.google.firebase.database.Exclude
import com.google.firebase.database.PropertyName

data class User(
    val uid: String = "",
    val name: String = "",

    // Email được lưu để phục vụ tìm kiếm bạn bè theo Gmail
    val email: String = "",

    @get:PropertyName("photo")
    @set:PropertyName("photo")
    var imageUrl: String? = null,
    val fcmToken: String? = null,
    @get:Exclude var unreadCount: Int = 1
)
