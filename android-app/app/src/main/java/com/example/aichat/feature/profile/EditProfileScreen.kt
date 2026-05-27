package com.example.aichat.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.aichat.core.model.UserProfile
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {
    val profile: StateFlow<UserProfile?> = profileRepository.profile
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun save(name: String, bio: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            profileRepository.updateProfile(name, bio)
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
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val currentName = profile?.displayName.orEmpty()
    val currentBio = profile?.bio.orEmpty()
    
    var editedName by remember(currentName) { mutableStateOf(currentName) }
    var editedBio by remember(currentBio) { mutableStateOf(currentBio) }
    
    val hasChanged = (editedName.trim() != currentName.trim() && editedName.isNotBlank()) ||
                     (editedBio.trim() != currentBio.trim())

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

            if (profile == null) {
                EditProfilePlaceholder()
            } else {
                Text("Username", style = MaterialTheme.typography.titleLarge)

                AppTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    placeholder = "Username",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Bio", style = MaterialTheme.typography.titleLarge)

                AppTextField(
                    value = editedBio,
                    onValueChange = { editedBio = it },
                    placeholder = "Tell others about yourself",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 3
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (hasChanged) {
                PrimaryButton(
                    text = "Save Changes",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    viewModel.save(editedName, editedBio, onSaved = onBack)
                }
            }
        }
    }
}

@Composable
private fun EditProfilePlaceholder() {
    Column(verticalArrangement = Arrangement.spacedBy(AppChrome.sectionSpacing)) {
        ShimmerTextLine(width = 104.dp, height = 22.dp)
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(24.dp)
        )
        ShimmerTextLine(width = 46.dp, height = 22.dp)
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            shape = RoundedCornerShape(24.dp)
        )
    }
}
