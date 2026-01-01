package com.example.vio.fm

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.vio.R
import com.example.vio.databinding.FragmentEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private var selectedImageUri: Uri? = null
    private var capturedBitmap: Bitmap? = null
    private var hasNewPhoto = false

    private val pickGallery = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            selectedImageUri = it
            capturedBitmap = null
            binding.imgAvatar.setImageURI(it)
            hasNewPhoto = true
        }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let {
            capturedBitmap = it
            selectedImageUri = null
            binding.imgAvatar.setImageBitmap(it)
            hasNewPhoto = true
        }
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            takePhoto.launch(null)
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
        val storageRef = FirebaseStorage.getInstance().reference.child("avatars/$uid.jpg")

        val uploadTask = when {
            selectedImageUri != null -> storageRef.putFile(selectedImageUri!!)
            capturedBitmap != null -> {
                val baos = java.io.ByteArrayOutputStream()
                capturedBitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                val data = baos.toByteArray()
                storageRef.putBytes(data)
            }
            else -> {
                updateProfile(uid, name, null)
                return
            }
        }

        uploadTask
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: java.lang.Exception("Upload failed")
                storageRef.downloadUrl
            }
            .addOnSuccessListener { uri ->
                updateProfile(uid, name, uri.toString())
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Tải ảnh thất bại", Toast.LENGTH_SHORT).show()
                binding.btnSave.isEnabled = true
                binding.btnSave.alpha = 1f
            }
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
