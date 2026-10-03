package com.burton.groupme.data.groupme

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenHolder @Inject constructor() {
    @Volatile
    var token: String = ""

    fun apply(auth: GroupMeAuth) {
        token = auth.accessToken
    }

    fun clear() = apply(GroupMeAuth())
}
