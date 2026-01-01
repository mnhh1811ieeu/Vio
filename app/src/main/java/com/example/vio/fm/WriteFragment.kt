package com.example.vio.fm

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.example.vio.MessageModel
import com.example.vio.R
import com.example.vio.adapter.ChatAdapter
import com.example.vio.databinding.FragmentWriteBinding
import com.example.vio.fm.WriteFragmentArgs



class WriteFragment : Fragment() {

    // WriteFragment: Màn hình chat giữa bạn (sender) và người kia (receiver)
    // Tóm tắt luồng:
    // - Nhận tham số người nhận từ SafeArgs (userId, userName, imageUrl)
    // - Lấy UID hiện tại từ FirebaseAuth làm senderId
    // - Tạo chatRoomId bằng cách ghép 2 UID theo thứ tự để đảm bảo duy nhất
    // - Lắng nghe Realtime DB: /chats/<chatRoomId> để cập nhật danh sách tin nhắn
    // - Gửi tin nhắn mới hoặc chỉnh sửa tin nhắn đang chọn (editingMessage)
    // - Menu cho mỗi tin nhắn: sửa/chia sẻ/sao chép/xóa

    // ViewBinding (backing property) để tránh memory leak khi destroy view
    private var _binding: FragmentWriteBinding? = null

    private val binding get() = _binding!!

    // Adapter hiển thị tin nhắn và danh sách dữ liệu cục bộ
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<MessageModel>()

    // Tham chiếu DB + các biến định danh cuộc trò chuyện
    private lateinit var database: DatabaseReference
    private lateinit var senderId: String
    private lateinit var receiverId: String
    private lateinit var receiverName: String
    private lateinit var receiverPhoto: String
    private lateinit var chatRoomId: String
    private lateinit var currentUserName: String
    private var editingMessage: MessageModel? = null // nếu khác null nghĩa là đang sửa một tin nhắn


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // 1) Lấy tham số truyền qua SafeArgs (thông tin người nhận)

        val args = WriteFragmentArgs.fromBundle(requireArguments())
        receiverId = args.userId
        receiverName = args.userName
        receiverPhoto = args.imageUrl

        // 2) Lấy senderId (UID người dùng hiện tại) và tham chiếu Realtime DB
        senderId = FirebaseAuth.getInstance().uid ?: return
        database = FirebaseDatabase.getInstance().reference

        // 3) Lấy tên hiện tại của bạn để gán vào trường senderName khi gửi tin nhắn
        database.child("users").child(senderId).child("name").get().addOnSuccessListener {
            currentUserName = it.value.toString()
        }

        // 4) Tạo chatRoomId (ghép 2 UID theo thứ tự) để duy nhất cho cặp 2 người
        chatRoomId = if (senderId < receiverId) senderId + receiverId
        else receiverId + senderId

