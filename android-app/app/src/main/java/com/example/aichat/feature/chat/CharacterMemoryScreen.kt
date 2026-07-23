package com.example.aichat.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.aichat.core.design.AppTextField
import com.example.aichat.core.design.PrimaryButton
import com.example.aichat.core.ui.AppBackButton
import com.example.aichat.core.ui.AppChrome
import com.example.aichat.core.ui.ScreenBackgroundBox
import com.example.aichat.core.ui.ShimmerBox
import com.example.aichat.core.ui.ShimmerTextLine
import com.example.aichat.core.ui.pageContentFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CharacterMemoryUiState(
    val shortTerm: String = "",
    val longTerm: String = "",
    val originalShortTerm: String = "",
    val originalLongTerm: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false
) {
    val hasChanges: Boolean
        get() = shortTerm != originalShortTerm || longTerm != originalLongTerm
}

@HiltViewModel
class CharacterMemoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CharacterMemoryRepository
) : ViewModel() {
    private val conversationId: String = checkNotNull(savedStateHandle["conversationId"])
    private val mutableState = MutableStateFlow(CharacterMemoryUiState())
    private val mutableEvents = MutableSharedFlow<String>()
    val uiState = mutableState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CharacterMemoryUiState()
    )
    val events = mutableEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.observeMemory(conversationId).collect { memory ->
                if (memory != null && !mutableState.value.hasChanges) {
                    mutableState.update {
                        it.copy(
                            shortTerm = memory.shortTerm,
                            longTerm = memory.longTerm,
                            originalShortTerm = memory.shortTerm,
                            originalLongTerm = memory.longTerm,
                            isLoading = false
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.refresh(conversationId)
                .onFailure { error ->
                    if (mutableState.value.isLoading) {
                        mutableState.update { it.copy(isLoading = false) }
                    }
                    mutableEvents.emit(error.message ?: "Couldn't load character memory.")
                }
        }
    }

    fun updateShortTerm(value: String) {
        mutableState.update {
            it.copy(shortTerm = value.take(SHORT_TERM_MEMORY_MAX_LENGTH))
        }
    }

    fun updateLongTerm(value: String) {
        mutableState.update {
            it.copy(longTerm = value.take(LONG_TERM_MEMORY_MAX_LENGTH))
        }
    }

    fun save() {
        val state = mutableState.value
        if (state.isSaving || !state.hasChanges) return
        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true) }
            repository.save(conversationId, state.shortTerm, state.longTerm)
                .onSuccess {
                    mutableState.update {
                        it.copy(
                            shortTerm = state.shortTerm.trim(),
                            longTerm = state.longTerm.trim(),
                            originalShortTerm = state.shortTerm.trim(),
                            originalLongTerm = state.longTerm.trim(),
                            isSaving = false
                        )
                    }
                    mutableEvents.emit("Character memory saved.")
                }
                .onFailure { error ->
                    mutableState.update { it.copy(isSaving = false) }
                    mutableEvents.emit(error.message ?: "Couldn't save character memory.")
                }
        }
    }
}

@Composable
fun CharacterMemoryRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    viewModel: CharacterMemoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    CharacterMemoryScreen(
        paddingValues = paddingValues,
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onShortTermChanged = viewModel::updateShortTerm,
        onLongTermChanged = viewModel::updateLongTerm,
        onSave = viewModel::save
    )
}

@Composable
private fun CharacterMemoryScreen(
    paddingValues: PaddingValues,
    state: CharacterMemoryUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onShortTermChanged: (String) -> Unit,
    onLongTermChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    ScreenBackgroundBox(snackbarHostState = snackbarHostState) {
        Column(
            modifier = Modifier.pageContentFrame(
                paddingValues = paddingValues,
                imeAware = true
            ),
            verticalArrangement = Arrangement.spacedBy(AppChrome.sectionSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppChrome.compactControlGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppBackButton(onClick = onBack)
                Text(
                    text = "Character Memory",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (state.isLoading) {
                CharacterMemoryPlaceholder()
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(AppChrome.sectionSpacing)
                ) {
                    MemoryEditor(
                        title = "Short-Term Memory",
                        description = "The current situation and recent events that still matter. This is refreshed as the roleplay progresses.",
                        value = state.shortTerm,
                        limit = SHORT_TERM_MEMORY_MAX_LENGTH,
                        minLines = 6,
                        maxLines = 12,
                        placeholder = "Current scene, active goals, relationships, and unresolved events.",
                        onValueChanged = onShortTermChanged
                    )
                    MemoryEditor(
                        title = "Long-Term Memory",
                        description = "Major events and durable facts the character should remember permanently.",
                        value = state.longTerm,
                        limit = LONG_TERM_MEMORY_MAX_LENGTH,
                        minLines = 10,
                        maxLines = 18,
                        placeholder = "Important events, promises, discoveries, relationship changes, and lasting details.",
                        onValueChanged = onLongTermChanged
                    )
                }

                PrimaryButton(
                    text = if (state.isSaving) "Saving..." else "Save Memory",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.hasChanges && !state.isSaving,
                    onClick = onSave
                )
            }
        }
    }
}

@Composable
private fun MemoryEditor(
    title: String,
    description: String,
    value: String,
    limit: Int,
    minLines: Int,
    maxLines: Int,
    placeholder: String,
    onValueChanged: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppTextField(
            value = value,
            onValueChange = { onValueChanged(it.take(limit)) },
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            maxLines = maxLines,
            shape = RoundedCornerShape(24.dp)
        )
        Text(
            text = "${value.length}/$limit",
            modifier = Modifier.align(Alignment.End),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CharacterMemoryPlaceholder() {
    Column(verticalArrangement = Arrangement.spacedBy(AppChrome.sectionSpacing)) {
        repeat(2) { index ->
            ShimmerTextLine(width = if (index == 0) 176.dp else 168.dp, height = 22.dp)
            ShimmerTextLine(width = 260.dp, height = 14.dp)
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (index == 0) 160.dp else 220.dp),
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}
