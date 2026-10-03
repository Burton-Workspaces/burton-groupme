package com.burton.groupme.ui.channel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.groupme.data.repository.GroupMeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val repository: GroupMeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val channelId: String = savedStateHandle["channelId"] ?: ""
    val snapshot = repository.state
    val history = repository.histories.map { it[channelId] }

    private val _draft = MutableStateFlow("")
    val draft = _draft.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    init {
        if (channelId.isNotBlank()) repository.openChannel(channelId)
    }

    fun onDraft(value: String) {
        _draft.value = value
    }

    fun send() {
        val text = _draft.value.trim()
        if (text.isBlank() || channelId.isBlank()) return
        viewModelScope.launch {
            _busy.value = true
            runCatching { repository.send(channelId, text) }
                .onSuccess { _draft.value = "" }
                .onFailure { _notice.value = it.message }
            _busy.value = false
        }
    }

    fun loadOlder() = repository.loadOlder(channelId)

    fun like(messageId: String) {
        viewModelScope.launch {
            runCatching { repository.toggleLike(channelId, messageId) }
                .onFailure { _notice.value = it.message }
        }
    }

    fun leave(onLeft: () -> Unit) {
        if (channelId.isBlank() || _busy.value) return
        viewModelScope.launch {
            _busy.value = true
            runCatching { repository.leave(channelId) }
                .onSuccess { onLeft() }
                .onFailure { _notice.value = it.message }
            _busy.value = false
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    override fun onCleared() {
        repository.closeChannel(channelId)
        super.onCleared()
    }
}
