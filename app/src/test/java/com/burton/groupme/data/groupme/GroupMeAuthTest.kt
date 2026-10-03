package com.burton.groupme.data.groupme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMeAuthTest {
    @Test
    fun presentWhenTokenIsSet() {
        assertFalse(GroupMeAuth().isPresent)
        assertTrue(GroupMeAuth(accessToken = "tok").isPresent)
        assertEquals("tok", GroupMeAuth(accessToken = "tok").accessToken)
    }
}
