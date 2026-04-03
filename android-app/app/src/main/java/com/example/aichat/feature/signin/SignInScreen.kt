package com.example.aichat.feature.signin

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.AppCard
import com.example.aichat.core.design.PrimaryButton
import com.example.aichat.core.ui.AppChrome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SignInUiState(
    val isSigningIn: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = SignInUiState(isSigningIn = true)
            val result = authRepository.signIn(activity)
            _uiState.value = SignInUiState(
                isSigningIn = false,
                errorMessage = result.exceptionOrNull()?.message
            )
        }
    }
}

@Composable
fun SignInRoute(viewModel: SignInViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppChrome.screenBottomPadding)
        ) {
            Column(
                modifier = Modifier.padding(AppChrome.screenBottomPadding),
                verticalArrangement = Arrangement.spacedBy(AppChrome.screenTopPadding)
            ) {
                Text(
                    text = "Character Chat",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = "Google sign-in is the only entry point. Configure the Worker URL and Google web client ID, then sign in here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PrimaryButton(
                    text = if (state.isSigningIn) "Signing in..." else "Continue with Google",
                    enabled = !state.isSigningIn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    viewModel.signIn(activity)
                }
                state.errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}
