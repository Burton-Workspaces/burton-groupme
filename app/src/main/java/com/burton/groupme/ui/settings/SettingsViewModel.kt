package com.burton.groupme.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.groupme.data.repository.GroupMeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: GroupMeRepository,
) : ViewModel() {
    val state = repository.state

    fun signOut() {
        viewModelScope.launch { repository.signOut() }
    }
}
