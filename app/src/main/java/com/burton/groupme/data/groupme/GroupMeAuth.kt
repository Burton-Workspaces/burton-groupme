package com.burton.groupme.data.groupme

data class GroupMeAuth(
    val accessToken: String = "",
) {
    val isPresent: Boolean get() = accessToken.isNotBlank()
}
