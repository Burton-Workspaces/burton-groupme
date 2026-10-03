package com.burton.groupme.ui.signin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.burton.groupme.BuildConfig
import com.burton.groupme.data.groupme.GroupMeApiException
import com.burton.groupme.data.repository.GroupMeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignInUi(
    val token: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val pasteOpen: Boolean = false,
    val oauthConfigured: Boolean = BuildConfig.GROUPME_CLIENT_ID.isNotBlank(),
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val repository: GroupMeRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow(SignInUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repository.state.collect { snap ->
                if (snap.tokenPresent) return@collect
                if (snap.error != null) {
                    _ui.update { it.copy(busy = false, error = snap.error) }
                }
            }
        }
    }

    fun onTokenChange(value: String) {
        _ui.update { it.copy(token = value, error = null) }
    }

    fun togglePaste() {
        _ui.update { it.copy(pasteOpen = !it.pasteOpen, error = null) }
    }

    fun connectWithGroupMe(openUrl: (String) -> Unit) {
        if (BuildConfig.GROUPME_CLIENT_ID.isBlank()) {
            _ui.update {
                it.copy(
                    error = "This build has no GroupMe Client ID. Fill groupme/client-id.txt after creating the GroupMe app, or paste an access token.",
                    pasteOpen = true,
                )
            }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            runCatching { repository.beginOAuth() }
                .onSuccess { url ->
                    _ui.update { it.copy(busy = false) }
                    openUrl(url)
                }
                .onFailure { error ->
                    _ui.update { it.copy(busy = false, error = connectError(error)) }
                }
        }
    }

    fun connect() {
        val token = _ui.value.token.trim()
        if (token.isBlank()) {
            _ui.update { it.copy(error = "Paste a GroupMe access token to connect.", pasteOpen = true) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            runCatching { repository.signIn(token) }
                .onFailure { error ->
                    _ui.update { it.copy(busy = false, error = connectError(error)) }
                }
        }
    }

    private fun connectError(error: Throwable): String {
        val code = (error as? GroupMeApiException)?.code ?: error.message.orEmpty()
        return when {
            code.contains("unauthorized", ignoreCase = true) ||
                code.contains("invalid", ignoreCase = true) ||
                code == "not_authed" -> "That token was rejected."
            code == "missing_client_id" ->
                "This build has no GroupMe Client ID. Fill groupme/client-id.txt after creating the GroupMe app."
            else -> error.message ?: "Could not connect."
        }
    }
}

fun openAuthorizeUrl(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .build()
            .launchUrl(context, uri)
    } catch (_: Exception) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
