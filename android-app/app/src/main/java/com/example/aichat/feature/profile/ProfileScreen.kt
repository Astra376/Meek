package com.example.aichat.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.AppCard
import com.example.aichat.core.design.CharacterPortrait
import com.example.aichat.core.design.PrimaryButton
import com.example.aichat.core.design.SecondaryButton
import com.example.aichat.core.design.StatRow
import com.example.aichat.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val stats: ProfileStats = ProfileStats(0, 0, 0)
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val userId = authRepository.sessionState.value.profile?.userId.orEmpty()
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<ProfileUiState> = combine(
        profileRepository.profile,
        settingsRepository.themeMode,
        profileRepository.stats(userId)
    ) { profile, themeMode, stats ->
        ProfileUiState(
            displayName = profile?.displayName.orEmpty(),
            email = profile?.email.orEmpty(),
            avatarUrl = profile?.avatarUrl,
            themeMode = themeMode,
            stats = stats
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState()
    )

    fun saveDisplayName(name: String) {
        viewModelScope.launch {
            profileRepository.updateDisplayName(name)
                .onSuccess { _events.emit("Profile updated.") }
                .onFailure { _events.emit(it.message ?: "Couldn't update profile.") }
        }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(themeMode)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}

@Composable
fun ProfileRoute(
    paddingValues: PaddingValues,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var nameField by remember(state.displayName) { mutableStateOf(state.displayName) }

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
                Text("Profile & settings", style = MaterialTheme.typography.headlineMedium)
            }
            item {
                AppCard {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CharacterPortrait(
                            name = state.displayName.ifBlank { "User" },
                            avatarUrl = state.avatarUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        )
                        Text(text = state.email, style = MaterialTheme.typography.bodyLarge)
                        OutlinedTextField(
                            value = nameField,
                            onValueChange = { nameField = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Display name") }
                        )
                        PrimaryButton(
                            text = "Save display name",
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            viewModel.saveDisplayName(nameField)
                        }
                    }
                }
            }
            item {
                AppCard {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Theme", style = MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                SecondaryButton(
                                    text = mode.name.lowercase().replaceFirstChar(Char::uppercase),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    viewModel.setThemeMode(mode)
                                }
                            }
                        }
                        Text(
                            text = "Current: ${state.themeMode.name.lowercase().replaceFirstChar(Char::uppercase)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                AppCard {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Activity", style = MaterialTheme.typography.titleLarge)
                        StatRow(left = "Owned characters", right = state.stats.ownedCount.toString())
                        StatRow(left = "Liked characters", right = state.stats.likedCount.toString())
                        StatRow(left = "Conversations", right = state.stats.conversationCount.toString())
                    }
                }
            }
            item {
                SecondaryButton(
                    text = "Sign out",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = viewModel::signOut
                )
            }
        }
    }
}
