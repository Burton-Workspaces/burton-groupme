package com.burton.groupme.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.burton.groupme.data.groupme.GroupMeAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.burtonStore: DataStore<Preferences> by preferencesDataStore("burton_groupme")

data class PendingOauth(
    val state: String,
)

@Singleton
class LocalPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.burtonStore

    val auth: Flow<GroupMeAuth> = store.data.map { prefs ->
        GroupMeAuth(accessToken = prefs[TOKEN].orEmpty())
    }

    val pendingOauth: Flow<PendingOauth?> = store.data.map { prefs ->
        val state = prefs[OAUTH_STATE].orEmpty()
        if (state.isBlank()) null else PendingOauth(state)
    }

    suspend fun setAuth(value: GroupMeAuth) {
        store.edit { prefs ->
            val access = value.accessToken.trim()
            if (access.isBlank()) {
                prefs.remove(TOKEN)
            } else {
                prefs[TOKEN] = access
            }
            prefs.remove(OAUTH_STATE)
        }
    }

    suspend fun clearToken() = setAuth(GroupMeAuth())

    suspend fun setPendingOauth(state: String) {
        store.edit { prefs ->
            prefs[OAUTH_STATE] = state
        }
    }

    suspend fun clearPendingOauth() {
        store.edit { prefs ->
            prefs.remove(OAUTH_STATE)
        }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("user_token")
        val OAUTH_STATE = stringPreferencesKey("oauth_state")
    }
}
