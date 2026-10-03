package com.burton.groupme

import androidx.lifecycle.ViewModel
import com.burton.groupme.data.repository.GroupMeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    repository: GroupMeRepository,
) : ViewModel() {
    val state = repository.state

    init {
        repository.start()
    }
}
