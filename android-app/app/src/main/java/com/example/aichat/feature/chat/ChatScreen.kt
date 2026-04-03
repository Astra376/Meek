package com.example.aichat.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.design.AppIcon
import com.example.aichat.core.design.AppIcons
import com.example.aichat.core.design.AppTextField
import com.example.aichat.core.design.CharacterPortrait
import com.example.aichat.core.design.IconCircleButton
import com.example.aichat.core.ui.AppBackButton
import com.example.aichat.core.ui.AppChrome
import com.example.aichat.core.ui.clearFocusOnTap
import com.example.aichat.core.model.ChatMessage
import com.example.aichat.core.model.ConversationDetail
import com.example.aichat.core.model.MessageRole
import com.example.aichat.core.model.StreamingDraft
import com.example.aichat.core.util.formatRelativeTime
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val conversation: ConversationDetail? = null,
    val draft: StreamingDraft? = null,
    val composerText: String = ""
) {
    val isStreaming: Boolean get() = draft != null
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository
) : ViewModel() {
    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])
    private val composerText = MutableStateFlow("")
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.observeConversation(conversationId),
        chatRepository.observeStreamingDraft(conversationId),
        composerText
    ) { conversation, draft, composer ->
        ChatUiState(
            conversation = conversation,
            draft = draft,
            composerText = composer
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState()
    )

    init {
        viewModelScope.launch {
            chatRepository.refreshConversation(conversationId)
                .onFailure { _events.emit(it.message ?: "Failed to load conversation.") }
        }
    }

    fun onComposerChanged(value: String) {
        composerText.value = value
    }

    fun send() {
        val text = composerText.value.trim()
        if (text.isBlank()) return
        composerText.value = ""
        viewModelScope.launch {
            chatRepository.sendMessage(conversationId, text)
                .onFailure {
                    composerText.value = text
                    _events.emit(it.message ?: "Message send failed.")
                }
        }
    }

    fun editMessage(messageId: String, newContent: String) {
        viewModelScope.launch {
            chatRepository.editMessage(messageId, newContent)
                .onFailure { _events.emit(it.message ?: "Edit failed.") }
        }
    }

    fun rewind(messageId: String) {
        viewModelScope.launch {
            chatRepository.rewind(messageId)
                .onFailure { _events.emit(it.message ?: "Rewind failed.") }
        }
    }

    fun regenerateLatestAssistant(messageId: String) {
        viewModelScope.launch {
            chatRepository.regenerateLatestAssistant(messageId)
                .onFailure { _events.emit(it.message ?: "Regeneration failed.") }
        }
    }

    fun selectRegeneration(messageId: String, regenerationId: String) {
        viewModelScope.launch {
            chatRepository.selectRegeneration(messageId, regenerationId)
                .onFailure { _events.emit(it.message ?: "Couldn't switch variant.") }
        }
    }
}

