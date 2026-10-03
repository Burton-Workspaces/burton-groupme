package com.burton.groupme.domain

object MessageText {
    fun display(text: String): String {
        if (text.isBlank()) return ""
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
}
