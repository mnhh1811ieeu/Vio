package com.example.vio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.vio.databinding.ActivityVoiceCommBinding

class VoiceCommActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceCommBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var isUserPaused = false
    private var isListening = false

    private val handler = Handler(Looper.getMainLooper())
    private val restartRunnable = Runnable { startListening() }
    private val fullTranscript = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceCommBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Giữ màn hình sáng
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        setupSpeechRecognizer()
        setupListeners()
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Cấu hình thời gian chờ
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                binding.btnMic.backgroundTintList = ContextCompat.getColorStateList(this@VoiceCommActivity, android.R.color.holo_green_light)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }

            override fun onError(error: Int) {
                isListening = false
                restartListening()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val formattedText = formatText(matches[0])
                    fullTranscript.append(formattedText)
                    binding.tvLiveText.text = fullTranscript.toString()
                    // Đã xóa lệnh cuộn ở đây
                }
                restartListening()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    // Ẩn mic khi bắt đầu nói
                    if (binding.btnMic.visibility == View.VISIBLE) {
                        binding.btnMic.visibility = View.GONE
                    }
                    binding.tvLiveText.text = "$fullTranscript ${matches[0]}"
                    // Đã xóa lệnh cuộn ở đây
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun formatText(input: String): String {
        var text = input.trim()
        text = text.replace(" phẩy", ",", true)
        text = text.replace(" chấm", ".", true)
        text = text.replace(" hỏi chấm", "?", true)
        if (fullTranscript.isNotEmpty()) return " $text"
        return text.replaceFirstChar { it.uppercase() }
    }

    private fun setupListeners() {
        binding.btnBackVoice.setOnClickListener { finish() }

        binding.btnMic.setOnClickListener {
            isUserPaused = false
            startListening()
            Toast.makeText(this, "Đang nghe...", Toast.LENGTH_SHORT).show()
        }

        // Chạm vào chữ để dừng và hiện lại Mic
        binding.tvLiveText.setOnClickListener {
            if (!isUserPaused) {
                isUserPaused = true
                stopListening()
                binding.btnMic.visibility = View.VISIBLE
                binding.btnMic.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.darker_gray)
            }
        }
    }

    private fun startListening() {
        handler.removeCallbacks(restartRunnable)
        if (!isUserPaused && !isListening) {
            try {
                runOnUiThread { speechRecognizer?.startListening(speechIntent) }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun stopListening() {
        handler.removeCallbacks(restartRunnable)
        try {
            speechRecognizer?.stopListening()
            isListening = false
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun restartListening() {
        if (!isUserPaused) handler.postDelayed(restartRunnable, 50)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}