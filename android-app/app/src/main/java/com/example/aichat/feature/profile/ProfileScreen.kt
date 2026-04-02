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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.AppIcon
import com.example.aichat.core.design.AppIcons
import com.example.aichat.core.design.CircleAvatar
import com.example.aichat.core.design.IconCircleButton
import com.example.aichat.core.design.IconPillButton
import com.example.aichat.core.model.CharacterSummary
import com.example.aichat.core.ui.AppChrome
import com.example.aichat.core.ui.CharacterSummaryCard
import com.example.aichat.core.ui.ScreenBackgroundBox
import com.example.aichat.core.ui.screenContentPadding
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

    ScreenBackgroundBox(snackbarHostState = snackbarHostState) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = screenContentPadding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(AppChrome.sectionSpacing),
            horizontalArrangement = Arrangement.spacedBy(AppChrome.gridSpacing)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Profile", style = MaterialTheme.typography.headlineMedium)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppChrome.screenTopPadding)
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
                        verticalArrangement = Arrangement.spacedBy(AppChrome.gridSpacing)
                    ) {
                        Text(
                            text = state.displayName.ifBlank { "User" },
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(AppChrome.compactControlGap)) {
                            IconPillButton(
                                text = "Edit Profile",
                                onClick = onOpenEditProfile,
                                leadingIcon = {
                                    AppIcon(AppIcons.edit, contentDescription = null)
                                }
                            )
                            IconCircleButton(onClick = onOpenSettings) {
                                AppIcon(AppIcons.settings, contentDescription = "Settings")
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                TabRow(
                    selectedTabIndex = section.ordinal,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    divider = {},
                    indicator = { tabPositions ->
                        Box(
                            modifier = Modifier
                                .tabIndicatorOffset(tabPositions[section.ordinal])
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.onSurface)
                        )
                    }
                ) {
                    Tab(
                        selected = section == ProfileSection.OWNED,
                        onClick = { section = ProfileSection.OWNED },
                        selectedContentColor = MaterialTheme.colorScheme.onSurface,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = null,
                        icon = {
                            AppIcon(
                                icon = if (section == ProfileSection.OWNED) {
                                    AppIcons.ownedFilled
                                } else {
                                    AppIcons.owned
                                },
                                contentDescription = "Your Characters"
                            )
                        }
                    )
                    Tab(
                        selected = section == ProfileSection.LIKED,
                        onClick = { section = ProfileSection.LIKED },
                        selectedContentColor = MaterialTheme.colorScheme.onSurface,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = null,
                        icon = {
                            AppIcon(
                                icon = if (section == ProfileSection.LIKED) {
                                    AppIcons.likedFilled
                                } else {
                                    AppIcons.liked
                                },
                                contentDescription = "Liked Characters"
                            )
                        }
                    )
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
