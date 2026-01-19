package com.example.vio.fm

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.example.vio.R
import com.example.vio.databinding.FragmentLoginBinding
import com.example.vio.util.TypingAnimationUtil

/**
 * LoginFragment
 * - Màn hình đăng nhập bằng Google.
 * - Luồng chính: nhấn nút Google Sign-In -> chọn tài khoản -> nhận idToken ->
 *   đăng nhập Firebase Auth -> lưu thông tin user lên Realtime Database -> điều hướng Home.
 * - Ghi chú: đang dùng startActivityForResult (đã deprecate) cho ngắn gọn; có thể nâng cấp sang
 *   ActivityResult APIs nếu cần.
 */
@Suppress("DEPRECATION")
class LoginFragment : Fragment() {

    private lateinit var binding: FragmentLoginBinding          // ViewBinding
    private lateinit var googleSignInClient: GoogleSignInClient // Client Google Sign-In
    private lateinit var typingAnimation: TypingAnimationUtil   // Hiệu ứng gõ chữ dòng giới thiệu
    private lateinit var auth: FirebaseAuth                     // Firebase Auth
    private val RC_SIGN_IN = 101                                // Request code cho Sign-In

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Khởi tạo hiệu ứng gõ chữ cho dòng giới thiệu
        typingAnimation = TypingAnimationUtil(binding.textView, getString(R.string.join_us))
        typingAnimation.start()

        // Khởi tạo FirebaseAuth
        auth = FirebaseAuth.getInstance()

        // Nếu đã đăng nhập trước đó thì đi thẳng vào Home
        if (auth.currentUser != null) {
            findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
            return
        }

        // Cấu hình yêu cầu Google Sign-In: lấy idToken + email để đăng nhập Firebase
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        // Tạo GoogleSignInClient từ cấu hình trên
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        // Xử lý nút "Đăng nhập với Google"
        binding.btnGoogleSignIn.setOnClickListener {
            // Dừng hiệu ứng gõ chữ để tránh chồng chéo giao diện
            typingAnimation.stop()
            // Force hiển thị màn chọn tài khoản: gọi signOut + revokeAccess rồi mới mở Intent Sign-In
            googleSignInClient.signOut().addOnCompleteListener {
                // revokeAccess giúp xóa ủy quyền để lần sau buộc chọn lại tài khoản/quyền
                googleSignInClient.revokeAccess().addOnCompleteListener {
                    val intent = googleSignInClient.signInIntent
                    startActivityForResult(intent, RC_SIGN_IN)
                }
            }
        }
    }

    @Deprecated("Deprecated API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            // Nhận kết quả chọn tài khoản Google
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful) {
                val account = task.result // Tài khoản Google đã chọn
                // Đổi idToken thành credential để đăng nhập Firebase
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener { taskResult ->
                    if (taskResult.isSuccessful) {
                        // Lưu thông tin cơ bản của user lên Realtime Database
                        saveUserToDatabase()
                        // Chuyển sang Home
                        findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                    } else {
                        // TODO: hiển thị thông báo lỗi nếu cần
                    }
                }
            }
        }
    }

    private fun saveUserToDatabase() {
        // Lưu user hiện tại vào node "users/{uid}" với các trường đơn giản: uid, name, photo
        val user = auth.currentUser ?: return
        val uid = user.uid
        val name = user.displayName ?: getString(R.string.unknown)
        val photo = user.photoUrl?.toString() ?: ""
        val email = user.email?.trim()?.lowercase() ?: ""

        val map = mapOf(
            "uid" to uid,
            "name" to name,
            "email" to email,
            "photo" to photo
        )

        FirebaseDatabase.getInstance().getReference("users")
            .child(uid)
            .setValue(map)
    }
}
