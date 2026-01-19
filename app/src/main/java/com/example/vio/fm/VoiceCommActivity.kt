package com.example.vio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.vio.databinding.ActivityVoiceCommBinding
import java.util.Locale

class VoiceCommActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityVoiceCommBinding

    // Công cụ chuyển văn bản thành giọng nói (Chị Google)
    private var tts: TextToSpeech? = null

    // Công cụ nhận diện giọng nói
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceCommBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Khởi tạo TextToSpeech
        tts = TextToSpeech(this, this)

        // 2. Khởi tạo SpeechRecognizer
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setupSpeechRecognizer()

        // 3. Xử lý sự kiện click
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBackVoice.setOnClickListener { finish() }

        // Bấm nút Micro để bắt đầu nói
        binding.btnMic.setOnClickListener {
            startListening()
        }

        // Bấm nút Loa để đọc lại văn bản đang hiện trên màn hình
        binding.btnSpeaker.setOnClickListener {
            val textToRead = binding.tvLiveText.text.toString()
            speakOut(textToRead)
        }

        // Bấm nút Bàn phím (Giả lập mở keyboard)
        binding.btnKeyboard.setOnClickListener {
            Toast.makeText(this, "Chức năng nhập phím đang phát triển", Toast.LENGTH_SHORT).show()
        }
    }

    // --- CẤU HÌNH NHẬN DIỆN GIỌNG NÓI (Speech-to-Text) ---
    private fun setupSpeechRecognizer() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.tvStatus.text = "Đang nghe... Hãy nói đi!"
                binding.imgSoundWave.alpha = 1.0f // Làm sáng icon sóng âm
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                binding.tvStatus.text = "Đang xử lý..."
                binding.imgSoundWave.alpha = 0.5f
            }

            override fun onError(error: Int) {
                binding.tvStatus.text = "Lỗi nhận diện. Hãy thử lại."
                binding.imgSoundWave.alpha = 0.5f
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0] // Lấy kết quả chính xác nhất
                    binding.tvLiveText.text = text
                    binding.tvStatus.text = "Hoàn tất."
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault()) // Tự động chọn ngôn ngữ máy
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Hãy nói điều gì đó...")

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Thiết bị không hỗ trợ nhận diện giọng nói", Toast.LENGTH_SHORT).show()
        }
    }

    // --- CẤU HÌNH ĐỌC VĂN BẢN (Text-to-Speech) ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("vi", "VN")) // Cố gắng set tiếng Việt
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Nếu không có tiếng Việt thì fallback về tiếng Anh US
                tts?.setLanguage(Locale.US)
            }
        }
    }

    private fun speakOut(text: String) {
        if (text.isNotEmpty()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        } else {
            Toast.makeText(this, "Không có nội dung để đọc", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        // Giải phóng tài nguyên khi thoát màn hình để tránh rò rỉ bộ nhớ
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
        }
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}