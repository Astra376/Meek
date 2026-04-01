package com.example.aichat.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.AppCard
import com.example.aichat.core.design.PrimaryButton
import com.example.aichat.core.design.SecondaryButton
import com.example.aichat.core.model.CharacterSummary
import com.example.aichat.core.model.CursorPage
import com.example.aichat.core.ui.CharacterSummaryCard
import com.example.aichat.feature.character.CharacterRepository
import com.example.aichat.feature.chatlist.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

data class HomeUiState(
    val feed: List<CharacterSummary> = emptyList(),
    val feedCursor: String? = null,
    val query: String = "",
    val searchResults: List<CharacterSummary> = emptyList(),
    val searchCursor: String? = null,
    val isFeedLoading: Boolean = true,
    val isSearchLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val homeRepository: HomeRepository,
    private val characterRepository: CharacterRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {
    private val userId = authRepository.sessionState.value.profile?.userId.orEmpty()
    private val _uiState = MutableStateFlow(HomeUiState())
    private val searchQuery = MutableStateFlow("")
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    init {
        refreshFeed()
        viewModelScope.launch {
            searchQuery
                .debounce(300)
                .collectLatest { query ->
                    if (query.isBlank()) return@collectLatest
                    loadSearchPage(reset = true)
                }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
        searchQuery.value = value
        if (value.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), searchCursor = null, isSearchLoading = false)
        }
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
        if (state.query.isBlank()) {
            viewModelScope.launch {
                val page = homeRepository.loadFeed(cursor = state.feedCursor)
                _uiState.value = state.copy(feed = state.feed + page.items, feedCursor = page.nextCursor)
            }
        } else {
            viewModelScope.launch {
                loadSearchPage(reset = false)
            }
        }
    }

    fun toggleLike(characterId: String) {
        viewModelScope.launch {
            characterRepository.toggleLike(characterId)
                .onFailure { _events.emit(it.message ?: "Couldn't update like.") }
            if (_uiState.value.query.isBlank()) refreshFeed() else loadSearchPage(reset = true)
        }
    }

    suspend fun ensureConversation(characterId: String): Result<String> {
        return conversationRepository.ensureConversation(userId, characterId)
    }

    private suspend fun loadSearchPage(reset: Boolean) {
        val state = _uiState.value
        if (state.query.isBlank()) return
        _uiState.value = state.copy(isSearchLoading = true)
        runCatching {
            homeRepository.search(
                query = state.query,
                cursor = if (reset) null else state.searchCursor
            )
        }.onSuccess { page ->
            _uiState.value = _uiState.value.copy(
                searchResults = if (reset) page.items else state.searchResults + page.items,
                searchCursor = page.nextCursor,
                isSearchLoading = false
            )
        }.onFailure {
            _uiState.value = _uiState.value.copy(isSearchLoading = false)
            _events.emit("Search failed.")
        }
    }
}

@Composable
fun HomeRoute(
    paddingValues: PaddingValues,
    onOpenConversation: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = paddingValues.calculateTopPadding() + 12.dp,
                end = 20.dp,
                bottom = paddingValues.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Discover characters", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "Browse public personalities, search by vibe, then jump straight into a conversation.",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search characters") },
                    placeholder = { Text("Name, tagline, or description") },
                    singleLine = true
                )
            }

            if (state.errorMessage != null && state.feed.isEmpty()) {
                item {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            val characters = if (state.query.isBlank()) state.feed else state.searchResults
            items(characters, key = { it.id }) { character ->
                CharacterSummaryCard(character = character) {
                    SecondaryButton(
                        text = if (character.likedByMe) "Liked" else "Like",
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.toggleLike(character.id)
                    }
                    PrimaryButton(
                        text = "Chat",
                        modifier = Modifier.weight(1f)
                    ) {
                        scope.launch {
                            viewModel.ensureConversation(character.id)
                                .onSuccess(onOpenConversation)
                                .onFailure { snackbarHostState.showSnackbar(it.message ?: "Couldn't open chat.") }
                        }
                    }
                }
            }

            val hasMore = if (state.query.isBlank()) state.feedCursor != null else state.searchCursor != null
            if (hasMore) {
                item {
                    SecondaryButton(
                        text = if (state.query.isBlank() && state.isFeedLoading || state.isSearchLoading) "Loading..." else "Load more",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !(state.isFeedLoading || state.isSearchLoading),
                        onClick = viewModel::loadMore
                    )
                }
            }
        }
    }
}
