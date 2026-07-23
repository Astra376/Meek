package com.example.aichat.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.AppIcon
import com.example.aichat.core.design.AppIcons
import com.example.aichat.core.design.AppTextField
import com.example.aichat.core.design.CircleAvatar
import com.example.aichat.core.design.CharacterPortrait
import com.example.aichat.core.design.IconCircleButton
import com.example.aichat.core.design.PrimaryButton
import com.example.aichat.core.design.SecondaryButton
import com.example.aichat.core.model.ChatMessage
import com.example.aichat.core.model.ConversationDetail
import com.example.aichat.core.model.MessageRole
import com.example.aichat.core.model.MessageSendState
import com.example.aichat.core.ui.AppBackButton
import com.example.aichat.core.ui.AppChrome
import com.example.aichat.core.ui.CircleAvatarPlaceholder
import com.example.aichat.core.ui.ShimmerTextLine
import com.example.aichat.core.ui.clearFocusOnTap
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val CHAT_MESSAGE_PAGE_SIZE = 20

data class ChatUiState(
    val conversation: ConversationDetail? = null,
    val activeStream: ActiveAssistantStream? = null,
    val composerText: String = "",
    val currentUserName: String = "You",
    val currentUserAvatarUrl: String? = null,
    val canLoadOlderMessages: Boolean = false
) {
    val isStreaming: Boolean
        get() = activeStream != null && activeStream.status != ActiveStreamStatus.PAUSED
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val conversationRepository: com.example.aichat.feature.chatlist.ConversationRepository,
    private val chatBackgroundRepository: ChatBackgroundRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])
    private val composerText = MutableStateFlow("")
    private val loadedMessageLimit = MutableStateFlow(CHAT_MESSAGE_PAGE_SIZE)
    private val _events = MutableSharedFlow<String>()
    private var activeStreamJob: Job? = null
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            chatRepository.refreshConversation(conversationId)
                .onFailure { _events.emit(it.message ?: "Couldn't load conversation.") }
            chatBackgroundRepository.ensureInitialBackground(conversationId)
                .onFailure { _events.emit(it.message ?: "Couldn't generate background scene.") }
            conversationRepository.markConversationRead(conversationId)
        }
    }

    val uiState: StateFlow<ChatUiState> = combine(
        loadedMessageLimit.flatMapLatest { limit ->
            chatRepository.observeConversation(conversationId, limit)
        },
        chatRepository.observeActiveStream(conversationId),
        composerText,
        authRepository.sessionState,
        chatRepository.observeMessageCount(conversationId)
    ) { conversation, activeStream, composer, session, messageCount ->
        ChatUiState(
            conversation = conversation,
            activeStream = activeStream,
            composerText = composer,
            currentUserName = session.profile?.displayName ?: "You",
            currentUserAvatarUrl = session.profile?.avatarUrl,
            canLoadOlderMessages = conversation != null && conversation.messages.size < messageCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState()
    )

    fun onComposerChanged(value: String) {
        composerText.value = value
    }

    fun loadOlderMessages() {
        loadedMessageLimit.value += CHAT_MESSAGE_PAGE_SIZE
    }

    fun send() {
        val text = composerText.value.trim()
        if (text.isBlank()) return
        composerText.value = ""
        launchStreamingAction {
            chatRepository.sendMessage(conversationId, text)
                .onFailure { error ->
                    val shouldRestoreComposer = error !is SendMessageFailedException || !error.accepted
                    if (shouldRestoreComposer && composerText.value.isBlank()) {
                        composerText.value = text
                    }
                    _events.emit(error.message ?: "Message send failed.")
                }
                .onSuccess {
                    chatBackgroundRepository.refreshIfSceneChanged(conversationId)
                        .onFailure { _events.emit(it.message ?: "Background scene update failed.") }
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
        launchStreamingAction {
            chatRepository.regenerateLatestAssistant(messageId)
                .onFailure { _events.emit(it.message ?: "Regeneration failed.") }
                .onSuccess {
                    chatBackgroundRepository.refreshIfSceneChanged(conversationId)
                        .onFailure { _events.emit(it.message ?: "Background scene update failed.") }
                }
        }
    }

    fun continueAssistant() {
        launchStreamingAction {
            chatRepository.continueAssistant(conversationId)
                .onFailure { _events.emit(it.message ?: "Couldn't continue chat.") }
                .onSuccess {
                    chatBackgroundRepository.refreshIfSceneChanged(conversationId)
                        .onFailure { _events.emit(it.message ?: "Background scene update failed.") }
                }
        }
    }

    fun pauseStreaming() {
        val activeStream = uiState.value.activeStream ?: return
        if (activeStream.status == ActiveStreamStatus.COMPLETED) {
            chatRepository.dismissSettledStream(conversationId, activeStream.draftKey)
            return
        }
        chatRepository.requestPause(conversationId)
        activeStreamJob?.cancel()
        activeStreamJob = null
    }

    fun dismissSettledStream(draftKey: String) {
        chatRepository.dismissSettledStream(conversationId, draftKey)
    }

    private fun launchStreamingAction(block: suspend () -> Unit) {
        activeStreamJob?.cancel()
        val job = viewModelScope.launch {
            try {
                block()
            } finally {
                if (activeStreamJob === this.coroutineContext[Job]) {
                    activeStreamJob = null
                }
            }
        }
        activeStreamJob = job
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
    var actionMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    val messages = state.conversation?.messages.orEmpty()

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(state.isStreaming) {
        if (state.isStreaming) {
            actionMessage = null
            editTarget = null
        }
    }

    ChatScreenContent(
        paddingValues = paddingValues,
        onBack = onBack,
        state = state,
        snackbarHostState = snackbarHostState,
        onComposerChanged = viewModel::onComposerChanged,
        onSend = viewModel::send,
        onContinue = viewModel::continueAssistant,
        onPause = viewModel::pauseStreaming,
        onStreamPresentationSettled = viewModel::dismissSettledStream,
        onLoadOlderMessages = viewModel::loadOlderMessages,
        onMessageLongPress = { actionMessage = it },
        onSelectVariant = { message, index ->
            viewModel.selectRegeneration(message.id, message.variantIdAt(index))
        },
        onSelectPreviousVariant = { message ->
            val index = message.variantIndex()
            if (index > 0) {
                val previousId = message.variantIdAt(index - 1)
                viewModel.selectRegeneration(message.id, previousId)
            }
        },
        onSelectNextVariant = { message ->
            val index = message.variantIndex()
            if (index < message.variantCount() - 1) {
                viewModel.selectRegeneration(message.id, message.variantIdAt(index + 1))
            } else {
                viewModel.regenerateLatestAssistant(message.id)
            }
        }
    )

    actionMessage?.let { message ->
        val isLatestAssistant = messages.firstOrNull { it.role == MessageRole.ASSISTANT && it.sendState == MessageSendState.SENT }?.id == message.id
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
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surface,
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
                PrimaryButton(
                    text = "Save",
                    onClick = {
                        viewModel.editMessage(message.id, editText)
                        editTarget = null
                    }
                )
            },
            dismissButton = {
                SecondaryButton(text = "Cancel", onClick = { editTarget = null })
            }
        )
    }
}

@Composable
internal fun ChatScreenContent(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    state: ChatUiState,
    snackbarHostState: SnackbarHostState,
    onComposerChanged: (String) -> Unit,
    onSend: () -> Unit,
    onContinue: () -> Unit,
    onPause: () -> Unit = {},
    onStreamPresentationSettled: (String) -> Unit = {},
    onLoadOlderMessages: () -> Unit,
    onMessageLongPress: (ChatMessage) -> Unit,
    onSelectVariant: (ChatMessage, Int) -> Unit,
    onSelectPreviousVariant: (ChatMessage) -> Unit,
    onSelectNextVariant: (ChatMessage) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    var followLatest by rememberSaveable { mutableStateOf(true) }
    var autoScrolling by remember { mutableStateOf(false) }

    val messages = state.conversation?.messages.orEmpty()
    val activeStream = state.activeStream
    val committedSendMessage = activeStream?.assistantMessageId?.let { assistantMessageId ->
        messages.firstOrNull { message ->
            message.id == assistantMessageId &&
                message.role == MessageRole.ASSISTANT &&
                message.sendState == MessageSendState.SENT
        }
    }
    val committedRegenerateMessage = activeStream?.targetMessageId?.let { messageId ->
        messages.firstOrNull { it.id == messageId }
    }
    val streamSourceText = when {
        activeStream == null -> ""
        activeStream.mode == ActiveStreamMode.SEND -> committedSendMessage?.visibleContent ?: activeStream.text
        activeStream.committedRegenerationId != null -> committedRegenerateMessage?.visibleContent ?: activeStream.text
        else -> activeStream.text
    }
    val committedStreamVisible = when {
        activeStream == null -> false
        activeStream.mode == ActiveStreamMode.SEND -> committedSendMessage != null
        activeStream.committedRegenerationId != null ->
            committedRegenerateMessage?.selectedRegenerationId == activeStream.committedRegenerationId
        else -> false
    }
    val streamDisplayText = rememberTypedStreamText(
        streamKey = activeStream?.draftKey,
        sourceText = streamSourceText,
        animate = activeStream?.status != ActiveStreamStatus.PAUSED,
        settleOnFinish = activeStream?.status == ActiveStreamStatus.COMPLETED && committedStreamVisible,
        onSettled = onStreamPresentationSettled
    )
    val showSendDraft = activeStream?.mode == ActiveStreamMode.SEND && committedSendMessage == null
    val latestItemIndex = 0
    val oldestLoadedItemIndex = messages.size + (if (showSendDraft) 1 else 0) - 1
    val isNearBottom by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex <= 0 && listState.firstVisibleItemScrollOffset < 24
        }
    }
    val isNearOldestLoaded by remember(listState, oldestLoadedItemIndex, state.canLoadOlderMessages) {
        derivedStateOf {
            if (!state.canLoadOlderMessages || oldestLoadedItemIndex < 0) {
                false
            } else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
                lastVisibleIndex >= oldestLoadedItemIndex - 4
            }
        }
    }
    val showJumpToLatest = messages.isNotEmpty() && !followLatest && !isNearBottom
    val imeBottom = WindowInsets.ime.getBottom(density)
    val isActiveStream = activeStream != null

    suspend fun scrollToLatest(animated: Boolean) {
        autoScrolling = true
        try {
            if (animated) {
                listState.animateScrollToItem(latestItemIndex)
            } else {
                listState.scrollToItem(latestItemIndex)
            }
        } finally {
            autoScrolling = false
        }
    }

    LaunchedEffect(isNearOldestLoaded, messages.size) {
        if (isNearOldestLoaded) {
            onLoadOlderMessages()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to isNearBottom }.collect { (isScrolling, atBottom) ->
            when {
                atBottom && !followLatest -> followLatest = true
                isScrolling && !autoScrolling && !atBottom && followLatest -> followLatest = false
            }
        }
    }

    LaunchedEffect(
        followLatest,
        streamDisplayText.length,
        activeStream?.status,
        showSendDraft,
        messages.firstOrNull()?.id,
        imeBottom
    ) {
        if (followLatest) {
            scrollToLatest(animated = !isActiveStream)
        }
    }

    Scaffold(
        modifier = Modifier.clearFocusOnTap(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatHeader(
                characterName = state.conversation?.character?.name ?: "Chat",
                avatarUrl = state.conversation?.character?.avatarUrl,
                isLoading = state.conversation == null,
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clearFocusOnTap()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            ChatSceneBackground(imageUrl = state.conversation?.backgroundSceneUrl)
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.conversation == null) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    ChatTranscriptPane(
                        modifier = Modifier.weight(1f),
                        state = listState,
                        messages = messages,
                        activeStream = activeStream,
                        streamDisplayText = streamDisplayText,
                        isStreaming = state.isStreaming,
                        showSendDraft = showSendDraft,
                        showPausedIndicator = activeStream?.status == ActiveStreamStatus.PAUSED,
                        characterName = state.conversation.character.name,
                        characterAvatarUrl = state.conversation.character.avatarUrl,
                        currentUserName = state.currentUserName,
                        currentUserAvatarUrl = state.currentUserAvatarUrl,
                        showJumpToLatest = showJumpToLatest,
                        contentPadding = PaddingValues(
                            start = AppChrome.screenHorizontalPadding,
                            top = innerPadding.calculateTopPadding() + AppChrome.compactHeaderVerticalPadding,
                            end = AppChrome.screenHorizontalPadding,
                            bottom = AppChrome.gridSpacing +
                                if (activeStream?.status == ActiveStreamStatus.PAUSED) 44.dp else 0.dp
                        ),
                        onJumpToLatest = {
                            followLatest = true
                            coroutineScope.launch {
                                scrollToLatest(animated = true)
                            }
                        },
                        onMessageTap = { focusManager.clearFocus(force = true) },
                        onMessageLongPress = onMessageLongPress,
                        onSelectVariant = onSelectVariant,
                        onSelectPreviousVariant = onSelectPreviousVariant,
                        onSelectNextVariant = onSelectNextVariant
                    )
                }

                ChatComposerBar(
                    composerText = state.composerText,
                    isStreaming = state.isStreaming,
                    canContinue = state.conversation?.messages?.firstOrNull { it.sendState == MessageSendState.SENT }?.role == MessageRole.ASSISTANT,
                    onComposerChanged = onComposerChanged,
                    onSend = {
                        followLatest = true
                        onSend()
                    },
                    onContinue = {
                        followLatest = true
                        onContinue()
                    },
                    onPause = onPause
                )
            }
        }
    }
}

