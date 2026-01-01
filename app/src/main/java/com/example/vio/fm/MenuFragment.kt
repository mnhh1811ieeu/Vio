package com.example.vio.fm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.example.vio.R
import com.example.vio.databinding.FragmentMenuBinding
import com.google.firebase.auth.FirebaseAuth

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

        binding.menuProfile.setOnClickListener {
            findNavController().navigate(R.id.profileFragment)
        }

        binding.menuEditProfile.setOnClickListener {
            findNavController().navigate(R.id.editProfileFragment)
        }

        binding.menuSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        binding.menuLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val options = navOptions {
                popUpTo(R.id.my_nav) { inclusive = false }
            }
            findNavController().navigate(R.id.loginFragment, null, options)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
