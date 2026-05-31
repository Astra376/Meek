package com.example.aichat.feature.character

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.design.AppIcon
import com.example.aichat.core.design.AppIcons
import com.example.aichat.core.design.AppTextField
import com.example.aichat.core.design.CharacterPortrait
import com.example.aichat.core.design.PrimaryButton
import com.example.aichat.core.design.SecondaryButton
import com.example.aichat.core.design.SelectionButton
import com.example.aichat.core.model.CharacterDraft
import com.example.aichat.core.model.CharacterVisibility
import com.example.aichat.core.ui.AppBackButton
import com.example.aichat.core.ui.AppChrome
import com.example.aichat.core.ui.ScreenBackgroundBox
import com.example.aichat.feature.chatlist.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class CharacterCreateStep {
    NAME,
    APPEARANCE,
    GREETING,
    VISIBILITY
}

data class CharacterStudioUiState(
    val draft: CharacterDraft = CharacterDraft(),
    val step: CharacterCreateStep = CharacterCreateStep.NAME,
    val portraitOptions: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val isGeneratingPortraits: Boolean = false,
    val isGeneratingGreeting: Boolean = false
)

@HiltViewModel
class CharacterStudioViewModel @Inject constructor(
    private val characterRepository: CharacterRepository,
    private val conversationRepository: ConversationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CharacterStudioUiState())
    val uiState: StateFlow<CharacterStudioUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    fun updateDraft(transform: (CharacterDraft) -> CharacterDraft) {
        _uiState.value = _uiState.value.copy(draft = transform(_uiState.value.draft))
    }

    fun goBack(onExit: () -> Unit) {
        _uiState.value = when (_uiState.value.step) {
            CharacterCreateStep.NAME -> {
                onExit()
                return
            }
            CharacterCreateStep.APPEARANCE -> _uiState.value.copy(step = CharacterCreateStep.NAME)
            CharacterCreateStep.GREETING -> _uiState.value.copy(step = CharacterCreateStep.APPEARANCE)
            CharacterCreateStep.VISIBILITY -> _uiState.value.copy(step = CharacterCreateStep.GREETING)
        }
    }

    fun goNext() {
        val current = _uiState.value
        val nextStep = when (current.step) {
            CharacterCreateStep.NAME -> CharacterCreateStep.APPEARANCE
            CharacterCreateStep.APPEARANCE -> CharacterCreateStep.GREETING
            CharacterCreateStep.GREETING -> CharacterCreateStep.VISIBILITY
            CharacterCreateStep.VISIBILITY -> CharacterCreateStep.VISIBILITY
        }
        _uiState.value = current.copy(step = nextStep)
    }

    fun generatePortraits() {
        val state = _uiState.value
        val prompt = state.draft.bio.ifBlank { state.draft.name }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingPortraits = true)
            runCatching {
                (1..4).map { index ->
                    async {
                        val variantPrompt = """
                            $prompt
                            Portrait option $index. Square full-bleed character image that fills the whole frame.
                            No circular avatar crop, no round frame, no border, no blank background outside the character art.
                        """.trimIndent()
                        characterRepository.generatePortrait(variantPrompt).getOrThrow()
                    }
                }.awaitAll()
            }.onSuccess { portraits ->
                _uiState.value = _uiState.value.copy(
                    isGeneratingPortraits = false,
                    portraitOptions = portraits,
                    draft = _uiState.value.draft.copy(avatarUrl = null)
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isGeneratingPortraits = false)
                _events.emit(it.message ?: "Portrait generation failed.")
            }
        }
    }

    fun selectPortrait(url: String) {
        updateDraft { it.copy(avatarUrl = url) }
    }

    fun uploadPortrait(uri: Uri) {
        updateDraft { it.copy(avatarUrl = uri.toString()) }
    }

    fun generateGreeting() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingGreeting = true)
            characterRepository.generateGreeting(state.draft.name, state.draft.bio)
                .onSuccess { greeting ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingGreeting = false,
                        draft = _uiState.value.draft.copy(tagline = greeting)
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isGeneratingGreeting = false)
                    _events.emit(it.message ?: "Greeting generation failed.")
                }
        }
    }

    fun createCharacter(ownerUserId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val draft = _uiState.value.draft
            val finalDraft = draft.copy(
                systemPrompt = characterRepository.buildSystemPrompt(draft)
            )
            characterRepository.saveCharacter(finalDraft)
                .onSuccess { characterId ->
                    conversationRepository.ensureConversation(ownerUserId, characterId)
                        .onSuccess { conversationId ->
                            _uiState.value = CharacterStudioUiState()
                            onCreated(conversationId)
                        }
                        .onFailure {
                            _uiState.value = _uiState.value.copy(isSaving = false)
                            _events.emit(it.message ?: "Failed to open chat.")
                        }
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
    ownerUserId: String = "",
    onBack: () -> Unit = {},
    onCreated: (String) -> Unit = {},
    viewModel: CharacterStudioViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    ScreenBackgroundBox(
        snackbarHostState = snackbarHostState,
        clearFocusOnTap = state.step != CharacterCreateStep.NAME
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            CharacterCreateHeader(
                onBack = { viewModel.goBack(onBack) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(
                        start = AppChrome.screenHorizontalPadding,
                        top = AppChrome.compactHeaderVerticalPadding
                    )
            )

            CharacterCreateStepContent(
                state = state,
                onNameChanged = { value -> viewModel.updateDraft { it.copy(name = value) } },
                onAppearanceChanged = { value -> viewModel.updateDraft { it.copy(bio = value) } },
                onGreetingChanged = { value -> viewModel.updateDraft { it.copy(tagline = value) } },
                onVisibilityChanged = { value -> viewModel.updateDraft { it.copy(visibility = value) } },
                onGeneratePortraits = viewModel::generatePortraits,
                onSelectPortrait = viewModel::selectPortrait,
                onUploadPortrait = viewModel::uploadPortrait,
                onGenerateGreeting = viewModel::generateGreeting,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = AppChrome.screenHorizontalPadding,
                        top = 104.dp,
                        end = AppChrome.screenHorizontalPadding,
                        bottom = 98.dp
                    )
            )

            CharacterCreateBottomAction(
                state = state,
                onNext = {
                    if (state.step == CharacterCreateStep.VISIBILITY) {
                        viewModel.createCharacter(ownerUserId, onCreated)
                    } else {
                        viewModel.goNext()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
            )
        }
    }
}

@Composable
private fun CharacterCreateHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppBackButton(onClick = onBack)
        Spacer(modifier = Modifier.width(AppChrome.compactControlGap))
        Text(
            text = "Create character",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CharacterCreateStepContent(
    state: CharacterStudioUiState,
    onNameChanged: (String) -> Unit,
    onAppearanceChanged: (String) -> Unit,
    onGreetingChanged: (String) -> Unit,
    onVisibilityChanged: (CharacterVisibility) -> Unit,
    onGeneratePortraits: () -> Unit,
    onSelectPortrait: (String) -> Unit,
    onUploadPortrait: (Uri) -> Unit,
    onGenerateGreeting: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state.step) {
        CharacterCreateStep.NAME -> NameStep(
            name = state.draft.name,
            onNameChanged = onNameChanged,
            modifier = modifier
        )
        CharacterCreateStep.APPEARANCE -> AppearanceStep(
            name = state.draft.name,
            description = state.draft.bio,
            selectedAvatarUrl = state.draft.avatarUrl,
            portraitOptions = state.portraitOptions,
            isGenerating = state.isGeneratingPortraits,
            onDescriptionChanged = onAppearanceChanged,
            onGenerate = onGeneratePortraits,
            onSelectPortrait = onSelectPortrait,
            onUploadPortrait = onUploadPortrait,
            modifier = modifier
        )
        CharacterCreateStep.GREETING -> GreetingStep(
            greeting = state.draft.tagline,
            isGenerating = state.isGeneratingGreeting,
            onGreetingChanged = onGreetingChanged,
            onGenerateGreeting = onGenerateGreeting,
            modifier = modifier
        )
        CharacterCreateStep.VISIBILITY -> VisibilityStep(
            visibility = state.draft.visibility,
            onVisibilityChanged = onVisibilityChanged,
            modifier = modifier
        )
    }
}

@Composable
private fun NameStep(
    name: String,
    onNameChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        StepTitle("What's your character's name?")
        AppTextField(
            value = name,
            onValueChange = onNameChanged,
            placeholder = "",
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            shape = RoundedCornerShape(999.dp)
        )
    }
}

@Composable
private fun AppearanceStep(
    name: String,
    description: String,
    selectedAvatarUrl: String?,
    portraitOptions: List<String>,
    isGenerating: Boolean,
    onDescriptionChanged: (String) -> Unit,
    onGenerate: () -> Unit,
    onSelectPortrait: (String) -> Unit,
    onUploadPortrait: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onUploadPortrait)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        StepTitle("What do they look like?")
        if (portraitOptions.isNotEmpty() || isGenerating) {
            PortraitOptionGrid(
                options = portraitOptions,
                selectedAvatarUrl = selectedAvatarUrl,
                isGenerating = isGenerating,
                onSelectPortrait = onSelectPortrait
            )
        }
        AppTextField(
            value = description,
            onValueChange = onDescriptionChanged,
            placeholder = "Describe what ${name.ifBlank { "your character" }} looks like, then tap Generate.",
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            minLines = 4,
            maxLines = 7,
            shape = RoundedCornerShape(24.dp)
        )
        PrimaryButton(
            text = if (isGenerating) "Generating..." else "Generate",
            enabled = !isGenerating && description.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { AppIcon(AppIcons.sparkle, contentDescription = null) },
            onClick = onGenerate
        )
        Text(
            text = "or",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SecondaryButton(
            text = "Upload an image",
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { AppIcon(AppIcons.createAction, contentDescription = null) },
            onClick = { launcher.launch("image/*") }
        )
    }
}

