package com.example.vio.fm

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.example.vio.MessageModel
import com.example.vio.R
import com.example.vio.User
import com.example.vio.adapter.UsersAdapter
import com.example.vio.databinding.FragmentHomeBinding
import com.example.vio.fm.HomeFragmentDirections


/**
 * HomeFragment (Màn hình chính):
 * - Hiển thị danh sách người dùng (trừ chính bạn) từ Realtime Database (node "users").
 * - Tính số tin nhắn chưa đọc (unread) bằng cách lắng nghe node "chats" và kiểm tra cờ kordim.
 * - Chọn user để chuyển sang màn hình chat (WriteFragment) với Safe Args.
 * - Nhấn Back 2 lần để thoát app.
 */
class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private val userList = ArrayList<User>() // Nguồn dữ liệu cho RecyclerView người dùng
    private lateinit var adapter: UsersAdapter // Adapter hiển thị danh sách người dùng + badge unread
    private val dbRef = FirebaseDatabase.getInstance().getReference("users") // Node users
    private val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid // UID hiện tại
    private var doubleBackToExitPressedOnce = false // Điều khiển Back 2 lần để thoát

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    // Khi click 1 user, điều hướng sang màn hình chat với user đó
    adapter = UsersAdapter(userList) { user ->
            markUserAsReadOptimistically(user)
            val action = HomeFragmentDirections
                .actionHomeFragmentToWriteFragment(
                    userId = user.uid,
                    userName = user.name,
                    imageUrl = user.imageUrl!!
                )
            findNavController().navigate(action)
        }
        binding.rvUsers.adapter = adapter

    // Tải danh sách users + tính unread, và load thông tin user hiện tại lên header
    fetchUsers()
    fetchCurrentUser()





    // Bắt sự kiện nhấn nút Back: yêu cầu nhấn 2 lần để thoát
    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (doubleBackToExitPressedOnce) {
                requireActivity().finish()
            } else {
                doubleBackToExitPressedOnce = true
                Toast.makeText(
                    requireContext(),
                    getString(R.string.press_again_to_exit),
                    Toast.LENGTH_SHORT
                ).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    doubleBackToExitPressedOnce = false
                }, 2000)
            }
        }


    }

    // Đọc danh sách users và tính số tin nhắn chưa đọc cho từng người
    private fun fetchUsers() {
        showSkeleton()
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            hideSkeleton()
            return
        }

        val chatsRef = FirebaseDatabase.getInstance().getReference("chats")
        chatsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(chatSnapshot: DataSnapshot) {
                val unreadMap: MutableMap<String, Int> = mutableMapOf()
                // Khởi tạo unread = 0 cho tất cả user (trừ chính mình)
                dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (snap in snapshot.children) {
                            val user = snap.getValue(User::class.java)
                            if (user != null && user.uid != currentUid) {
                                unreadMap[user.uid] = 0
                            }
                        }

                        // Duyệt các phòng chat và cập nhật unread theo tin nhắn cuối cùng
                        for (chatRoom in chatSnapshot.children) {
                            val chatRoomId = chatRoom.key
                            for (msgSnap in chatRoom.children) {
                                val lastMessageSnap = chatRoom.children.last()
                                val message = lastMessageSnap.getValue(MessageModel::class.java)

                                if (message != null && message.receiverId == currentUid) {
                                    val expectedChatRoomId =
                                        getChatRoomId(message.senderId, message.receiverId)
                                    if (chatRoomId == expectedChatRoomId) {
                                        if (message.kordim) {
                                            // Nếu đã xem (kordim = true) => đảm bảo unread = 0
                                            unreadMap[message.senderId] = 0
                                            Log.d(
                                                "HomeFragment",
                                                "Message isSeen: true for senderId: ${message.senderId}, set unreadCount to 0"
                                            )
                                        } else {
                                            // Nếu chưa xem => tăng bộ đếm
                                            unreadMap[message.senderId] =
                                                (unreadMap[message.senderId] ?: 0) + 1
                                            Log.d(
                                                "HomeFragment",
                                                "Message isSeen: false for senderId: ${message.senderId}, unreadCount: ${unreadMap[message.senderId]}"
                                            )
                                           }
                                    }
                                }
                            }
                        }
                        Log.d("HomeFragment", "Unread counts: $unreadMap")

                        // Cập nhật danh sách người dùng kèm unread
                        userList.clear()
                        for (snap in snapshot.children) {
                            val user = snap.getValue(User::class.java)
                            if (user != null && user.uid != currentUid) {
                                user.unreadCount = unreadMap[user.uid] ?: 0
                                userList.add(user)
                                Log.d(
                                    "HomeFragment",
                                    "User: ${user.name}, UnreadCount: ${user.unreadCount}"
                                )
                            }
                        }
                        // Thông báo cho adapter dữ liệu đã thay đổi để render lại
                        binding.rvUsers.adapter = adapter
                        adapter.notifyDataSetChanged()
                        hideSkeleton()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("HomeFragment", "Error fetching users: ${error.message}")
                        hideSkeleton()
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeFragment", "Error fetching chats: ${error.message}")
                hideSkeleton()
            }
        })
    }

    // Tạo ID phòng chat duy nhất từ 2 UID bằng cách ghép theo thứ tự chữ cái
    private fun getChatRoomId(senderId: String, receiverId: String): String {
        return if (senderId < receiverId) senderId + receiverId
        else receiverId + senderId
    }

    override fun onResume() {
        super.onResume()
        fetchUsers() // Mỗi lần quay lại Home sẽ làm tươi danh sách
    }

    private fun fetchCurrentUser() {
        if (currentUserUid == null) return
        dbRef.child(currentUserUid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentUser = snapshot.getValue(User::class.java)
                currentUser?.let {
                    binding.myName.text = it.name


                    Glide.with(binding.myAvatar.context)
                        .load(
                            it.imageUrl ?: "https://cdn-icons-png.flaticon.com/512/634/634742.png"
                        )
                        .circleCrop()
                        .into(binding.myAvatar)
                }
            }

            override fun onCancelled(error: DatabaseError) {

            }
        })
    }

    private fun showSkeleton() {
        binding.homeSkeleton.visibility = View.VISIBLE
        binding.rvUsers.visibility = View.INVISIBLE
        binding.progress.visibility = View.GONE
    }

    private fun hideSkeleton() {
        binding.homeSkeleton.visibility = View.GONE
        binding.rvUsers.visibility = View.VISIBLE
        binding.progress.visibility = View.GONE
    }

    private fun markUserAsReadOptimistically(user: User) {
        val index = userList.indexOfFirst { it.uid == user.uid }
        if (index != -1 && userList[index].unreadCount > 0) {
            userList[index].unreadCount = 0
            adapter.notifyItemChanged(index)
        }
    }
}
