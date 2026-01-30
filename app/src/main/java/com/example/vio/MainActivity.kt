package com.example.vio

import android.app.Application
import android.os.Bundle
import android.util.Log // Import Log
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.cloudinary.android.MediaManager
import com.example.vio.databinding.ActivityMainBinding
import com.example.vio.fm.AICameraFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.database.FirebaseDatabase

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    // Biến để xử lý bấm đúp (Double Click)
    private var lastClickTime: Long = 0
    private val DOUBLE_CLICK_TIME_DELTA: Long = 500

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        // Yêu cầu đăng nhập cho các tab cần bảo vệ
        binding.bottomNav.setOnItemSelectedListener { item ->
            val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
            val requiresLogin = when (item.itemId) {
                R.id.contactsFragment -> true
                else -> false
            }

            if (requiresLogin && !isLoggedIn) {
                android.widget.Toast.makeText(this, "Bạn cần đăng nhập để tiếp tục", android.widget.Toast.LENGTH_SHORT).show()
                navController.navigate(R.id.loginFragment)
                return@setOnItemSelectedListener false
            }

            NavigationUI.onNavDestinationSelected(item, navController)
            true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility = when (destination.id) {
                R.id.loginFragment, R.id.writeFragment, R.id.AICameraFragment -> View.GONE
                else -> View.VISIBLE
            }
        }

        // --- [SỬA LỖI QUAN TRỌNG] ---
        // Thay vì chỉ gọi 1 lần, ta lắng nghe sự kiện đăng nhập.
        // Bất cứ khi nào user đăng nhập thành công, code này sẽ chạy tự động.
        setupAuthListener()
    }

    private fun setupAuthListener() {
        FirebaseAuth.getInstance().addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                // Người dùng vừa đăng nhập (hoặc đã đăng nhập sẵn) -> Lưu Token ngay
                Log.d("FCM_DEBUG", "Phát hiện người dùng đã đăng nhập: ${firebaseAuth.currentUser?.uid}")
                updateFCMToken()
            } else {
                Log.d("FCM_DEBUG", "Người dùng chưa đăng nhập.")
            }
        }
    }

    private fun updateFCMToken() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e("FCM_DEBUG", "❌ Lỗi sinh Token: ${task.exception}")
                    return@addOnCompleteListener
                }
                val token = task.result
                Log.d("FCM_DEBUG", "✅ Token lấy được từ thiết bị: $token")

                // Lưu token vào node users/{uid}/fcmToken
                val ref = FirebaseDatabase.getInstance().getReference("users")
                ref.child(currentUser.uid).child("fcmToken").setValue(token)
                    .addOnSuccessListener {
                        Log.d("FCM_DEBUG", "✅ Đã lưu Token lên Firebase Database thành công!")
                    }
                    .addOnFailureListener { e ->
                        Log.e("FCM_DEBUG", "❌ Lỗi lưu Token lên Database: ${e.message}")
                    }
            }
        }
    }

    // --- Xử lý phím cứng Volume ---
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val clickTime = System.currentTimeMillis()
            if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                handleVolumeDoublePress()
                return true
            }
            lastClickTime = clickTime
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleVolumeDoublePress() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()

        if (currentFragment is AICameraFragment) {
            currentFragment.onVolumeDoublePressed()
        } else {
            try {
                navHostFragment?.navController?.navigate(R.id.AICameraFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

// Class VioApp giữ nguyên
class VioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
        val apiKey = BuildConfig.CLOUDINARY_API_KEY
        val apiSecret = BuildConfig.CLOUDINARY_API_SECRET
        if (cloudName.isNotBlank() && apiKey.isNotBlank() && apiSecret.isNotBlank()) {
            val config = mapOf(
                "cloud_name" to cloudName,
                "api_key" to apiKey,
                "api_secret" to apiSecret,
                "secure" to true
            )
            MediaManager.init(this, config)
        }
    }
}