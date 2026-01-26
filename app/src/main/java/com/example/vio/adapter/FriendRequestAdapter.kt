package com.example.vio.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.vio.User
import com.example.vio.databinding.ItemFriendRequestBinding // Bạn cần tạo layout này (xem bên dưới)

class FriendRequestAdapter(
    private val requests: List<User>,
    private val onAccept: (User) -> Unit,
    private val onDecline: (User) -> Unit
) : RecyclerView.Adapter<FriendRequestAdapter.RequestViewHolder>() {

    inner class RequestViewHolder(val binding: ItemFriendRequestBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val binding = ItemFriendRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        val user = requests[position]
        holder.binding.tvReqName.text = user.name
        holder.binding.tvReqEmail.text = user.email

        Glide.with(holder.itemView.context)
            .load(user.imageUrl ?: "https://cdn-icons-png.flaticon.com/512/634/634742.png")
            .circleCrop()
            .into(holder.binding.ivReqAvatar)

        holder.binding.btnAccept.setOnClickListener { onAccept(user) }
        holder.binding.btnDecline.setOnClickListener { onDecline(user) }
    }

    override fun getItemCount(): Int = requests.size
}