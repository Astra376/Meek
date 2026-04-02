package com.example.aichat.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.design.AppTextField
import com.example.aichat.core.design.PrimaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {
    val displayName: StateFlow<String> = profileRepository.profile
        .map { it?.displayName.orEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    fun save(name: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            profileRepository.updateDisplayName(name)
                .onSuccess { onSaved() }
        }
    }
}

@Composable
fun EditProfileRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val currentName by viewModel.displayName.collectAsStateWithLifecycle()
    var editedName by remember(currentName) { mutableStateOf(currentName) }
    val focusRequester = remember { FocusRequester() }
    val hasChanged = editedName.trim() != currentName.trim() && editedName.isNotBlank()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = paddingValues.calculateTopPadding())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text("Edit Profile", style = MaterialTheme.typography.headlineMedium)
        }

        AppTextField(
            value = editedName,
            onValueChange = { editedName = it },
            placeholder = "Username",
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true
        )

        Spacer(modifier = Modifier.weight(1f))

        if (hasChanged) {
            PrimaryButton(
                text = "Save Changes",
                modifier = Modifier.fillMaxWidth()
            ) {
                viewModel.save(editedName, onSaved = onBack)
            }
        }
    }
}
