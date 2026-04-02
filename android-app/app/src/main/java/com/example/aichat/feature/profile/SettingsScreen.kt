package com.example.aichat.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.aichat.core.auth.AuthRepository
import com.example.aichat.core.design.SecondaryButton
import com.example.aichat.core.design.SelectionDot
import com.example.aichat.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeMode.SYSTEM
    )

    fun setTheme(themeMode: ThemeMode) {
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
fun SettingsRoute(
    paddingValues: PaddingValues,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = paddingValues.calculateTopPadding())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        Text("App Theme", style = MaterialTheme.typography.titleLarge)
        ThemeOptionRow(
            text = "System",
            selected = themeMode == ThemeMode.SYSTEM,
            icon = { Icon(Icons.Outlined.BrightnessAuto, contentDescription = null) },
            onClick = { viewModel.setTheme(ThemeMode.SYSTEM) }
        )
        ThemeOptionRow(
            text = "Dark",
            selected = themeMode == ThemeMode.DARK,
            icon = { Icon(Icons.Outlined.DarkMode, contentDescription = null) },
            onClick = { viewModel.setTheme(ThemeMode.DARK) }
        )
        ThemeOptionRow(
            text = "Light",
            selected = themeMode == ThemeMode.LIGHT,
            icon = { Icon(Icons.Outlined.LightMode, contentDescription = null) },
            onClick = { viewModel.setTheme(ThemeMode.LIGHT) }
        )
        Spacer(modifier = Modifier.weight(1f))
        SecondaryButton(
            text = "Log Out",
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(Icons.Outlined.Logout, contentDescription = null)
            },
            onClick = viewModel::signOut
        )
    }
}

@Composable
private fun ThemeOptionRow(
    text: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        SelectionDot(selected = selected)
    }
}
