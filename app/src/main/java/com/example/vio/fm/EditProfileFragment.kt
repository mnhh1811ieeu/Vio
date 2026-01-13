package com.example.vio.fm

import android.Manifest
import android.app.Activity
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
import com.bumptech.glide.Glide
import com.example.vio.R
import com.example.vio.data.CloudinaryImageService
import com.example.vio.databinding.FragmentEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.yalantis.ucrop.UCrop
import java.io.File

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

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
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefillFromAccount()

        binding.avatarCard.setOnClickListener { showPhotoChooser() }
        binding.imgAvatar.setOnClickListener { showPhotoChooser() }

        binding.btnSave.setOnClickListener {
            saveProfile()
        }
    }

    private fun prefillFromAccount() {
        val user = FirebaseAuth.getInstance().currentUser
        val name = user?.displayName
        val email = user?.email
        val photo = user?.photoUrl

        if (!name.isNullOrBlank()) {
            binding.edtName.setText(name)
        }
        if (!email.isNullOrBlank()) {
            binding.edtEmail.setText(email)
        }
        photo?.let {
            Glide.with(this)
                .load(it)
                .placeholder(R.drawable.img_1)
                .error(R.drawable.img_1)
                .circleCrop()
                .into(binding.imgAvatar)
        }
    }

    private fun showPhotoChooser() {
        val options = arrayOf("Chọn từ thư viện", "Chụp ảnh")
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Chọn ảnh")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickGallery.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                    1 -> requestCameraPermission.launch(Manifest.permission.CAMERA)
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

        CloudinaryImageService.uploadAvatar(requireContext(), uid, photoUri) { result ->
            result.onSuccess { url ->
                updateProfile(uid, name, url)
            }.onFailure { err ->
                Toast.makeText(requireContext(), "Upload thất bại: ${err.message}", Toast.LENGTH_SHORT).show()
                binding.btnSave.isEnabled = true
                binding.btnSave.alpha = 1f
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
                updateAuthProfile(name, photoUrl)
                Toast.makeText(requireContext(), "Đã lưu", Toast.LENGTH_SHORT).show()
                hasNewPhoto = false
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Lưu thất bại", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                binding.btnSave.isEnabled = true
                binding.btnSave.alpha = 1f
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