        // 5) Khởi tạo RecyclerView + Adapter và callback để mở popup menu mỗi tin nhắn
        chatAdapter = ChatAdapter(messages, senderId) { view, position, message ->
            showPopupMenu(view, position, message)
        }
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }

        // 6) Đánh dấu đã xem và bắt đầu lắng nghe realtime tin nhắn
        markMessagesAsSeen()
        listenForMessages()

        // 7) Gửi tin nhắn hoặc cập nhật tin nhắn đang sửa
        binding.btnSend.setOnClickListener {
            val newText = binding.edtMessage.text.toString().trim()
            if (newText.isEmpty()) return@setOnClickListener

            if (editingMessage != null) {
                // Chế độ chỉnh sửa: chỉ cho phép sửa tin nhắn của chính mình
                val targetMessage = editingMessage!!
                if (targetMessage.senderId != senderId) {
                    // Không phải tin nhắn của bạn -> bỏ qua
                    Toast.makeText(requireContext(), getString(R.string.edit_not_allowed), Toast.LENGTH_SHORT).show()
                    editingMessage = null
                    binding.edtMessage.setText("")
                    return@setOnClickListener
                }

                database.child("chats").child(chatRoomId).get()
                    .addOnSuccessListener { snapshot ->
                        val now = System.currentTimeMillis()
                        for (child in snapshot.children) {
                            val msg = child.getValue(MessageModel::class.java)
                            if (msg?.message == targetMessage.message && msg.senderId == targetMessage.senderId) {
                                // Lấy map lịch sử hiện tại (nếu có) và append bản cũ
                                val currentHistory = msg.editHistory?.toMutableMap() ?: mutableMapOf()
                                // Thêm entry: timestamp -> nội dung trước sửa
                                currentHistory[now.toString()] = msg.message
                                child.ref.child("editHistory").setValue(currentHistory)

                                // Vẫn lưu originalMessage lần đầu để tương thích
                                if (msg.originalMessage == null) {
                                    child.ref.child("originalMessage").setValue(msg.message)
                                }

                                // Đặt cờ edited + thời điểm sửa cuối
                                child.ref.child("edited").setValue(true)
                                child.ref.child("editedAt").setValue(now)

                                // Cập nhật nội dung tin nhắn mới
                                child.ref.child("message").setValue(newText)
                                    .addOnSuccessListener {
                                        editingMessage = null
                                        binding.edtMessage.setText("")
                                        binding.edtMessage.clearFocus()
                                    }
                                break
                            }
                        }
                    }
            } else {
                // Gửi tin nhắn mới: tạo MessageModel và push vào /chats/<chatRoomId>
                val m = MessageModel(
                    message = newText,
                    senderId = senderId,
                    senderName = currentUserName,
                    receiverId = receiverId,
                    kordim = false
                )
                database.child("chats").child(chatRoomId).push().setValue(m)
                binding.edtMessage.setText("")
                binding.edtMessage.clearFocus()
            }
        }


        // 8) Hiển thị header: tên + ảnh đại diện người nhận
        binding.name.text = receiverName
        Glide.with(requireContext()).load(receiverPhoto).into(binding.image)

        // Nút quay lại
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // 9) Khi bàn phím hiện (layout thay đổi), cuộn xuống cuối danh sách để thấy tin mới
        binding.chatRecyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom && chatAdapter.itemCount > 0) {
                binding.chatRecyclerView.postDelayed({
                    binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                }, 100)
            }
        }
    }

    private fun showPopupMenu(view: View, position: Int, message: MessageModel) {
        // Tạo PopupMenu gắn với view của item tin nhắn và inflate menu từ resource
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.message_popup_menu, popup.menu)


        // Đổi màu icon menu (nếu có) cho phù hợp theme
        for (i in 0 until popup.menu.size()) {
            val item = popup.menu.getItem(i)
            val icon = item.icon
            icon?.mutate()?.setTint(ContextCompat.getColor(requireContext(), R.color.black))
            item.icon = icon
        }

        // Thủ thuật reflection để bắt PopupMenu hiển thị icon (không phải API chính thức)
        try {
            val fields = popup.javaClass.declaredFields
            for (field in fields) {
                if (field.name == "mPopup") {
                    field.isAccessible = true
                    val menuPopupHelper = field.get(popup)
                    val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                    val setForceIcons =
                        classPopupHelper.getMethod("setForceShowIcon", Boolean::class.java)
                    setForceIcons.invoke(menuPopupHelper, true)
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Ẩn tùy chọn sửa nếu không phải tin nhắn của bạn
        popup.menu.findItem(R.id.menu_edit)?.isVisible = (message.senderId == senderId)

        // Xử lý các hành động trên từng item: sửa/chia sẻ/sao chép/xóa
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_edit -> {
                    // Chỉ cho phép sửa tin nhắn của chính mình
                    return@setOnMenuItemClickListener if (message.senderId == senderId) {
                        editMessage(message)
                        true
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.edit_not_allowed), Toast.LENGTH_SHORT).show()
                        false
                    }
                }

                R.id.share_message -> {
                    // Mở chia sẻ văn bản ra ứng dụng khác
                    shareText(message.message)
                    true
                }


                R.id.copy_message -> {
                    // Sao chép nội dung vào clipboard
                    copyToClipboard(message.message, context)
                    true
                }

                R.id.menu_delete -> {
                    // Hiển thị dialog xác nhận xóa: có tùy chọn xóa cho cả hai bên
                    val context = requireContext()
                    val dialogView =
                        LayoutInflater.from(context).inflate(R.layout.dialog_delete, null)

                    val checkbox = dialogView.findViewById<CheckBox>(R.id.checkbox_delete_both)
                    val cancelText = dialogView.findViewById<TextView>(R.id.bekor)
                    val deleteText = dialogView.findViewById<TextView>(R.id.och)

                    val dialog =
                        AlertDialog.Builder(context).setView(dialogView).setCancelable(true)
                            .create()

                    // Hủy: chỉ đóng dialog
                    cancelText.setOnClickListener {
                        dialog.dismiss()
                    }

                    // Xóa: đọc checkbox để quyết định xóa cho cả hai hay chỉ của mình
                    deleteText.setOnClickListener {
                        val deleteForBoth = checkbox.isChecked
                        dialog.dismiss()

                        // Duyệt node chats/<chatRoomId> để tìm đúng message và xóa/ẩn theo lựa chọn
                        database.child("chats").child(chatRoomId).get()
                            .addOnSuccessListener { snapshot ->
                                for (child in snapshot.children) {
                                    val msg = child.getValue(MessageModel::class.java)
                                    if (msg?.message == message.message && msg.senderId == message.senderId) {
                                        if (deleteForBoth) {
                                            // Xóa hoàn toàn: cả hai bên đều mất tin nhắn
                                            child.ref.removeValue().addOnSuccessListener {
                                                chatAdapter.removeMessage(position)
                                            }
                                        } else {
                                            // Chỉ xóa ở phía mình: đánh dấu ẩn cục bộ
                                            val myUid = senderId
                                            val otherUid = if (myUid == msg.senderId) msg.receiverId else msg.senderId
                                            child.ref.child("hiddenFor").child(myUid)
                                                .setValue(true)
                                                .addOnSuccessListener {
                                                    // Kiểm tra nếu phía còn lại cũng đã ẩn -> xóa khỏi DB để tối ưu
                                                    child.ref.child("hiddenFor").child(otherUid)
                                                        .get()
                                                        .addOnSuccessListener { otherHiddenSnap ->
                                                            val otherHidden = otherHiddenSnap.getValue(Boolean::class.java) == true
                                                            if (otherHidden) {
                                                                // Cả hai đều đã ẩn -> xóa node tin nhắn
                                                                child.ref.removeValue().addOnSuccessListener {
                                                                    chatAdapter.removeMessage(position)
                                                                }
                                                            } else {
                                                                // Chỉ ẩn ở phía mình -> gỡ khỏi UI của mình
                                                                chatAdapter.removeMessage(position)
                                                            }
                                                        }
                                                }
                                        }
                                        break
                                    }
                                }
                            }
                    }
                    dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    dialog.show()
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    private fun listenForMessages() {
        // Đăng ký lắng nghe thay đổi tại /chats/<chatRoomId>
        database.child("chats").child(chatRoomId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Nếu view đã bị hủy (binding null) thì bỏ qua để tránh crash
                    if (_binding == null) return

                    // Cập nhật lại danh sách tin nhắn cục bộ từ snapshot
                    messages.clear()
                    for (child in snapshot.children) {
                        // Nếu tin nhắn đã được đánh dấu ẩn cho riêng mình thì bỏ qua khi render
                        val hiddenForMe = child.child("hiddenFor").child(senderId)
                            .getValue(Boolean::class.java) == true
                        if (hiddenForMe) continue

                        val message = child.getValue(MessageModel::class.java)
                        if (message != null) {
                            messages.add(message)
                        }
                    }

                    // Báo cho adapter biết dữ liệu đã thay đổi để render lại
                    chatAdapter.notifyDataSetChanged()

                    // Cuộn xuống cuối để thấy tin nhắn mới nhất
                    if (messages.isNotEmpty()) {
                        binding.chatRecyclerView.scrollToPosition(messages.size - 1)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("WriteFragment", "Error listening for messages: ${error.message}")
                }
            })
    }

    private fun editMessage(message: MessageModel) {
        // Đưa nội dung tin nhắn vào ô nhập và focus để người dùng chỉnh sửa
        editingMessage = message
        binding.edtMessage.setText(message.message)
        binding.edtMessage.setSelection(message.message.length)
        binding.edtMessage.requestFocus()

        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.edtMessage, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun copyToClipboard(text: String, context: Context?) {
        // Sao chép văn bản vào clipboard và hiện Toast báo đã sao chép
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.copied_message_label), text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, getString(R.string.copied_message), Toast.LENGTH_SHORT).show()
    }

    private fun markMessagesAsSeen() {
        // Đánh dấu các tin nhắn mà bạn là người nhận (receiverId == senderId) là đã xem (kordim = true)
        val chatRef = database.child("chats").child(chatRoomId)
        chatRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (msgSnap in snapshot.children) {
                    val message = msgSnap.getValue(MessageModel::class.java) ?: continue
                    if (message.receiverId == senderId && !message.kordim) {
                        msgSnap.ref.child("kordim").setValue(true)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("WriteFragment", "Failed to mark messages as seen: ${error.message}")
            }
        })
    }

    private fun shareText(text: String) {
        // Chia sẻ nội dung văn bản qua các ứng dụng khác bằng ACTION_SEND
        val sendIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = android.content.Intent.createChooser(sendIntent, getString(R.string.share_message))
        startActivity(shareIntent)
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // giải phóng binding khi view bị destroy
    }
}

