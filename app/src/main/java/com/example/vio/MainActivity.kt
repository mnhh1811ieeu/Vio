package com.example.vio

import android.app.Application
import android.os.Bundle
import android.view.KeyEvent // Nhớ import cái này
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.cloudinary.android.MediaManager
import com.example.vio.databinding.ActivityMainBinding
import com.example.vio.fm.AICameraFragment // Import Fragment Camera

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    // Biến để xử lý bấm đúp (Double Click)
    private var lastClickTime: Long = 0
    private val DOUBLE_CLICK_TIME_DELTA: Long = 500 // Khoảng cách giữa 2 lần ấn là 0.5 giây

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility = when (destination.id) {
                R.id.loginFragment, R.id.writeFragment, R.id.AICameraFragment -> View.GONE
                else -> View.VISIBLE
            }
        }
    }

    // --- THÊM ĐOẠN NÀY ĐỂ BẮT PHÍM TĂNG ÂM LƯỢNG ---
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val clickTime = System.currentTimeMillis()
            if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                // Phát hiện Bấm Đúp -> Xử lý ngay
                handleVolumeDoublePress()
                return true // Chặn không cho tăng âm lượng thật
            }
            lastClickTime = clickTime
            return true // Chặn sự kiện đơn lẻ
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleVolumeDoublePress() {
        // Tìm xem đang đứng ở màn hình nào
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()

        if (currentFragment is AICameraFragment) {
            // 1. Nếu đang ở màn hình Camera -> Gọi hàm chụp
            currentFragment.onVolumeDoublePressed()
        } else {
            // 2. Nếu đang ở màn hình khác (Home, Chat...) -> Mở Camera
            try {
                navHostFragment?.navController?.navigate(R.id.AICameraFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

// Class VioApp giữ nguyên không đổi
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