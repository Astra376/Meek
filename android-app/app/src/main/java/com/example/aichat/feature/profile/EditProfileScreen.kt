package com.example.aichat.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.design.AppTextField
import com.example.aichat.core.design.PrimaryButton
import com.example.aichat.core.ui.AppBackButton
import com.example.aichat.core.ui.AppChrome
import com.example.aichat.core.ui.ScreenBackgroundBox
import com.example.aichat.core.ui.pageContentFrame
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
    val hasChanged = editedName.trim() != currentName.trim() && editedName.isNotBlank()

    ScreenBackgroundBox(clearFocusOnTap = true) {
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
                Text("Edit Profile", style = MaterialTheme.typography.headlineMedium)
            }

            Text("Username", style = MaterialTheme.typography.titleLarge)

            AppTextField(
                value = editedName,
                onValueChange = { editedName = it },
                placeholder = "Username",
                modifier = Modifier.fillMaxWidth(),
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
}
