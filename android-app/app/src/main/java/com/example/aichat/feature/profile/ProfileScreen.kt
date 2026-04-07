package com.example.aichat.feature.profile

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.example.aichat.core.design.SelectionButton
import com.example.aichat.core.model.CharacterSummary
import com.example.aichat.core.ui.AppChrome
import com.example.aichat.core.ui.CharacterSummaryCard
import com.example.aichat.core.ui.ScreenBackgroundBox
import com.example.aichat.core.ui.MainPageHeader
import com.example.aichat.core.ui.screenContentPadding
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
    LIKED,
    RECENT,
    INTERACTED
}

data class ProfileUiState(
    val displayName: String = "",
    val avatarUrl: String? = null,
    val bio: String? = null,
    val userId: String = "",
    val owned: List<CharacterSummary> = emptyList(),
    val liked: List<CharacterSummary> = emptyList(),
    val recent: List<CharacterSummary> = emptyList(),
    val interacted: List<CharacterSummary> = emptyList()
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    authRepository: AuthRepository,
    profileRepository: ProfileRepository,
    private val characterRepository: CharacterRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {
    private val userId = authRepository.sessionState.value.profile?.userId.orEmpty()

    val uiState: StateFlow<ProfileUiState> = combine(
        profileRepository.profile,
        characterRepository.observeOwnedCharacters(userId),
        characterRepository.observeLikedCharacters(),
        conversationRepository.observeConversations(userId)
    ) { profile, owned, liked, conversations ->
        // For Recent and Interacted, we need to map conversations back to CharacterSummary.
        // We'll use the ones we already have in owned/liked or fetch missing ones if possible.
        // For simplicity in this UI refactor, we'll build the list from available data.
        val charMap = (owned + liked).associateBy { it.id }.toMutableMap()
        
        val recentChars = conversations
            .sortedByDescending { it.lastMessageAt ?: it.updatedAt }
            .mapNotNull { conv ->
                charMap[conv.characterId] ?: CharacterSummary(
                    id = conv.characterId,
                    ownerUserId = "",
                    name = conv.characterName,
                    tagline = "",
                    bio = "",
                    systemPrompt = "",
                    visibility = com.example.aichat.core.model.CharacterVisibility.PUBLIC,
                    avatarUrl = conv.characterAvatarUrl,
                    publicChatCount = 0,
                    likeCount = 0,
                    likedByMe = false,
                    lastActiveAt = conv.lastMessageAt ?: conv.updatedAt,
                    createdAt = conv.startedAt,
                    updatedAt = conv.updatedAt
                )
            }
            .distinctBy { it.id }

        ProfileUiState(
            displayName = profile?.displayName.orEmpty(),
            avatarUrl = profile?.avatarUrl,
            bio = profile?.bio,
            userId = userId,
            owned = owned,
            liked = liked,
            recent = recentChars,
            interacted = recentChars // Temporary proxy until message count is implemented
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState()
    )

    init {
        viewModelScope.launch {
            characterRepository.refreshOwnedCharacters()
            characterRepository.refreshLikedCharacters()
        }
    }

    suspend fun ensureConversation(characterId: String): Result<String> {
        return conversationRepository.ensureConversation(userId, characterId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRoute(
    paddingValues: PaddingValues,
    onOpenSearch: () -> Unit = {},
    onOpenActivity: () -> Unit,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.Start) {
                                Text(text = "${state.owned.size}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                Text(text = "characters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.Start) {
                                Text(text = "0", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                Text(text = "followers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.Start) {
                                Text(text = "0", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                                Text(text = "following", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            state.bio?.takeIf { it.isNotBlank() }?.let { bio ->
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppChrome.compactControlGap)
                ) {
                    IconPillButton(
                        text = "Edit Profile",
                        onClick = onOpenEditProfile,
                        modifier = Modifier.weight(1f)
                    )
                    IconPillButton(
                        text = "Share Profile",
                        onClick = { /* No functionality yet */ },
                        modifier = Modifier.weight(1f)
                    )
                    IconCircleButton(onClick = onOpenSettings) {
                        AppIcon(AppIcons.settings, contentDescription = "Settings")
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.LocalIndication provides null) {
                SecondaryTabRow(
                    selectedTabIndex = ProfileSection.entries.indexOf(section),
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    indicator = {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(ProfileSection.entries.indexOf(section)),
                            color = MaterialTheme.colorScheme.primary,
                            height = 2.dp
                        )
                    },
                    divider = {}
                ) {
                    ProfileSection.entries.forEach { s ->
                        val isSelected = section == s
                        val icon = when (s) {
                            ProfileSection.OWNED -> if (isSelected) AppIcons.createdFilled else AppIcons.created
                            ProfileSection.LIKED -> if (isSelected) AppIcons.likedFilled else AppIcons.liked
                            ProfileSection.RECENT -> AppIcons.activity // No bold version available
                            ProfileSection.INTERACTED -> if (isSelected) AppIcons.chats else AppIcons.chatsOutline
                        }
                        Tab(
                            selected = isSelected,
                            onClick = { section = s },
                            icon = {
                                AppIcon(
                                    icon = icon,
                                    contentDescription = s.name,
                                    size = 24.dp,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            },
                            selectedContentColor = MaterialTheme.colorScheme.onBackground,
                            unselectedContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
                }
            }
            val characters = when (section) {
                ProfileSection.OWNED -> state.owned
                ProfileSection.LIKED -> state.liked
                ProfileSection.RECENT -> state.recent
                ProfileSection.INTERACTED -> state.interacted
            }
            if (characters.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = when (section) {
                            ProfileSection.OWNED -> "No Characters Yet."
                            ProfileSection.LIKED -> "No Liked Characters Yet."
                            ProfileSection.RECENT -> "No Recent Activity."
                            ProfileSection.INTERACTED -> "No Interacted Characters."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(characters, key = { it.id }) { character ->
                CharacterSummaryCard(
                    character = character,
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