@Composable
fun ChatRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var actionMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(state.conversation?.messages?.size, state.draft?.content) {
        val totalItems = (state.conversation?.messages?.size ?: 0) + if (state.draft != null) 1 else 0
        if (totalItems > 0) {
            listState.scrollToItem(totalItems - 1)
        }
    }

    Scaffold(
        modifier = Modifier.clearFocusOnTap(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatHeader(
                characterName = state.conversation?.character?.name ?: "Chat",
                avatarUrl = state.conversation?.character?.avatarUrl,
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clearFocusOnTap()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(
                    start = AppChrome.screenHorizontalPadding,
                    top = innerPadding.calculateTopPadding() + AppChrome.compactHeaderVerticalPadding,
                    end = AppChrome.screenHorizontalPadding,
                    bottom = AppChrome.gridSpacing
                ),
                verticalArrangement = Arrangement.spacedBy(AppChrome.compactControlGap)
            ) {
                state.conversation?.messages?.let { messages ->
                    val latestAssistantId = messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            isLatestAssistant = message.id == latestAssistantId,
                            onTap = { focusManager.clearFocus(force = true) },
                            onLongPress = { actionMessage = message },
                            onSelectPreviousVariant = {
                                val index = message.regenerations.indexOfFirst { it.id == message.selectedRegenerationId }.coerceAtLeast(0)
                                if (index > 0) {
                                    viewModel.selectRegeneration(message.id, message.regenerations[index - 1].id)
                                }
                            },
                            onSelectNextVariant = {
                                val index = message.regenerations.indexOfFirst { it.id == message.selectedRegenerationId }.coerceAtLeast(0)
                                if (index < message.regenerations.lastIndex) {
                                    viewModel.selectRegeneration(message.id, message.regenerations[index + 1].id)
                                }
                            }
                        )
                    }
                }
                state.draft?.let { draft ->
                    item(key = "draft-${draft.conversationId}") {
                        DraftBubble(content = draft.content)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(
                        horizontal = AppChrome.screenHorizontalPadding,
                        vertical = AppChrome.screenTopPadding
                    ),
                horizontalArrangement = Arrangement.spacedBy(AppChrome.compactControlGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTextField(
                    value = state.composerText,
                    onValueChange = viewModel::onComposerChanged,
                    placeholder = "Message...",
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp, max = 160.dp),
                    minLines = 1,
                    maxLines = 6,
                    shape = RoundedCornerShape(999.dp)
                )
                IconCircleButton(
                    selected = state.composerText.isNotBlank() && !state.isStreaming,
                    enabled = !state.isStreaming && state.composerText.isNotBlank(),
                    onClick = viewModel::send
                ) {
                    AppIcon(AppIcons.send, contentDescription = "Send")
                }
            }
        }
    }

    actionMessage?.let { message ->
        val isLatestAssistant = state.conversation?.messages?.lastOrNull { it.role == MessageRole.ASSISTANT }?.id == message.id
        MessageActionsDialog(
            canRegenerate = isLatestAssistant,
            onDismiss = { actionMessage = null },
            onEdit = {
                editTarget = message
                editText = message.visibleContent
                actionMessage = null
            },
            onRewind = {
                viewModel.rewind(message.id)
                actionMessage = null
            },
            onRegenerate = {
                viewModel.regenerateLatestAssistant(message.id)
                actionMessage = null
            }
        )
    }

    editTarget?.let { message ->
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("Edit Message") },
            text = {
                AppTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    placeholder = "Message",
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.editMessage(message.id, editText)
                        editTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatHeader(
    characterName: String,
    avatarUrl: String?,
    onBack: () -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
    ) {
        Column {
            Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(
                            horizontal = AppChrome.screenHorizontalPadding,
                            vertical = AppChrome.compactHeaderVerticalPadding
                        ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppBackButton(onClick = onBack)
                Spacer(modifier = Modifier.size(AppChrome.compactControlGap))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(AppChrome.gridSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CharacterPortrait(
                        name = characterName,
                        avatarUrl = avatarUrl,
                        modifier = Modifier
                            .size(40.dp)
                            .aspectRatio(1f)
                    )
                    Text(
                        text = characterName,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(background, background.copy(alpha = 0f))
                        )
                    )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isLatestAssistant: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSelectPreviousVariant: () -> Unit,
    onSelectNextVariant: () -> Unit
) {
    val isUser = message.role == MessageRole.USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = onLongPress
                ),
            shape = RoundedCornerShape(24.dp),
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = message.visibleContent,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append(formatRelativeTime(message.updatedAt))
                        if (message.edited) append(" (Edited)")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        if (isLatestAssistant && message.regenerations.size > 1) {
            val currentIndex = message.regenerations.indexOfFirst { it.id == message.selectedRegenerationId }.coerceAtLeast(0)
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSelectPreviousVariant, enabled = currentIndex > 0) {
                    AppIcon(AppIcons.previous, contentDescription = "Previous variant")
                }
                Text(
                    text = "Variant ${currentIndex + 1}/${message.regenerations.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onSelectNextVariant, enabled = currentIndex < message.regenerations.lastIndex) {
                    AppIcon(AppIcons.next, contentDescription = "Next variant")
                }
            }
        }
    }
}

@Composable
private fun DraftBubble(content: String) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (content.isBlank()) "Thinking..." else content,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun MessageActionsDialog(
    canRegenerate: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRewind: () -> Unit,
    onRegenerate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message Actions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Long-press actions are destructive where noted.")
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onRewind) { Text("Rewind") }
                if (canRegenerate) {
                    TextButton(onClick = onRegenerate) { Text("Regenerate") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
