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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.AppIcon
import com.example.aichat.core.design.AppIcons
import com.example.aichat.core.design.CircleAvatar
import com.example.aichat.core.model.ConversationSummary
import com.example.aichat.core.ui.AppChrome
import com.example.aichat.core.ui.ScreenBackgroundBox
import com.example.aichat.core.ui.screenContentPadding
import com.example.aichat.feature.chatlist.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NewHomeUiState(
    val recentChats: List<ConversationSummary> = emptyList(),
    val totalUnreadCount: Int = 0
)

@HiltViewModel
class NewHomeViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewHomeUiState())
    val uiState: StateFlow<NewHomeUiState> = _uiState.asStateFlow()

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
                _uiState.value = NewHomeUiState(
                    recentChats = sortedChats,
                    totalUnreadCount = unreadCount
                )
            }
        }
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

    ScreenBackgroundBox(
        snackbarHostState = snackbarHostState
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(screenContentPadding(paddingValues))
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenChats() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Unread (${state.totalUnreadCount})",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View all chats",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
        }
    }
}

@Composable
fun CreateStoryNode(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                icon = AppIcons.create,
                contentDescription = "Create Character",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 28.dp
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
        showRedOutline -> MaterialTheme.colorScheme.error // Or a custom red accent
        showGreyOutline -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    
    val outlineWidth = if (showRedOutline || showGreyOutline) 2.5.dp else 0.dp
    val gapWidth = if (showRedOutline || showGreyOutline) 3.dp else 0.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp + outlineWidth * 2 + gapWidth * 2)
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
