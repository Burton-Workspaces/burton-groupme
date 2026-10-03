package com.burton.groupme.data.repository

import com.burton.groupme.BuildConfig
import com.burton.groupme.data.groupme.GroupMeApi
import com.burton.groupme.data.groupme.GroupMeApiException
import com.burton.groupme.data.groupme.GroupMeAuth
import com.burton.groupme.data.groupme.GroupMeOAuth
import com.burton.groupme.data.groupme.TokenHolder
import com.burton.groupme.data.parse.GroupMeCodec
import com.burton.groupme.data.parse.TinyJson.objList
import com.burton.groupme.data.parse.TinyJson.responseList
import com.burton.groupme.data.parse.TinyJson.responseObj
import com.burton.groupme.domain.ChannelHistory
import com.burton.groupme.domain.Conversation
import com.burton.groupme.domain.GroupMeMessage
import com.burton.groupme.domain.GroupMeSnapshot
import com.burton.groupme.domain.GroupMeUser
import com.burton.groupme.domain.SearchHit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupMeRepository @Inject constructor(
    private val api: GroupMeApi,
    private val prefs: LocalPrefs,
    private val tokens: TokenHolder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val hydrateLock = Mutex()

    private val _state = MutableStateFlow(GroupMeSnapshot())
    val state: StateFlow<GroupMeSnapshot> = _state.asStateFlow()

    private val _histories = MutableStateFlow<Map<String, ChannelHistory>>(emptyMap())
    val histories: StateFlow<Map<String, ChannelHistory>> = _histories.asStateFlow()

    private var started = false
    private var pollJob: Job? = null
    private var channelPoll: Job? = null
    private var openChannelId: String? = null
    private val leftGroupIds = ConcurrentHashMap.newKeySet<String>()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            prefs.auth.distinctUntilChanged().collect { auth ->
                tokens.apply(auth)
                if (!auth.isPresent) {
                    pollJob?.cancel()
                    channelPoll?.cancel()
                    leftGroupIds.clear()
                    _state.value = GroupMeSnapshot()
                    _histories.value = emptyMap()
                } else {
                    _state.update { it.copy(tokenPresent = true, scanning = true, error = null) }
                    runCatching { hydrate() }
                        .onFailure { fail(it) }
                    startPolling()
                }
            }
        }
    }

    suspend fun beginOAuth(): String {
        val clientId = BuildConfig.GROUPME_CLIENT_ID
        if (clientId.isBlank()) {
            throw GroupMeApiException("oauth.authorize", "missing_client_id")
        }
        prefs.setPendingOauth(GroupMeOAuth.randomState())
        return GroupMeOAuth.authorizeUrl(clientId)
    }

    suspend fun completeOAuth(token: String, state: String) {
        _state.update { it.copy(scanning = true, error = null) }
        try {
            if (token.isBlank()) {
                throw GroupMeApiException("oauth.callback", "oauth_missing_token")
            }
            val pending = prefs.pendingOauth.first()
            if (pending != null && state.isNotBlank() && pending.state != state) {
                throw GroupMeApiException("oauth.callback", "oauth_state_mismatch")
            }
            tokens.apply(GroupMeAuth(accessToken = token))
            hydrate(token = token)
            persistAuth(GroupMeAuth(accessToken = token))
        } catch (error: Exception) {
            fail(error)
            throw error
        }
    }

    fun failOauth(message: String) {
        _state.update { it.copy(scanning = false, error = message) }
    }

    suspend fun signIn(token: String) {
        val trimmed = token.trim()
        require(trimmed.isNotBlank()) { "Token is blank" }
        tokens.apply(GroupMeAuth(accessToken = trimmed))
        _state.update { it.copy(tokenPresent = true, scanning = true, error = null) }
        try {
            hydrate(token = trimmed)
            persistAuth(GroupMeAuth(accessToken = trimmed))
        } catch (error: Exception) {
            tokens.apply(prefs.auth.first())
            fail(error)
            throw error
        }
    }

    suspend fun signOut() {
        pollJob?.cancel()
        channelPoll?.cancel()
        tokens.clear()
        prefs.clearToken()
        leftGroupIds.clear()
        _state.value = GroupMeSnapshot()
        _histories.value = emptyMap()
    }

    fun refresh() {
        scope.launch {
            runCatching { hydrate() }.onFailure { fail(it) }
        }
    }

    fun openChannel(channelId: String) {
        openChannelId = channelId
        scope.launch {
            runCatching { loadHistory(channelId, older = false) }.onFailure { failHistory(channelId, it) }
        }
        channelPoll?.cancel()
        channelPoll = scope.launch {
            while (isActive) {
                delay(CHANNEL_POLL_MS)
                val id = openChannelId ?: continue
                runCatching { loadHistory(id, older = false) }
            }
        }
    }

    fun closeChannel(channelId: String) {
        if (openChannelId == channelId) {
            openChannelId = null
            channelPoll?.cancel()
        }
    }

    fun loadOlder(channelId: String) {
        scope.launch {
            runCatching { loadHistory(channelId, older = true) }.onFailure { failHistory(channelId, it) }
        }
    }

    suspend fun send(channelId: String, text: String) {
        val guid = UUID.randomUUID().toString()
        if (GroupMeCodec.isDirect(channelId)) {
            val recipient = GroupMeCodec.otherUserId(channelId)
            callPost(
                "direct_messages",
                mapOf(
                    "direct_message" to mapOf(
                        "source_guid" to guid,
                        "recipient_id" to recipient,
                        "text" to text,
                    ),
                ),
            )
        } else {
            callPost(
                "groups/$channelId/messages",
                mapOf(
                    "message" to mapOf(
                        "source_guid" to guid,
                        "text" to text,
                    ),
                ),
            )
        }
        loadHistory(channelId, older = false)
    }

    suspend fun leave(channelId: String) {
        try {
            if (GroupMeCodec.isDirect(channelId) || channelId.isBlank()) {
                throw GroupMeApiException("groups.leave", "direct_cannot_leave")
            }
            val me = _state.value.account?.id.orEmpty()
            val conversation = _state.value.conversation(channelId)
            if (conversation?.createdBy(me) == true) {
                throw GroupMeApiException("groups.leave", "creator_cannot_leave")
            }
            val membershipId = conversation?.membershipId.orEmpty().ifBlank {
                lookupMembershipId(channelId, me)
            }
            if (membershipId.isNotBlank()) {
                callPost("groups/$channelId/members/$membershipId/remove")
            } else {
                callPost("groups/$channelId/leave")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw Exception(friendly(error), error)
        }
        leftGroupIds += channelId
        dropConversation(channelId)
        runCatching { hydrate() }
    }

    suspend fun toggleLike(channelId: String, messageId: String) {
        val me = _state.value.account?.id.orEmpty()
        val message = _histories.value[channelId]?.messages?.firstOrNull { it.id == messageId }
        val liked = message?.likedByMe(me) == true
        val conversationId = likeTarget(channelId, message)
        val suffix = if (liked) "unlike" else "like"
        runCatching {
            callPost("messages/$conversationId/$messageId/$suffix")
        }
        loadHistory(channelId, older = false)
    }

    suspend fun search(query: String): List<SearchHit> {
        val needle = query.trim()
        if (needle.isBlank()) return emptyList()
        val lowered = needle.lowercase()
        val snapshot = _state.value
        val fromConversations = snapshot.conversations.mapNotNull { conversation ->
            val haystack = listOf(conversation.name, conversation.topic, conversation.latestText)
                .joinToString(" ")
                .lowercase()
            if (!haystack.contains(lowered)) return@mapNotNull null
            val ts = conversation.latestTs.toLongOrNull() ?: conversation.updatedAt
            SearchHit(
                conversationId = conversation.id,
                conversationName = conversation.title(),
                message = GroupMeMessage(
                    id = "preview-${conversation.id}",
                    conversationId = conversation.id,
                    userId = conversation.otherUserId,
                    username = conversation.title(),
                    avatarUrl = conversation.imageUrl,
                    text = conversation.latestText.ifBlank { conversation.topic }.ifBlank { conversation.title() },
                    createdAt = ts,
                    system = false,
                    likedBy = emptyList(),
                    files = emptyList(),
                ),
            )
        }
        val fromHistories = _histories.value.values.flatMap { history ->
            val conversation = snapshot.conversation(history.channelId)
            history.messages.mapNotNull { message ->
                val haystack = listOf(message.text, message.username).joinToString(" ").lowercase()
                if (!haystack.contains(lowered)) return@mapNotNull null
                SearchHit(
                    conversationId = history.channelId,
                    conversationName = conversation?.title() ?: history.channelId,
                    message = message,
                )
            }
        }
        return (fromHistories + fromConversations)
            .distinctBy { "${it.conversationId}-${it.message.id}-${it.message.text}" }
            .sortedByDescending { it.message.createdAt }
            .take(60)
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(LIST_POLL_MS)
                runCatching { hydrate() }
            }
        }
    }

    private suspend fun hydrate(token: String = "") {
        val authToken = token.ifBlank { requireToken() }
        hydrateLock.withLock {
            _state.update { it.copy(scanning = it.account == null, tokenPresent = true) }
            val meBody = api.get(authToken, "users/me")
            val me = GroupMeCodec.account(meBody.responseObj())
                ?: throw GroupMeApiException("users/me", "missing_user")
            val users = linkedMapOf<String, GroupMeUser>()
            users[me.id] = GroupMeUser(me.id, me.name, me.name, me.imageUrl)
            val groupsRaw = paged(authToken, "groups", mapOf("per_page" to "50"))
            groupsRaw.forEach { group ->
                group.objList("members").mapNotNull(GroupMeCodec::user).forEach { user ->
                    users[user.id] = user
                }
            }
            val chatsRaw = paged(authToken, "chats", mapOf("per_page" to "40"))
            chatsRaw.forEach { chat ->
                val other = (chat["other_user"] as? Map<*, *>)?.entries
                    ?.associate { it.key.toString() to it.value }
                    .orEmpty()
                GroupMeCodec.user(other)?.let { users[it.id] = it }
            }
            val groups = groupsRaw.mapNotNull { GroupMeCodec.group(it, me.id) }
            val chats = chatsRaw.mapNotNull(GroupMeCodec::chat)
            val incoming = groups + chats
            leftGroupIds.removeAll { id -> incoming.none { it.id == id } }
            val conversations = incoming.filterNot { it.id in leftGroupIds }.sortedWith(
                compareByDescending<Conversation> { it.updatedAt }
                    .thenBy { it.title().lowercase() },
            )
            _state.value = GroupMeSnapshot(
                tokenPresent = true,
                account = me,
                conversations = conversations,
                users = users,
                scanning = false,
                error = null,
            )
        }
    }

    private suspend fun paged(
        token: String,
        path: String,
        params: Map<String, String>,
        limitPages: Int = 8,
    ): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>()
        repeat(limitPages) { index ->
            val page = index + 1
            val body = api.get(token, path, params + mapOf("page" to page.toString()))
            val batch = body.responseList()
            if (batch.isEmpty()) return out
            out += batch
            val perPage = params["per_page"]?.toIntOrNull() ?: 50
            if (batch.size < perPage) return out
        }
        return out
    }

    private suspend fun loadHistory(channelId: String, older: Boolean) {
        val current = _histories.value[channelId] ?: ChannelHistory(
            channelId = channelId,
            messages = emptyList(),
            hasOlder = true,
            oldest = "",
            loading = true,
            error = null,
        )
        if (older && !current.hasOlder) return
        putHistory(current.copy(loading = true, error = null))
        val params = mutableMapOf("limit" to if (older) "40" else "80")
        if (older && current.oldest.isNotBlank()) params["before_id"] = current.oldest
        val body = if (GroupMeCodec.isDirect(channelId)) {
            callGet(
                "direct_messages",
                params + mapOf("other_user_id" to GroupMeCodec.otherUserId(channelId)),
            )
        } else {
            callGet("groups/$channelId/messages", params)
        }
        val payload = body.responseObj()
        val incoming = payload.objList("messages").ifEmpty { body.responseList() }
            .mapNotNull { GroupMeCodec.message(it, likeFallback(channelId)) }
            .sortedBy { it.createdAt }
        val merged = if (older) {
            (incoming + current.messages).distinctBy { it.id }.sortedBy { it.createdAt }
        } else {
            mergeNewer(current.messages, incoming)
        }
        val oldest = merged.firstOrNull()?.id.orEmpty()
        putHistory(
            ChannelHistory(
                channelId = channelId,
                messages = merged,
                hasOlder = incoming.size >= (params["limit"]?.toIntOrNull() ?: 80),
                oldest = oldest,
                loading = false,
                error = null,
            ),
        )
    }

    private fun mergeNewer(
        existing: List<GroupMeMessage>,
        incoming: List<GroupMeMessage>,
    ): List<GroupMeMessage> {
        if (existing.isEmpty()) return incoming
        val byId = existing.associateBy { it.id }.toMutableMap()
        incoming.forEach { byId[it.id] = it }
        return byId.values.sortedBy { it.createdAt }
    }

    private suspend fun lookupMembershipId(groupId: String, meId: String): String {
        val body = callGet("groups/$groupId")
        return GroupMeCodec.membershipId(body.responseObj().objList("members"), meId)
    }

    private fun dropConversation(channelId: String) {
        closeChannel(channelId)
        _histories.update { it - channelId }
        _state.update { snapshot ->
            snapshot.copy(conversations = snapshot.conversations.filterNot { it.id == channelId })
        }
    }

    private fun likeTarget(channelId: String, message: GroupMeMessage?): String {
        if (message != null && message.conversationId.isNotBlank() && !GroupMeCodec.isDirect(message.conversationId)) {
            return message.conversationId
        }
        if (!GroupMeCodec.isDirect(channelId)) return channelId
        val me = _state.value.account?.id.orEmpty()
        val other = GroupMeCodec.otherUserId(channelId)
        return message?.conversationId?.takeIf { it.isNotBlank() && '+' in it }
            ?: GroupMeCodec.likeConversationId(me, other)
    }

    private fun likeFallback(channelId: String): String {
        if (!GroupMeCodec.isDirect(channelId)) return channelId
        val me = _state.value.account?.id.orEmpty()
        return GroupMeCodec.likeConversationId(me, GroupMeCodec.otherUserId(channelId))
    }

    private suspend fun callGet(
        path: String,
        params: Map<String, String> = emptyMap(),
    ): Map<String, Any?> = api.get(requireToken(), path, params)

    private suspend fun callPost(
        path: String,
        body: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> = api.post(requireToken(), path, body)

    private fun requireToken(): String {
        val token = tokens.token
        check(token.isNotBlank()) { "not_authed" }
        return token
    }

    private suspend fun persistAuth(auth: GroupMeAuth) {
        tokens.apply(auth)
        prefs.setAuth(auth)
    }

    private fun putHistory(history: ChannelHistory) {
        _histories.update { it + (history.channelId to history) }
    }

    private fun failHistory(channelId: String, error: Throwable) {
        val current = _histories.value[channelId]
        putHistory(
            (current ?: ChannelHistory(channelId, emptyList(), false, "", false, null))
                .copy(loading = false, error = friendly(error)),
        )
    }

    private fun fail(error: Throwable) {
        _state.update {
            it.copy(scanning = false, error = friendly(error), tokenPresent = tokens.token.isNotBlank())
        }
    }

    private fun friendly(error: Throwable): String {
        val code = (error as? GroupMeApiException)?.code ?: error.message.orEmpty()
        return when {
            code == "missing_client_id" ->
                "This build has no GroupMe Client ID. Fill groupme/client-id.txt after creating the GroupMe app."
            code == "oauth_state_mismatch" ->
                "That GroupMe login expired. Tap Connect with GroupMe again."
            code == "oauth_missing_token" || code == "access_denied" ->
                "GroupMe login was cancelled."
            code.contains("unauthorized", ignoreCase = true) ||
                code.contains("invalid", ignoreCase = true) ||
                code == "not_authed" ||
                code.contains("401") ->
                "Session expired. Connect with GroupMe again."
            code.contains("429") || code.contains("rate", ignoreCase = true) ->
                "GroupMe rate-limited this phone. Wait a moment and retry."
            code == "direct_cannot_leave" ->
                "Direct messages cannot be left."
            code == "creator_cannot_leave" || code.contains("creator", ignoreCase = true) ->
                "You created this group, so GroupMe will not let you leave."
            code == "missing_membership" ->
                "Could not find your membership in this group."
            else -> error.message ?: "GroupMe request failed."
        }
    }

    companion object {
        private const val LIST_POLL_MS = 4_000L
        private const val CHANNEL_POLL_MS = 3_000L
    }
}
