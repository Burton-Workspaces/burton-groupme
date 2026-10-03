package com.burton.groupme.ui.home

import androidx.lifecycle.ViewModel
import com.burton.groupme.data.repository.GroupMeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: GroupMeRepository,
) : ViewModel() {
    val state = repository.state

    init {
        repository.start()
    }

    fun refresh() = repository.refresh()
}
