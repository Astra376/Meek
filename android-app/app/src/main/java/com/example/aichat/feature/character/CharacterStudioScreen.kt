package com.example.aichat.feature.character

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.design.AppIcon
import com.example.aichat.core.design.AppIcons
import com.example.aichat.core.design.AppTextField
import com.example.aichat.core.design.CharacterPortrait
import com.example.aichat.core.design.IconPillButton
import com.example.aichat.core.design.PrimaryButton
import com.example.aichat.core.design.SelectionButton
import com.example.aichat.core.ui.AppChrome
import com.example.aichat.core.ui.ScreenBackgroundBox
import com.example.aichat.core.ui.MainPageHeader
import com.example.aichat.core.ui.screenContentPadding
import com.example.aichat.core.model.CharacterDraft
import com.example.aichat.core.model.CharacterVisibility
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class CharacterStudioUiState(
    val draft: CharacterDraft = CharacterDraft(),
    val isSaving: Boolean = false,
    val isGeneratingPortrait: Boolean = false
)

@HiltViewModel
class CharacterStudioViewModel @Inject constructor(
    private val characterRepository: CharacterRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CharacterStudioUiState())
    val uiState: StateFlow<CharacterStudioUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    fun updateDraft(transform: (CharacterDraft) -> CharacterDraft) {
        _uiState.value = _uiState.value.copy(draft = transform(_uiState.value.draft))
    }

    fun generatePortrait() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingPortrait = true)
            val seedSource = _uiState.value.draft.name.ifBlank { _uiState.value.draft.tagline }
            characterRepository.generatePortrait(seedSource)
                .onSuccess { portrait ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingPortrait = false,
                        draft = _uiState.value.draft.copy(avatarUrl = portrait)
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isGeneratingPortrait = false)
                    _events.emit(it.message ?: "Portrait generation failed.")
                }
        }
    }

    fun saveCharacter() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            characterRepository.saveCharacter(_uiState.value.draft)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        draft = CharacterDraft()
                    )
                    _events.emit("Character saved.")
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _events.emit(it.message ?: "Failed to save character.")
                }
        }
    }
}

@Composable
fun CharacterStudioRoute(
    paddingValues: PaddingValues,
    onOpenSearch: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    viewModel: CharacterStudioViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    ScreenBackgroundBox(
        snackbarHostState = snackbarHostState,
        clearFocusOnTap = true
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = screenContentPadding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(AppChrome.sectionSpacing)
        ) {

            item {
                Column(verticalArrangement = Arrangement.spacedBy(AppChrome.gridSpacing)) {
                    CharacterPortrait(
                        name = state.draft.name.ifBlank { "New Character" },
                        avatarUrl = state.draft.avatarUrl,
                        modifier = Modifier
                            .size(184.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AppChrome.compactControlGap)) {
                        PrimaryButton(
                            text = if (state.isGeneratingPortrait) "Generating..." else "Generate",
                            modifier = Modifier.weight(1f),
                            enabled = !state.isGeneratingPortrait,
                            leadingIcon = {
                                AppIcon(AppIcons.sparkle, contentDescription = null)
                            },
                            onClick = viewModel::generatePortrait
                        )
                        IconPillButton(
                            text = "Clear",
                            onClick = { viewModel.updateDraft { CharacterDraft() } },
                            leadingIcon = {
                                AppIcon(AppIcons.clear, contentDescription = "Clear")
                            }
                        )
                    }
                    AppTextField(
                        value = state.draft.name,
                        onValueChange = { value -> viewModel.updateDraft { it.copy(name = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Name",
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                    AppTextField(
                        value = state.draft.tagline,
                        onValueChange = { value -> viewModel.updateDraft { it.copy(tagline = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Tagline",
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                    AppTextField(
                        value = state.draft.bio,
                        onValueChange = { value -> viewModel.updateDraft { it.copy(bio = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Bio",
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(24.dp)
                    )
                    AppTextField(
                        value = state.draft.systemPrompt,
                        onValueChange = { value -> viewModel.updateDraft { it.copy(systemPrompt = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "System Prompt",
                        minLines = 4,
                        maxLines = 8,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(AppChrome.compactControlGap)) {
                        SelectionButton(
                            text = "Public",
                            selected = state.draft.visibility == CharacterVisibility.PUBLIC,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                AppIcon(AppIcons.public, contentDescription = null)
                            },
                            onClick = { viewModel.updateDraft { it.copy(visibility = CharacterVisibility.PUBLIC) } }
                        )
                        SelectionButton(
                            text = "Private",
                            selected = state.draft.visibility == CharacterVisibility.PRIVATE,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                AppIcon(AppIcons.lock, contentDescription = null)
                            },
                            onClick = { viewModel.updateDraft { it.copy(visibility = CharacterVisibility.PRIVATE) } }
                        )
                    }
                    PrimaryButton(
                        text = if (state.isSaving) "Creating..." else "Create",
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            AppIcon(AppIcons.createAction, contentDescription = null)
                        },
                        onClick = viewModel::saveCharacter
                    )
                }
            }
        }
    }
}
