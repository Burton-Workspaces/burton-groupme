package com.burton.groupme.ui.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.burton.groupme.ui.components.BurtonModalSheet
import com.burton.groupme.ui.components.ComposeBar
import com.burton.groupme.ui.components.MessageRow
import com.burton.groupme.ui.components.MessagesSkeleton
import com.burton.groupme.ui.components.compactWith
import com.burton.groupme.ui.theme.BurtonDanger
import com.burton.groupme.ui.theme.BurtonIvory
import com.burton.groupme.ui.theme.BurtonMute
import com.burton.groupme.ui.theme.BurtonSand

@Composable
fun ChannelScreen(
    onBack: () -> Unit,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle(initialValue = null)
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val conversation = snapshot.conversation(viewModel.channelId)
    val title = conversation?.title() ?: "Group"
    val createdByMe = conversation?.createdBy(snapshot.account?.id.orEmpty()) == true
    var showLeave by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val messages = history?.messages.orEmpty()
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = BurtonIvory)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = BurtonIvory,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    conversation?.let { conv ->
                        conv.topic.ifBlank {
                            when {
                                conv.memberCount > 0 -> "${conv.memberCount} members"
                                else -> "${messages.size} messages"
                            }
                        }
                    } ?: "Loading",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BurtonMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (conversation?.isDirect == false) {
                IconButton(onClick = { showLeave = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Group options", tint = BurtonIvory)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            history == null || (history?.loading == true && messages.isEmpty()) -> {
                MessagesSkeleton()
                Spacer(Modifier.weight(1f))
            }
            history?.error != null && messages.isEmpty() -> {
                Text(history?.error ?: "", color = BurtonIvory)
                TextButton(onClick = { viewModel.loadOlder() }) {
                    Text("Retry", color = BurtonSand)
                }
                Spacer(Modifier.weight(1f))
            }
            messages.isEmpty() -> {
                Text("Nothing in this conversation yet.", color = BurtonMute)
                Spacer(Modifier.weight(1f))
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (history?.hasOlder == true) {
                        item(key = "older") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (history?.loading == true) {
                                    CircularProgressIndicator(color = BurtonSand)
                                } else {
                                    TextButton(onClick = viewModel::loadOlder) {
                                        Text("Load older", color = BurtonSand)
                                    }
                                }
                            }
                        }
                    }
                    itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                        MessageRow(
                            message = message,
                            snapshot = snapshot,
                            compact = compactWith(messages.getOrNull(index - 1), message),
                            onLike = { viewModel.like(message.id) },
                        )
                    }
                }
            }
        }
        if (notice != null && !showLeave) {
            Text(notice ?: "", color = BurtonIvory, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = viewModel::clearNotice) { Text("Dismiss", color = BurtonSand) }
        }
        ComposeBar(
            value = draft,
            onValueChange = viewModel::onDraft,
            onSend = viewModel::send,
            placeholder = "Message $title",
            enabled = !busy,
        )
    }
    if (showLeave && conversation?.isDirect == false) {
        LeaveGroupSheet(
            name = title,
            createdByMe = createdByMe,
            busy = busy,
            error = notice,
            onDismiss = {
                showLeave = false
                viewModel.clearNotice()
            },
            onLeave = { viewModel.leave(onBack) },
        )
    }
}

@Composable
private fun LeaveGroupSheet(
    name: String,
    createdByMe: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onLeave: () -> Unit,
) {
    BurtonModalSheet(onDismiss = onDismiss) {
        if (createdByMe) {
            Text("You created this group", style = MaterialTheme.typography.headlineMedium, color = BurtonIvory)
            Spacer(Modifier.height(8.dp))
            Text(
                "GroupMe will not let the creator leave $name.",
                style = MaterialTheme.typography.bodyLarge,
                color = BurtonMute,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDismiss) { Text("Close", color = BurtonSand) }
        } else {
            Text("Leave $name?", style = MaterialTheme.typography.headlineMedium, color = BurtonIvory)
            Spacer(Modifier.height(8.dp))
            Text(
                "You'll stop seeing this group on Home.",
                style = MaterialTheme.typography.bodyLarge,
                color = BurtonMute,
            )
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error, style = MaterialTheme.typography.bodyMedium, color = BurtonIvory)
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onLeave,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = BurtonDanger, contentColor = BurtonIvory),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "Leaving…" else "Leave group")
            }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", color = BurtonSand) }
        }
    }
}
