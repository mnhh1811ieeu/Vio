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
 */
@Suppress("DEPRECATION")
class LoginFragment : Fragment() {

    private lateinit var binding: FragmentLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var typingAnimation: TypingAnimationUtil
    private lateinit var auth: FirebaseAuth
    private val RC_SIGN_IN = 101

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        typingAnimation = TypingAnimationUtil(binding.textView, getString(R.string.join_us))
        typingAnimation.start()

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
            return
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        binding.btnGoogleSignIn.setOnClickListener {
            typingAnimation.stop()
            googleSignInClient.signOut().addOnCompleteListener {
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
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful) {
                val account = task.result
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener { taskResult ->
                    if (taskResult.isSuccessful) {
                        saveUserToDatabase()
                        findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                    } else {
                        // Handle error
                    }
                }
            }
        }
    }

    private fun saveUserToDatabase() {
        val user = auth.currentUser ?: return
        val uid = user.uid
        val name = user.displayName ?: getString(R.string.unknown)
        val photo = user.photoUrl?.toString() ?: ""
        val email = user.email?.trim()?.lowercase() ?: ""

        // [QUAN TRỌNG] Sử dụng Map<String, Any> để dùng cho updateChildren
        val updates = mapOf<String, Any>(
            "uid" to uid,
            "name" to name,
            "email" to email,
            "photo" to photo
            // KHÔNG đưa fcmToken vào đây để tránh ghi đè làm mất token
        )

        // [SỬA LỖI] Thay setValue() bằng updateChildren()
        // updateChildren: Chỉ sửa các trường có trong map, giữ nguyên các trường khác (như fcmToken)
        FirebaseDatabase.getInstance().getReference("users")
            .child(uid)
            .updateChildren(updates)
    }
}