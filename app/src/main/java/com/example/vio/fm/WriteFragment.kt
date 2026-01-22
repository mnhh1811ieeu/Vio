package com.example.vio.fm

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer // [MỚI] Import MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
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
import com.example.vio.api.RetrofitClient // [MỚI] Import API
import com.example.vio.data.UserCache
import com.example.vio.databinding.FragmentWriteBinding
import com.example.vio.fm.WriteFragmentArgs
import com.example.vio.vm.UsersViewModel
import kotlinx.coroutines.Dispatchers // [MỚI] Coroutines
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch // [MỚI]
import kotlinx.coroutines.withContext // [MỚI]
import java.io.File // [MỚI] File IO
import java.io.FileOutputStream // [MỚI]

class WriteFragment : Fragment() {

    private var _binding: FragmentWriteBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<MessageModel>()

    private lateinit var database: DatabaseReference
    private lateinit var senderId: String
    private lateinit var receiverId: String
    private lateinit var receiverName: String
    private lateinit var receiverPhoto: String
    private lateinit var chatRoomId: String
    private lateinit var currentUserName: String
    private var canChat: Boolean = false
    private var editingMessage: MessageModel? = null
    private val userNames: MutableMap<String, String> = mutableMapOf()
    private val friendsRef = FirebaseDatabase.getInstance().getReference("friends")
    private var friendshipListener: ValueEventListener? = null
    private val usersVm: UsersViewModel by viewModels()

    // [MỚI] Biến MediaPlayer để phát âm thanh
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = WriteFragmentArgs.fromBundle(requireArguments())
        receiverId = args.userId
        receiverName = args.userName
        receiverPhoto = args.imageUrl

        senderId = FirebaseAuth.getInstance().uid ?: return
        database = FirebaseDatabase.getInstance().reference

        database.child("users").child(senderId).child("name").get().addOnSuccessListener {
            currentUserName = it.value.toString()
        }

        chatRoomId = if (senderId < receiverId) senderId + receiverId else receiverId + senderId

