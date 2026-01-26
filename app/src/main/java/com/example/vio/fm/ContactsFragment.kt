package com.example.vio.fm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.vio.User
import com.example.vio.adapter.FriendRequestAdapter
import com.example.vio.adapter.UsersAdapter
import com.example.vio.databinding.FragmentContactsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ContactsFragment : Fragment() {
    private var _binding: FragmentContactsBinding? = null
    // Chỉ dùng 'binding' ở các hàm vòng đời chính (onViewCreated).
    // Trong callback Firebase, dùng '_binding' để an toàn.
    private val binding get() = _binding!!

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db = FirebaseDatabase.getInstance()
    private val usersRef = db.getReference("users")
    private val friendsRef = db.getReference("friends")
    private val requestsRef = db.getReference("friend_requests")

    private val friends = mutableListOf<User>()
    private val requests = mutableListOf<User>()

    private lateinit var friendsAdapter: UsersAdapter
    private lateinit var requestsAdapter: FriendRequestAdapter

    private var searchedUser: User? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupListeners()

        listenForFriendRequests()
        listenForFriends()
    }

    private fun setupRecyclerViews() {
        friendsAdapter = UsersAdapter(friends) { user ->
            val action = ContactsFragmentDirections.actionContactsFragmentToWriteFragment(
                userId = user.uid,
                userName = user.name,
                imageUrl = user.imageUrl ?: ""
            )
            findNavController().navigate(action)
        }
        binding.rvContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContacts.adapter = friendsAdapter

        requestsAdapter = FriendRequestAdapter(
            requests,
            onAccept = { user -> acceptFriendRequest(user) },
            onDecline = { user -> declineFriendRequest(user) }
        )
        binding.rvFriendRequests.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFriendRequests.adapter = requestsAdapter
    }

    private fun setupListeners() {
        binding.btnSearchFriend.setOnClickListener { searchFriendByEmail() }

        binding.btnAddFriend.setOnClickListener {
            val user = searchedUser ?: return@setOnClickListener
            sendFriendRequest(user)
        }
    }

    // --- LOGIC TÌM KIẾM ---
    private fun searchFriendByEmail() {
        val email = binding.etSearchEmail.text?.toString()?.trim()?.lowercase().orEmpty()
        binding.searchResultCard.visibility = View.GONE
        binding.tvSearchStatus.visibility = View.GONE

        val currentUid = auth.currentUser?.uid ?: return

        if (email.isEmpty()) {
            showSearchStatus("Vui lòng nhập email")
            return
        }

        usersRef.orderByChild("email").equalTo(email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // SỬA: Kiểm tra binding null để tránh crash
                    val bind = _binding ?: return

                    if (!snapshot.hasChildren()) {
                        showSearchStatus("Không tìm thấy user này")
                        return
                    }

                    val userSnap = snapshot.children.first()
                    val user = userSnap.getValue(User::class.java)

                    if (user == null || user.uid == currentUid) {
                        showSearchStatus("Không thể kết bạn với chính mình")
                        return
                    }

                    searchedUser = user
                    // SỬA: Dùng biến 'bind' (từ _binding) thay vì 'binding'
                    bind.searchResultCard.visibility = View.VISIBLE
                    bind.tvSearchName.text = user.name
                    bind.tvSearchEmail.text = user.email

                    // Kiểm tra context trước khi load ảnh
                    if (context != null) {
                        Glide.with(this@ContactsFragment)
                            .load(user.imageUrl ?: "https://cdn-icons-png.flaticon.com/512/634/634742.png")
                            .circleCrop()
                            .into(bind.ivSearchAvatar)
                    }

                    checkFriendStatus(user.uid)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun checkFriendStatus(targetUid: String) {
        val currentUid = auth.currentUser?.uid ?: return

        friendsRef.child(currentUid).child(targetUid).addListenerForSingleValueEvent(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                // SỬA: Dùng _binding?.
                val bind = _binding ?: return

                if(snapshot.exists()) {
                    bind.btnAddFriend.isEnabled = false
                    bind.btnAddFriend.text = "Đã là bạn bè"
                } else {
                    requestsRef.child(targetUid).child(currentUid).addListenerForSingleValueEvent(object : ValueEventListener{
                        override fun onDataChange(snap: DataSnapshot) {
                            val innerBind = _binding ?: return // Kiểm tra lại lần nữa vì đây là callback lồng nhau
                            if(snap.exists()) {
                                innerBind.btnAddFriend.isEnabled = false
                                innerBind.btnAddFriend.text = "Đã gửi lời mời"
                            } else {
                                innerBind.btnAddFriend.isEnabled = true
                                innerBind.btnAddFriend.text = "Gửi lời mời kết bạn"
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --- LOGIC GỬI LỜI MỜI ---
    private fun sendFriendRequest(user: User) {
        val currentUid = auth.currentUser?.uid ?: return
        binding.btnAddFriend.isEnabled = false

        requestsRef.child(user.uid).child(currentUid).setValue("pending")
            .addOnSuccessListener {
                // SỬA: Dùng _binding?.
                _binding?.btnAddFriend?.text = "Đã gửi lời mời"
                if (context != null) {
                    Toast.makeText(context, "Đã gửi lời mời tới ${user.name}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                _binding?.btnAddFriend?.isEnabled = true
                if (context != null) {
                    Toast.makeText(context, "Lỗi: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // --- LOGIC NHẬN LỜI MỜI ---
    private fun listenForFriendRequests() {
        val currentUid = auth.currentUser?.uid ?: return

        requestsRef.child(currentUid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                val senderIds = mutableListOf<String>()
                for (child in snapshot.children) {
                    senderIds.add(child.key!!)
                }
                loadRequestUsersInfo(senderIds)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadRequestUsersInfo(ids: List<String>) {
        // SỬA: Kiểm tra an toàn ngay đầu hàm
        val bind = _binding ?: return

        if (ids.isEmpty()) {
            requests.clear()
            requestsAdapter.notifyDataSetChanged()
            bind.rvFriendRequests.visibility = View.GONE
            bind.tvRequestTitle.visibility = View.GONE
            return
        }

        requests.clear()
        var loadedCount = 0
        for (id in ids) {
            usersRef.child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val innerBind = _binding ?: return // SỬA: Kiểm tra lại binding

                    val user = snapshot.getValue(User::class.java)
                    if (user != null) requests.add(user)

                    loadedCount++
                    if (loadedCount == ids.size) {
                        requestsAdapter.notifyDataSetChanged()
                        innerBind.rvFriendRequests.visibility = View.VISIBLE
                        innerBind.tvRequestTitle.visibility = View.VISIBLE
                        innerBind.tvRequestTitle.text = "Lời mời kết bạn (${requests.size})"
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun acceptFriendRequest(sender: User) {
        val currentUid = auth.currentUser?.uid ?: return
        friendsRef.child(currentUid).child(sender.uid).setValue(true)
        friendsRef.child(sender.uid).child(currentUid).setValue(true)
        requestsRef.child(currentUid).child(sender.uid).removeValue()
        Toast.makeText(context, "Đã trở thành bạn bè với ${sender.name}", Toast.LENGTH_SHORT).show()
    }

    private fun declineFriendRequest(sender: User) {
        val currentUid = auth.currentUser?.uid ?: return
        requestsRef.child(currentUid).child(sender.uid).removeValue()
        Toast.makeText(context, "Đã xóa lời mời", Toast.LENGTH_SHORT).show()
    }

    private fun listenForFriends() {
        val currentUid = auth.currentUser?.uid ?: return
        friendsRef.child(currentUid).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                val friendIds = snapshot.children.mapNotNull { it.key }
                loadFriendsInfo(friendIds)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadFriendsInfo(ids: List<String>) {
        // SỬA: Kiểm tra an toàn ngay đầu hàm
        val bind = _binding ?: return

        if (ids.isEmpty()) {
            friends.clear()
            friendsAdapter.notifyDataSetChanged()
            bind.tvEmptyContacts.visibility = View.VISIBLE
            return
        }

        friends.clear()
        var count = 0
        for (id in ids) {
            usersRef.child(id).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // SỬA QUAN TRỌNG NHẤT: Kiểm tra lại binding trong callback con
                    val innerBind = _binding ?: return

                    val user = snapshot.getValue(User::class.java)
                    if (user != null) friends.add(user)
                    count++

                    if (count == ids.size) {
                        friends.sortBy { it.name }
                        friendsAdapter.notifyDataSetChanged()
                        // Dùng innerBind (đã check null) thay vì binding
                        innerBind.tvEmptyContacts.visibility = View.GONE
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun showSearchStatus(msg: String) {
        // SỬA: Dùng _binding?.
        _binding?.let {
            it.tvSearchStatus.visibility = View.VISIBLE
            it.tvSearchStatus.text = msg
            it.searchResultCard.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}