@Composable
private fun ChatSceneBackground(imageUrl: String?) {
    val fallback = MaterialTheme.colorScheme.background
    val requestedUrl = imageUrl?.takeIf { it.startsWith("http") }
    var visibleUrl by remember { mutableStateOf<String?>(null) }
    var pendingUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(requestedUrl) {
        if (requestedUrl != visibleUrl && requestedUrl != pendingUrl) {
            pendingUrl = requestedUrl
        }
    }

    val visiblePainter = rememberAsyncImagePainter(visibleUrl)
    val pendingPainter = rememberAsyncImagePainter(pendingUrl)
    val pendingReady = pendingUrl == null || pendingPainter.state is AsyncImagePainter.State.Success
    val pendingAlpha by animateFloatAsState(
        targetValue = if (pendingUrl != null && pendingReady) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "chat-background-image-alpha",
        finishedListener = { alpha ->
            if (alpha == 1f && pendingUrl != null) {
                visibleUrl = pendingUrl
                pendingUrl = null
            }
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(fallback)
    ) {
        if (visibleUrl != null) {
            Image(
                painter = visiblePainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (pendingUrl != null && pendingReady) {
            Image(
                painter = pendingPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = pendingAlpha }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.58f))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.22f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.78f)
                        )
                    )
                )
        )
    }
}

