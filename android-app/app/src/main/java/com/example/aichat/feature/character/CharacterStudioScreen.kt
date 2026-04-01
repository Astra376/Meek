package com.example.aichat.feature.character

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.CharacterPortrait
import com.example.aichat.core.design.PrimaryButton
import com.example.aichat.core.design.SecondaryButton
import com.example.aichat.core.design.SelectionButton
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
    authRepository: AuthRepository,
    private val characterRepository: CharacterRepository
) : ViewModel() {
    private val userId = authRepository.sessionState.value.profile?.userId.orEmpty()
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
            characterRepository.saveCharacter(userId, _uiState.value.draft)
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
    viewModel: CharacterStudioViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = paddingValues.calculateTopPadding() + 12.dp,
                end = 20.dp,
                bottom = paddingValues.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Create character", style = MaterialTheme.typography.headlineMedium)
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CharacterPortrait(
                        name = state.draft.name.ifBlank { "New character" },
                        avatarUrl = state.draft.avatarUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(
                            text = if (state.isGeneratingPortrait) "Generating..." else "Generate portrait",
                            modifier = Modifier.weight(1f),
                            enabled = !state.isGeneratingPortrait,
                            onClick = viewModel::generatePortrait
                        )
                        SecondaryButton(
                            text = "Clear",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.updateDraft { CharacterDraft() } }
                        )
                    }
                    OutlinedTextField(
                        value = state.draft.name,
                        onValueChange = { value -> viewModel.updateDraft { it.copy(name = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") }
                    )
                    OutlinedTextField(
                        value = state.draft.tagline,
                        onValueChange = { value -> viewModel.updateDraft { it.copy(tagline = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tagline") }
                    )
                    OutlinedTextField(
                        value = state.draft.description,
                        onValueChange = { value -> viewModel.updateDraft { it.copy(description = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        label = { Text("Description") }
                    )
                    OutlinedTextField(
                        value = state.draft.systemPrompt,
                        onValueChange = { value -> viewModel.updateDraft { it.copy(systemPrompt = value) } },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        label = { Text("System prompt") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectionButton(
                            text = "Public",
                            selected = state.draft.visibility == CharacterVisibility.PUBLIC,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.updateDraft { it.copy(visibility = CharacterVisibility.PUBLIC) } }
                        )
                        SelectionButton(
                            text = "Private",
                            selected = state.draft.visibility == CharacterVisibility.PRIVATE,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.updateDraft { it.copy(visibility = CharacterVisibility.PRIVATE) } }
                        )
                    }
                    PrimaryButton(
                        text = if (state.isSaving) "Saving..." else "Create character",
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::saveCharacter
                    )
                }
            }
        }
    }
}
