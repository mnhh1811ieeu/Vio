package com.example.vio

import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.cloudinary.android.MediaManager
import com.example.vio.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility = when (destination.id) {
                R.id.loginFragment, R.id.writeFragment -> View.GONE
                else -> View.VISIBLE
            }
        }
    }
}

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