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
import com.example.vio.adapter.UsersAdapter
import com.example.vio.databinding.FragmentContactsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ContactsFragment : Fragment() {
    private var _binding: FragmentContactsBinding? = null
    private val binding get() = _binding!!

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val usersRef = FirebaseDatabase.getInstance().getReference("users")
    private val friendsRef = FirebaseDatabase.getInstance().getReference("friends")

    private val friends = mutableListOf<User>()
    private val friendIds = mutableSetOf<String>()
    private lateinit var adapter: UsersAdapter
    private var searchedUser: User? = null

    private var friendsListener: ValueEventListener? = null
    private var usersListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = UsersAdapter(friends) { user ->
            val action = ContactsFragmentDirections.actionContactsFragmentToWriteFragment(
                userId = user.uid,
                userName = user.name,
                imageUrl = user.imageUrl ?: ""
            )
            findNavController().navigate(action)
        }
        binding.rvContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContacts.adapter = adapter

        binding.btnSearchFriend.setOnClickListener { searchFriendByEmail() }
        binding.btnAddFriend.setOnClickListener { addSearchedFriend() }

        listenForFriends()
    }

    private fun listenForFriends() {
        val currentUid = auth.currentUser?.uid ?: return

        friendsListener?.let { friendsRef.removeEventListener(it) }
        friendsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                friendIds.clear()

                for (ownerSnap in snapshot.children) {
                    val ownerId = ownerSnap.key ?: continue
                    for (child in ownerSnap.children) {
                        val friendId = child.key ?: continue
                        if (ownerId == currentUid) friendIds.add(friendId)
                        if (friendId == currentUid) friendIds.add(ownerId)
                    }
                }

                if (friendIds.isEmpty()) {
                    friends.clear()
                    adapter.notifyDataSetChanged()
                    updateEmptyState()
                    return
                }

                usersListener?.let { usersRef.removeEventListener(it) }
                usersListener = object : ValueEventListener {
                    override fun onDataChange(userSnap: DataSnapshot) {
                        friends.clear()
                        for (snap in userSnap.children) {
                            val user = snap.getValue(User::class.java)
                            if (user != null && friendIds.contains(user.uid)) {
                                friends.add(user)
                            }
                        }
                        adapter.notifyDataSetChanged()
                        updateEmptyState()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        updateEmptyState()
                    }
                }
                usersRef.addListenerForSingleValueEvent(usersListener as ValueEventListener)
            }

            override fun onCancelled(error: DatabaseError) {
                if (context != null) {
                    Toast.makeText(requireContext(), "Không tải được danh sách bạn bè", Toast.LENGTH_SHORT).show()
                }
            }
        }
        friendsRef.addValueEventListener(friendsListener as ValueEventListener)
    }

    private fun searchFriendByEmail() {
        val email = binding.etSearchEmail.text?.toString()?.trim()?.lowercase().orEmpty()

        binding.searchResultCard.visibility = View.GONE
        binding.tvSearchStatus.visibility = View.GONE

        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            showSearchStatus("Bạn chưa đăng nhập")
            return
        }

        if (email.isEmpty()) {
            showSearchStatus("Vui lòng nhập Gmail để tìm kiếm")
            return
        }

        if (!email.endsWith("@gmail.com")) {
            showSearchStatus("Chỉ hỗ trợ tìm kiếm Gmail")
            return
        }

        usersRef.orderByChild("email").equalTo(email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.hasChildren()) {
                        showSearchStatus("Không tìm thấy người dùng với Gmail này")
                        return
                    }

                    val userSnap = snapshot.children.firstOrNull()
                    val user = userSnap?.getValue(User::class.java)

                    if (user == null) {
                        showSearchStatus("Không đọc được thông tin người dùng")
                        return
                    }

                    if (user.uid == currentUid) {
                        showSearchStatus("Đây là tài khoản của bạn")
                        return
                    }

                    searchedUser = user
                    binding.searchResultCard.visibility = View.VISIBLE
                    binding.tvSearchStatus.visibility = View.GONE

                    binding.tvSearchName.text = user.name
                    binding.tvSearchEmail.text = user.email

                    Glide.with(this@ContactsFragment)
                        .load(user.imageUrl ?: "https://cdn-icons-png.flaticon.com/512/634/634742.png")
                        .circleCrop()
                        .into(binding.ivSearchAvatar)

                    updateAddButtonState(user)
                }

                override fun onCancelled(error: DatabaseError) {
                    showSearchStatus("Lỗi tìm kiếm: ${error.message}")
                }
            })
    }

    private fun addSearchedFriend() {
        val target = searchedUser ?: run {
            showSearchStatus("Hãy tìm người dùng trước")
            return
        }
        val currentUid = auth.currentUser?.uid ?: return

        if (friendIds.contains(target.uid)) {
            updateAddButtonState(target)
            showSearchStatus("Hai bạn đã là bạn bè")
            return
        }

        binding.btnAddFriend.isEnabled = false
        friendsRef.child(currentUid).child(target.uid).setValue(true)
            .addOnCompleteListener { task ->
                binding.btnAddFriend.isEnabled = true
                if (task.isSuccessful) {
                    friendIds.add(target.uid)
                    updateAddButtonState(target)
                    showSearchStatus("Đã kết bạn với ${target.name}")
                } else {
                    showSearchStatus("Kết bạn thất bại: ${task.exception?.message}")
                }
            }
    }

    private fun updateAddButtonState(user: User) {
        val isFriend = friendIds.contains(user.uid)
        binding.btnAddFriend.isEnabled = !isFriend
        binding.btnAddFriend.text = if (isFriend) "Đã là bạn" else "Kết bạn"
    }

    private fun showSearchStatus(message: String) {
        _binding?.let {
            it.tvSearchStatus.visibility = View.VISIBLE
            it.tvSearchStatus.text = message
            it.searchResultCard.visibility = View.GONE
        }
    }

    private fun updateEmptyState() {
        _binding?.let {
            if (friends.isEmpty()) {
                it.tvEmptyContacts.visibility = View.VISIBLE
                it.rvContacts.visibility = View.GONE
            } else {
                it.tvEmptyContacts.visibility = View.GONE
                it.rvContacts.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        friendsListener?.let { friendsRef.removeEventListener(it) }
        usersListener?.let { usersRef.removeEventListener(it) }
        _binding = null
    }
}