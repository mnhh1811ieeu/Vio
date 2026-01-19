package com.example.vio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.vio.databinding.ActivityAiCameraBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AICameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiCameraBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    // Mặc định dùng Camera sau
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isFlashOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Kiểm tra quyền Camera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Xử lý các nút bấm
        setupListeners()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupListeners() {
        // Nút Back
        binding.btnBack.setOnClickListener { finish() }

        // Nút Đổi Camera (Trước/Sau)
        binding.btnSwitchCamera.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera() // Khởi động lại camera với lens mới
        }

        // Nút Flash (Chỉ bật/tắt icon, logic thực tế cần bind với camera control)
        binding.btnFlash.setOnClickListener {
            isFlashOn = !isFlashOn
            val icon = if (isFlashOn) R.drawable.ic_flash_off else R.drawable.ic_flash_off // Bạn nhớ thêm icon ic_flash_on nhé
            binding.btnFlash.setImageResource(icon)
            Toast.makeText(this, "Flash: $isFlashOn", Toast.LENGTH_SHORT).show()
        }

        // Nút Chụp/Nhận diện
        binding.btnCapture.setOnClickListener {
            takePhoto()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview: Hiển thị hình ảnh lên màn hình
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                // Gắn kết vòng đời camera với Activity
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // Đây là nơi bạn sẽ gọi Model AI để nhận diện
        // Hiện tại mình làm giả lập kết quả để demo

        binding.tvResultText.text = "Đang xử lý..."

        // Giả lập delay 1 giây rồi hiện kết quả
        binding.root.postDelayed({
            val result = listOf("Xin chào", "Cảm ơn", "Tạm biệt", "Tôi yêu bạn").random()
            binding.tvResultText.text = result
            Toast.makeText(this, "Đã nhận diện: $result", Toast.LENGTH_SHORT).show()
        }, 1000)

        // TODO: Tích hợp TensorFlow Lite hoặc MediaPipe ở đây để nhận diện cử chỉ tay thật
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Bạn cần cấp quyền Camera để sử dụng tính năng này.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}