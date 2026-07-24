package com.example.aichat.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.model.CharacterSummary
import com.example.aichat.core.model.PublicProfile
import com.example.aichat.core.network.userFacingMessage
import com.example.aichat.core.design.PrimaryButton
import com.example.aichat.core.ui.AppBackButton
import com.example.aichat.core.ui.AppChrome
import com.example.aichat.core.ui.CharacterSummaryCard
import com.example.aichat.core.ui.CharacterSummaryCardPlaceholder
import com.example.aichat.core.ui.ProfileCountStat
import com.example.aichat.core.ui.ProfileHeader
import com.example.aichat.core.ui.ProfileHeaderPlaceholder
import com.example.aichat.core.ui.ScreenBackgroundBox
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CreatorProfileUiState(
    val profile: PublicProfile? = null,
    val characters: List<CharacterSummary> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false
)

@HiltViewModel
class CreatorProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CreatorProfileRepository
) : ViewModel() {
    private val userId: String = checkNotNull(savedStateHandle["userId"])
    private val _uiState = MutableStateFlow(CreatorProfileUiState())
    val uiState: StateFlow<CreatorProfileUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = CreatorProfileUiState(isLoading = true)
            val profileResult = runCatching { repository.getProfile(userId) }
            val charactersResult = runCatching { repository.getCharacters(userId) }
            val profile = profileResult.getOrNull()
            val characters = charactersResult.getOrNull()
            _uiState.value = CreatorProfileUiState(
                profile = profile,
                characters = characters?.items.orEmpty(),
                nextCursor = characters?.nextCursor,
                isLoading = false
            )
            profileResult.exceptionOrNull()?.let {
                _events.emit(it.userFacingMessage("Couldn't load creator profile."))
            }
            charactersResult.exceptionOrNull()?.let {
                _events.emit(it.userFacingMessage("Couldn't load creator characters."))
            }
        }
    }

    fun loadMore() {
        val cursor = _uiState.value.nextCursor ?: return
        if (_uiState.value.isLoadingMore) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            runCatching { repository.getCharacters(userId, cursor) }
                .onSuccess { page ->
                    _uiState.value = _uiState.value.copy(
                        characters = (_uiState.value.characters + page.items).distinctBy { it.id },
                        nextCursor = page.nextCursor,
                        isLoadingMore = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                    _events.emit(it.userFacingMessage("Couldn't load more characters."))
                }
        }
    }
}

@Composable
fun CreatorProfileRoute(
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    onError: ((String) -> Unit)? = null,
    viewModel: CreatorProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, onError) {
        viewModel.events.collect { message ->
            if (onError != null) {
                onError(message)
            } else {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    ScreenBackgroundBox(snackbarHostState = snackbarHostState.takeIf { onError == null }) {
        CreatorProfileContent(
            state = state,
            onBack = onBack,
            onOpenCharacter = onOpenCharacter,
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::refresh
        )
    }
}

@Composable
internal fun CreatorProfileContent(
    state: CreatorProfileUiState,
    onBack: () -> Unit,
    onOpenCharacter: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = AppChrome.screenHorizontalPadding,
            end = AppChrome.screenHorizontalPadding,
            bottom = AppChrome.screenBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(AppChrome.sectionSpacing),
        horizontalArrangement = Arrangement.spacedBy(AppChrome.gridSpacing)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppChrome.compactControlGap)
            ) {
                AppBackButton(onClick = onBack)
                Text(
                    text = "Creator Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            val profile = state.profile
            if (profile != null) {
                ProfileHeader(
                    name = profile.displayName,
                    avatarUrl = profile.avatarUrl,
                    stats = listOf(
                        ProfileCountStat(profile.characterCount, "characters"),
                        ProfileCountStat(profile.interactionCount, "interactions"),
                        ProfileCountStat(profile.likeCount, "likes")
                    )
                )
            } else if (state.isLoading) {
                ProfileHeaderPlaceholder()
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppChrome.compactControlGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Creator profile unavailable.",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PrimaryButton(
                        text = "Retry",
                        onClick = onRetry
                    )
                }
            }
        }

        state.profile?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Characters",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (state.isLoading && state.characters.isEmpty()) {
            items(6) {
                CharacterSummaryCardPlaceholder(modifier = Modifier.fillMaxWidth())
            }
        } else if (state.characters.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "No public characters yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(state.characters, key = { it.id }) { character ->
            CharacterSummaryCard(
                character = character,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenCharacter(character.id) }
            )
        }

        if (state.nextCursor != null && !state.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LaunchedEffect(state.nextCursor) {
                    onLoadMore()
                }
            }
        }

        if (state.isLoadingMore) {
            items(2) {
                CharacterSummaryCardPlaceholder(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
