package com.example.vio.fm

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.vio.R
import com.example.vio.data.CloudinaryImageService
import com.example.vio.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.yalantis.ucrop.UCrop
import java.io.File

class ProfileFragment : Fragment() {

    // View binding: sử dụng backing property để tránh memory leak
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Firebase instances
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    private var selectedImageUri: Uri? = null
    private var cameraImageUri: Uri? = null
    private var hasNewPhoto = false

    private val pickGallery = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { startCrop(it) }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) {
            startCrop(cameraImageUri!!)
        }
    }

    private val cropImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val output = result.data?.let { UCrop.getOutput(it) }
            output?.let {
                selectedImageUri = it
                binding.imgAvatar.setImageURI(it)
                hasNewPhoto = true
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val error = result.data?.let { UCrop.getError(it) }
            Toast.makeText(requireContext(), error?.localizedMessage ?: "Crop lỗi", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraImageUri = prepareCameraUri()
            takePhoto.launch(cameraImageUri)
        } else {
            Toast.makeText(requireContext(), "Cần quyền camera để chụp ảnh", Toast.LENGTH_SHORT).show()
        }
    }

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

        prefillFromAccount()

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        binding.avatarCard.setOnClickListener { showPhotoChooser() }
        binding.imgAvatar.setOnClickListener { showPhotoChooser() }

        binding.btnSave.setOnClickListener { saveProfile() }
    }

    private fun prefillFromAccount() {
        val user = FirebaseAuth.getInstance().currentUser
        val name = user?.displayName
        val email = user?.email
        val photo = user?.photoUrl

        if (!name.isNullOrBlank()) binding.edtName.setText(name)
        if (!email.isNullOrBlank()) binding.edtEmail.setText(email)

        photo?.let {
            Glide.with(this)
                .load(it)
                .thumbnail(0.2f)
                .override(256)
                .placeholder(R.drawable.img_1)
                .error(R.drawable.img_1)
                .circleCrop()
                .into(binding.imgAvatar)
        }
    }

    // Logout được xử lý ở Menu, không thực hiện tại đây

    override fun onDestroyView() {
        super.onDestroyView()
        // Giải phóng binding để tránh leak
        _binding = null
    }

    private fun showPhotoChooser() {
        val options = arrayOf("Chọn từ thư viện", "Chụp ảnh")
        AlertDialog.Builder(requireContext())
            .setTitle("Chọn ảnh")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickGallery.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    1 -> requestCameraPermission.launch(android.Manifest.permission.CAMERA)
                }
            }
            .show()
    }

    private fun saveProfile() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Chưa đăng nhập", Toast.LENGTH_SHORT).show()
            return
        }

        val name = binding.edtName.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Vui lòng nhập tên", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSave.isEnabled = false
        binding.btnSave.alpha = 0.6f

        if (hasNewPhoto) {
            uploadPhotoThenSave(user.uid, name)
        } else {
            updateProfile(user.uid, name, user.photoUrl?.toString())
        }
    }

    private fun uploadPhotoThenSave(uid: String, name: String) {
        val photoUri = selectedImageUri
        if (photoUri == null) {
            updateProfile(uid, name, null)
            return
        }

        val ctx = context
        if (ctx == null) {
            // Fragment đã tách khỏi màn hình, bỏ qua xử lý
            return
        }

        CloudinaryImageService.uploadAvatar(ctx, uid, photoUri) { result ->
            result.onSuccess { url ->
                updateProfile(uid, name, url)
            }.onFailure { err ->
                val b = _binding ?: return@onFailure
                context?.let { Toast.makeText(it, "Upload thất bại: ${err.message}", Toast.LENGTH_SHORT).show() }
                b.btnSave.isEnabled = true
                b.btnSave.alpha = 1f
            }
        }
    }

    private fun startCrop(source: Uri) {
        val destFile = File.createTempFile("avatar_cropped_", ".jpg", requireContext().cacheDir)
        val destUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            destFile
        )

        val options = UCrop.Options().apply {
            setCompressionQuality(90)
            setCircleDimmedLayer(true)
            setHideBottomControls(true)
            setFreeStyleCropEnabled(false)
        }

        val intent = UCrop.of(source, destUri)
            .withAspectRatio(1f, 1f)
            .withOptions(options)
            .getIntent(requireContext())
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)

        cropImage.launch(intent)
    }

    private fun prepareCameraUri(): Uri {
        val file = File.createTempFile("avatar_capture_", ".jpg", requireContext().cacheDir)
        return FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )
    }

    private fun updateProfile(uid: String, name: String, photoUrl: String?) {
        val updates = hashMapOf<String, Any>("name" to name)
        photoUrl?.let { updates["photo"] = it }

        FirebaseDatabase.getInstance().getReference("users")
            .child(uid)
            .updateChildren(updates)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                updateAuthProfile(name, photoUrl)
                context?.let { Toast.makeText(it, "Đã lưu", Toast.LENGTH_SHORT).show() }
                hasNewPhoto = false
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                context?.let { Toast.makeText(it, "Lưu thất bại", Toast.LENGTH_SHORT).show() }
            }
            .addOnCompleteListener {
                val b = _binding ?: return@addOnCompleteListener
                b.btnSave.isEnabled = true
                b.btnSave.alpha = 1f
            }
    }

    private fun updateAuthProfile(name: String, photoUrl: String?) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val profileUpdates = userProfileChangeRequest {
            displayName = name
            photoUrl?.let { this.photoUri = Uri.parse(it) }
        }
        user.updateProfile(profileUpdates)
    }
}
