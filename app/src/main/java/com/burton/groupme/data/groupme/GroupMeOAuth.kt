package com.burton.groupme.data.groupme

import java.security.SecureRandom
import java.util.Base64

object GroupMeOAuth {
    const val REDIRECT_SCHEME = "burtongroupme"
    const val REDIRECT_HOST = "oauth"
    const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST"
    const val AUTHORIZE_URL = "https://oauth.groupme.com/oauth/authorize"

    fun authorizeUrl(clientId: String, state: String): String {
        val query = listOf(
            "client_id" to clientId,
            "redirect_uri" to REDIRECT_URI,
            "state" to state,
        ).joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "$AUTHORIZE_URL?$query"
    }

    fun randomState(random: SecureRandom = SecureRandom()): String {
        val buffer = ByteArray(24)
        random.nextBytes(buffer)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer)
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
