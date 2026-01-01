package com.example.vio.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.vio.MessageModel
import com.example.vio.R

class ChatAdapter(
    private val messages: MutableList<MessageModel>,
    private val currentUserId: String,
    private val onMessageLongClick: (View, Int, MessageModel) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {
    // Adapter chịu trách nhiệm hiển thị danh sách message trong RecyclerView.
    // - `messages`: nguồn dữ liệu mutable chứa MessageModel
    // - `currentUserId`: để phân biệt message của chính mình và của người khác
    // - `onMessageLongClick`: callback khi người dùng tương tác (ở đây được gọi từ click listener)

    // ViewHolder giữ các View con trong item layout để tái sử dụng hiệu quả
    inner class ChatViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        // Layout/Views dành cho message của chính bạn (right side thường)
        val youLayout = view.findViewById<LinearLayout>(R.id.youMessageLayout)
        val youName = view.findViewById<TextView>(R.id.youName)
        val youMessage = view.findViewById<TextView>(R.id.youMessage)
        val youHistoryContainer = view.findViewById<LinearLayout>(R.id.youHistoryContainer)
        val youEditedInfo = view.findViewById<TextView>(R.id.youEditedInfo)

        // Layout/Views dành cho message của người khác (left side thường)
        val otherLayout = view.findViewById<LinearLayout>(R.id.otherMessageLayout)
        val otherName = view.findViewById<TextView>(R.id.otherName)
        val otherMessage = view.findViewById<TextView>(R.id.otherMessage)
        val otherHistoryContainer = view.findViewById<LinearLayout>(R.id.otherHistoryContainer)
        val otherEditedInfo = view.findViewById<TextView>(R.id.otherEditedInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_item, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]

        // Nếu sender của message chính là user hiện tại -> hiển thị layout "you"
        if (message.senderId == currentUserId) {
            holder.youLayout.visibility = View.VISIBLE
            holder.otherLayout.visibility = View.GONE

            // Hiện tên và nội dung (ở đây tên được hard-coded là "Siz" — có thể đổi thành getString)
            holder.youName.setText(R.string.you)
            holder.youMessage.text = message.message

            // Hiển thị lịch sử chỉnh sửa (nếu có)
            holder.youHistoryContainer.removeAllViews()
            holder.youHistoryContainer.visibility = View.GONE
            if (message.edited) {
                holder.youEditedInfo.visibility = View.VISIBLE
                holder.youEditedInfo.text = holder.view.context.getString(R.string.edited_view_history)

                val history = message.editHistory ?: emptyMap()
                if (history.isNotEmpty()) {
                    history.toSortedMap().forEach { (_, prevText) ->
                        val tv = TextView(holder.view.context)
                        tv.text = prevText
                        tv.setPadding(8, 6, 8, 6)
                        tv.setTextColor(0xFF333333.toInt())
                        tv.textSize = 13f
                        tv.setBackgroundColor(0xFFEEEEEE.toInt())
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.topMargin = 4
                        holder.youHistoryContainer.addView(tv, params)
                    }
                } else if (message.originalMessage != null) {
                    // Tương thích cũ: nếu chỉ có originalMessage
                    val tv = TextView(holder.view.context)
                    tv.text = message.originalMessage
                    tv.setPadding(8, 6, 8, 6)
                    tv.setTextColor(0xFF333333.toInt())
                    tv.textSize = 13f
                    tv.setBackgroundColor(0xFFEEEEEE.toInt())
                    holder.youHistoryContainer.addView(tv)
                }
            } else {
                holder.youEditedInfo.visibility = View.GONE
            }

            // Gắn listener: khi người dùng nhấn vào message của mình, gọi callback để hiện menu
            // Lưu ý: callback được truyền từ Fragment/Activity nơi adapter được khởi tạo
            holder.youLayout.setOnClickListener {
                onMessageLongClick(it, position, message)
            }

            // Toggle hiển thị lịch sử
            holder.youEditedInfo.setOnClickListener {
                if (holder.youHistoryContainer.visibility == View.VISIBLE) {
                    holder.youHistoryContainer.visibility = View.GONE
                    holder.youEditedInfo.text = holder.view.context.getString(R.string.edited_view_history)
                } else {
                    holder.youHistoryContainer.visibility = View.VISIBLE
                    holder.youEditedInfo.text = holder.view.context.getString(R.string.edited_hide_history)
                }
            }
        } else {
            // Message của người khác -> hiển thị layout "other"
            holder.youLayout.visibility = View.GONE
            holder.otherLayout.visibility = View.VISIBLE

            holder.otherName.text = message.senderName // tên người gửi
            holder.otherMessage.text = message.message // nội dung message

            holder.otherHistoryContainer.removeAllViews()
            holder.otherHistoryContainer.visibility = View.GONE
            if (message.edited) {
                holder.otherEditedInfo.visibility = View.VISIBLE
                holder.otherEditedInfo.text = holder.view.context.getString(R.string.edited_view_history)

                val history = message.editHistory ?: emptyMap()
                if (history.isNotEmpty()) {
                    history.toSortedMap().forEach { (_, prevText) ->
                        val tv = TextView(holder.view.context)
                        tv.text = prevText
                        tv.setPadding(8, 6, 8, 6)
                        tv.setTextColor(0xFF333333.toInt())
                        tv.textSize = 13f
                        tv.setBackgroundColor(0xFFEEEEEE.toInt())
                        val params = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        params.topMargin = 4
                        holder.otherHistoryContainer.addView(tv, params)
                    }
                } else if (message.originalMessage != null) {
                    val tv = TextView(holder.view.context)
                    tv.text = message.originalMessage
                    tv.setPadding(8, 6, 8, 6)
                    tv.setTextColor(0xFF333333.toInt())
                    tv.textSize = 13f
                    tv.setBackgroundColor(0xFFEEEEEE.toInt())
                    holder.otherHistoryContainer.addView(tv)
                }
            } else {
                holder.otherEditedInfo.visibility = View.GONE
            }

            holder.otherEditedInfo.setOnClickListener {
                if (holder.otherHistoryContainer.visibility == View.VISIBLE) {
                    holder.otherHistoryContainer.visibility = View.GONE
                    holder.otherEditedInfo.text = holder.view.context.getString(R.string.edited_view_history)
                } else {
                    holder.otherHistoryContainer.visibility = View.VISIBLE
                    holder.otherEditedInfo.text = holder.view.context.getString(R.string.edited_hide_history)
                }
            }

            // Gắn listener tương tự cho layout của người khác
            holder.otherLayout.setOnClickListener {
                onMessageLongClick(it, position, message)
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    fun removeMessage(position: Int) {
        if (position >= 0 && position < messages.size) {
            messages.removeAt(position)
            notifyItemRemoved(position)
        }
    }

}