        chatAdapter = ChatAdapter(messages, senderId) { view, position, message ->
            showPopupMenu(view, position, message)
        }
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
            setHasFixedSize(true)
        }

        preloadNamesFromCache()
        collectNamesFromVm()
        usersVm.loadIfNeeded()
        markMessagesAsSeen()
        listenForMessages()
        observeFriendship()

        binding.btnSend.setOnClickListener {
            if (!canChat) {
                Toast.makeText(requireContext(), getString(R.string.add_friend_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val newText = binding.edtMessage.text.toString().trim()
            if (newText.isEmpty()) return@setOnClickListener

            if (editingMessage != null) {
                val targetMessage = editingMessage!!
                if (targetMessage.senderId != senderId) {
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
                                val currentHistory = msg.editHistory?.toMutableMap() ?: mutableMapOf()
                                currentHistory[now.toString()] = msg.message
                                child.ref.child("editHistory").setValue(currentHistory)
                                if (msg.originalMessage == null) {
                                    child.ref.child("originalMessage").setValue(msg.message)
                                }
                                child.ref.child("edited").setValue(true)
                                child.ref.child("editedAt").setValue(now)
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

        binding.name.text = receiverName
        Glide.with(requireContext()).load(receiverPhoto).into(binding.image)

        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.chatRecyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom && chatAdapter.itemCount > 0) {
                binding.chatRecyclerView.postDelayed({
                    binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                }, 100)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun showPopupMenu(view: View, position: Int, message: MessageModel) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.message_popup_menu, popup.menu)

        for (i in 0 until popup.menu.size()) {
            val item = popup.menu.getItem(i)
            val icon = item.icon
            icon?.mutate()?.setTint(ContextCompat.getColor(requireContext(), R.color.black))
            item.icon = icon
        }

        try {
            val fields = popup.javaClass.declaredFields
            for (field in fields) {
                if (field.name == "mPopup") {
                    field.isAccessible = true
                    val menuPopupHelper = field.get(popup)
                    val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                    val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.java)
                    setForceIcons.invoke(menuPopupHelper, true)
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        popup.menu.findItem(R.id.menu_edit)?.isVisible = (message.senderId == senderId)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                // [MỚI] THÊM XỬ LÝ NÚT ĐỌC TIN NHẮN TẠI ĐÂY
                R.id.menu_speak -> {
                    playMessageAudio(message.message)
                    true
                }

                R.id.menu_edit -> {
                    if (message.senderId == senderId) {
                        editMessage(message)
                        true
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.edit_not_allowed), Toast.LENGTH_SHORT).show()
                        false
                    }
                }

                R.id.share_message -> {
                    shareText(message.message)
                    true
                }

                R.id.copy_message -> {
                    copyToClipboard(message.message, context)
                    true
                }

                R.id.menu_delete -> {
                    val context = requireContext()
                    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_delete, null)
                    val checkbox = dialogView.findViewById<CheckBox>(R.id.checkbox_delete_both)
                    val cancelText = dialogView.findViewById<TextView>(R.id.bekor)
                    val deleteText = dialogView.findViewById<TextView>(R.id.och)
                    val dialog = AlertDialog.Builder(context).setView(dialogView).setCancelable(true).create()
                    cancelText.setOnClickListener { dialog.dismiss() }
                    deleteText.setOnClickListener {
                        val deleteForBoth = checkbox.isChecked
                        dialog.dismiss()
                        database.child("chats").child(chatRoomId).get().addOnSuccessListener { snapshot ->
                            for (child in snapshot.children) {
                                val msg = child.getValue(MessageModel::class.java)
                                if (msg?.message == message.message && msg.senderId == message.senderId) {
                                    if (deleteForBoth) {
                                        child.ref.removeValue().addOnSuccessListener { chatAdapter.removeMessage(position) }
                                    } else {
                                        val myUid = senderId
                                        val otherUid = if (myUid == msg.senderId) msg.receiverId else msg.senderId
                                        child.ref.child("hiddenFor").child(myUid).setValue(true).addOnSuccessListener {
                                            child.ref.child("hiddenFor").child(otherUid).get().addOnSuccessListener { otherHiddenSnap ->
                                                val otherHidden = otherHiddenSnap.getValue(Boolean::class.java) == true
                                                if (otherHidden) {
                                                    child.ref.removeValue().addOnSuccessListener { chatAdapter.removeMessage(position) }
                                                } else {
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

    // [MỚI] HÀM QUAN TRỌNG: GỌI API VÀ PHÁT LOA
    private fun playMessageAudio(text: String) {
        Toast.makeText(requireContext(), "Đang tải giọng đọc...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Gọi API
                val responseBody = RetrofitClient.instance.getTtsAudio(mapOf("text" to text))

                // 2. Lưu file tạm
                val bytes = responseBody.bytes()
                val tempFile = File.createTempFile("tts_audio", ".mp3", requireContext().cacheDir)
                val outputStream = FileOutputStream(tempFile)
                outputStream.write(bytes)
                outputStream.close()

                // 3. Phát nhạc
                withContext(Dispatchers.Main) {
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer()
                        mediaPlayer?.apply {
                            setDataSource(tempFile.absolutePath)
                            prepare()
                            start()
                            setOnCompletionListener {
                                it.release()
                                mediaPlayer = null
                                tempFile.delete()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(requireContext(), "Lỗi phát file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Lỗi kết nối Server: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun listenForMessages() {
        database.child("chats").child(chatRoomId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (_binding == null) return
                    messages.clear()
                    for (child in snapshot.children) {
                        val hiddenForMe = child.child("hiddenFor").child(senderId)
                            .getValue(Boolean::class.java) == true
                        if (hiddenForMe) continue

                        val message = child.getValue(MessageModel::class.java)
                        if (message != null) {
                            val displayName = userNames[message.senderId]
                            if (!displayName.isNullOrBlank()) {
                                message.senderName = displayName
                            }
                            messages.add(message)
                        }
                    }
                    chatAdapter.notifyDataSetChanged()
                    if (messages.isNotEmpty()) {
                        binding.chatRecyclerView.scrollToPosition(messages.size - 1)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("WriteFragment", "Error listening for messages: ${error.message}")
                }
            })
    }

    private fun preloadNamesFromCache() {
        val cached = UserCache.namesSnapshot()
        if (cached.isNotEmpty()) {
            userNames.putAll(cached)
        }
    }

    private fun collectNamesFromVm() {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            usersVm.users.collectLatest { map ->
                if (map.isNotEmpty()) {
                    userNames.clear()
                    for ((uid, summary) in map) {
                        userNames[uid] = summary.name
                    }
                    UserCache.putAll(map.mapValues { it.value.name to it.value.photo })
                    chatAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun editMessage(message: MessageModel) {
        editingMessage = message
        binding.edtMessage.setText(message.message)
        binding.edtMessage.setSelection(message.message.length)
        binding.edtMessage.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.edtMessage, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun copyToClipboard(text: String, context: Context?) {
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getString(R.string.copied_message_label), text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, getString(R.string.copied_message), Toast.LENGTH_SHORT).show()
    }

    private fun markMessagesAsSeen() {
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
            override fun onCancelled(error: DatabaseError) { }
        })
    }

    private fun observeFriendship() {
        friendshipListener?.let { friendsRef.removeEventListener(it) }
        friendshipListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val safeBinding = _binding ?: return
                val forward = snapshot.child(senderId).child(receiverId).getValue(Boolean::class.java) == true
                val backward = snapshot.child(receiverId).child(senderId).getValue(Boolean::class.java) == true
                canChat = forward || backward
                safeBinding.btnSend.isEnabled = canChat
                safeBinding.edtMessage.isEnabled = canChat
                safeBinding.tvChatRestriction.visibility = if (canChat) View.GONE else View.VISIBLE
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        friendsRef.addValueEventListener(friendshipListener as ValueEventListener)
    }

    private fun shareText(text: String) {
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
        friendshipListener?.let { friendsRef.removeEventListener(it) }
        // [MỚI] Giải phóng MediaPlayer
        mediaPlayer?.release()
        mediaPlayer = null
        _binding = null
    }
}