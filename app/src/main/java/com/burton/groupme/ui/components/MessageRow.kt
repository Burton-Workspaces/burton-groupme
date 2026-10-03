package com.burton.groupme.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.burton.groupme.domain.GroupMeMessage
import com.burton.groupme.domain.GroupMeSnapshot
import com.burton.groupme.domain.MessageText
import com.burton.groupme.ui.theme.BurtonCharcoal
import com.burton.groupme.ui.theme.BurtonIvory
import com.burton.groupme.ui.theme.BurtonMute
import com.burton.groupme.ui.theme.BurtonSand
import com.burton.groupme.ui.theme.BurtonVoid
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageRow(
    message: GroupMeMessage,
    snapshot: GroupMeSnapshot,
    compact: Boolean,
    onLike: () -> Unit,
) {
    val user = snapshot.users[message.userId]
    val name = user?.label ?: message.username.ifBlank { snapshot.userLabel(message.userId) }
    val body = MessageText.display(message.text)
    val avatar = user?.imageUrl.orEmpty().ifBlank { message.avatarUrl }
    if (message.isSystem) {
        Text(
            text = body.ifBlank { "System message" },
            style = MaterialTheme.typography.bodyMedium,
            color = BurtonMute,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLike)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (compact) {
            Spacer(Modifier.width(40.dp))
        } else {
            UserAvatar(url = avatar, name = name)
        }
        Column(modifier = Modifier.weight(1f)) {
            if (!compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        color = BurtonIvory,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatTs(message.createdAt), style = MaterialTheme.typography.labelSmall, color = BurtonMute)
                }
                Spacer(Modifier.height(4.dp))
            }
            if (body.isNotBlank()) {
                Text(body, style = MaterialTheme.typography.bodyLarge, color = BurtonIvory)
            }
            message.files.forEach { file ->
                Spacer(Modifier.height(8.dp))
                if (file.isImage && file.previewUrl.isNotBlank()) {
                    AsyncImage(
                        model = file.previewUrl,
                        contentDescription = file.title.ifBlank { file.name },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 280.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BurtonCharcoal),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        file.title.ifBlank { file.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = BurtonSand,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BurtonCharcoal, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
            if (message.likedBy.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val me = snapshot.account?.id.orEmpty()
                    val mine = message.likedByMe(me)
                    Text(
                        "♥ ${message.likeCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (mine) BurtonVoid else BurtonIvory,
                        modifier = Modifier
                            .background(
                                if (mine) BurtonSand else BurtonCharcoal,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

private val clock = SimpleDateFormat("h:mm a", Locale.getDefault())

fun formatTs(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return ""
    return clock.format(Date(epochSeconds * 1000))
}

fun compactWith(previous: GroupMeMessage?, current: GroupMeMessage): Boolean {
    if (previous == null || current.isSystem || previous.isSystem) return false
    if (previous.userId.isBlank() || previous.userId != current.userId) return false
    return current.createdAt - previous.createdAt in 0 until 300
}
