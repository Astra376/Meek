package com.example.aichat.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.AppCard
import com.example.aichat.core.design.AppIcon
import com.example.aichat.core.design.AppIcons
import com.example.aichat.core.design.CharacterPortrait
import com.example.aichat.core.design.CircleAvatar
import com.example.aichat.core.design.SecondaryButton
import com.example.aichat.core.model.CharacterSummary
import com.example.aichat.core.model.ConversationSummary
import com.example.aichat.core.ui.AppChrome
import com.example.aichat.core.ui.CharacterSummaryCard
import com.example.aichat.core.ui.ScreenBackgroundBox
import com.example.aichat.core.ui.screenContentPadding
import com.example.aichat.core.util.formatRelativeTimeWords
import com.example.aichat.feature.chatlist.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewHomeUiState(
    val recentChats: List<ConversationSummary> = emptyList(),
    val totalUnreadCount: Int = 0,
    val topPicks: List<CharacterSummary> = emptyList(),
    val recommendedFeed: List<CharacterSummary> = emptyList(),
    val recommendedCursor: String? = null,
    val isFeedLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class NewHomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val conversationRepository: ConversationRepository,
    private val homeRepository: HomeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewHomeUiState())
    val uiState: StateFlow<NewHomeUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    init {
        val userId = authRepository.sessionState.value.profile?.userId.orEmpty()
        viewModelScope.launch {
            conversationRepository.observeConversations(userId).collect { chats ->
                val unreadCount = chats.sumOf { it.unreadCount }
                val sortedChats = chats.sortedWith(
                    compareByDescending<ConversationSummary> { it.unreadCount > 0 }
                        .thenByDescending { it.hasUnreadBadge }
                        .thenByDescending { it.updatedAt }
                )
                _uiState.value = _uiState.value.copy(
                    recentChats = sortedChats,
                    totalUnreadCount = unreadCount
                )
            }
        }
        refreshFeed()
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFeedLoading = true, errorMessage = null)
            runCatching { homeRepository.loadFeed(cursor = null) }
                .onSuccess { page ->
                    val picks = page.items.take(4)
                    val recs = page.items.drop(4)
                    _uiState.value = _uiState.value.copy(
                        topPicks = picks,
                        recommendedFeed = recs,
                        recommendedCursor = page.nextCursor,
                        isFeedLoading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isFeedLoading = false,
                        errorMessage = "Failed to load the feed."
                    )
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        viewModelScope.launch {
            runCatching { homeRepository.loadFeed(cursor = state.recommendedCursor) }
                .onSuccess { page ->
                    _uiState.value = state.copy(
                        recommendedFeed = state.recommendedFeed + page.items,
                        recommendedCursor = page.nextCursor
                    )
                }
                .onFailure { _events.emit("Couldn't load more characters.") }
        }
    }

    suspend fun ensureConversation(characterId: String): Result<String> {
        val userId = authRepository.sessionState.value.profile?.userId.orEmpty()
        return conversationRepository.ensureConversation(userId, characterId)
    }
}

