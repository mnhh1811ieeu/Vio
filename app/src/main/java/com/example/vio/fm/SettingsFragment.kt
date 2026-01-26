package com.example.vio.fm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.example.vio.R
import com.example.vio.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- XỬ LÝ LOGIC BACK ---
        binding.btnBack.setOnClickListener {
            // Quay lại màn hình trước đó trong Stack
            findNavController().popBackStack()
        }
        // ------------------------

        binding.swNotifications.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(requireContext(), if (isChecked) "Bật thông báo" else "Tắt thông báo", Toast.LENGTH_SHORT).show()
        }

        binding.swDarkMode.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(requireContext(), if (isChecked) "Bật chế độ tối" else "Tắt chế độ tối", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogoutContainer.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            Toast.makeText(requireContext(), getString(R.string.logged_out), Toast.LENGTH_SHORT).show()

            // Điều hướng về màn hình đăng nhập và xóa backstack để không back lại được setting
            findNavController().navigate(R.id.loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}