@Composable
internal fun ChatTranscriptPane(
    modifier: Modifier = Modifier,
    state: LazyListState,
    messages: List<ChatMessage>,
    activeStream: ActiveAssistantStream?,
    streamDisplayText: String,
    isStreaming: Boolean,
    showSendDraft: Boolean,
    showPausedIndicator: Boolean,
    characterName: String,
    characterAvatarUrl: String?,
    currentUserName: String,
    currentUserAvatarUrl: String?,
    showJumpToLatest: Boolean,
    contentPadding: PaddingValues,
    onJumpToLatest: () -> Unit,
    onMessageTap: () -> Unit,
    onMessageLongPress: (ChatMessage) -> Unit,
    onSelectVariant: (ChatMessage, Int) -> Unit,
    onSelectPreviousVariant: (ChatMessage) -> Unit,
    onSelectNextVariant: (ChatMessage) -> Unit
) {
    val latestAssistantId = messages.firstOrNull {
        it.role == MessageRole.ASSISTANT && it.sendState == MessageSendState.SENT
    }?.id

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("chat-transcript"),
            state = state,
            contentPadding = contentPadding,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(AppChrome.compactControlGap)
        ) {
            if (showSendDraft) {
                item(key = activeStream?.draftKey ?: "send-draft") {
                    DraftBubble(
                        content = streamDisplayText,
                        showTypingIndicator = streamDisplayText.isBlank() &&
                            activeStream?.status != ActiveStreamStatus.PAUSED,
                        characterName = characterName,
                        characterAvatarUrl = characterAvatarUrl
                    )
                }
            }

            items(
                items = messages,
                key = { message ->
                    if (
                        activeStream?.mode == ActiveStreamMode.SEND &&
                        activeStream.assistantMessageId == message.id
                    ) {
                        activeStream.draftKey
                    } else {
                        message.id
                    }
                }
            ) { message ->
                val isActiveSendMessage =
                    activeStream?.mode == ActiveStreamMode.SEND &&
                        activeStream.assistantMessageId == message.id
                val isActiveRegenerate =
                    activeStream?.mode == ActiveStreamMode.REGENERATE &&
                        activeStream.targetMessageId == message.id
                val displayContent = when {
                    isActiveSendMessage || isActiveRegenerate -> streamDisplayText
                    else -> message.visibleContent
                }
                MessageBubble(
                    message = message,
                    displayContent = displayContent,
                    showTypingIndicator = (isActiveSendMessage || isActiveRegenerate) &&
                        displayContent.isBlank() &&
                        activeStream?.status != ActiveStreamStatus.PAUSED,
                    showGenerationPage = isActiveRegenerate &&
                        activeStream?.status != ActiveStreamStatus.PAUSED,
                    isLatestAssistant = message.id == latestAssistantId,
                    actionsEnabled = !isStreaming && message.sendState == MessageSendState.SENT,
                    variantControlsEnabled = !isStreaming,
                    characterName = characterName,
                    characterAvatarUrl = characterAvatarUrl,
                    currentUserName = currentUserName,
                    currentUserAvatarUrl = currentUserAvatarUrl,
                    onTap = onMessageTap,
                    onLongPress = { onMessageLongPress(message) },
                    onSelectVariant = { index -> onSelectVariant(message, index) },
                    onSelectPreviousVariant = { onSelectPreviousVariant(message) },
                    onSelectNextVariant = { onSelectNextVariant(message) }
                )
            }
        }

        if (showJumpToLatest) {
            SecondaryButton(
                text = "Jump to Latest",
                modifier = Modifier
                    .testTag("jump-to-latest")
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = AppChrome.screenHorizontalPadding,
                        bottom = AppChrome.screenBottomPadding
                    ),
                onClick = onJumpToLatest
            )
        }

        if (showPausedIndicator) {
            PausedIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = AppChrome.screenBottomPadding)
            )
        }
    }
}

