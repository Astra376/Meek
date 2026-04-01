package com.example.aichat.feature.chatlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.AppCard
import com.example.aichat.core.design.CircleAvatar
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
        contentPadding = PaddingValues(
            start = 20.dp,
            top = paddingValues.calculateTopPadding() + 12.dp,
            end = 20.dp,
            bottom = paddingValues.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Chats", style = MaterialTheme.typography.headlineMedium)
        }
        if (conversations.isEmpty()) {
            item {
                AppCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("No conversations yet", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Start a conversation from Home or Studio and it will appear here.",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        items(conversations, key = { it.id }) { conversation ->
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenConversation(conversation.id) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    CircleAvatar(
                        name = conversation.characterName,
                        avatarUrl = conversation.characterAvatarUrl,
                        modifier = Modifier.size(62.dp)
                    )
                    Text(
                        text = conversation.characterName,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}