@Composable
private fun PortraitOptionGrid(
    options: List<String>,
    selectedAvatarUrl: String?,
    isGenerating: Boolean,
    onSelectPortrait: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppChrome.gridSpacing)) {
        repeat(2) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(AppChrome.gridSpacing)) {
                repeat(2) { column ->
                    val index = row * 2 + column
                    val url = options.getOrNull(index)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    ) {
                        when {
                            url != null -> {
                                CharacterPortrait(
                                    name = "Portrait ${index + 1}",
                                    avatarUrl = url,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { onSelectPortrait(url) }
                                        .border(
                                            width = if (url == selectedAvatarUrl) 3.dp else 0.dp,
                                            color = if (url == selectedAvatarUrl) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                Color.Transparent
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                )
                            }
                            isGenerating -> {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingStep(
    greeting: String,
    isGenerating: Boolean,
    onGreetingChanged: (String) -> Unit,
    onGenerateGreeting: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Greeting",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box {
                Column {
                    AppTextField(
                        value = greeting,
                        onValueChange = onGreetingChanged,
                        enabled = !isGenerating,
                        placeholder = "Example: Took you long enough. I've been waiting.",
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 7,
                        shape = RoundedCornerShape(
                            topStart = 24.dp,
                            topEnd = 24.dp,
                            bottomEnd = 0.dp,
                            bottomStart = 0.dp
                        )
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isGenerating, onClick = onGenerateGreeting)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(
                            AppIcons.sparkle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Write for me",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (isGenerating) {
                    Box(
                        modifier = Modifier
                            .matchParentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun VisibilityStep(
    visibility: CharacterVisibility,
    onVisibilityChanged: (CharacterVisibility) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StepTitle("Visibility")
        VisibilityOption(
            title = "Public",
            body = "Anyone can find, view, and chat.",
            selected = visibility == CharacterVisibility.PUBLIC,
            onClick = { onVisibilityChanged(CharacterVisibility.PUBLIC) }
        )
        VisibilityOption(
            title = "Unlisted",
            body = "Only people with a link can view and chat.",
            selected = visibility == CharacterVisibility.UNLISTED,
            onClick = { onVisibilityChanged(CharacterVisibility.UNLISTED) }
        )
        VisibilityOption(
            title = "Private",
            body = "Only you can view and chat.",
            selected = visibility == CharacterVisibility.PRIVATE,
            onClick = { onVisibilityChanged(CharacterVisibility.PRIVATE) }
        )
    }
}

@Composable
private fun VisibilityOption(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    SelectionButton(
        text = title,
        selected = selected,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    )
    Text(
        text = body,
        modifier = Modifier.padding(start = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun CharacterCreateBottomAction(
    state: CharacterStudioUiState,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = when (state.step) {
        CharacterCreateStep.NAME -> state.draft.name.isNotBlank()
        CharacterCreateStep.APPEARANCE -> !state.draft.avatarUrl.isNullOrBlank()
        CharacterCreateStep.GREETING -> state.draft.tagline.isNotBlank() && !state.isGeneratingGreeting
        CharacterCreateStep.VISIBILITY -> !state.isSaving
    } && !state.isGeneratingPortraits

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = AppChrome.screenHorizontalPadding,
                        vertical = AppChrome.screenBottomPadding
                    )
            ) {
                PrimaryButton(
                    text = when {
                        state.isSaving -> "Creating..."
                        state.step == CharacterCreateStep.VISIBILITY -> "Create"
                        else -> "Next"
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = if (state.step == CharacterCreateStep.VISIBILITY) {
                        { AppIcon(AppIcons.createAction, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = onNext
                )
            }
        }
    }
}
