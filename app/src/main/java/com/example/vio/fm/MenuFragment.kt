package com.example.vio.fm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.vio.R
import com.example.vio.databinding.FragmentMenuBinding
import com.google.firebase.auth.FirebaseAuth
import android.widget.Toast

class MenuFragment : Fragment() {

    private var _binding: FragmentMenuBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        val isLoggedIn = auth.currentUser != null

        // Cập nhật trạng thái nút đăng nhập/đăng xuất
        binding.tvAuthAction.text = if (isLoggedIn) "Đăng xuất" else "Đăng nhập"
        binding.ivAuthIcon.setColorFilter(if (isLoggedIn) 0xFFE53935.toInt() else 0xFF0091FF.toInt())

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.menuProfile.setOnClickListener {
            if (auth.currentUser == null) {
                Toast.makeText(requireContext(), "Bạn cần đăng nhập để xem hồ sơ", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.loginFragment)
                return@setOnClickListener
            }
            findNavController().navigate(R.id.profileFragment)
        }

        binding.menuSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        binding.btnAuthContainer.setOnClickListener {
            if (auth.currentUser == null) {
                Toast.makeText(requireContext(), "Bạn cần đăng nhập", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.loginFragment)
            } else {
                auth.signOut()
                Toast.makeText(requireContext(), getString(R.string.logged_out), Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.loginFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
