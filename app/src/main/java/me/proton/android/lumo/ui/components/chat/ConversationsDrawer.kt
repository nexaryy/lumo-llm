package me.proton.android.lumo.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.proton.android.lumo.R
import me.proton.android.lumo.chat.viewmodel.ChatViewModel
import me.proton.android.lumo.data.db.entity.ConversationEntity
import me.proton.android.lumo.data.db.entity.LumoEntity
import me.proton.android.lumo.ui.theme.LumoTheme

@Composable
fun ConversationsDrawer(
    drawerState: androidx.compose.material3.DrawerState,
    lumo: LumoEntity?,
    conversations: List<ConversationEntity>,
    currentConversationId: Long?,
    onSelectConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    onTogglePin: (ConversationEntity) -> Unit,
    onDeleteConversation: (ConversationEntity) -> Unit,
    onSwitchLumo: () -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader(lumo, onSwitchLumo)
                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Conversations",
                        style = MaterialTheme.typography.labelLarge,
                        color = LumoTheme.colors.textWeak,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onNewConversation) {
                        Icon(Icons.Filled.Add, contentDescription = "New chat")
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(conversations, key = { it.id }) { conv ->
                        ConversationRow(
                            conversation = conv,
                            isSelected = conv.id == currentConversationId,
                            onClick = { onSelectConversation(conv.id) },
                            onTogglePin = { onTogglePin(conv) },
                            onDelete = { onDeleteConversation(conv) },
                        )
                    }
                }

                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = false,
                    onClick = onOpenSettings,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    colors = NavigationDrawerItemDefaults.colors(),
                )
            }
        },
        content = content,
    )
}

@Composable
private fun DrawerHeader(lumo: LumoEntity?, onSwitchLumo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSwitchLumo() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val accent = remember(lumo?.accentColor) {
            runCatching { Color(android.graphics.Color.parseColor(lumo?.accentColor ?: "#6D4AFF")) }
                .getOrDefault(Color(0xFF6D4AFF))
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.lumo_icon),
                contentDescription = null,
                tint = Color.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lumo?.name ?: "Lumo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = lumo?.description?.ifBlank { null } ?: "Tap to switch Lumo",
                style = MaterialTheme.typography.bodySmall,
                color = LumoTheme.colors.textWeak,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onTogglePin) {
            Icon(
                imageVector = if (conversation.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = "Pin",
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
        }
    }
}