@Composable
private fun ChatComposerBar(
    composerText: String,
    isStreaming: Boolean,
    canContinue: Boolean,
    onComposerChanged: (String) -> Unit,
    onSend: () -> Unit,
    onContinue: () -> Unit,
    onPause: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(
                horizontal = AppChrome.screenHorizontalPadding,
                vertical = AppChrome.screenTopPadding
            ),
        horizontalArrangement = Arrangement.spacedBy(AppChrome.compactControlGap),
        verticalAlignment = Alignment.Bottom
    ) {
        AppTextField(
            value = composerText,
            onValueChange = onComposerChanged,
            placeholder = "Message...",
            modifier = Modifier
                .testTag("chat-composer")
                .weight(1f)
                .heightIn(min = 46.dp, max = 160.dp),
            minLines = 1,
            maxLines = 6,
            shape = RoundedCornerShape(24.dp)
        )
        val canSend = composerText.isNotBlank()
        val useContinue = !isStreaming && !canSend && canContinue
        IconCircleButton(
            selected = isStreaming || canSend || useContinue,
            enabled = isStreaming || canSend || useContinue,
            onClick = when {
                isStreaming -> onPause
                canSend -> onSend
                else -> onContinue
            }
        ) {
            Crossfade(
                targetState = when {
                    isStreaming -> AppIcons.stop
                    useContinue -> AppIcons.forward
                    else -> AppIcons.send
                },
                label = "chat-send-icon"
            ) { icon ->
                AppIcon(
                    icon,
                    contentDescription = when {
                        isStreaming -> "Pause response"
                        useContinue -> "Continue"
                        else -> "Send"
                    }
                )
            }
        }
    }
}

