package com.example.aichat.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.SecondaryButton
import com.example.aichat.core.model.CharacterSummary
import com.example.aichat.core.ui.CharacterSummaryCard
import com.example.aichat.core.util.authorLabel
import com.example.aichat.feature.chatlist.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class HomeUiState(
    val feed: List<CharacterSummary> = emptyList(),
    val feedCursor: String? = null,
    val isFeedLoading: Boolean = true,
    val errorMessage: String? = null,
    val currentUserId: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val homeRepository: HomeRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {
    private val userId = authRepository.sessionState.value.profile?.userId.orEmpty()
    private val _uiState = MutableStateFlow(HomeUiState(currentUserId = userId))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    init {
        refreshFeed()
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isFeedLoading = true, errorMessage = null)
            runCatching { homeRepository.loadFeed(cursor = null) }
                .onSuccess { page ->
                    _uiState.value = _uiState.value.copy(
                        feed = page.items,
                        feedCursor = page.nextCursor,
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
            runCatching { homeRepository.loadFeed(cursor = state.feedCursor) }
                .onSuccess { page ->
                    _uiState.value = state.copy(feed = state.feed + page.items, feedCursor = page.nextCursor)
                }
                .onFailure { _events.emit("Couldn't load more characters.") }
        }
    }

    suspend fun ensureConversation(characterId: String): Result<String> {
        return conversationRepository.ensureConversation(userId, characterId)
    }
}

@Composable
fun HomeRoute(
    paddingValues: PaddingValues,
    onOpenSearch: () -> Unit,
    onOpenConversation: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val searchInteractionSource = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SnackbarHost(hostState = snackbarHostState)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = paddingValues.calculateTopPadding() + 16.dp,
                end = 20.dp,
                bottom = paddingValues.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Discover", style = MaterialTheme.typography.headlineMedium)
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        modifier = Modifier
                            .size(26.dp)
                            .clickable(
                                interactionSource = searchInteractionSource,
                                indication = null,
                                onClick = onOpenSearch
                            ),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (state.errorMessage != null && state.feed.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = state.errorMessage ?: "Failed to load the feed.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            items(state.feed, key = { it.id }) { character ->
                CharacterSummaryCard(
                    character = character,
                    authorLabel = authorLabel(character.ownerUserId, state.currentUserId),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    scope.launch {
                        viewModel.ensureConversation(character.id)
                            .onSuccess(onOpenConversation)
                            .onFailure { snackbarHostState.showSnackbar(it.message ?: "Couldn't open chat.") }
                    }
                }
            }
            if (state.feedCursor != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SecondaryButton(
                        text = if (state.isFeedLoading) "Loading..." else "Load More",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isFeedLoading,
                        onClick = viewModel::loadMore
                    )
                }
            }
        }
    }
}
