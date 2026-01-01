package com.example.vio.adapter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vio.R
import com.example.vio.User

import com.example.vio.databinding.ItemUsersBinding


class UsersAdapter(
    private var users: List<User>,
    private val onItemClick: (User) -> Unit
) : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {
    // Adapter hiển thị danh sách user trong màn Home
    // - `users`: danh sách User (có thể chứa trường unreadCount để hiển thị badge)
    // - `onItemClick`: callback khi user item được click (mở chat với user đó)
    inner class UserViewHolder(private val binding: ItemUsersBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Bind dữ liệu User vào các view trong item
        fun bind(user: User) {

            // Hiển thị tên user
            binding.tvUserName.text = user.name

            // Debug log: thông tin user + số lượng tin chưa đọc
            Log.d("UsersAdapter", "User: ${user.name}, UnreadCount: ${user.unreadCount}")

            // Hiển thị badge số tin nhắn chưa đọc nếu > 0, ngược lại ẩn view
            binding.tvUnreadCount.text = "" // reset trước khi set
            if (user.unreadCount > 0) {
                binding.tvUnreadCount.text = user.unreadCount.toString()
                binding.tvUnreadCount.visibility = View.VISIBLE
                Log.d("UsersAdapter", "tvUnreadCount VISIBLE for ${user.name}")
            } else {
                binding.tvUnreadCount.visibility = View.GONE
                Log.d("UsersAdapter", "tvUnreadCount GONE for ${user.name}")
            }

            // Tải avatar bằng Glide; nếu user.imageUrl null thì dùng placeholder mặc định
            Glide.with(binding.root.context)
                .load(user.imageUrl ?: "https://cdn-icons-png.flaticon.com/512/634/634742.png")
                .placeholder(R.drawable.img_1)
                .error(R.drawable.img_1)
                .circleCrop()
                .into(binding.ivUserImage)

            // Khi click vào item -> gọi callback truyền User về Fragment/Activity để xử lý
            binding.root.setOnClickListener {
                onItemClick(user)
            }

        }

        // Clear state khi ViewHolder bị recycle để tránh hiển thị dữ liệu cũ
        fun clear() {
            binding.tvUnreadCount.text = ""
            binding.tvUnreadCount.visibility = View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUsersBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun getItemCount() = users.size

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun onViewRecycled(holder: UserViewHolder) {
        super.onViewRecycled(holder)
        holder.clear() // View qayta ishlatilganda tozalash
    }
}
