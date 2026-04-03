package com.example.aichat.feature.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.CharacterPortrait
import com.example.aichat.core.ui.AppChrome
import com.example.aichat.core.ui.screenContentPadding
import com.example.aichat.core.model.ConversationSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ChatListViewModel @Inject constructor(
    authRepository: AuthRepository,
    conversationRepository: ConversationRepository
) : ViewModel() {
    private val userId = authRepository.sessionState.value.profile?.userId.orEmpty()
    val conversations: StateFlow<List<ConversationSummary>> =
        conversationRepository.observeConversations(userId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            conversationRepository.refreshConversations(userId)
        }
    }
}

@Composable
fun ChatListRoute(
    paddingValues: PaddingValues,
    onOpenConversation: (String) -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenContentPadding(paddingValues),
        verticalArrangement = Arrangement.spacedBy(AppChrome.sectionSpacing)
    ) {
        item {
            Text("Chats", style = MaterialTheme.typography.headlineMedium)
        }
        if (conversations.isEmpty()) {
            item {
                Column(modifier = Modifier.padding(top = AppChrome.bottomBarVerticalPadding)) {
                    Text("No Conversations Yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Start a conversation from Home or Create and it will appear here.",
                        modifier = Modifier.padding(top = AppChrome.compactHeaderVerticalPadding),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(conversations, key = { it.id }) { conversation ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clickable { onOpenConversation(conversation.id) },
                horizontalArrangement = Arrangement.spacedBy(AppChrome.listRowGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CharacterPortrait(
                    name = conversation.characterName,
                    avatarUrl = conversation.characterAvatarUrl,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                )
                Text(
                    text = conversation.characterName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 21.sp
                    )
                )
            }
        }
    }
}