@Composable
private fun ChatHeader(
    characterName: String,
    avatarUrl: String?,
    isLoading: Boolean,
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
                    if (isLoading) {
                        CircleAvatarPlaceholder(size = 40.dp)
                        ShimmerTextLine(width = 128.dp, height = 22.dp)
                    } else {
                        CharacterPortrait(
                            name = characterName,
                            avatarUrl = avatarUrl,
                            modifier = Modifier
                                .size(40.dp)
                                .aspectRatio(1f)
                        )
                        Text(
                            text = characterName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
    modifier: Modifier = Modifier,
    message: ChatMessage,
    displayContent: String,
    showTypingIndicator: Boolean,
    showGenerationPage: Boolean,
    isLatestAssistant: Boolean,
    actionsEnabled: Boolean,
    variantControlsEnabled: Boolean,
    characterName: String,
    characterAvatarUrl: String?,
    currentUserName: String,
    currentUserAvatarUrl: String?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSelectVariant: (Int) -> Unit,
    onSelectPreviousVariant: () -> Unit,
    onSelectNextVariant: () -> Unit
) {
    val isUser = message.role == MessageRole.USER
    val background = MaterialTheme.colorScheme.background
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f).compositeOver(background)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f).compositeOver(background)
    }
    val avatarName = if (isUser) currentUserName else characterName
    val avatarUrl = if (isUser) currentUserAvatarUrl else characterAvatarUrl
    val variants = remember(message.content, message.regenerations, displayContent, showTypingIndicator, showGenerationPage) {
        if (showTypingIndicator && !showGenerationPage) {
            listOf(displayContent)
        } else {
            val generated = message.variantTexts()
            if (!showGenerationPage && displayContent != message.visibleContent) listOf(displayContent) else generated
        }
    }
    val currentIndex = if (variants.size == message.variantCount()) message.variantIndex() else 0
    val hasGenerationPage = isLatestAssistant && !isUser && (variantControlsEnabled || showGenerationPage)

    if (isLatestAssistant && (variants.size > 1 || hasGenerationPage)) {
        VariantMessagePager(
            variants = variants,
            currentIndex = currentIndex,
            hasGenerationPage = hasGenerationPage,
            generationPageText = if (showGenerationPage) displayContent else "",
            generationPageLoading = showGenerationPage && showTypingIndicator,
            generationRequestEnabled = variantControlsEnabled,
            isUser = isUser,
            avatarName = avatarName,
            avatarUrl = avatarUrl,
            bubbleColor = bubbleColor,
            variantControlsEnabled = variantControlsEnabled,
            onTap = onTap,
            onLongPress = onLongPress,
            onSelectVariant = onSelectVariant,
            onSelectPreviousVariant = onSelectPreviousVariant,
            onSelectNextVariant = onSelectNextVariant,
            modifier = modifier
        )
    } else {
        MessageVariantPage(
            modifier = modifier,
            text = displayContent,
            pageIndex = currentIndex,
            pageCount = variants.size,
            isUser = isUser,
            avatarName = avatarName,
            avatarUrl = avatarUrl,
            bubbleColor = bubbleColor,
            showTypingIndicator = showTypingIndicator,
            showVariantControls = false,
            variantControlsEnabled = variantControlsEnabled,
            onTap = onTap,
            onLongPress = onLongPress,
            onPrevious = onSelectPreviousVariant,
            onNext = onSelectNextVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VariantMessagePager(
    modifier: Modifier = Modifier,
    variants: List<String>,
    currentIndex: Int,
    hasGenerationPage: Boolean,
    generationPageText: String,
    generationPageLoading: Boolean,
    generationRequestEnabled: Boolean,
    isUser: Boolean,
    avatarName: String,
    avatarUrl: String?,
    bubbleColor: Color,
    variantControlsEnabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onSelectVariant: (Int) -> Unit,
    onSelectPreviousVariant: () -> Unit,
    onSelectNextVariant: () -> Unit
) {
    val generationPage = variants.size
    val pageCount = variants.size + if (hasGenerationPage) 1 else 0
    val pagerState = rememberPagerState(
        initialPage = currentIndex,
        pageCount = { pageCount }
    )
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var committedPage by remember(variants.size) {
        mutableStateOf(currentIndex.coerceIn(variants.indices))
    }
    var settledVisualPage by remember(variants.size, hasGenerationPage) {
        mutableStateOf(currentIndex.coerceIn(variants.indices))
    }
    var generationRequested by remember(variants.size, hasGenerationPage) {
        mutableStateOf(false)
    }
    var pageHeights by remember(pageCount) {
        mutableStateOf(List(pageCount) { 0 })
    }

    LaunchedEffect(pagerState, variants.size, hasGenerationPage, generationRequestEnabled) {
        snapshotFlow { pagerState.settledPage to pagerState.isScrollInProgress }.collect { (page, isScrollInProgress) ->
            if (isScrollInProgress) return@collect
            when {
                page == generationPage && hasGenerationPage -> {
                    settledVisualPage = page
                    if (generationRequestEnabled && !generationRequested) {
                        generationRequested = true
                        onSelectNextVariant()
                    }
                }
                page in variants.indices -> {
                    val previousHeight = pageHeights.getOrElse(settledVisualPage) { 0 }
                    val nextHeight = pageHeights.getOrElse(page) { 0 }
                    val shouldPersistSelection = page != committedPage
                    settledVisualPage = page
                    generationRequested = false
                    if (shouldPersistSelection) {
                        committedPage = page
                        onSelectVariant(page)
                    }
                    if (nextHeight > previousHeight) {
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            }
        }
    }

    // The visible height is pinned to the settled page so the container only
    // resizes once a swipe comes to rest (animateContentSize), not while an
    // adjacent, taller variant is peeking in mid-drag. The pager measures its
    // pages unbounded so onSizeChanged still reports each page's full height.
    val density = LocalDensity.current
    val settledHeightPx = pageHeights.getOrElse(settledVisualPage) { 0 }
    val isSettledHeightMeasured = settledHeightPx > 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 220))
            .then(
                if (isSettledHeightMeasured) {
                    Modifier.height(with(density) { settledHeightPx.toDp() })
                } else {
                    Modifier
                }
            )
            .clipToBounds()
            .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSettledHeightMeasured) {
                        Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true)
                    } else {
                        Modifier
                    }
                ),
            pageSpacing = 16.dp,
            beyondViewportPageCount = 0,
            verticalAlignment = Alignment.Top
        ) { page ->
            val isGenerationPage = page == generationPage
            MessageVariantPage(
                modifier = Modifier.onSizeChanged { size ->
                    if (page in pageHeights.indices && pageHeights[page] != size.height) {
                        pageHeights = pageHeights.toMutableList().also { heights ->
                            heights[page] = size.height
                        }
                    }
                },
                text = if (isGenerationPage) generationPageText else variants[page],
                pageIndex = page.coerceAtMost(variants.lastIndex),
                pageCount = variants.size,
                isUser = isUser,
                avatarName = avatarName,
                avatarUrl = avatarUrl,
                bubbleColor = bubbleColor,
                showTypingIndicator = isGenerationPage && (generationPageLoading || generationPageText.isBlank()),
                showVariantControls = !isGenerationPage,
                variantControlsEnabled = variantControlsEnabled,
                onTap = onTap,
                onLongPress = onLongPress,
                onPrevious = {
                    if (pagerState.currentPage > 0) {
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    } else {
                        onSelectPreviousVariant()
                    }
                },
                onNext = {
                    if (pagerState.currentPage < variants.lastIndex) {
                        coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        coroutineScope.launch { pagerState.animateScrollToPage(generationPage) }
                    }
                }
            )
        }
    }
}

