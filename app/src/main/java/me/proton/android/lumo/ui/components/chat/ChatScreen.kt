package me.proton.android.lumo.ui.components.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.proton.android.lumo.R
import me.proton.android.lumo.chat.viewmodel.ChatViewModel
import me.proton.android.lumo.data.db.entity.MessageEntity
import me.proton.android.lumo.data.db.entity.MessageStatus
import me.proton.android.lumo.ui.theme.LumoTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLumoManager: () -> Unit,
    onOpenSpeech: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                is ChatViewModel.ChatEvent.SpeechTranscript -> {
                    input = if (input.isBlank()) ev.text else "$input ${ev.text}"
                }
                is ChatViewModel.ChatEvent.ShowError -> Unit
            }
        }
    }

    val lumo = state.currentLumo

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.lumo_icon),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = lumo?.name ?: "Lumo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            state.currentConversation?.title?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LumoTheme.colors.textWeak,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Conversations")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.newConversation() }) {
                        Icon(Icons.Filled.Add, contentDescription = "New chat")
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Manage Lumos") },
                            onClick = { menuOpen = false; onOpenLumoManager() },
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { menuOpen = false; onOpenSettings() },
                        )
                    }
                },
            )
        },
        bottomBar = {
            MessageInput(
                text = input,
                onTextChange = { input = it },
                isGenerating = state.isGenerating,
                onSend = {
                    if (input.isNotBlank()) {
                        viewModel.send(input)
                        input = ""
                        keyboard?.hide()
                    }
                },
                onCancel = { viewModel.cancelStreaming() },
                onMic = onOpenSpeech,
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            if (state.showApiConfigBanner) {
                ApiConfigBanner(onConfigure = onOpenSettings)
            }

            if (state.messages.isEmpty() && !state.isGenerating) {
                EmptyChatState(lumoName = lumo?.name ?: "Lumo")
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageBubble(msg, lumo?.accentColor ?: "#6D4AFF")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: MessageEntity, accentColorHex: String) {
    val accent = remember(accentColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(accentColorHex)) }
            .getOrDefault(Color(0xFF6D4AFF))
    }
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            CatAvatar(accent)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    if (isUser) RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp)
                    else RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)
                )
                .background(
                    if (isUser) accent
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (message.status == MessageStatus.STREAMING && message.content.isBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lumo is thinking…", color = if (isUser) Color.White else LumoTheme.colors.textNorm)
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                Text(
                    text = message.content,
                    color = if (isUser) Color.White else LumoTheme.colors.textNorm,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (message.status == MessageStatus.ERROR && message.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Error: ${message.error}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun CatAvatar(accent: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.lumo_icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun EmptyChatState(lumoName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.lumo_cat_on_laptop),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(180.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Talk to $lumoName",
            style = MaterialTheme.typography.titleLarge,
            color = LumoTheme.colors.textNorm,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Send a message below to get started. Your chats are saved on this device and never deleted.",
            style = MaterialTheme.typography.bodyMedium,
            color = LumoTheme.colors.textWeak,
        )
    }
}

@Composable
private fun ApiConfigBanner(onConfigure: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "No API key set — open Settings to configure your LLM.",
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onConfigure) {
            Text("Configure")
        }
    }
}

@Composable
private fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onMic: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        IconButton(onClick = onMic) {
            Icon(Icons.Filled.Mic, contentDescription = "Speak")
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            placeholder = { Text("Message Lumo…") },
            maxLines = 6,
            shape = RoundedCornerShape(20.dp),
        )
        if (isGenerating) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Stop, contentDescription = "Stop")
            }
        } else {
            IconButton(onClick = onSend, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