@Composable
fun NewHomeRoute(
    paddingValues: PaddingValues,
    onOpenConversation: (String) -> Unit,
    onOpenStudio: () -> Unit,
    onOpenChats: () -> Unit,
    viewModel: NewHomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    ScreenBackgroundBox(
        snackbarHostState = snackbarHostState
    ) {
        val gridPadding = screenContentPadding(paddingValues)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = gridPadding.calculateTopPadding(),
                bottom = gridPadding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(AppChrome.sectionSpacing)
        ) {
            item {
                Column {
                    // Unread Header
                    SectionHeader(
                        title = "Unread (${state.totalUnreadCount})",
                        modifier = Modifier.padding(horizontal = AppChrome.screenHorizontalPadding),
                        onClick = onOpenChats
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = AppChrome.screenHorizontalPadding)
                    ) {
                        item {
                            CreateStoryNode(onClick = onOpenStudio)
                        }
                        items(state.recentChats, key = { it.id }) { chat ->
                            StoryNode(
                                chat = chat,
                                onClick = { onOpenConversation(chat.id) }
                            )
                        }
                    }

                    if (state.topPicks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(
                            title = "Top Picks",
                            modifier = Modifier.padding(horizontal = AppChrome.screenHorizontalPadding),
                            onClick = null
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = AppChrome.screenHorizontalPadding)
                        ) {
                            items(state.topPicks, key = { it.id }) { character ->
                                TopPickCard(
                                    character = character,
                                    onClick = {
                                        scope.launch {
                                            viewModel.ensureConversation(character.id)
                                                .onSuccess(onOpenConversation)
                                                .onFailure { snackbarHostState.showSnackbar(it.message ?: "Couldn't open chat.") }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (state.recentChats.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionHeader(
                            title = "Continue",
                            modifier = Modifier.padding(horizontal = AppChrome.screenHorizontalPadding),
                            onClick = onOpenChats
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = AppChrome.screenHorizontalPadding)
                        ) {
                            items(state.recentChats, key = { "continue_${it.id}" }) { chat ->
                                ContinueNode(
                                    chat = chat,
                                    onClick = { onOpenConversation(chat.id) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader(
                        title = "Recommended",
                        modifier = Modifier.padding(horizontal = AppChrome.screenHorizontalPadding),
                        onClick = null
                    )
                }
            }

            val errorMessage = state.errorMessage
            if (errorMessage != null && state.recommendedFeed.isEmpty()) {
                item {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = AppChrome.screenHorizontalPadding)
                    )
                }
            }

            items(state.recommendedFeed.chunked(2), key = { it.first().id }) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppChrome.screenHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(AppChrome.gridSpacing)
                ) {
                    CharacterSummaryCard(
                        character = pair[0],
                        modifier = Modifier.weight(1f)
                    ) {
                        scope.launch {
                            viewModel.ensureConversation(pair[0].id)
                                .onSuccess(onOpenConversation)
                                .onFailure { snackbarHostState.showSnackbar(it.message ?: "Couldn't open chat.") }
                        }
                    }
                    if (pair.size > 1) {
                        CharacterSummaryCard(
                            character = pair[1],
                            modifier = Modifier.weight(1f)
                        ) {
                            scope.launch {
                                viewModel.ensureConversation(pair[1].id)
                                    .onSuccess(onOpenConversation)
                                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "Couldn't open chat.") }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            if (state.recommendedCursor != null) {
                item {
                    SecondaryButton(
                        text = if (state.isFeedLoading) "Loading..." else "Load More",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AppChrome.screenHorizontalPadding),
                        enabled = !state.isFeedLoading,
                        onClick = viewModel::loadMore
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)?) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        if (onClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "View all",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CreateStoryNode(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                icon = AppIcons.create,
                contentDescription = "Create Character",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 32.dp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StoryNode(chat: ConversationSummary, onClick: () -> Unit) {
    val showRedOutline = chat.unreadCount > 0
    val showGreyOutline = !showRedOutline && chat.hasUnreadBadge
    
    val outlineColor = when {
        showRedOutline -> MaterialTheme.colorScheme.error
        showGreyOutline -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    
    val outlineWidth = if (showRedOutline || showGreyOutline) 2.5.dp else 0.dp
    val gapWidth = if (showRedOutline || showGreyOutline) 3.dp else 0.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp + outlineWidth * 2 + gapWidth * 2)
                .clip(CircleShape)
                .border(outlineWidth, outlineColor, CircleShape)
                .padding(outlineWidth + gapWidth)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            CircleAvatar(
                name = chat.characterName,
                avatarUrl = chat.characterAvatarUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = chat.characterName,
            style = MaterialTheme.typography.labelMedium,
            color = if (showRedOutline) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (showRedOutline) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TopPickCard(character: CharacterSummary, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier
            .width(230.dp)
            .height(240.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
            ) {
                CharacterPortrait(
                    name = character.name,
                    avatarUrl = character.avatarUrl,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(
                        topStart = 22.dp,
                        topEnd = 22.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
                    .padding(14.dp)
            ) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        lineHeight = 26.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = character.tagline,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.94f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ContinueNode(chat: ConversationSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CharacterPortrait(
            name = chat.characterName,
            avatarUrl = chat.characterAvatarUrl,
            modifier = Modifier.size(76.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = chat.characterName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = AppIcons.activity,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatRelativeTimeWords(chat.lastMessageAt ?: chat.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
