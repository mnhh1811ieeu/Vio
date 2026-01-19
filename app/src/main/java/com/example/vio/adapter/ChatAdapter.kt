package com.example.vio.adapter

import android.graphics.Color
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

    inner class ChatViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val youLayout = view.findViewById<LinearLayout>(R.id.youMessageLayout)
        val youName = view.findViewById<TextView>(R.id.youName)
        val youMessage = view.findViewById<TextView>(R.id.youMessage)
        val youHistoryContainer = view.findViewById<LinearLayout>(R.id.youHistoryContainer)
        val youEditedInfo = view.findViewById<TextView>(R.id.youEditedInfo)

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

    override fun getItemId(position: Int): Long = position.toLong()

    init {
        setHasStableIds(true)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]

        // ----------------------------------------------------
        // PHẦN CỦA SENDER (BẠN)
        // ----------------------------------------------------
        if (message.senderId == currentUserId) {
            holder.youLayout.visibility = View.VISIBLE
            holder.otherLayout.visibility = View.GONE

            holder.youName.setText(R.string.you)
            holder.youMessage.text = message.message
            // KHÔNG sửa background của youMessage (giữ nguyên giao diện chính)

            // --- Xử lý phần LỊCH SỬ ---
            holder.youHistoryContainer.removeAllViews()
            holder.youHistoryContainer.visibility = View.GONE

            if (message.edited) {
                holder.youEditedInfo.visibility = View.VISIBLE
                holder.youEditedInfo.text = holder.view.context.getString(R.string.edited_view_history)

                val history = message.editHistory ?: emptyMap()
                // Lấy list lịch sử hoặc message gốc cũ
                val historyList = if (history.isNotEmpty()) history.toSortedMap().values else listOfNotNull(message.originalMessage)

                for (prevText in historyList) {
                    val tv = TextView(holder.view.context)
                    tv.text = prevText

                    // Style: Tăng padding lên chút vì bo góc 18dp khá lớn
                    tv.setPadding(24, 16, 24, 16)

                    // ===> SỬ DỤNG BACKGROUND MÀU XANH <===
                    tv.setBackgroundResource(R.drawable.bg_original_sender)

                    // Nền sáng hơn -> dùng chữ đậm
                    tv.setTextColor(Color.parseColor("#0F172A"))
                    tv.textSize = 13f

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.topMargin = 12 // Cách nhau ra một chút
                    holder.youHistoryContainer.addView(tv, params)
                }
            } else {
                holder.youEditedInfo.visibility = View.GONE
            }

            holder.youLayout.setOnClickListener {
                onMessageLongClick(it, position, message)
            }

            holder.youEditedInfo.setOnClickListener {
                toggleHistory(holder.youHistoryContainer, holder.youEditedInfo)
            }

        }
        // ----------------------------------------------------
        // PHẦN CỦA RECEIVER (NGƯỜI KHÁC)
        // ----------------------------------------------------
        else {
            holder.youLayout.visibility = View.GONE
            holder.otherLayout.visibility = View.VISIBLE

            holder.otherName.text = message.senderName
            holder.otherMessage.text = message.message
            // KHÔNG sửa background của otherMessage (giữ nguyên giao diện chính)

            // --- Xử lý phần LỊCH SỬ ---
            holder.otherHistoryContainer.removeAllViews()
            holder.otherHistoryContainer.visibility = View.GONE

            if (message.edited) {
                holder.otherEditedInfo.visibility = View.VISIBLE
                holder.otherEditedInfo.text = holder.view.context.getString(R.string.edited_view_history)

                val history = message.editHistory ?: emptyMap()
                val historyList = if (history.isNotEmpty()) history.toSortedMap().values else listOfNotNull(message.originalMessage)

                for (prevText in historyList) {
                    val tv = TextView(holder.view.context)
                    tv.text = prevText

                    tv.setPadding(24, 16, 24, 16)

                    // ===> SỬ DỤNG BACKGROUND MÀU XÁM NHẠT <===
                    tv.setBackgroundResource(R.drawable.bg_original_receiver)

                    // Nền sáng -> chữ đậm
                    tv.setTextColor(Color.parseColor("#0F172A"))
                    tv.textSize = 13f

                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.topMargin = 12
                    holder.otherHistoryContainer.addView(tv, params)
                }
            } else {
                holder.otherEditedInfo.visibility = View.GONE
            }

            holder.otherEditedInfo.setOnClickListener {
                toggleHistory(holder.otherHistoryContainer, holder.otherEditedInfo)
            }

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

    // Hàm phụ trợ để ẩn/hiện lịch sử gọn gàng hơn
    private fun toggleHistory(container: View, infoText: TextView) {
        if (container.visibility == View.VISIBLE) {
            container.visibility = View.GONE
            infoText.text = infoText.context.getString(R.string.edited_view_history)
        } else {
            container.visibility = View.VISIBLE
            infoText.text = infoText.context.getString(R.string.edited_hide_history)
        }
    }
}