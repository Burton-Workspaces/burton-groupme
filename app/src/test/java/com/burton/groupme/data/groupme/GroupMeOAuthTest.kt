package com.burton.groupme.data.groupme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class GroupMeOAuthTest {
    @Test
    fun authorizeUrlIsOfficialClientIdOnly() {
        val url = URI(GroupMeOAuth.authorizeUrl("abc123"))
        val query = url.rawQuery.split("&").associate { part ->
            val (key, value) = part.split("=", limit = 2)
            key to java.net.URLDecoder.decode(value, Charsets.UTF_8)
        }
        assertEquals("abc123", query["client_id"])
        assertEquals("oauth.groupme.com", url.host)
        assertFalse(query.containsKey("redirect_uri"))
        assertFalse(query.containsKey("client_secret"))
    }

    @Test
    fun httpsCallbackIsPagesOauthPath() {
        assertEquals(
            "https://burton-workspaces.github.io/burton-groupme/oauth/",
            GroupMeOAuth.CALLBACK_URL,
        )
        assertTrue(GroupMeOAuth.CALLBACK_URL.startsWith("https://"))
    }

    @Test
    fun acceptsCustomSchemeAndHttpsPagesCallback() {
        assertTrue(GroupMeOAuth.isCallback("burtongroupme", "oauth", null))
        assertTrue(GroupMeOAuth.isCallback("https", "burton-workspaces.github.io", "/burton-groupme/oauth"))
        assertTrue(GroupMeOAuth.isCallback("https", "burton-workspaces.github.io", "/burton-groupme/oauth/"))
        assertFalse(GroupMeOAuth.isCallback("https", "burton-workspaces.github.io", "/burton-groupme/docs"))
        assertFalse(GroupMeOAuth.isCallback("https", "example.com", "/burton-groupme/oauth"))
    }

    @Test
    fun randomStateIsUrlSafe() {
        val state = GroupMeOAuth.randomState()
        assertTrue(state.length >= 16)
        assertFalse(state.contains("+"))
        assertFalse(state.contains("/"))
    }
}