@Composable
private fun MessageVariantPage(
    modifier: Modifier = Modifier,
    text: String,
    pageIndex: Int,
    pageCount: Int,
    isUser: Boolean,
    avatarName: String,
    avatarUrl: String?,
    bubbleColor: Color,
    showTypingIndicator: Boolean,
    showVariantControls: Boolean,
    variantControlsEnabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            if (!isUser) {
                CircleAvatar(
                    name = avatarName,
                    avatarUrl = avatarUrl,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
            }

            MessageSurfaceContent(
                bubbleColor = bubbleColor,
                text = text,
                showTypingIndicator = showTypingIndicator,
                onTap = onTap,
                onLongPress = onLongPress,
                modifier = Modifier.weight(1f)
            )

            if (isUser) {
                Spacer(modifier = Modifier.size(8.dp))
                CircleAvatar(
                    name = avatarName,
                    avatarUrl = avatarUrl,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        if (showVariantControls) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconCircleButton(
                    containerSize = 36.dp,
                    enabled = variantControlsEnabled && pageIndex > 0,
                    onClick = onPrevious
                ) {
                    AppIcon(AppIcons.previous, contentDescription = "Previous variant", size = 20.dp)
                }
                Text(
                    text = "Variant ${pageIndex + 1}/$pageCount",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconCircleButton(
                    containerSize = 36.dp,
                    enabled = variantControlsEnabled,
                    onClick = onNext
                ) {
                    AppIcon(AppIcons.next, contentDescription = "Next variant", size = 20.dp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageSurfaceContent(
    bubbleColor: Color,
    text: String,
    showTypingIndicator: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(24.dp),
        color = bubbleColor
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            if (showTypingIndicator) {
                TypingDotsIndicator()
            } else {
                Text(
                    text = roleplayAnnotatedText(text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DraftBubble(
    modifier: Modifier = Modifier,
    content: String,
    showTypingIndicator: Boolean,
    characterName: String,
    characterAvatarUrl: String?
) {
    val background = MaterialTheme.colorScheme.background
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            CircleAvatar(
                name = characterName,
                avatarUrl = characterAvatarUrl,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f).compositeOver(background)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    if (showTypingIndicator) {
                        TypingDotsIndicator()
                    } else {
                        Text(
                            text = roleplayAnnotatedText(content),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun ChatMessage.variantCount(): Int = 1 + regenerations.size

private fun ChatMessage.variantIndex(): Int {
    val selected = selectedRegenerationId ?: return 0
    val regenIndex = regenerations.indexOfFirst { it.id == selected }
    return if (regenIndex == -1) 0 else regenIndex + 1
}

private fun ChatMessage.variantIdAt(index: Int): String {
    return if (index <= 0) ChatRepository.ORIGINAL_VARIANT_ID else regenerations[index - 1].id
}

private fun ChatMessage.variantTexts(): List<String> = listOf(content) + regenerations.map { it.content }

@Composable
private fun roleplayAnnotatedText(value: String) = buildAnnotatedString {
    var index = 0
    while (index < value.length) {
        val triple = value.indexOf("***", index)
        val double = value.indexOf("**", index).let { if (it >= 0 && it != triple) it else -1 }
        val single = value.indexOf("*", index).let { if (it >= 0 && it != triple && it != double) it else -1 }
        val next = listOf(triple, double, single).filter { it >= 0 }.minOrNull() ?: -1
        if (next == -1) {
            append(value.substring(index))
            break
        }
        append(value.substring(index, next))
        val marker = when (next) {
            triple -> "***"
            double -> "**"
            else -> "*"
        }
        val end = value.indexOf(marker, next + marker.length)
        if (end == -1) {
            append(marker)
            index = next + marker.length
            continue
        }
        val style = when (marker) {
            "***" -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            "**" -> SpanStyle(fontWeight = FontWeight.Bold)
            else -> SpanStyle(fontStyle = FontStyle.Italic)
        }
        pushStyle(style)
        append(value.substring(next + marker.length, end))
        pop()
        index = end + marker.length
    }
}

@Composable
private fun TypingDotsIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()
    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val scale by transition.animateFloat(
                initialValue = 0.7f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        0.7f at 0
                        1.15f at 180 + (index * 120)
                        0.7f at 420 + (index * 120)
                        0.7f at 900
                    },
                    repeatMode = RepeatMode.Restart
                )
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(999.dp)
                    )
            )
        }
    }
}

@Composable
private fun PausedIndicator(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("chat-paused"),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
    ) {
        Text(
            text = "Paused",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun rememberTypedStreamText(
    streamKey: String?,
    sourceText: String,
    animate: Boolean,
    settleOnFinish: Boolean,
    onSettled: (String) -> Unit
): String {
    var displayedText by remember(streamKey) { mutableStateOf("") }
    var settled by remember(streamKey) { mutableStateOf(false) }

    LaunchedEffect(streamKey, sourceText, animate) {
        if (streamKey == null) {
            displayedText = sourceText
            return@LaunchedEffect
        }

        if (sourceText.isEmpty()) {
            displayedText = ""
            return@LaunchedEffect
        }

        if (displayedText.length > sourceText.length) {
            displayedText = sourceText
        }

        if (!sourceText.startsWith(displayedText)) {
            displayedText = if (displayedText.isBlank()) {
                ""
            } else {
                sourceText
            }
        }

        if (!animate) {
            displayedText = sourceText
            return@LaunchedEffect
        }

        while (displayedText.length < sourceText.length) {
            delay(18L)
            displayedText = sourceText.take(displayedText.length + 1)
        }
    }

    LaunchedEffect(streamKey, displayedText, sourceText, settleOnFinish) {
        if (!settleOnFinish || settled) return@LaunchedEffect
        if (streamKey != null && sourceText.isNotEmpty() && displayedText == sourceText) {
            settled = true
            onSettled(streamKey)
        }
    }

    return displayedText
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
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Message Actions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose how you want to adjust this message.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SecondaryButton(
                    text = "Edit",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onEdit
                )
                SecondaryButton(
                    text = "Rewind",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRewind
                )
                if (canRegenerate) {
                    SecondaryButton(
                        text = "Regenerate",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onRegenerate
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            SecondaryButton(text = "Close", onClick = onDismiss)
        }
    )
}
