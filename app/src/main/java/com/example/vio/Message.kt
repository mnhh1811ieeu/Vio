package com.example.vio


data class MessageModel(
    var message: String = "",
    var senderId: String = "",
    var senderName: String = "",

    var receiverId: String = "",

    var kordim: Boolean = false, // đã xem

    // Trường phục vụ chức năng chỉnh sửa
    var edited: Boolean = false,          // true nếu tin nhắn đã được sửa
    var editedAt: Long? = null,           // thời điểm sửa (epoch millis)
    var originalMessage: String? = null,  // nội dung trước khi sửa (lưu lần đầu sửa) - giữ để tương thích cũ
    var editHistory: Map<String, String>? = null, // map<timestampMillis, previousText>
    // Danh sách người dùng đã chọn "xóa cho mình" (ẩn cục bộ). Key = uid, value=true.
    // Khi cả hai user đã ẩn -> message có thể được xóa khỏi DB.
    var hiddenBy: Map<String, Boolean>? = null
)

