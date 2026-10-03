package com.burton.groupme.domain

enum class ConversationKind {
    GROUP,
    DM,
}

data class Account(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val imageUrl: String,
)

data class GroupMeUser(
    val id: String,
    val name: String,
    val nickname: String,
    val imageUrl: String,
) {
    val label: String
        get() = nickname.ifBlank { name.ifBlank { id } }
}

data class Conversation(
    val id: String,
    val name: String,
    val kind: ConversationKind,
    val topic: String,
    val unread: Int,
    val memberCount: Int,
    val latestText: String,
    val latestTs: String,
    val otherUserId: String,
    val imageUrl: String,
    val updatedAt: Long,
) {
    val isDirect: Boolean get() = kind == ConversationKind.DM

    fun title(): String = name.ifBlank {
        if (isDirect) "Direct message" else "Group"
    }
}

data class GroupMeFile(
    val id: String,
    val name: String,
    val title: String,
    val type: String,
    val url: String,
    val thumbUrl: String,
) {
    val previewUrl: String get() = thumbUrl.ifBlank { url }
    val isImage: Boolean
        get() = type == "image" ||
            type == "linked_image" ||
            name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    companion object {
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "heic", "bmp")
    }
}

data class GroupMeReaction(
    val name: String,
    val count: Int,
    val userIds: List<String>,
) {
    fun mine(userId: String) = userId.isNotBlank() && userId in userIds
}

data class GroupMeMessage(
    val id: String,
    val conversationId: String,
    val userId: String,
    val username: String,
    val avatarUrl: String,
    val text: String,
    val createdAt: Long,
    val system: Boolean,
    val likedBy: List<String>,
    val files: List<GroupMeFile>,
) {
    val isSystem: Boolean get() = system
    val likeCount: Int get() = likedBy.size
    fun likedByMe(userId: String) = userId.isNotBlank() && userId in likedBy
}

data class SearchHit(
    val conversationId: String,
    val conversationName: String,
    val message: GroupMeMessage,
)

data class ChannelHistory(
    val channelId: String,
    val messages: List<GroupMeMessage>,
    val hasOlder: Boolean,
    val oldest: String,
    val loading: Boolean,
    val error: String?,
)

data class GroupMeSnapshot(
    val tokenPresent: Boolean = false,
    val account: Account? = null,
    val conversations: List<Conversation> = emptyList(),
    val users: Map<String, GroupMeUser> = emptyMap(),
    val scanning: Boolean = false,
    val error: String? = null,
) {
    val groups: List<Conversation> get() = conversations.filter { !it.isDirect }
    val directs: List<Conversation> get() = conversations.filter { it.isDirect }

    fun conversation(id: String): Conversation? = conversations.firstOrNull { it.id == id }

    fun userLabel(userId: String): String =
        users[userId]?.label ?: userId.ifBlank { "Unknown" }
}
