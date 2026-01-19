package com.example.vio.fm

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.vio.MessageModel
import com.example.vio.R
import com.example.vio.User
import com.example.vio.adapter.UsersAdapter
import com.example.vio.databinding.FragmentChatListBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ChatListFragment : Fragment() {

    private lateinit var binding: FragmentChatListBinding
    private val userList = ArrayList<User>()
    private lateinit var adapter: UsersAdapter
    private val dbRef = FirebaseDatabase.getInstance().getReference("users")
    private val friendsRef = FirebaseDatabase.getInstance().getReference("friends")
    private val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
    private var friendsListener: ValueEventListener? = null
    private var chatsListener: ValueEventListener? = null
    private var friendIds: Set<String> = emptySet()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentChatListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        adapter = UsersAdapter(userList) { user ->
            markUserAsReadOptimistically(user)
            val action = ChatListFragmentDirections
                .actionChatListFragmentToWriteFragment(
                    userId = user.uid,
                    userName = user.name,
                    imageUrl = user.imageUrl ?: ""
                )
            findNavController().navigate(action)
        }
        binding.rvUsers.adapter = adapter

        fetchUsers()
        fetchCurrentUser()
    }

    private fun fetchUsers() {
        showSkeleton()
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            hideSkeleton()
            return
        }

        friendsListener?.let { friendsRef.child(currentUid).removeEventListener(it) }
        friendsListener = object : ValueEventListener {
            override fun onDataChange(friendsSnapshot: DataSnapshot) {
                val ids = mutableSetOf<String>()
                for (ownerSnap in friendsSnapshot.children) {
                    val ownerId = ownerSnap.key ?: continue
                    for (child in ownerSnap.children) {
                        val friendId = child.key ?: continue
                        if (ownerId == currentUid) ids.add(friendId)
                        if (friendId == currentUid) ids.add(ownerId)
                    }
                }

                friendIds = ids

                if (friendIds.isEmpty()) {
                    userList.clear()
                    adapter.notifyDataSetChanged()
                    hideSkeleton()
                    return
                }

                listenForChats(currentUid)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatListFragment", "Error fetching friends: ${error.message}")
                hideSkeleton()
            }
        }
        friendsRef.addValueEventListener(friendsListener!!)
    }

    private fun listenForChats(currentUid: String) {
        val chatsRef = FirebaseDatabase.getInstance().getReference("chats")
        chatsListener?.let { chatsRef.removeEventListener(it) }
        chatsListener = object : ValueEventListener {
            override fun onDataChange(chatSnapshot: DataSnapshot) {
                val unreadMap: MutableMap<String, Int> = mutableMapOf()
                friendIds.forEach { unreadMap[it] = 0 }

                for (chatRoom in chatSnapshot.children) {
                    val chatRoomId = chatRoom.key
                    val lastMessageSnap = chatRoom.children.lastOrNull() ?: continue
                    val message = lastMessageSnap.getValue(MessageModel::class.java) ?: continue

                    if (message.receiverId == currentUid && friendIds.contains(message.senderId)) {
                        val expectedChatRoomId = getChatRoomId(message.senderId, message.receiverId)
                        if (chatRoomId == expectedChatRoomId) {
                            if (message.kordim) {
                                unreadMap[message.senderId] = 0
                            } else {
                                unreadMap[message.senderId] = (unreadMap[message.senderId] ?: 0) + 1
                            }
                        }
                    }
                }

                dbRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        userList.clear()
                        for (snap in snapshot.children) {
                            val user = snap.getValue(User::class.java)
                            if (user != null && friendIds.contains(user.uid)) {
                                user.unreadCount = unreadMap[user.uid] ?: 0
                                userList.add(user)
                            }
                        }
                        adapter.notifyDataSetChanged()
                        hideSkeleton()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("ChatListFragment", "Error fetching users: ${error.message}")
                        hideSkeleton()
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatListFragment", "Error fetching chats: ${error.message}")
                hideSkeleton()
            }
        }
        chatsRef.addValueEventListener(chatsListener!!)
    }

    private fun getChatRoomId(senderId: String, receiverId: String): String {
        return if (senderId < receiverId) senderId + receiverId else receiverId + senderId
    }

    override fun onResume() {
        super.onResume()
        fetchUsers()
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
                // No-op
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

    override fun onDestroyView() {
        super.onDestroyView()
        val chatsRef = FirebaseDatabase.getInstance().getReference("chats")
        chatsListener?.let { chatsRef.removeEventListener(it) }
        currentUserUid?.let { uid ->
            friendsListener?.let { friendsRef.removeEventListener(it) }
        }
    }
}
