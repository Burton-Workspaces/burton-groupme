package com.burton.groupme.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTextTest {
    @Test
    fun unescapesHtmlEntities() {
        assertEquals("a < b & c", MessageText.display("a &lt; b &amp; c"))
    }

    @Test
    fun trimsBlankSafe() {
        assertEquals("", MessageText.display("   "))
        assertEquals("hello", MessageText.display(" hello "))
    }
}
