package com.burton.groupme.data.parse

import com.burton.groupme.domain.ConversationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMeCodecTest {
    @Test
    fun parsesAccount() {
        val account = GroupMeCodec.account(
            TinyJson.parseObject(
                """{"id":"123","name":"Ada","email":"ada@burton.work","phone_number":"+1555","image_url":"https://img"}""",
            ),
        )!!
        assertEquals("123", account.id)
        assertEquals("Ada", account.name)
        assertEquals("ada@burton.work", account.email)
        assertEquals("https://img", account.imageUrl)
    }

    @Test
    fun parsesGroupAndMembers() {
        val group = GroupMeCodec.group(
            TinyJson.parseObject(
                """
                {
                  "id":"g1",
                  "name":"Family",
                  "description":"home",
                  "image_url":"https://g",
                  "creator_user_id":"u1",
                  "updated_at":1700000000,
                  "members":[{"id":"mem-1","user_id":"u1","nickname":"Ada","image_url":"https://a"}],
                  "messages":{
                    "count":12,
                    "last_message_created_at":1700000100,
                    "preview":{"nickname":"Ada","text":"hello"}
                  }
                }
                """.trimIndent(),
            ),
            meId = "u1",
        )!!
        assertEquals(ConversationKind.GROUP, group.kind)
        assertEquals("Family", group.name)
        assertEquals("hello", group.latestText)
        assertEquals("1700000100", group.latestTs)
        assertEquals(1, group.memberCount)
        assertEquals("u1", group.creatorUserId)
        assertEquals("mem-1", group.membershipId)
        assertTrue(group.createdBy("u1"))
        assertFalse(group.createdBy("u2"))
        assertFalse(group.isDirect)
    }

    @Test
    fun membershipIdUsesUserIdNotMembershipId() {
        val members = listOf(
            mapOf<String, Any?>("id" to "mem-9", "user_id" to "u9", "nickname" to "Ada"),
            mapOf<String, Any?>("id" to "mem-2", "user_id" to "u2", "nickname" to "Bob"),
        )
        assertEquals("mem-9", GroupMeCodec.membershipId(members, "u9"))
        assertEquals("", GroupMeCodec.membershipId(members, "missing"))
        assertEquals("", GroupMeCodec.membershipId(members, ""))
    }

    @Test
    fun parsesDirectChat() {
        val chat = GroupMeCodec.chat(
            TinyJson.parseObject(
                """
                {
                  "updated_at":99,
                  "other_user":{"id":"u2","name":"Bob","avatar_url":"https://b"},
                  "last_message":{"created_at":100,"text":"hey","name":"Bob"}
                }
                """.trimIndent(),
            ),
        )!!
        assertEquals(ConversationKind.DM, chat.kind)
        assertEquals("dm:u2", chat.id)
        assertEquals("Bob", chat.name)
        assertEquals("hey", chat.latestText)
        assertTrue(chat.isDirect)
        assertEquals("u2", chat.otherUserId)
    }

    @Test
    fun parsesMessageLikesAndImages() {
        val message = GroupMeCodec.message(
            TinyJson.parseObject(
                """
                {
                  "id":"m1",
                  "user_id":"u1",
                  "group_id":"g1",
                  "name":"Ada",
                  "avatar_url":"https://a",
                  "text":"hello",
                  "created_at":1700000000,
                  "system":false,
                  "favorited_by":["u1","u2"],
                  "attachments":[{"type":"image","url":"https://i.groupme.com/a.png"}]
                }
                """.trimIndent(),
            ),
        )!!
        assertEquals("m1", message.id)
        assertEquals("g1", message.conversationId)
        assertEquals(2, message.likeCount)
        assertTrue(message.likedByMe("u1"))
        assertFalse(message.likedByMe("u9"))
        assertEquals("https://i.groupme.com/a.png", message.files.single().previewUrl)
        assertTrue(message.files.single().isImage)
    }

    @Test
    fun skipsMentionAttachmentsAndKeepsLocationName() {
        val message = GroupMeCodec.message(
            TinyJson.parseObject(
                """
                {
                  "id":"m2",
                  "text":"meet here",
                  "created_at":1,
                  "attachments":[
                    {"type":"mentions","user_ids":["u1"],"loci":[[0,4]]},
                    {"type":"location","name":"The Flag","lat":"1","lng":"2"}
                  ]
                }
                """.trimIndent(),
            ),
        )!!
        assertTrue(message.text.contains("meet here"))
        assertTrue(message.text.contains("The Flag"))
        assertTrue(message.files.isEmpty())
    }

    @Test
    fun buildsDirectMessageLikeId() {
        assertEquals("1+9", GroupMeCodec.likeConversationId("9", "1"))
        assertEquals("dm:42", GroupMeCodec.dmId("42"))
        assertTrue(GroupMeCodec.isDirect("dm:42"))
        assertEquals("42", GroupMeCodec.otherUserId("dm:42"))
    }
}
