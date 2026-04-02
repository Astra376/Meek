package com.example.aichat.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.AppTextField
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<CharacterSummary> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val currentUserId: String = ""
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val homeRepository: HomeRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {
    private val userId = authRepository.sessionState.value.profile?.userId.orEmpty()
    private val _uiState = MutableStateFlow(SearchUiState(currentUserId = userId))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()
    private val queryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryFlow.debounce(220).collectLatest { query ->
                if (query.isBlank()) {
                    _uiState.value = _uiState.value.copy(results = emptyList(), nextCursor = null, isLoading = false)
                } else {
                    search(reset = true)
                }
            }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
        queryFlow.value = value
    }

    fun loadMore() {
        viewModelScope.launch {
            search(reset = false)
        }
    }

    suspend fun ensureConversation(characterId: String): Result<String> {
        return conversationRepository.ensureConversation(userId, characterId)
    }

    private suspend fun search(reset: Boolean) {
        val state = _uiState.value
        if (state.query.isBlank()) return
        _uiState.value = state.copy(isLoading = true)
        runCatching {
            homeRepository.search(
                query = state.query,
                cursor = if (reset) null else state.nextCursor
            )
        }.onSuccess { page ->
            _uiState.value = _uiState.value.copy(
                results = if (reset) page.items else state.results + page.items,
                nextCursor = page.nextCursor,
                isLoading = false
            )
        }.onFailure {
            _uiState.value = _uiState.value.copy(isLoading = false)
            _events.emit("Search failed.")
        }
    }
}

@Composable
fun SearchRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = paddingValues.calculateTopPadding() + 16.dp,
                end = 20.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SnackbarHost(hostState = snackbarHostState)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    AppTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = "Search",
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        shape = RoundedCornerShape(999.dp),
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                        }
                    )
                }
            }
            if (state.query.isNotBlank() && !state.isLoading && state.results.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "No Characters Found.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.results, key = { it.id }) { character ->
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
            if (state.nextCursor != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SecondaryButton(
                        text = if (state.isLoading) "Loading..." else "Load More",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading,
                        onClick = viewModel::loadMore
                    )
                }
            }
        }
    }
}
