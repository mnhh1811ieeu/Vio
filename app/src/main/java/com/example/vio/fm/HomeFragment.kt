package com.example.vio.fm

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.vio.R
import com.example.vio.User
import com.example.vio.databinding.FragmentHomeBinding
import com.example.vio.AICameraActivity
import com.example.vio.VoiceCommActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private val dbRef = FirebaseDatabase.getInstance().getReference("users")
    private val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
    private var doubleBackToExitPressedOnce = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Đổi FragmentHomeBinding cho khớp với layout XML của bạn
        // (Nếu layout tên là activity_home.xml nhưng dùng cho fragment thì cần đổi tên layout thành fragment_home.xml)
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Nút Camera AI (Dịch ký hiệu)
        binding.btnAICamera.setOnClickListener {
            startActivity(Intent(requireContext(), AICameraActivity::class.java))
        }

        // 2. Nút Giao tiếp Giọng nói
        binding.btnVoiceComm.setOnClickListener {
            startActivity(Intent(requireContext(), VoiceCommActivity::class.java))
        }

        // 3. Nút Trợ lý ảo (Mắt Thần AI)
        binding.btnAssistant.setOnClickListener {
            try {
                // SỬA THÀNH CÁCH NÀY:
                // R.id.AICameraFragment phải trùng với ID bạn vừa đặt trong file navigation xml ở Bước 1
                findNavController().navigate(R.id.AICameraFragment)
            } catch (e: Exception) {
                // Nếu chưa cấu hình xong Bước 1 mà chạy thì sẽ vào đây
                Toast.makeText(context, "Lỗi điều hướng: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }

        // 4. Nút Tạo Chat Mới (Điều hướng bằng Navigation Component)
        // Thay đoạn cũ bằng đoạn này:
        binding.btnNewChat.setOnClickListener {
            try {
                // Sử dụng đúng ID vừa thêm trong file XML
                findNavController().navigate(R.id.action_homeFragment_to_chatListFragment)
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi điều hướng: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Nút Thông báo ở Header
        binding.btnNotification.setOnClickListener {
            Toast.makeText(context, "Không có thông báo mới", Toast.LENGTH_SHORT).show()
        }

        // Tải thông tin người dùng
        fetchCurrentUser()
        ensureEmailSynced()

        // Xử lý nút Back (Nhấn 2 lần để thoát)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (doubleBackToExitPressedOnce) {
                requireActivity().finish()
            } else {
                doubleBackToExitPressedOnce = true
                Toast.makeText(
                    requireContext(),
                    getString(R.string.press_again_to_exit), // Đảm bảo string này có trong strings.xml
                    Toast.LENGTH_SHORT
                ).show()
                Handler(Looper.getMainLooper()).postDelayed({
                    doubleBackToExitPressedOnce = false
                }, 2000)
            }
        }
    }

    private fun fetchCurrentUser() {
        if (currentUserUid == null) return
        dbRef.child(currentUserUid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val ctx = context ?: return
                val b = binding
                val currentUser = snapshot.getValue(User::class.java)
                currentUser?.let {
                    b.tvUsername.text = it.name

                    // Load ảnh đại diện
                    Glide.with(ctx)
                        .load(it.imageUrl ?: "https://cdn-icons-png.flaticon.com/512/634/634742.png")
                        .thumbnail(0.2f)
                        .override(256)
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .circleCrop()
                        .into(b.imgAvatar)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                context?.let { Toast.makeText(it, "Lỗi tải dữ liệu: ${error.message}", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    // Đồng bộ email vào node users nếu trước đây chưa có (để tìm kiếm Gmail hoạt động)
    private fun ensureEmailSynced() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val email = user.email?.trim()?.lowercase() ?: return
        val uid = user.uid
        dbRef.child(uid).child("email").get().addOnSuccessListener { snap ->
            val existing = snap.getValue(String::class.java)
            if (existing.isNullOrBlank()) {
                dbRef.child(uid).child("email").setValue(email)
            }
        }
    }
}