package com.example.vio.fm

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.vio.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.example.vio.R

class ProfileFragment : Fragment() {

    // View binding: sử dụng backing property để tránh memory leak
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Firebase instances
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate layout bằng ViewBinding
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Khi view được tạo: load thông tin user lên UI và gán listener cho nút logout
        loadUserInfo()

        binding.btnLogout.setOnClickListener {
            // Hiện dialog xác nhận trước khi logout
            showLogoutDialog()
        }
    }

    // Đọc thông tin user từ FirebaseAuth và hiển thị lên layout
    private fun loadUserInfo() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Nếu có URL ảnh, dùng Glide để load; nếu không có, dùng placeholder
            val photoUrl = currentUser.photoUrl
            if (photoUrl != null) {
                Glide.with(this)
                    .load(photoUrl)
                    .placeholder(R.drawable.img_1) // ảnh tạm
                    .error(R.drawable.img_1)       // nếu load lỗi
                    .into(binding.imgProfile)
            } else {
                binding.imgProfile.setImageResource(R.drawable.img_1)
            }

            // Hiển thị tên và email; nếu null thì dùng chuỗi mặc định từ resources
            binding.tvNickname.text = currentUser.displayName ?: getString(R.string.no_name)
            binding.tvEmail.text = currentUser.email ?: getString(R.string.no_email)
        }
    }

    // Hiển thị dialog cảnh báo khi user muốn đăng xuất
    // Lưu ý: nội dung dialog hiện đang bằng tiếng Uzbek — có thể thay thành resource
    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.alert_title)) // tiêu đề dialog
            .setMessage(getString(R.string.logout_warning))
            .setPositiveButton(getString(R.string.log_out)) { dialog, _ ->
                dialog.dismiss()
                // Gọi hàm thực hiện logout
                logoutUser()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss() // đóng dialog nếu user đổi ý
            }
            .show()
    }


    // Thực hiện logout: hiện tại code xóa messages trong DB trước rồi signOut.
    // Nếu việc xóa chậm thì có thể gây chậm/treo; cân nhắc thực hiện signOut ngay và xóa dữ liệu bất đồng bộ.
    private fun logoutUser() {
        val uid = auth.currentUser?.uid ?: return
        val userMessagesRef = database.reference.child("messages").child(uid)

        // Xóa node messages của user
        userMessagesRef.removeValue().addOnCompleteListener {
            // Sau khi xóa xong (hoặc nếu không có dữ liệu) thì đăng xuất và điều hướng về Login
            auth.signOut()
            findNavController().navigate(R.id.loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Giải phóng binding để tránh leak
        _binding = null
    }
}
