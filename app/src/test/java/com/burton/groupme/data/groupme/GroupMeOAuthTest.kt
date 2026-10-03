package com.burton.groupme.data.groupme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class GroupMeOAuthTest {
    @Test
    fun authorizeUrlIncludesClientIdRedirectAndState() {
        val url = URI(GroupMeOAuth.authorizeUrl("abc123", "state-1"))
        val query = url.rawQuery.split("&").associate { part ->
            val (key, value) = part.split("=", limit = 2)
            key to java.net.URLDecoder.decode(value, Charsets.UTF_8)
        }
        assertEquals("abc123", query["client_id"])
        assertEquals(GroupMeOAuth.REDIRECT_URI, query["redirect_uri"])
        assertEquals("state-1", query["state"])
        assertEquals("oauth.groupme.com", url.host)
        assertFalse(query.containsKey("client_secret"))
    }

    @Test
    fun randomStateIsUrlSafe() {
        val state = GroupMeOAuth.randomState()
        assertTrue(state.length >= 16)
        assertFalse(state.contains("+"))
        assertFalse(state.contains("/"))
    }
}
