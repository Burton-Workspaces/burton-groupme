package com.burton.groupme.data.groupme

import java.security.SecureRandom
import java.util.Base64

object GroupMeOAuth {
    const val REDIRECT_SCHEME = "burtongroupme"
    const val REDIRECT_HOST = "oauth"
    const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST"

    /** HTTPS URL registered at dev.groupme.com. Pages hops to [REDIRECT_URI]. */
    const val CALLBACK_URL = "https://burton-workspaces.github.io/burton-groupme/oauth/"
    const val CALLBACK_HOST = "burton-workspaces.github.io"
    const val CALLBACK_PATH_PREFIX = "/burton-groupme/oauth"

    const val AUTHORIZE_URL = "https://oauth.groupme.com/oauth/authorize"

    fun authorizeUrl(clientId: String): String {
        val query = "client_id=${encode(clientId)}"
        return "$AUTHORIZE_URL?$query"
    }

    fun randomState(random: SecureRandom = SecureRandom()): String {
        val buffer = ByteArray(24)
        random.nextBytes(buffer)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer)
    }

    fun isCallback(scheme: String?, host: String?, path: String?): Boolean {
        if (scheme == REDIRECT_SCHEME && host == REDIRECT_HOST) return true
        if (scheme == "https" && host == CALLBACK_HOST) {
            val p = path.orEmpty()
            return p == CALLBACK_PATH_PREFIX || p.startsWith("$CALLBACK_PATH_PREFIX/")
        }
        return false
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
