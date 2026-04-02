package com.example.aichat.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.CircleAvatar
import com.example.aichat.core.design.IconCircleButton
import com.example.aichat.core.design.IconPillButton
import com.example.aichat.core.model.CharacterSummary
import com.example.aichat.core.ui.CharacterSummaryCard
import com.example.aichat.core.util.authorLabel
import com.example.aichat.feature.character.CharacterRepository
import com.example.aichat.feature.chatlist.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ProfileSection {
    OWNED,
    LIKED
}

data class ProfileUiState(
    val displayName: String = "",
    val avatarUrl: String? = null,
    val userId: String = "",
    val owned: List<CharacterSummary> = emptyList(),
    val liked: List<CharacterSummary> = emptyList()
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    characterRepository: CharacterRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {
    private val userId = authRepository.sessionState.value.profile?.userId.orEmpty()

    val uiState: StateFlow<ProfileUiState> = combine(
        profileRepository.profile,
        characterRepository.observeOwnedCharacters(userId),
        characterRepository.observeLikedCharacters()
    ) { profile, owned, liked ->
        ProfileUiState(
            displayName = profile?.displayName.orEmpty(),
            avatarUrl = profile?.avatarUrl,
            userId = userId,
            owned = owned,
            liked = liked
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState()
    )

    suspend fun ensureConversation(characterId: String): Result<String> {
        return conversationRepository.ensureConversation(userId, characterId)
    }
}

@Composable
fun ProfileRoute(
    paddingValues: PaddingValues,
    onOpenConversation: (String) -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(ProfileSection.OWNED) }

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
                Text("Profile", style = MaterialTheme.typography.headlineMedium)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircleAvatar(
                        name = state.displayName.ifBlank { "User" },
                        avatarUrl = state.avatarUrl,
                        modifier = Modifier
                            .size(104.dp)
                            .aspectRatio(1f)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = state.displayName.ifBlank { "User" },
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconPillButton(
                                text = "Edit Profile",
                                onClick = onOpenEditProfile,
                                leadingIcon = {
                                    Icon(Icons.Outlined.Edit, contentDescription = null)
                                }
                            )
                            IconCircleButton(onClick = onOpenSettings) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconCircleButton(
                        selected = section == ProfileSection.OWNED,
                        onClick = { section = ProfileSection.OWNED }
                    ) {
                        Icon(Icons.Outlined.GridView, contentDescription = "Your Characters")
                    }
                    IconCircleButton(
                        selected = section == ProfileSection.LIKED,
                        onClick = { section = ProfileSection.LIKED }
                    ) {
                        Icon(Icons.Outlined.FavoriteBorder, contentDescription = "Liked Characters")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            val characters = if (section == ProfileSection.OWNED) state.owned else state.liked
            if (characters.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = if (section == ProfileSection.OWNED) {
                            "No Characters Yet."
                        } else {
                            "No Liked Characters Yet."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(characters, key = { it.id }) { character ->
                CharacterSummaryCard(
                    character = character,
                    authorLabel = authorLabel(character.ownerUserId, state.userId),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    scope.launch {
                        viewModel.ensureConversation(character.id)
                            .onSuccess(onOpenConversation)
                            .onFailure { snackbarHostState.showSnackbar(it.message ?: "Couldn't open chat.") }
                    }
                }
            }
        }
    }
}
