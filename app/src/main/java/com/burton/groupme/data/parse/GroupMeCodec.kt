package com.burton.groupme.data.parse

import com.burton.groupme.data.parse.TinyJson.bool
import com.burton.groupme.data.parse.TinyJson.int
import com.burton.groupme.data.parse.TinyJson.long
import com.burton.groupme.data.parse.TinyJson.obj
import com.burton.groupme.data.parse.TinyJson.objList
import com.burton.groupme.data.parse.TinyJson.str
import com.burton.groupme.data.parse.TinyJson.strList
import com.burton.groupme.domain.Account
import com.burton.groupme.domain.Conversation
import com.burton.groupme.domain.ConversationKind
import com.burton.groupme.domain.GroupMeFile
import com.burton.groupme.domain.GroupMeMessage
import com.burton.groupme.domain.GroupMeUser

object GroupMeCodec {
    fun account(raw: Map<String, Any?>): Account? {
        val id = raw.str("id").ifBlank { raw.str("user_id") }
        if (id.isBlank()) return null
        return Account(
            id = id,
            name = raw.str("name"),
            email = raw.str("email"),
            phone = raw.str("phone_number"),
            imageUrl = raw.str("image_url"),
        )
    }

    fun user(raw: Map<String, Any?>): GroupMeUser? {
        val id = raw.str("user_id").ifBlank { raw.str("id") }
        if (id.isBlank()) return null
        return GroupMeUser(
            id = id,
            name = raw.str("name"),
            nickname = raw.str("nickname").ifBlank { raw.str("name") },
            imageUrl = raw.str("image_url").ifBlank { raw.str("avatar_url") },
        )
    }

    fun group(raw: Map<String, Any?>): Conversation? {
        val id = raw.str("id")
        if (id.isBlank()) return null
        val messages = raw.obj("messages")
        val preview = messages.obj("preview")
        val latestText = preview.str("text").ifBlank { preview.str("nickname") }
        return Conversation(
            id = id,
            name = raw.str("name"),
            kind = ConversationKind.GROUP,
            topic = raw.str("description"),
            unread = messages.int("unread_count"),
            memberCount = raw.objList("members").size.takeIf { it > 0 } ?: raw.int("members_count"),
            latestText = latestText,
            latestTs = epochToTs(messages.long("last_message_created_at")),
            otherUserId = "",
            imageUrl = raw.str("image_url"),
            updatedAt = raw.long("updated_at"),
        )
    }

    fun chat(raw: Map<String, Any?>): Conversation? {
        val other = raw.obj("other_user")
        val otherId = other.str("id")
        if (otherId.isBlank()) return null
        val last = raw.obj("last_message")
        return Conversation(
            id = dmId(otherId),
            name = other.str("name"),
            kind = ConversationKind.DM,
            topic = "",
            unread = raw.int("unread_count"),
            memberCount = 2,
            latestText = last.str("text"),
            latestTs = epochToTs(last.long("created_at").takeIf { it > 0 } ?: raw.long("updated_at")),
            otherUserId = otherId,
            imageUrl = other.str("avatar_url").ifBlank { other.str("image_url") },
            updatedAt = raw.long("updated_at"),
        )
    }

    fun message(raw: Map<String, Any?>, fallbackConversationId: String = ""): GroupMeMessage? {
        val id = raw.str("id")
        if (id.isBlank()) return null
        val created = raw.long("created_at")
        val attachments = raw.objList("attachments")
        val files = attachments.mapNotNull(::file)
        val extra = attachments.map { it.str("name").ifBlank { it.str("title") } }
            .filter { it.isNotBlank() }
        val text = raw.str("text")
        val location = attachments.firstOrNull { it.str("type") == "location" }
        val locationText = location?.str("name").orEmpty()
        val combined = listOf(text, locationText)
            .plus(extra.filter { part -> part !in files.map { file -> file.title } })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
        val liked = raw.strList("favorited_by")
        val groupId = raw.str("group_id")
        val conversationId = raw.str("conversation_id")
            .ifBlank { groupId }
            .ifBlank { fallbackConversationId }
        return GroupMeMessage(
            id = id,
            conversationId = conversationId,
            userId = raw.str("user_id").ifBlank { raw.str("sender_id") },
            username = raw.str("name"),
            avatarUrl = raw.str("avatar_url"),
            text = combined,
            createdAt = created,
            system = raw.bool("system"),
            likedBy = liked,
            files = files,
        )
    }

    fun dmId(otherUserId: String) = "dm:$otherUserId"

    fun isDirect(conversationId: String) = conversationId.startsWith("dm:")

    fun otherUserId(conversationId: String) = conversationId.removePrefix("dm:")

    fun likeConversationId(me: String, other: String): String {
        val (left, right) = if (me <= other) me to other else other to me
        return "$left+$right"
    }

    fun epochToTs(epochSeconds: Long): String = if (epochSeconds <= 0L) "" else epochSeconds.toString()

    private fun file(raw: Map<String, Any?>): GroupMeFile? {
        val type = raw.str("type")
        val url = raw.str("url").ifBlank { raw.str("preview_url") }
        if (type !in IMAGE_TYPES && url.isBlank()) return null
        if (type == "mentions" || type == "emoji" || type == "location") return null
        if (url.isBlank()) return null
        val title = raw.str("name").ifBlank { raw.str("title") }.ifBlank { type.ifBlank { "attachment" } }
        return GroupMeFile(
            id = url,
            name = title,
            title = title,
            type = type.ifBlank { "image" },
            url = url,
            thumbUrl = raw.str("preview_url").ifBlank { url },
        )
    }

    private val IMAGE_TYPES = setOf("image", "linked_image")
}
