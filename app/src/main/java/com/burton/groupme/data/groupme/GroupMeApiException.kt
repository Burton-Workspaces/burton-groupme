package com.burton.groupme.data.groupme

class GroupMeApiException(val method: String, val code: String) : RuntimeException("$method: $code")
