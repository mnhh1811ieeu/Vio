package com.example.vio.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vio.data.UserSummary
import com.example.vio.data.UsersRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UsersViewModel : ViewModel() {
    private val _users = MutableStateFlow<Map<String, UserSummary>>(emptyMap())
    val users: StateFlow<Map<String, UserSummary>> = _users

    fun loadIfNeeded() {
        if (_users.value.isNotEmpty()) return
        viewModelScope.launch {
            _users.value = UsersRepository.getUsers()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _users.value = UsersRepository.refresh()
        }
    }
}
