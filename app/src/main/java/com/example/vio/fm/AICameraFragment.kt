package com.example.vio.fm

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.vio.api.AnalyzeResponse
import com.example.vio.api.RetrofitClient
import com.example.vio.databinding.FragmentAICameraActivityBinding
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AICameraFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentAICameraActivityBinding? = null
    private val binding get() = _binding!!

    private var tts: TextToSpeech? = null
    private var isProcessing = false

    // Biến cho CameraX
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAICameraActivityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tts = TextToSpeech(context, this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Xin quyền Camera nếu chưa có
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            // Yêu cầu quyền (bạn có thể thêm logic requestPermissions ở đây nếu cần)
            Toast.makeText(context, "Cần cấp quyền Camera", Toast.LENGTH_SHORT).show()
        }

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    // Khởi động CameraX
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            // ImageCapture (Chụp ảnh)
            imageCapture = ImageCapture.Builder().build()

            // Chọn Camera sau
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("AICamera", "Lỗi khởi tạo camera", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // --- HÀM ĐƯỢC GỌI TỪ MAIN ACTIVITY KHI BẤM ĐÚP VOLUME UP ---
    fun onVolumeDoublePressed() {
        if (!isProcessing) {
            takePhoto()
        }
    }

    private fun takePhoto() {
        // Đảm bảo imageCapture đã sẵn sàng
        val imageCapture = imageCapture ?: return

        isProcessing = true
        speakOut("Đang chụp...")
        binding.progressBar.visibility = View.VISIBLE

        // Tạo file tạm để lưu ảnh
        val photoFile = File(requireContext().cacheDir, "gemini_capture.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("AICamera", "Chụp ảnh thất bại: ${exc.message}", exc)
                    isProcessing = false
                    binding.progressBar.visibility = View.GONE
                    speakOut("Lỗi camera.")
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Chụp xong -> Gửi luôn
                    val savedUri = Uri.fromFile(photoFile)
                    uploadImage(savedUri)
                }
            }
        )
    }

    private fun uploadImage(uri: Uri) {
        speakOut("Đang phân tích ảnh.")

        val file = File(uri.path!!)

        // Gửi header image/jpeg để tránh lỗi Server
        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("image", file.name, requestFile)

        RetrofitClient.instance.analyzeImage(body).enqueue(object : Callback<AnalyzeResponse> {
            override fun onResponse(call: Call<AnalyzeResponse>, response: Response<AnalyzeResponse>) {
                resetUI()
                if (response.isSuccessful) {
                    val text = response.body()?.analysis ?: "Không nhìn rõ."
                    binding.tvResult.text = text
                    speakOut(text)
                } else {
                    speakOut("Lỗi máy chủ.")
                }
            }

            override fun onFailure(call: Call<AnalyzeResponse>, t: Throwable) {
                resetUI()
                speakOut("Lỗi kết nối mạng.")
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.setLanguage(Locale("vi", "VN"))
            speakOut("Mắt thần sẵn sàng. Bấm đúp nút tăng âm lượng để chụp.")
        }
    }

    private fun speakOut(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun resetUI() {
        isProcessing = false
        binding.progressBar.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        tts?.stop()
        tts?.shutdown()
        _binding = null
    }
}