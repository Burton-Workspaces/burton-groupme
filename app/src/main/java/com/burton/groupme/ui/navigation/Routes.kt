package com.burton.groupme.ui.navigation

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val CHANNEL = "channel/{channelId}"

    fun channel(channelId: String) = "channel/${enc(channelId)}"

    private fun enc(value: String) = android.net.Uri.encode(value)
}
